import { useState } from 'react'
import { Pill, AlertTriangle, ChevronDown, ChevronRight } from 'lucide-react'
import { SectionCard } from './SectionCard'
import { cn } from '@/lib/utils'
import type { Medication } from '@/lib/types'

function MedRow({ med }: { med: Medication }) {
  const [expanded, setExpanded] = useState(false)
  const hasAlerts = med.alerts.length > 0
  return (
    <>
      <tr className={cn('border-b border-slate-100 cursor-pointer hover:bg-slate-50 transition-colors', hasAlerts && 'bg-red-50 hover:bg-red-100')} onClick={() => hasAlerts && setExpanded(!expanded)}>
        <td className="px-3 py-2.5">
          <div className="flex items-center gap-1.5">
            {hasAlerts && <AlertTriangle className="h-3.5 w-3.5 text-red-500 flex-shrink-0" />}
            <span className="text-sm font-medium text-slate-900">{med.name}</span>
            {hasAlerts && <span className="ml-auto text-slate-400">{expanded ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}</span>}
          </div>
          {med.indication && <p className="text-xs text-slate-400 mt-0.5 pl-5">{med.indication}</p>}
        </td>
        <td className="px-3 py-2.5 text-sm text-slate-700 whitespace-nowrap">{med.dosage}</td>
        <td className="px-3 py-2.5 text-sm text-slate-700">{med.frequency}</td>
        <td className="px-3 py-2.5"><span className="text-xs px-2 py-0.5 bg-slate-100 text-slate-600 rounded-full">{med.route}</span></td>
        <td className="px-3 py-2.5 text-xs text-slate-400">{med.source ?? '—'}</td>
      </tr>
      {expanded && hasAlerts && (
        <tr className="bg-red-50 border-b border-red-100">
          <td colSpan={5} className="px-3 pb-3 pt-0">
            <div className="pl-5 space-y-1">
              {med.alerts.map((alert, i) => (
                <div key={i} className="flex items-start gap-2">
                  <AlertTriangle className="h-3.5 w-3.5 text-red-500 flex-shrink-0 mt-0.5" />
                  <span className="text-xs text-red-700 font-medium">{alert}</span>
                </div>
              ))}
            </div>
          </td>
        </tr>
      )}
    </>
  )
}

export function MedicationTable({ medications }: { medications: Medication[] }) {
  const alertCount = medications.filter((m) => m.alerts.length > 0).length
  return (
    <SectionCard title="Medications" icon={<Pill className="h-3.5 w-3.5" />}
      headerExtra={alertCount > 0 ? (
        <span className="flex items-center gap-1 text-xs font-semibold text-red-700 bg-red-100 border border-red-200 px-2 py-0.5 rounded-full">
          <AlertTriangle className="h-3 w-3" />{alertCount} alert{alertCount !== 1 ? 's' : ''}
        </span>
      ) : undefined}>
      <div className="overflow-x-auto -mx-4 -mb-4">
        <table className="w-full min-w-[600px] text-left">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200">
              {['Medication', 'Dose', 'Frequency', 'Route', 'Source'].map((h) => (
                <th key={h} className="px-3 py-2 text-xs font-semibold text-slate-500 uppercase tracking-wide">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {medications.length > 0 ? medications.map((m) => <MedRow key={m.id} med={m} />) : (
              <tr><td colSpan={5} className="px-3 py-6 text-sm text-slate-400 text-center">No medications extracted</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </SectionCard>
  )
}
