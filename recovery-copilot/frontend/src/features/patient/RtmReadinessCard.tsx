import { CircleCheck, CircleDashed, FileCheck2, RefreshCw, Sparkles } from 'lucide-react'
import { useState } from 'react'
import {
  useApproveDocument,
  useRegenerateDocument,
  useRtmDocuments,
  useRtmStatus,
} from '../../api/queries'
import AIAttribution from '../../components/AIAttribution'
import Disclosure from '../../components/Disclosure'
import SectionCard from '../../components/SectionCard'
import { RefreshOverlay, SkeletonCard } from '../../components/Skeleton'
import { useToast } from '../../components/Toast'

const pillBase = 'rounded-full px-2.5 py-1 text-[10.5px] font-black uppercase leading-none'
const pillGood = `${pillBase} bg-risk-low-bg text-risk-low`
const pillWait = `${pillBase} bg-risk-med-bg text-risk-med`

function ChecklistRow({ done, label, detail }: { done: boolean; label: string; detail?: string }) {
  return (
    <li className="flex items-center gap-2.5 py-2 first:pt-0 last:pb-0">
      {done ? (
        <CircleCheck size={16} className="shrink-0 text-risk-low" />
      ) : (
        <CircleDashed size={16} className="shrink-0 text-faint" />
      )}
      <span className="text-[13px] font-extrabold text-ink">{label}</span>
      {detail && <span className="text-[12px] font-semibold text-faint">· {detail}</span>}
    </li>
  )
}

function DocumentationList({ patientId }: { patientId: string }) {
  const { data, isLoading } = useRtmDocuments(patientId)
  const approve = useApproveDocument(patientId)
  const regenerate = useRegenerateDocument(patientId)
  const toast = useToast()
  const [busyId, setBusyId] = useState<number | null>(null)

  if (isLoading) return <SkeletonCard lines={3} />
  const documents = data?.documents ?? []
  if (documents.length === 0) return null

  return (
    <div className="space-y-3">
      {documents.map((doc) => (
        <div key={doc.id} className="rounded-[14px] border border-line bg-white/70 p-3.5">
          <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2">
            <AIAttribution
              label={doc.kind === 'encounter_note' ? 'AI encounter note' : 'AI monthly summary'}
              generatedAt={doc.created_at}
              provider={doc.provider}
            />
            {doc.status === 'approved' ? (
              <span className={pillGood}>Approved</span>
            ) : (
              <span className={pillWait}>Awaiting review</span>
            )}
          </div>
          <p className="text-[13px] font-black tracking-tight text-ink">{doc.title}</p>
          <p className="mt-1 text-[12.5px] font-semibold leading-[1.55] text-body">{doc.body}</p>
          {doc.status !== 'approved' && (
            <div className="mt-2.5 flex gap-2">
              <button
                type="button"
                disabled={approve.isPending}
                onClick={() =>
                  approve.mutate(doc.id, {
                    onSuccess: () => toast('Documentation approved'),
                    onError: () => toast('Approval failed — try again', 'warning'),
                  })
                }
                className="inline-flex cursor-pointer items-center gap-1.5 rounded-btn bg-gradient-to-br from-oxy to-oxy-light px-3 py-1.5 text-[12px] font-extrabold text-white shadow-[0_6px_16px_rgba(47,128,237,.3)] transition-transform duration-150 hover:-translate-y-px disabled:pointer-events-none disabled:opacity-50"
              >
                <FileCheck2 size={13} /> Approve
              </button>
              <button
                type="button"
                disabled={regenerate.isPending && busyId === doc.id}
                onClick={() => {
                  setBusyId(doc.id)
                  regenerate.mutate(doc.id, {
                    onSuccess: () => toast('Draft regenerated', 'info'),
                    onError: () => toast('Regeneration failed — try again', 'warning'),
                    onSettled: () => setBusyId(null),
                  })
                }}
                className="inline-flex cursor-pointer items-center gap-1.5 rounded-btn border border-line bg-white px-3 py-1.5 text-[12px] font-extrabold text-body transition-colors duration-150 hover:text-ink disabled:pointer-events-none disabled:opacity-50"
              >
                <RefreshCw
                  size={13}
                  className={regenerate.isPending && busyId === doc.id ? 'animate-spin' : ''}
                />
                Regenerate
              </button>
            </div>
          )}
        </div>
      ))}
      <p className="text-[11px] font-semibold text-faint">
        AI drafts are editable records for provider review — nothing is filed without approval.
      </p>
    </div>
  )
}

