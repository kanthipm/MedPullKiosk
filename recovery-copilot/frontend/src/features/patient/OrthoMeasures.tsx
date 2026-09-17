import { BookOpenText, Stethoscope } from 'lucide-react'
import type { CSSProperties } from 'react'
import { usePatientOrthoMeasures } from '../../api/queries'
import type { OrthoMeasure } from '../../api/types'
import Sparkline from '../../components/charts/Sparkline'
import SectionCard from '../../components/SectionCard'
import { RefreshOverlay, SkeletonLine } from '../../components/Skeleton'
import { METRIC_STATUS } from '../../lib/risk'

const SOURCE: Record<OrthoMeasure['source'], { label: string; cls: string }> = {
  patient_reported: { label: 'Patient-reported', cls: 'bg-brand-tint text-brand' },
  clinician_entered: { label: 'Clinic-measured', cls: 'bg-soft text-body' },
  derived: { label: 'Derived', cls: 'bg-soft text-muted' },
}

function MeasureRow({
  m,
  index,
  refreshing,
}: {
  m: OrthoMeasure
  index: number
  refreshing: boolean
}) {
  const s = METRIC_STATUS[m.status]
  const source = SOURCE[m.source] ?? SOURCE.derived
  const active = m.status === 'flag' || m.status === 'watch'
  return (
    <li
      style={{ '--rise-delay': `${index * 45}ms` } as CSSProperties}
      className="rise relative py-3.5 pl-[14px] first:pt-0 last:pb-0"
    >
      <RefreshOverlay show={refreshing} />
      <span
        aria-hidden
        className={`absolute bottom-3.5 left-0 top-3.5 w-[2px] rounded-full ${s.spine} ${
          index === 0 ? 'top-0' : ''
        }`}
      />
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1.5">
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <span className="text-[13.5px] font-semibold tracking-[-.01em] text-ink">{m.name}</span>
          <span className={`chip ${source.cls}`}>{source.label}</span>
          <span className="micro hidden sm:inline">{m.family}</span>
        </div>
        <span className={`chip shrink-0 uppercase tracking-[.03em] ${s.pill}`}>
          {active ? m.status_text : m.status === 'nodata' ? m.status_text : m.status_text || s.label}
        </span>
      </div>

      <div className="mt-2 grid items-start gap-x-5 gap-y-2 sm:grid-cols-[minmax(0,1fr)_200px]">
        <div className="min-w-0">
          <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
            {m.value != null ? (
              <span className="font-mono text-[22px] font-medium leading-none tabular-nums tracking-[-.02em] text-ink">
                {m.value}
                {m.unit && (
                  <span className="ml-0.5 text-[12px] font-medium tracking-normal text-muted">
                    {m.unit}
                  </span>
                )}
              </span>
            ) : (
              <span className="font-mono text-[22px] font-medium leading-none text-faint">—</span>
            )}
            {m.delta && (
              <span className="text-[11px] font-medium uppercase tracking-[.04em] text-faint">
                {m.delta}
              </span>
            )}
          </div>
          <p className="mt-2 text-[13px] font-medium leading-[1.5] text-body">{m.finding}</p>
          {m.next_step && (
            <p className="mt-1.5 flex items-start gap-1.5 text-[12.5px] font-medium text-brand">
              <span aria-hidden>→</span> {m.next_step}
            </p>
          )}
        </div>
        <div className="sm:pt-1">
          <Sparkline series={m.series} baseline={m.reference} unit={m.series_unit} />
          <p className="mt-1 text-right text-[10.5px] font-medium text-faint">{m.coverage_text}</p>
        </div>
      </div>

      <p className="mt-2.5 flex items-start gap-1.5 border-t border-line pt-2 text-[11px] font-medium leading-[1.5] text-faint">
        <BookOpenText size={12} className="mt-[2px] shrink-0" aria-hidden />
        <span>
          <span className="text-muted">Evidence · </span>
          {m.evidence}
          {m.guarded && ' · guarded phrasing'}
        </span>
      </p>
    </li>
  )
}

/** Five procedure-specific measures — pain curve, load tolerance, ROM
 *  milestone, wound drainage ladder, nocturnal disruption — drafted with the
 *  practice's orthopedic surgeons and PTs and thresholded from the cited
 *  literature. They sit beside the risk tier and never set it. */
export default function OrthoMeasures({
  patientId,
  refreshing,
}: {
  patientId: string
  refreshing: boolean
}) {
  const { data, isLoading } = usePatientOrthoMeasures(patientId)
  const summary = data?.summary
  const aside = summary ? (
    <span
      className={`chip ${
        summary.flagged > 0
          ? 'bg-risk-high-bg text-risk-high'
          : summary.watch > 0
            ? 'bg-risk-med-bg text-risk-med'
            : 'bg-risk-low-bg text-risk-low'
      }`}
    >
      {summary.flagged > 0
        ? `${summary.flagged} flagged${summary.watch ? ` · ${summary.watch} to watch` : ''}`
        : summary.watch > 0
          ? `${summary.watch} to watch`
          : 'Within milestones'}
    </span>
  ) : null

  return (
    <SectionCard
      title="Orthopedic recovery measures"
      eyebrow={
        <span className="mb-1 inline-flex items-center gap-1.5 text-[10.5px] font-medium uppercase tracking-[.1em] text-faint">
          <Stethoscope size={11} className="text-brand" aria-hidden />
          <span className="text-brand">Clinician-developed</span>
          <span className="normal-case tracking-normal">· evidence-anchored</span>
        </span>
      }
      aside={aside}
    >
      {isLoading || !data ? (
        <div className="space-y-3">
          <SkeletonLine className="h-3.5 w-1/3" />
          <SkeletonLine className="h-3.5 w-full" />
          <SkeletonLine className="h-3.5 w-2/3" />
          <SkeletonLine className="h-3.5 w-1/2" />
        </div>
      ) : (
        <>
          <ul className="divide-y divide-line">
            {data.measures.map((m, i) => (
              <MeasureRow key={m.key} m={m} index={i} refreshing={refreshing} />
            ))}
          </ul>
          <p className="mt-3 border-t border-line pt-2.5 text-[11px] font-medium leading-[1.5] text-faint">
            Developed with {data.provenance.developed_with.charAt(0).toLowerCase()}
            {data.provenance.developed_with.slice(1)}. {data.provenance.evidence_base}.{' '}
            {data.provenance.boundary}
          </p>
        </>
      )}
    </SectionCard>
  )
}
