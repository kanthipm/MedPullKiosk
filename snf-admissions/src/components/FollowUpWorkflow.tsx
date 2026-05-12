import { useState } from 'react'
import {
  ChevronDown, ChevronRight, Phone, ClipboardList,
  Clock, CheckCircle2, XCircle, AlertTriangle, Circle,
  Activity,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import type {
  FollowUpWorkflow, FollowUpAction, FollowUpOwner,
  FollowUpUrgency, FollowUpStatus, AdmissionReadiness,
} from '@/lib/types'

// ─── Config maps ─────────────────────────────────────────────────────────────

const OWNER_CONFIG: Record<FollowUpOwner, { label: string; color: string }> = {
  admissions:       { label: 'Admissions',        color: 'bg-blue-100 text-blue-700 border-blue-200' },
  nursing:          { label: 'Nursing',            color: 'bg-purple-100 text-purple-700 border-purple-200' },
  billing:          { label: 'Billing / Insurance', color: 'bg-teal-100 text-teal-700 border-teal-200' },
  case_management:  { label: 'Case Management',   color: 'bg-indigo-100 text-indigo-700 border-indigo-200' },
  infection_control:{ label: 'Infection Control', color: 'bg-orange-100 text-orange-700 border-orange-200' },
  pharmacy:         { label: 'Pharmacy Review',   color: 'bg-pink-100 text-pink-700 border-pink-200' },
}

const URGENCY_CONFIG: Record<FollowUpUrgency, { label: string; color: string; dot: string }> = {
  critical: { label: 'Critical', color: 'text-red-700',    dot: 'bg-red-500' },
  high:     { label: 'High',     color: 'text-amber-700',  dot: 'bg-amber-500' },
  medium:   { label: 'Medium',   color: 'text-blue-600',   dot: 'bg-blue-400' },
  low:      { label: 'Low',      color: 'text-slate-500',  dot: 'bg-slate-300' },
}

const STATUS_CONFIG: Record<FollowUpStatus, { label: string; color: string; icon: React.ReactNode }> = {
  pending:               { label: 'Pending Follow-Up',      color: 'bg-slate-100 text-slate-600 border-slate-200',   icon: <Circle className="h-3 w-3" /> },
  awaiting_response:     { label: 'Awaiting Response',      color: 'bg-blue-50 text-blue-700 border-blue-200',       icon: <Clock className="h-3 w-3" /> },
  needs_clinical_review: { label: 'Needs Clinical Review',  color: 'bg-purple-50 text-purple-700 border-purple-200', icon: <ClipboardList className="h-3 w-3" /> },
  resolved:              { label: 'Resolved',               color: 'bg-emerald-50 text-emerald-700 border-emerald-200', icon: <CheckCircle2 className="h-3 w-3" /> },
  admission_blocker:     { label: 'Admission Blocker',      color: 'bg-red-50 text-red-700 border-red-200',          icon: <XCircle className="h-3 w-3" /> },
}

const READINESS_CONFIG: Record<AdmissionReadiness, { label: string; bg: string; text: string; border: string; icon: React.ReactNode }> = {
  ready:              { label: 'Ready for Admission',          bg: 'bg-emerald-50', text: 'text-emerald-800', border: 'border-emerald-300', icon: <CheckCircle2 className="h-5 w-5 text-emerald-600" /> },
  ready_with_warnings:{ label: 'Ready with Warnings',          bg: 'bg-amber-50',   text: 'text-amber-800',   border: 'border-amber-300',   icon: <AlertTriangle className="h-5 w-5 text-amber-500" /> },
  pending_critical:   { label: 'Pending Critical Information', bg: 'bg-red-50',     text: 'text-red-800',     border: 'border-red-300',     icon: <XCircle className="h-5 w-5 text-red-500" /> },
  high_risk:          { label: 'High-Risk Transfer',           bg: 'bg-red-50',     text: 'text-red-900',     border: 'border-red-400',     icon: <AlertTriangle className="h-5 w-5 text-red-600" /> },
}

// ─── Admission readiness badge ────────────────────────────────────────────────

export function AdmissionReadinessBadge({ readiness, reason }: { readiness: AdmissionReadiness; reason: string }) {
  const cfg = READINESS_CONFIG[readiness]
  return (
    <div className={cn('flex items-start gap-3 px-4 py-3 rounded-lg border', cfg.bg, cfg.border)}>
      <div className="flex-shrink-0 mt-0.5">{cfg.icon}</div>
      <div>
        <p className={cn('text-sm font-bold', cfg.text)}>{cfg.label}</p>
        <p className={cn('text-xs mt-0.5 leading-snug', cfg.text, 'opacity-80')}>{reason}</p>
      </div>
    </div>
  )
}

// ─── Workflow card ────────────────────────────────────────────────────────────

function WorkflowCard({ action }: { action: FollowUpAction }) {
  const [showLog, setShowLog] = useState(false)
  const ownerCfg = OWNER_CONFIG[action.owner]
  const urgencyCfg = URGENCY_CONFIG[action.urgency]
  const statusCfg = STATUS_CONFIG[action.status]
  const isBlocker = action.status === 'admission_blocker'
  const isResolved = action.status === 'resolved'

  return (
    <div className={cn(
      'bg-white rounded-xl border shadow-sm overflow-hidden flex flex-col',
      isBlocker ? 'border-red-300' : isResolved ? 'border-emerald-200' : 'border-slate-200',
    )}>
      {/* Card header */}
      <div className={cn(
        'px-4 py-3 border-b flex items-start justify-between gap-3',
        isBlocker ? 'bg-red-50 border-red-100' : 'bg-slate-50 border-slate-100',
      )}>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-slate-900 leading-snug">{action.issueTitle}</p>
          <p className="text-xs text-slate-500 mt-0.5 leading-snug">{action.description}</p>
        </div>
        <span className={cn('inline-flex items-center gap-1 text-xs font-semibold px-2 py-1 rounded-md border flex-shrink-0', statusCfg.color)}>
          {statusCfg.icon}{statusCfg.label}
        </span>
      </div>

      {/* Card body */}
      <div className="px-4 py-3 space-y-3 flex-1">
        {/* Owner + urgency row */}
        <div className="flex items-center gap-2 flex-wrap">
          <span className={cn('text-xs font-semibold px-2 py-0.5 rounded border', ownerCfg.color)}>
            {ownerCfg.label}
          </span>
          <span className={cn('flex items-center gap-1 text-xs font-bold', urgencyCfg.color)}>
            <span className={cn('h-1.5 w-1.5 rounded-full flex-shrink-0', urgencyCfg.dot)} />
            {urgencyCfg.label}
          </span>
        </div>

        {/* Suggested action */}
        <div>
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">Suggested Action</p>
          <p className="text-sm text-slate-700 leading-snug">{action.suggestedAction}</p>
        </div>

        {/* Communication action */}
        {action.communicationAction && (
          <div className="flex items-start gap-2 bg-blue-50 border border-blue-100 rounded-lg px-3 py-2">
            <Phone className="h-3.5 w-3.5 text-blue-500 flex-shrink-0 mt-0.5" />
            <p className="text-xs text-blue-700 font-medium leading-snug">{action.communicationAction}</p>
          </div>
        )}
      </div>

      {/* Activity log toggle */}
      {action.activityLog.length > 0 && (
        <div className="border-t border-slate-100">
          <button
            onClick={() => setShowLog(!showLog)}
            className="w-full flex items-center justify-between px-4 py-2 text-xs text-slate-400 hover:text-slate-600 hover:bg-slate-50 transition-colors"
          >
            <span className="flex items-center gap-1.5"><Activity className="h-3 w-3" />Activity ({action.activityLog.length})</span>
            {showLog ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
          </button>
          {showLog && (
            <div className="px-4 pb-3 space-y-2">
              {action.activityLog.map((ev, i) => (
                <div key={i} className="flex gap-3">
                  <div className="flex flex-col items-center flex-shrink-0">
                    <div className="h-1.5 w-1.5 rounded-full bg-slate-300 mt-1.5" />
                    {i < action.activityLog.length - 1 && <div className="w-px flex-1 bg-slate-100 min-h-[12px]" />}
                  </div>
                  <div className="pb-1">
                    <p className="text-xs text-slate-700 leading-snug">{ev.event}</p>
                    <p className="text-xs text-slate-400 mt-0.5">{ev.timestamp}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Section header counts ────────────────────────────────────────────────────

function StatusCount({ actions }: { actions: FollowUpAction[] }) {
  const blockers = actions.filter((a) => a.status === 'admission_blocker').length
  const critical = actions.filter((a) => a.urgency === 'critical' && a.status !== 'resolved').length
  const pending  = actions.filter((a) => a.status === 'pending' || a.status === 'awaiting_response').length
  const resolved = actions.filter((a) => a.status === 'resolved').length

  return (
    <div className="flex items-center gap-1.5 flex-wrap">
      {blockers > 0 && (
        <span className="text-xs font-semibold text-red-700 bg-red-100 border border-red-200 px-2 py-0.5 rounded-full">
          {blockers} blocker{blockers !== 1 ? 's' : ''}
        </span>
      )}
      {critical > 0 && (
        <span className="text-xs font-semibold text-amber-700 bg-amber-100 border border-amber-200 px-2 py-0.5 rounded-full">
          {critical} critical
        </span>
      )}
      {pending > 0 && (
        <span className="text-xs font-semibold text-slate-600 bg-slate-100 border border-slate-200 px-2 py-0.5 rounded-full">
          {pending} pending
        </span>
      )}
      {resolved > 0 && (
        <span className="text-xs font-semibold text-emerald-700 bg-emerald-100 border border-emerald-200 px-2 py-0.5 rounded-full">
          {resolved} resolved
        </span>
      )}
    </div>
  )
}

// ─── Main export ──────────────────────────────────────────────────────────────

export function FollowUpWorkflowSection({ workflow }: { workflow: FollowUpWorkflow }) {
  const { actions } = workflow

  // Sort: admission blockers first, then by urgency
  const urgencyOrder: Record<FollowUpUrgency, number> = { critical: 0, high: 1, medium: 2, low: 3 }
  const sorted = [...actions].sort((a, b) => {
    if (a.status === 'admission_blocker' && b.status !== 'admission_blocker') return -1
    if (b.status === 'admission_blocker' && a.status !== 'admission_blocker') return 1
    if (a.status === 'resolved' && b.status !== 'resolved') return 1
    if (b.status === 'resolved' && a.status !== 'resolved') return -1
    return urgencyOrder[a.urgency] - urgencyOrder[b.urgency]
  })

  return (
    <div className="space-y-4">
      {/* Section header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <ClipboardList className="h-4 w-4 text-slate-400" />
          <h2 className="text-sm font-semibold text-slate-700 uppercase tracking-wider">Follow-Up Actions</h2>
        </div>
        <StatusCount actions={actions} />
      </div>

      {/* Cards grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {sorted.map((action) => (
          <WorkflowCard key={action.id} action={action} />
        ))}
      </div>

      {/* Disclaimer */}
      <p className="text-xs text-slate-400 text-center">
        AI-generated workflow recommendations. Staff review required before initiating any follow-up action.
      </p>
    </div>
  )
}
