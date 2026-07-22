import { useState } from 'react'
import type { CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { useWorklist, type AskResult } from '../../api/queries'
import type { WorklistPatient } from '../../api/types'
import AskBar from './AskBar'
import PracticeOverviewStrip from './PracticeOverviewStrip'
import AIAttribution from '../../components/AIAttribution'
import ConfidenceChip from '../../components/ConfidenceChip'
import GuardrailFootnote from '../../components/GuardrailFootnote'
import SectionCard from '../../components/SectionCard'
import { SkeletonCard } from '../../components/Skeleton'
import EmptyState from '../../components/EmptyState'
import { relativeTime } from '../../lib/format'
import { PRIORITY, type Priority } from '../../lib/risk'

type Filter = 'all' | 'high' | 'missing_data'

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'high', label: 'Needs review' },
  { key: 'missing_data', label: 'Missing data' },
]

const TIER_ORDER: Priority[] = ['high', 'medium', 'missing_data', 'low']

function headline(stats: { high: number; missing: number }): string {
  if (stats.high === 1) return '1 patient needs your attention today'
  if (stats.high > 1) return `${stats.high} patients need your attention today`
  if (stats.missing > 0) return 'No urgent reviews — some data gaps to check'
  return 'All patients are recovering as expected'
}

export default function WorklistPage() {
  const { data, isLoading, isError } = useWorklist()
  const [filter, setFilter] = useState<Filter>('all')
  const [askResult, setAskResult] = useState<AskResult | null>(null)

  if (isLoading) {
    return (
      <div className="space-y-4">
        <SkeletonCard lines={2} />
        <SkeletonCard lines={5} />
        <SkeletonCard lines={5} />
      </div>
    )
  }
  if (isError || !data) {
    return (
      <EmptyState title="The worklist couldn't be loaded.">
        Check that the API is running, then reload this page.
      </EmptyState>
    )
  }

  // An active AI answer narrows the roster to its cited patients; the
  // segmented filter applies otherwise.
  const askIds = askResult && askResult.patient_ids.length > 0 ? new Set(askResult.patient_ids) : null
  const groups = TIER_ORDER.map((tier) => ({
    tier,
    patients: data.patients.filter((p) =>
      askIds ? askIds.has(p.id) && p.priority === tier
             : p.priority === tier && (filter === 'all' || p.priority === filter),
    ),
  })).filter((g) => g.patients.length > 0)

  let riseIndex = 0

  return (
    <div>
      <div className="rise" style={{ '--rise-delay': '0ms' } as CSSProperties}>
        <h1 className="text-[26px] font-black tracking-tight text-ink">
          {headline(data.stats)}
        </h1>
        <p className="mt-1 text-[13px] font-bold text-faint">
          {data.stats.total} patients monitored · {data.stats.high}{' '}
          {data.stats.high === 1 ? 'needs' : 'need'} review · {data.stats.missing} missing data ·{' '}
          {data.stats.low} stable
        </p>
      </div>

      <div className="rise mt-5" style={{ '--rise-delay': '40ms' } as CSSProperties}>
        <PracticeOverviewStrip />
      </div>

      <SectionCard
        sum
        spine="bg-oxy"
        className="rise mt-6"
        style={{ '--rise-delay': '60ms' } as CSSProperties}
        eyebrow={
          <AIAttribution
            label="AI daily briefing"
            generatedAt={data.briefing.generated_at}
            provider={data.briefing.provider}
          />
        }
      >
        <p className="text-[13.5px] font-semibold leading-[1.55] text-body">
          {data.briefing.text}
        </p>
      </SectionCard>

      <div className="rise mt-6" style={{ '--rise-delay': '100ms' } as CSSProperties}>
        <AskBar
          result={askResult}
          onResult={setAskResult}
          onClear={() => setAskResult(null)}
        />
      </div>

      <div
        className="rise mt-8 flex items-center justify-between"
        style={{ '--rise-delay': '140ms' } as CSSProperties}
      >
        <h2 className="text-[13px] font-black uppercase tracking-[.08em] text-faint">
          {askIds ? 'Matching patients' : 'Patients'}
        </h2>
        {!askIds && (
          <div className="segment w-auto flex-none">
            {FILTERS.map((f) => (
              <button
                key={f.key}
                type="button"
                onClick={() => setFilter(f.key)}
                className={filter === f.key ? 'on px-4' : 'px-4'}
              >
                {f.label}
              </button>
            ))}
          </div>
        )}
      </div>

      {groups.length === 0 && (
        <EmptyState title={askIds ? 'No patients matched that question.' : 'No patients match this filter.'}>
          {askIds ? 'Clear the question to see the full roster.' : 'Switch back to All to see the full roster.'}
        </EmptyState>
      )}

      {groups.map(({ tier, patients }) => (
        <div key={tier} className="mt-5">
          <h3 className="mb-2.5 flex items-center gap-2 px-1 text-[11px] font-black uppercase tracking-[.08em] text-muted">
            {PRIORITY[tier].label}
            <span
              className={`inline-flex h-[20px] min-w-[22px] items-center justify-center rounded-full px-2 text-[11px] font-black ${PRIORITY[tier].pill.split(' ').slice(0, 2).join(' ')}`}
            >
              {patients.length}
            </span>
          </h3>
          <div className="space-y-2.5">
            {patients.map((p) => (
              <WorklistRow key={p.id} patient={p} index={riseIndex++} />
            ))}
          </div>
        </div>
      ))}

      <GuardrailFootnote className="mt-8" />
    </div>
  )
}

