import { ChevronRight, CircleCheck, CircleDashed } from 'lucide-react'
import { useState } from 'react'
import type { CSSProperties } from 'react'
import { usePatientMetrics } from '../../api/queries'
import type { MetricInsight, PatientMetrics } from '../../api/types'
import AdherenceDots from '../../components/AdherenceDots'
import Sparkline from '../../components/charts/Sparkline'
import TrajectoryChart from '../../components/charts/TrajectoryChart'
import SectionCard from '../../components/SectionCard'
import { RefreshOverlay, SkeletonCard } from '../../components/Skeleton'
import { METRIC_STATUS } from '../../lib/risk'

/** Demo report-card: left status accent, uppercase status pill, finding,
 *  suggested next step, and coverage detail. */
function MetricCard({ m, index }: { m: MetricInsight; index: number }) {
  const s = METRIC_STATUS[m.status]
  return (
    <div
      style={{ '--rise-delay': `${index * 40}ms` } as CSSProperties}
      className="rise relative overflow-hidden rounded-[18px] border border-ink/[.04] bg-white p-4 pl-5 shadow-row"
    >
      <span aria-hidden className={`absolute inset-y-0 left-0 w-1 ${s.spine}`} />
      <div className="flex items-center justify-between gap-2">
        <span className="text-[13.5px] font-black tracking-tight text-ink">{m.name}</span>
        <span
          className={`shrink-0 whitespace-nowrap rounded-full px-2.5 py-1 text-[10px] font-black uppercase leading-none tracking-[.03em] ${s.pill}`}
        >
          {m.status === 'flag' || m.status === 'watch' ? m.status_text : s.label}
        </span>
      </div>
      <div className="mt-2">
        <Sparkline series={m.series} baseline={m.baseline_mean} unit={m.unit} />
      </div>
      <p className="mt-2 text-[12px] font-semibold leading-relaxed text-body">{m.finding}</p>
      {m.next_step && (
        <p className="mt-2 flex items-start gap-1.5 text-[12px] font-extrabold text-oxy">
          <span aria-hidden>→</span> {m.next_step}
        </p>
      )}
      <p className="mt-1.5 text-[10.5px] font-bold text-faint">
        {m.coverage_text}
        {m.guarded && ' · guarded phrasing'}
      </p>
    </div>
  )
}

function SignalsBody({
  data,
  rtm,
  refreshing,
}: {
  data: PatientMetrics
  rtm: { days_with_data: number; window_days: number; qualifies: boolean; enrolled: boolean }
  refreshing: boolean
}) {
  return (
    <div className="space-y-4">
      <SectionCard title="Recovery trajectory">
        <RefreshOverlay show={refreshing} />
        <TrajectoryChart
          actual={data.trajectory.actual}
          expected={data.trajectory.expected}
          changePointDay={data.trajectory.change_point_day}
        />
      </SectionCard>

      {data.composite.drivers.length > 0 && (
        <SectionCard
          title="Multi-signal deviation"
          aside={
            <span
              className={`rounded-full px-2.5 py-1 text-[10.5px] font-black uppercase leading-none tracking-[.03em] ${
                data.composite.level === 'high'
                  ? 'bg-risk-high-bg text-risk-high'
                  : data.composite.level === 'elevated'
                    ? 'bg-risk-med-bg text-risk-med'
                    : 'bg-risk-low-bg text-risk-low'
              }`}
            >
              {data.composite.level === 'high'
                ? 'High'
                : data.composite.level === 'elevated'
                  ? 'Elevated'
                  : 'Normal'}
            </span>
          }
        >
          <RefreshOverlay show={refreshing} />
          <div className="space-y-2.5">
            {data.composite.drivers.map((d) => (
              <div key={d.metric_type} className="flex items-center gap-3">
                <span className="w-36 shrink-0 text-xs font-bold text-muted">{d.label}</span>
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-line">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-oxy to-oxy-light transition-[width] duration-500 ease-out"
                    style={{ width: `${Math.round(d.contribution * 100)}%` }}
                  />
                </div>
                <span className="w-9 shrink-0 text-right text-[11px] font-bold tabular-nums text-faint">
                  {Math.round(d.contribution * 100)}%
                </span>
              </div>
            ))}
          </div>
          <p className="mt-3 text-[10.5px] font-bold text-faint">
            Contribution of each signal to the deviation index — for review, not a diagnosis.
          </p>
        </SectionCard>
      )}

      <div className="grid gap-3.5 sm:grid-cols-2">
        {data.metrics.map((m, i) => (
          <MetricCard key={m.metric_key} m={m} index={i} />
        ))}
      </div>

      <SectionCard title="Adherence & monitoring">
        <RefreshOverlay show={refreshing} />
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="mb-1.5 text-[11px] font-bold text-faint">
              Task adherence · last 14 days ({data.adherence.verified} verified,{' '}
              {data.adherence.self_attested} self-attested)
            </p>
            <AdherenceDots days={data.adherence.days} rate={data.adherence.rate} />
          </div>
          <div className="flex items-center gap-2 text-[13px] font-bold text-body">
            {rtm.qualifies ? (
              <CircleCheck size={16} className="text-risk-low" />
            ) : (
              <CircleDashed size={16} className="text-faint" />
            )}
            {rtm.enrolled ? (
              <>
                <span className="tabular-nums">
                  {rtm.days_with_data} of {rtm.window_days} monitoring days
                </span>
                <span className="text-[11px] font-bold text-faint">
                  {rtm.qualifies ? '· meets 16-day threshold' : '· below 16-day threshold'}
                </span>
              </>
            ) : (
              <span className="text-[12px] font-bold text-faint">
                Monitoring begins at RTM enrollment
              </span>
            )}
          </div>
        </div>
      </SectionCard>
    </div>
  )
}

/** Supporting evidence behind one toggle: collapsed to a summary line, or
 *  open with everything in full detail — trajectory, deviation drivers,
 *  metric cards with next steps, adherence & monitoring. */
export default function SignalsSection({
  patientId,
  rtm,
  refreshing,
}: {
  patientId: string
  rtm: { days_with_data: number; window_days: number; qualifies: boolean; enrolled: boolean }
  refreshing: boolean
}) {
  const [open, setOpen] = useState(false)
  const { data, isLoading } = usePatientMetrics(patientId, open)

  const flaggedCount = data?.metrics.filter((m) => m.status === 'flag').length

  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="group flex w-full cursor-pointer items-center gap-2 rounded-lg px-1 py-2 text-left text-[13px] font-extrabold text-body transition-colors duration-150 hover:text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-oxy"
      >
        <ChevronRight
          size={16}
          className={`text-faint transition-transform duration-200 ${open ? 'rotate-90' : ''}`}
        />
        Supporting signals
        <span className="text-xs font-semibold text-faint">
          {open
            ? 'full detail'
            : `trajectory · wearable trends · adherence${flaggedCount ? ` · ${flaggedCount} flagged` : ''}`}
        </span>
      </button>

      {open && (
        <div className="mt-2 animate-fadeIn">
          {isLoading && <SkeletonCard lines={4} />}
          {data && <SignalsBody data={data} rtm={rtm} refreshing={refreshing} />}
        </div>
      )}
    </div>
  )
}
