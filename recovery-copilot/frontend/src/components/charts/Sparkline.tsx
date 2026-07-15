import { Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip } from 'recharts'
import { shortDate } from '../../lib/format'

/** 14-day micro-trend inside a metric card. Neutral ink — status lives in the
 *  card's pill, never in the line color. */
export default function Sparkline({
  series,
  baseline,
  unit,
}: {
  series: { date: string; value: number }[]
  baseline?: number | null
  unit: string
}) {
  if (series.length === 0) {
    return <div className="h-10 rounded bg-soft" />
  }
  return (
    <div className="h-10">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={series} margin={{ top: 4, right: 2, bottom: 2, left: 2 }}>
          {baseline != null && (
            <ReferenceLine y={baseline} stroke="#e4e9f4" strokeDasharray="3 3" />
          )}
          <Line
            type="monotone"
            dataKey="value"
            stroke="#6b7793"
            strokeWidth={1.5}
            dot={false}
            isAnimationActive={false}
          />
          <Tooltip
            cursor={{ stroke: '#c8d0e0', strokeWidth: 1 }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null
              const p = payload[0].payload as { date: string; value: number }
              return (
                <div className="rounded-lg border border-line bg-white px-2 py-1 text-xs shadow-card">
                  <span className="text-faint">{shortDate(p.date)}</span>{' '}
                  <span className="font-medium tabular-nums text-body">
                    {Math.round(p.value * 10) / 10} {unit}
                  </span>
                </div>
              )
            }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