function WorklistRow({ patient: p, index }: { patient: WorklistPatient; index: number }) {
  const high = p.priority === 'high'
  return (
    <Link
      to={`/patients/${p.id}`}
      style={{ '--rise-delay': `${160 + index * 45}ms` } as CSSProperties}
      className={`rise relative flex cursor-pointer items-center gap-4 rounded-row border px-4 py-3.5 transition-[transform,box-shadow] duration-150 hover:-translate-y-0.5 focus-visible:outline focus-visible:outline-2 focus-visible:outline-oxy active:translate-y-0 ${
        high
          ? 'border-risk-high/30 bg-gradient-to-b from-white to-risk-high-bg shadow-high-row hover:shadow-[0_12px_28px_rgba(229,72,77,.24)]'
          : 'border-line bg-white shadow-row hover:shadow-lift'
      }`}
    >
      {high && (
        <span
          aria-hidden
          className="absolute bottom-2 left-0 top-2 w-1 rounded-r-[4px] bg-risk-high"
        />
      )}
      <span
        aria-hidden
        className={`grid h-9 w-9 shrink-0 place-items-center rounded-[12px] text-[12px] font-black text-white ${
          high ? 'bg-gradient-to-br from-risk-high to-[#ff7a59]' : 'bg-gradient-to-br from-[#7c9cff] to-[#6c5ce7]'
        }`}
      >
        {p.initials}
      </span>
      <span className="w-48 shrink-0">
        <span className="block truncate text-sm font-black tracking-tight text-ink">{p.name}</span>
        <span className="block truncate text-[11px] font-bold text-faint">
          {p.procedure_display.replace(/\s*\(.*\)$/, '')} · Day{' '}
          <span className="tabular-nums">{p.postop_day}</span>
        </span>
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[12.5px] font-semibold leading-snug text-body">
          {p.reason}
        </span>
        <ConfidenceChip level={p.data_confidence.level} />
      </span>
      <span
        className="hidden w-24 shrink-0 text-right md:block"
        title={
          p.rtm.enrolled
            ? 'RTM monitoring days since enrollment (16-of-30 target)'
            : 'RTM enrollment in progress — monitoring days accrue after enrollment'
        }
      >
        <span
          className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10.5px] font-black tabular-nums leading-none ${
            p.rtm.eligible ? 'bg-risk-low-bg text-risk-low' : 'bg-line/50 text-muted'
          }`}
        >
          {p.rtm.enrolled ? `${Math.min(p.rtm.days, p.rtm.target)}/${p.rtm.target} d` : 'enrolling'}
        </span>
      </span>
      <span className="hidden w-32 shrink-0 text-right sm:block">
        <span className="block text-[11px] font-bold tabular-nums text-faint">
          {relativeTime(p.last_checkin_at)}
        </span>
        <span className="block text-[11px] font-bold text-faint">{p.assigned_provider.name}</span>
      </span>
    </Link>
  )
}