export default function RtmReadinessCard({
  patientId,
  refreshing,
}: {
  patientId: string
  refreshing: boolean
}) {
  const { data: rtm, isLoading } = useRtmStatus(patientId)

  if (isLoading) return <SkeletonCard lines={4} />
  if (!rtm) return null

  const monitoringPct = Math.min(100, Math.round((rtm.monitoring.days / rtm.monitoring.target) * 100))

  return (
    <SectionCard
      title="RTM readiness"
      spine={rtm.ready_to_bill ? 'bg-risk-low' : undefined}
      aside={
        rtm.ready_to_bill ? (
          <span className={pillGood}>Ready to bill</span>
        ) : (
          <span className={pillWait}>In progress</span>
        )
      }
    >
      <RefreshOverlay show={refreshing} />

      {/* Monitoring progress (CPT 98985/98977) — days accrue from enrollment */}
      <div className="mb-4">
        <div className="flex items-baseline justify-between">
          <span className="text-[11px] font-black uppercase tracking-[.06em] text-faint">
            Monitoring progress
          </span>
          <span className="text-[13px] font-black tabular-nums text-ink">
            {rtm.monitoring.enrolled
              ? `${Math.min(rtm.monitoring.days, rtm.monitoring.target)} / ${rtm.monitoring.target} days`
              : `— / ${rtm.monitoring.target} days`}
          </span>
        </div>
        <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-line/60">
          <div
            className={`h-full rounded-full transition-[width] duration-500 ${
              rtm.monitoring.eligible
                ? 'bg-gradient-to-r from-risk-low to-emerald-400'
                : 'bg-gradient-to-r from-oxy to-oxy-light'
            }`}
            style={{ width: `${rtm.monitoring.enrolled ? monitoringPct : 0}%` }}
          />
        </div>
        <p className="mt-1 text-[11.5px] font-bold text-faint">
          {!rtm.monitoring.enrolled
            ? 'Monitoring days begin accruing once enrollment is complete'
            : rtm.monitoring.eligible
              ? `Monitoring eligible — ${rtm.monitoring.days} monitoring days this window (16 required)`
              : `${rtm.monitoring.target - rtm.monitoring.days} more monitoring days accrue in this 30-day window`}
        </p>
      </div>

      {/* Enrollment + treatment management checklist */}
      <ul className="divide-y divide-line">
        <ChecklistRow
          done={rtm.enrollment.education_complete}
          label="Education complete"
        />
        <ChecklistRow done={rtm.enrollment.consent_complete} label="Consent complete" />
        <ChecklistRow
          done={rtm.enrollment.baseline_complete}
          label="Baseline complete"
          detail={rtm.enrollment.pathway ?? undefined}
        />
        <ChecklistRow
          done={rtm.treatment_management.minutes >= 20}
          label={`Provider review: ${rtm.treatment_management.minutes} minutes`}
          detail="20 min target"
        />
        <ChecklistRow
          done={rtm.treatment_management.interactive_communication}
          label="Interactive communication"
          detail={rtm.treatment_management.interactive_communication ? 'logged' : 'required'}
        />
        <ChecklistRow
          done={rtm.documentation_ready}
          label={`Documentation: ${rtm.documentation_ready ? 'ready' : 'awaiting approval'}`}
        />
      </ul>

      {/* Billing eligibility (deterministic — compliance engine output) */}
      <div className="mt-4">
        <span className="text-[11px] font-black uppercase tracking-[.06em] text-faint">
          Billing eligibility
        </span>
        <div className="mt-1.5 flex flex-wrap gap-1.5">
          {rtm.billing.map((code) => (
            <span
              key={code.cpt}
              title={code.note}
              className={`${pillBase} ${
                code.eligible ? 'bg-risk-low-bg text-risk-low' : 'bg-line/50 text-faint'
              }`}
            >
              CPT {code.cpt}
              {code.units > 1 ? ` ×${code.units}` : ''}
              {!code.eligible && code.note ? ` — ${code.note}` : ''}
            </span>
          ))}
        </div>
      </div>

      {/* Suggested next action */}
      <div className="mt-4 flex items-start gap-3 rounded-[14px] bg-[#f3f7ff] p-3">
        <Sparkles size={15} className="mt-0.5 shrink-0 text-oxy" />
        <span>
          <span className="block text-[11px] font-black uppercase tracking-[.06em] text-faint">
            Suggested next action
          </span>
          <span className="block text-[13px] font-extrabold text-ink">{rtm.suggested_action}</span>
        </span>
      </div>

      <div className="mt-3">
        <Disclosure label="Documentation" hint="AI-drafted, provider-approved">
          <DocumentationList patientId={patientId} />
        </Disclosure>
      </div>
    </SectionCard>
  )
}
