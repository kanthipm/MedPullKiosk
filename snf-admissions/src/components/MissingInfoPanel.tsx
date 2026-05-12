import { AlertTriangle, XCircle } from 'lucide-react'
import { SectionCard } from './SectionCard'
import { cn } from '@/lib/utils'
import type { IntakeIssue } from '@/lib/types'

function IssueCard({ issue }: { issue: IntakeIssue }) {
  const isError = issue.severity === 'error'
  return (
    <div className={cn('flex gap-3 p-3 rounded-lg border', isError ? 'bg-red-50 border-red-200' : 'bg-amber-50 border-amber-200')}>
      <div className="flex-shrink-0 mt-0.5">
        {isError ? <XCircle className="h-4 w-4 text-red-500" /> : <AlertTriangle className="h-4 w-4 text-amber-500" />}
      </div>
      <div>
        <p className={cn('text-sm font-semibold leading-tight', isError ? 'text-red-800' : 'text-amber-800')}>{issue.title}</p>
        <p className={cn('text-xs mt-0.5 leading-snug', isError ? 'text-red-700' : 'text-amber-700')}>{issue.description}</p>
      </div>
    </div>
  )
}

export function IntakeIssuesPanel({ issues }: { issues: IntakeIssue[] }) {
  const errors = issues.filter((i) => i.severity === 'error')
  return (
    <SectionCard title="Intake Issues" icon={<XCircle className="h-3.5 w-3.5" />}
      headerExtra={errors.length > 0 && (
        <span className="text-xs font-semibold text-red-700 bg-red-100 border border-red-200 px-2 py-0.5 rounded-full">{errors.length} critical</span>
      )}>
      {errors.length === 0 ? (
        <p className="text-sm text-emerald-700 flex items-center gap-2"><span className="h-2 w-2 rounded-full bg-emerald-500 flex-shrink-0" />No critical issues.</p>
      ) : (
        <div className="space-y-2.5">
          {errors.map((i) => <IssueCard key={i.id} issue={i} />)}
        </div>
      )}
    </SectionCard>
  )
}

export function MissingInfoPanel({ issues }: { issues: IntakeIssue[] }) {
  const warnings = issues.filter((i) => i.severity === 'warning')
  return (
    <SectionCard title="Missing Information" icon={<AlertTriangle className="h-3.5 w-3.5" />}
      headerExtra={warnings.length > 0 && (
        <span className="text-xs font-semibold text-amber-700 bg-amber-100 border border-amber-200 px-2 py-0.5 rounded-full">{warnings.length} warning{warnings.length !== 1 ? 's' : ''}</span>
      )}>
      {warnings.length === 0 ? (
        <p className="text-sm text-emerald-700 flex items-center gap-2"><span className="h-2 w-2 rounded-full bg-emerald-500 flex-shrink-0" />No missing information.</p>
      ) : (
        <div className="space-y-2.5">
          {warnings.map((i) => <IssueCard key={i.id} issue={i} />)}
        </div>
      )}
    </SectionCard>
  )
}
