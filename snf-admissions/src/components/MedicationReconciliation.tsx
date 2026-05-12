import { useState } from 'react'
import {
  AlertTriangle, CheckCircle2, XCircle, Clock, FileText,
  ChevronDown, ChevronRight, Pencil, Check, X, ShieldAlert, Info,
} from 'lucide-react'
import { SectionCard } from './SectionCard'
import { cn } from '@/lib/utils'
import type { MedReconciliation, ReconMedication, ReconStatus, MedRiskLevel, ReconAlertSeverity } from '@/lib/types'

// ─── Status helpers ──────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<ReconStatus, { label: string; color: string; icon: React.ReactNode }> = {
  verified:         { label: 'Verified',          color: 'bg-emerald-50 text-emerald-700 border-emerald-200',  icon: <CheckCircle2 className="h-3 w-3" /> },
  conflict:         { label: 'Conflict Detected',  color: 'bg-red-50 text-red-700 border-red-200',             icon: <XCircle className="h-3 w-3" /> },
  missing_from_mar: { label: 'Missing from MAR',   color: 'bg-amber-50 text-amber-700 border-amber-200',       icon: <AlertTriangle className="h-3 w-3" /> },
  discontinued:     { label: 'Discontinued',        color: 'bg-slate-100 text-slate-500 border-slate-200',      icon: <X className="h-3 w-3" /> },
  needs_review:     { label: 'Needs Review',        color: 'bg-blue-50 text-blue-700 border-blue-200',          icon: <Clock className="h-3 w-3" /> },
}

const RISK_CONFIG: Record<MedRiskLevel, { label: string; color: string }> = {
  high:     { label: 'High Risk', color: 'bg-red-100 text-red-700 border-red-200' },
  moderate: { label: 'Moderate',  color: 'bg-amber-100 text-amber-700 border-amber-200' },
  standard: { label: 'Standard',  color: 'bg-slate-100 text-slate-500 border-slate-200' },
}

const ALERT_CONFIG: Record<ReconAlertSeverity, { color: string; bg: string; icon: React.ReactNode; label: string }> = {
  critical: { color: 'text-red-700',    bg: 'bg-red-50 border-red-200',    icon: <XCircle className="h-4 w-4 text-red-500 flex-shrink-0" />,      label: 'Critical' },
  warning:  { color: 'text-amber-700',  bg: 'bg-amber-50 border-amber-200', icon: <AlertTriangle className="h-4 w-4 text-amber-500 flex-shrink-0" />, label: 'Warning' },
  info:     { color: 'text-blue-700',   bg: 'bg-blue-50 border-blue-200',   icon: <Info className="h-4 w-4 text-blue-500 flex-shrink-0" />,          label: 'Info' },
}

// ─── Summary widget ──────────────────────────────────────────────────────────

function SummaryWidget({ summary }: { summary: MedReconciliation['summary'] }) {
  const metrics = [
    { label: 'Medications Reviewed', value: summary.totalReviewed,            color: 'text-slate-800' },
    { label: 'Discrepancies Detected', value: summary.discrepanciesDetected,  color: summary.discrepanciesDetected > 0 ? 'text-red-600' : 'text-emerald-600' },
    { label: 'High-Risk Medications', value: summary.highRiskMedications,     color: summary.highRiskMedications > 0 ? 'text-amber-600' : 'text-slate-800' },
    { label: 'Allergy Conflicts',      value: summary.unresolvedAllergyConflicts, color: summary.unresolvedAllergyConflicts > 0 ? 'text-red-600' : 'text-emerald-600' },
  ]
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
      {metrics.map((m) => (
        <div key={m.label} className="bg-slate-50 border border-slate-200 rounded-lg px-4 py-3 text-center">
          <p className={cn('text-2xl font-bold tabular-nums', m.color)}>{m.value}</p>
          <p className="text-xs text-slate-500 mt-0.5 leading-tight">{m.label}</p>
        </div>
      ))}
    </div>
  )
}

// ─── Reconciliation table row ────────────────────────────────────────────────

