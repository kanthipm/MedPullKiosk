import {
  Area,
  ComposedChart,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

interface Props {
  actual: { day: number; v: number }[]
  expected: { day: number; lo: number; mid: number; hi: number }[]
  changePointDay?: number | null
}

/** Functional recovery index vs the expected band for this procedure.
 *  One accent series (actual, blue); the expectation is a quiet gray band. */
export default function TrajectoryChart({ actual, expected, changePointDay }: Props) {
  const byDay = new Map<number, Record<string, number | number[] | null>>()
  for (const e of expected) {
    byDay.set(e.day, { day: e.day, band: [e.lo, e.hi], mid: e.mid, actual: null })
  }
  for (const a of actual) {
    const row = byDay.get(a.day) ?? { day: a.day, band: null, mid: null, actual: null }
    row.actual = a.v
    byDay.set(a.day, row)
  }
  const data = [...byDay.values()].sort((a, b) => (a.day as number) - (b.day as number))

  return (
    <div>
      <div className="mb-2 flex items-center gap-4 text-xs text-muted">
        <span className="inline-flex items-center gap-1.5">
          <span className="h-0.5 w-4 rounded bg-blue-600" /> Actual
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-2.5 w-4 rounded-sm bg-slate-200" /> Expected range
        </span>
      </div>
      <div className="h-40">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 8 }}>
            <XAxis
              dataKey="day"
              tickLine={false}
              axisLine={false}
              tick={{ fontSize: 11, fill: '#8a93a8' }}
              tickFormatter={(d: number) => `Day ${d}`}
              interval="preserveStartEnd"
            />
            <YAxis hide domain={[0, (dataMax: number) => Math.min(1.1, dataMax * 1.2)]} />
            <Area
              dataKey="band"
              stroke="none"
              fill="#e4e9f4"
              fillOpacity={0.6}
              isAnimationActive={false}
              connectNulls
            />
            <Line
              dataKey="mid"
              stroke="#8a93a8"
              strokeWidth={1}
              strokeDasharray="4 3"
              dot={false}
              isAnimationActive={false}
              connectNulls
            />
            <Line
              dataKey="actual"
              stroke="#2f80ed"
              strokeWidth={2}
              dot={false}
              isAnimationActive={false}
              connectNulls
            />
            {changePointDay != null && (
              <ReferenceLine
                x={changePointDay}
                stroke="#e07b00"
                strokeDasharray="4 3"
                label={{ value: 'Change', position: 'top', fontSize: 10, fill: '#e07b00' }}
              />
            )}
            <Tooltip
              cursor={{ stroke: '#c8d0e0', strokeWidth: 1 }}
              content={({ active, payload, label }) => {
                if (!active || !payload?.length) return null
                const row = payload[0].payload as { actual: number | null; mid: number | null }
                return (
                  <div className="rounded-lg border border-line bg-white px-2.5 py-1.5 text-xs shadow-card">
                    <div className="text-faint">Post-op day {label}</div>
                    {row.actual != null && (
                      <div className="font-medium tabular-nums text-body">
                        Actual {Math.round(row.actual * 100)}%
                      </div>
                    )}
                    {row.mid != null && (
                      <div className="tabular-nums text-muted">
                        Expected {Math.round(row.mid * 100)}%
                      </div>
                    )}
                  </div>
                )
              }}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
