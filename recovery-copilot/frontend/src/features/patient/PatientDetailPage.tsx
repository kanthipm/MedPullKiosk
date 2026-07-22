import { useIsFetching } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { useState } from 'react'
import type { CSSProperties } from 'react'
import { Link, useParams } from 'react-router-dom'
import { usePatient, useRecompute } from '../../api/queries'
import AIAttribution from '../../components/AIAttribution'
import ConfidenceChip from '../../components/ConfidenceChip'
import EmptyState from '../../components/EmptyState'
import GuardrailFootnote from '../../components/GuardrailFootnote'
import PriorityBadge from '../../components/PriorityBadge'
import SectionCard from '../../components/SectionCard'
import { RefreshOverlay, SkeletonCard } from '../../components/Skeleton'
import { useToast } from '../../components/Toast'
import { relativeTime } from '../../lib/format'
import { PRIORITY, URGENCY } from '../../lib/risk'
import ActionBar from './ActionBar'
import CheckinHistory from './CheckinHistory'
import RecoveryTimeline from './RecoveryTimeline'
import RtmReadinessCard from './RtmReadinessCard'
import SignalsSection from './SignalsSection'
import useReviewTimeTracker from './useReviewTimeTracker'

function rise(index: number) {
  return { className: 'rise', style: { '--rise-delay': `${index * 55}ms` } as CSSProperties }
}

export default function PatientDetailPage() {
  const { id = '' } = useParams()
  const { data: p, isLoading, isError } = usePatient(id)
  useReviewTimeTracker(id)
  const recompute = useRecompute(id)
  const toast = useToast()
  const fetchingPatient = useIsFetching({ queryKey: ['patient', id] })
  // Hold the shimmer for a minimum beat: a sub-200ms overlay reads as flicker
  // when the fallback engine answers instantly (LLM calls naturally run longer).
  const [minHold, setMinHold] = useState(false)
  const refreshing = recompute.isPending || minHold || (!!p && fetchingPatient > 0)

  if (isLoading) {
    return (
      <div className="space-y-4">
        <SkeletonCard lines={2} />
        <SkeletonCard lines={4} />
        <SkeletonCard lines={3} />
      </div>
    )
  }
  if (isError || !p) {
    return (
      <EmptyState title="This patient couldn't be loaded.">
        <Link to="/" className="font-bold text-oxy hover:underline">
          Back to the worklist
        </Link>
      </EmptyState>
    )
  }

  const onRefresh = () => {
    setMinHold(true)
    window.setTimeout(() => setMinHold(false), 1100)
    recompute.mutate(undefined, {
      onSuccess: () => toast('Analysis refreshed — new AI summary generated', 'info'),
      onError: () => toast('Refresh failed — try again', 'warning'),
    })
  }

  return (
    <div>
      <div {...rise(0)}>
        <Link
          to="/"
          className="inline-flex items-center gap-1 text-[13px] font-extrabold text-muted transition-colors duration-150 hover:text-ink"
        >
          <ArrowLeft size={15} /> Worklist
        </Link>

        <div className="mt-3 flex flex-wrap items-center gap-3">
          <span
            aria-hidden
            className={`grid h-11 w-11 place-items-center rounded-[14px] text-[14px] font-black text-white shadow-lift ${
              p.risk.level === 'high'
                ? 'bg-gradient-to-br from-risk-high to-[#ff7a59]'
                : 'bg-gradient-to-br from-[#7c9cff] to-[#6c5ce7]'
            }`}
          >
            {p.initials}
          </span>
          <h1 className="text-[26px] font-black tracking-tight text-ink">{p.name}</h1>
          <PriorityBadge priority={p.risk.level} />
          <ConfidenceChip level={p.data_confidence.level} />
        </div>
        <p className="mt-1.5 text-[13px] font-bold text-faint">
          {p.age} {p.sex} · {p.procedure_display} · Post-op day{' '}
          <span className="tabular-nums">{p.postop_day}</span> · {p.surgeon}
          {p.device && <> · {p.device.model}</>} ·{' '}
          {p.last_checkin_at
            ? `Last check-in ${relativeTime(p.last_checkin_at).toLowerCase()}`
            : 'No check-in yet'}
        </p>
      </div>

      <div {...rise(1)}>
        <div className="mt-5">
          <ActionBar
            patientId={p.id}
            patientName={p.name}
            onRefresh={onRefresh}
            refreshing={refreshing}
          />
        </div>
      </div>

      <div className="mt-5 space-y-4">
        <SectionCard
          sum
          spine={PRIORITY[p.risk.level].spine}
          {...rise(2)}
          eyebrow={
            <AIAttribution
              label="AI recovery summary"
              generatedAt={p.summary.generated_at}
              provider={p.summary.provider}
            />
          }
        >
          <RefreshOverlay show={refreshing} />
          <p className="text-[13.5px] font-semibold leading-[1.6] text-body">{p.summary.text}</p>
        </SectionCard>

        {p.actions.length > 0 && (
          <SectionCard title="Suggested follow-up" {...rise(3)}>
            <RefreshOverlay show={refreshing} />
            <ul className="divide-y divide-line">
              {p.actions.map((a, i) => (
                <li key={i} className="flex items-start gap-3 py-2.5 first:pt-0 last:pb-0">
                  <span
                    className={`mt-0.5 shrink-0 rounded-full px-2.5 py-1 text-[10.5px] font-black leading-none ${URGENCY[a.urgency]?.pill ?? URGENCY.routine.pill}`}
                  >
                    {URGENCY[a.urgency]?.label ?? 'Routine'}
                  </span>
                  <span>
                    <span className="block text-[13.5px] font-extrabold text-ink">{a.title}</span>
                    {a.detail && (
                      <span className="block text-[12.5px] font-semibold leading-snug text-muted">
                        {a.detail}
                      </span>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          </SectionCard>
        )}

        <div {...rise(4)}>
          <RtmReadinessCard patientId={p.id} refreshing={refreshing} />
        </div>

        <div {...rise(5)}>
          <RecoveryTimeline patientId={p.id} trajectory={p.trajectory} refreshing={refreshing} />
        </div>

        <div {...rise(6)}>
          <CheckinHistory patientId={p.id} />
        </div>

        <div {...rise(7)}>
          <SignalsSection patientId={p.id} rtm={p.rtm} refreshing={refreshing} />
        </div>
      </div>

      <GuardrailFootnote className="mt-8" />
    </div>
  )
}