function ReconRow({ med }: { med: ReconMedication }) {
  const [expanded, setExpanded] = useState(false)
  const [editing, setEditing] = useState(false)
  const [note, setNote] = useState('')
  const [savedNote, setSavedNote] = useState('')

  const statusCfg = STATUS_CONFIG[med.status]
  const riskCfg = RISK_CONFIG[med.riskLevel]
  const needsAttention = med.status === 'conflict' || med.status === 'missing_from_mar'
  const hasSnippets = med.sourceSnippets.length > 0

  return (
    <>
      <tr className={cn(
        'border-b border-slate-100 hover:bg-slate-50 transition-colors',
        needsAttention && 'bg-red-50/40 hover:bg-red-50',
      )}>
        {/* Medication + high-risk flag */}
        <td className="px-3 py-3">
          <div className="flex items-start gap-1.5">
            {hasSnippets && (
              <button onClick={() => setExpanded(!expanded)} className="mt-0.5 text-slate-400 hover:text-slate-600 flex-shrink-0">
                {expanded ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
              </button>
            )}
            <div>
              <span className="text-sm font-medium text-slate-900">{med.name}</span>
              {med.highRiskCategory && (
                <span className="ml-2 text-xs font-semibold text-red-600 bg-red-100 border border-red-200 px-1.5 py-0.5 rounded uppercase tracking-wide">
                  {med.highRiskCategory}
                </span>
              )}
              {med.highRiskReason && (
                <p className="text-xs text-amber-700 mt-0.5 leading-snug">{med.highRiskReason}</p>
              )}
            </div>
          </div>
        </td>
        {/* Dose */}
        <td className="px-3 py-3 text-sm text-slate-700 whitespace-nowrap">{med.dose}</td>
        {/* Frequency */}
        <td className="px-3 py-3 text-sm text-slate-600">{med.frequency}</td>
        {/* Sources */}
        <td className="px-3 py-3">
          <div className="flex flex-wrap gap-1">
            {med.sources.map((s) => (
              <span key={s} className="text-xs px-1.5 py-0.5 bg-slate-100 text-slate-600 rounded border border-slate-200">{s}</span>
            ))}
          </div>
        </td>
        {/* Status */}
        <td className="px-3 py-3">
          <span className={cn('inline-flex items-center gap-1 text-xs font-semibold px-2 py-1 rounded-md border', statusCfg.color)}>
            {statusCfg.icon}{statusCfg.label}
          </span>
        </td>
        {/* Risk */}
        <td className="px-3 py-3">
          <span className={cn('text-xs font-semibold px-2 py-0.5 rounded border', riskCfg.color)}>{riskCfg.label}</span>
        </td>
        {/* Confidence */}
        <td className="px-3 py-3">
          <div className="flex items-center gap-1.5">
            <div className="h-1.5 w-12 bg-slate-200 rounded-full overflow-hidden">
              <div className={cn('h-full rounded-full', med.confidence >= 0.9 ? 'bg-emerald-500' : med.confidence >= 0.75 ? 'bg-amber-500' : 'bg-red-500')}
                style={{ width: `${Math.round(med.confidence * 100)}%` }} />
            </div>
            <span className="text-xs text-slate-500 tabular-nums">{Math.round(med.confidence * 100)}%</span>
          </div>
        </td>
        {/* Inline note / edit */}
        <td className="px-3 py-3">
          {editing ? (
            <div className="flex items-center gap-1">
              <input
                autoFocus
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="Staff note…"
                className="text-xs border border-slate-300 rounded px-2 py-1 w-36 focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
              <button onClick={() => { setSavedNote(note); setEditing(false) }} className="text-emerald-600 hover:text-emerald-700"><Check className="h-3.5 w-3.5" /></button>
              <button onClick={() => { setNote(savedNote); setEditing(false) }} className="text-slate-400 hover:text-slate-600"><X className="h-3.5 w-3.5" /></button>
            </div>
          ) : (
            <button onClick={() => setEditing(true)} className="flex items-center gap-1 text-xs text-slate-400 hover:text-slate-700 group">
              <Pencil className="h-3 w-3" />
              <span className="hidden group-hover:inline">
                {savedNote || 'Add note'}
              </span>
              {savedNote && <span className="text-slate-600 text-xs max-w-[80px] truncate">{savedNote}</span>}
            </button>
          )}
        </td>
      </tr>

      {/* Expanded source snippets */}
      {expanded && (
        <tr className="bg-slate-50 border-b border-slate-100">
          <td colSpan={8} className="px-4 pb-3 pt-1">
            <div className="space-y-2 pl-5">
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-1.5">
                <FileText className="h-3 w-3" />Source Citations
              </p>
              {med.sourceSnippets.map((s, i) => (
                <div key={i} className="flex gap-2">
                  <span className="text-xs font-semibold text-slate-600 bg-slate-200 px-1.5 py-0.5 rounded h-fit whitespace-nowrap flex-shrink-0">{s.document}</span>
                  <p className="text-xs text-slate-500 italic leading-relaxed">"{s.text}"</p>
                </div>
              ))}
            </div>
          </td>
        </tr>
      )}
    </>
  )
}

// ─── Alerts panel ────────────────────────────────────────────────────────────

function AlertsPanel({ alerts }: { alerts: MedReconciliation['alerts'] }) {
  const criticals = alerts.filter((a) => a.severity === 'critical')
  const warnings  = alerts.filter((a) => a.severity === 'warning')
  const infos     = alerts.filter((a) => a.severity === 'info')
  const ordered   = [...criticals, ...warnings, ...infos]

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden h-fit">
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100 bg-slate-50">
        <div className="flex items-center gap-2">
          <ShieldAlert className="h-3.5 w-3.5 text-slate-500" />
          <h2 className="text-xs font-semibold text-slate-600 uppercase tracking-wider">Reconciliation Alerts</h2>
        </div>
        {criticals.length > 0 && (
          <span className="text-xs font-semibold text-red-700 bg-red-100 border border-red-200 px-2 py-0.5 rounded-full">
            {criticals.length} critical
          </span>
        )}
      </div>
      <div className="p-3 space-y-2">
        {ordered.length === 0 ? (
          <p className="text-sm text-emerald-700 flex items-center gap-2 p-2">
            <CheckCircle2 className="h-4 w-4 flex-shrink-0" />No reconciliation alerts identified.
          </p>
        ) : ordered.map((alert) => {
          const cfg = ALERT_CONFIG[alert.severity]
          return (
            <div key={alert.id} className={cn('rounded-lg border p-3', cfg.bg)}>
              <div className="flex items-start gap-2">
                {cfg.icon}
                <div>
                  <span className={cn('text-xs font-bold uppercase tracking-wide', cfg.color)}>{cfg.label}</span>
                  <p className={cn('text-xs mt-0.5 leading-snug', cfg.color)}>{alert.message}</p>
                  {alert.medications.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-1.5">
                      {alert.medications.map((m) => (
                        <span key={m} className="text-xs px-1.5 py-0.5 bg-white/70 border border-current/20 rounded font-medium opacity-80">{m}</span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ─── Recommended actions ──────────────────────────────────────────────────────

function RecommendedActions({ actions }: { actions: MedReconciliation['recommendedActions'] }) {
  const urgent  = actions.filter((a) => a.priority === 'urgent')
  const routine = actions.filter((a) => a.priority === 'routine')

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <div className="flex items-center gap-2 px-4 py-3 border-b border-slate-100 bg-slate-50">
        <Check className="h-3.5 w-3.5 text-slate-500" />
        <h2 className="text-xs font-semibold text-slate-600 uppercase tracking-wider">Recommended Actions</h2>
      </div>
      <div className="p-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {urgent.length > 0 && (
            <div>
              <p className="text-xs font-bold text-red-700 uppercase tracking-wide mb-2">Urgent — Before Admission</p>
              <ol className="space-y-2">
                {urgent.map((a, i) => (
                  <li key={a.id} className="flex gap-2.5">
                    <span className="flex-shrink-0 h-5 w-5 rounded-full bg-red-100 border border-red-200 text-red-700 text-xs font-bold flex items-center justify-center">{i + 1}</span>
                    <p className="text-sm text-slate-700 leading-snug">{a.action}</p>
                  </li>
                ))}
              </ol>
            </div>
          )}
          {routine.length > 0 && (
            <div>
              <p className="text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">Routine — Within 24–48 Hours</p>
              <ol className="space-y-2">
                {routine.map((a, i) => (
                  <li key={a.id} className="flex gap-2.5">
                    <span className="flex-shrink-0 h-5 w-5 rounded-full bg-slate-100 border border-slate-200 text-slate-600 text-xs font-bold flex items-center justify-center">{i + 1}</span>
                    <p className="text-sm text-slate-700 leading-snug">{a.action}</p>
                  </li>
                ))}
              </ol>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── Main export ──────────────────────────────────────────────────────────────

export function MedicationReconciliation({ reconciliation }: { reconciliation: MedReconciliation }) {
  const { medications, alerts, summary, recommendedActions } = reconciliation
  const criticalCount = alerts.filter((a) => a.severity === 'critical').length

  return (
    <div className="space-y-4">
      <SectionCard
        title="Medication Reconciliation Review"
        icon={<ShieldAlert className="h-3.5 w-3.5" />}
        headerExtra={
          <div className="flex items-center gap-2">
            {criticalCount > 0 && (
              <span className="flex items-center gap-1 text-xs font-semibold text-red-700 bg-red-100 border border-red-200 px-2 py-0.5 rounded-full">
                <XCircle className="h-3 w-3" />{criticalCount} critical
              </span>
            )}
          </div>
        }
      >
        <SummaryWidget summary={summary} />

        {/* Table + alerts side by side */}
        <div className="grid grid-cols-1 xl:grid-cols-[1fr_300px] gap-4 items-start">
          {/* Main table */}
          <div className="overflow-x-auto -mx-4 -mb-4">
            <table className="w-full min-w-[900px] text-left">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  {['Medication', 'Dose', 'Frequency', 'Source Documents', 'Reconciliation Status', 'Risk Level', 'Confidence', 'Staff Notes'].map((h) => (
                    <th key={h} className="px-3 py-2 text-xs font-semibold text-slate-500 uppercase tracking-wide whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {medications.length > 0
                  ? medications.map((m) => <ReconRow key={m.id} med={m} />)
                  : <tr><td colSpan={8} className="px-3 py-6 text-sm text-slate-400 text-center">No medications extracted</td></tr>
                }
              </tbody>
            </table>
          </div>

          {/* Alerts panel */}
          <div className="-mb-4">
            <AlertsPanel alerts={alerts} />
          </div>
        </div>
      </SectionCard>

      {/* Recommended actions */}
      {recommendedActions.length > 0 && <RecommendedActions actions={recommendedActions} />}

      {/* Disclaimer */}
      <p className="text-xs text-slate-400 text-center px-4">
        AI-assisted reconciliation support. Clinical review required before any medication administration decisions.
      </p>
    </div>
  )
}
