import { Brain, Shield } from 'lucide-react'
import { SectionCard } from './SectionCard'
import { cn, riskColor, riskDot } from '@/lib/utils'
import type { ClinicalSummary } from '@/lib/types'

function Sub({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="mb-3 last:mb-0">
      <p className="text-xs font-semibold text-slate-500 mb-0.5">{label}</p>
      <p className="text-sm text-slate-800 leading-snug">{value ?? '—'}</p>
    </div>
  )
}

export function ClinicalSummaryCard({ clinical }: { clinical: ClinicalSummary }) {
  return (
    <SectionCard title="Clinical Summary" icon={<Brain className="h-3.5 w-3.5" />}>
      <div className="mb-4 p-3 bg-blue-50 rounded-lg border border-blue-100">
        <p className="text-xs font-semibold text-blue-700 mb-1">AI Summary</p>
        <p className="text-sm text-slate-800 leading-relaxed">{clinical.summary ?? 'No summary extracted.'}</p>
      </div>
      <Sub label="Mobility Status" value={clinical.mobilityStatus} />
      <Sub label="Rehab Needs" value={clinical.rehabNeeds} />
      <Sub label="Psychiatric / Behavioral Risks" value={clinical.psychiatricRisks} />
      <div className="mb-3">
        <p className="text-xs font-semibold text-slate-500 mb-1">Fall Risk</p>
        <span className={cn('inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full border', riskColor(clinical.fallRisk))}>
          <span className={cn('h-1.5 w-1.5 rounded-full', riskDot(clinical.fallRisk))} />
          {clinical.fallRisk === 'unknown' ? 'Unknown' : clinical.fallRisk.charAt(0).toUpperCase() + clinical.fallRisk.slice(1)}
        </span>
      </div>
      <Sub label="Medication Adherence Concerns" value={clinical.medicationAdherenceConcerns} />
      {clinical.precautions.length > 0 && (
        <div>
          <p className="text-xs font-semibold text-slate-500 mb-1.5 flex items-center gap-1"><Shield className="h-3 w-3" />Active Precautions</p>
          <ul className="space-y-1">
            {clinical.precautions.map((p, i) => (
              <li key={i} className="flex items-start gap-2">
                <span className="h-1.5 w-1.5 rounded-full bg-amber-500 mt-1.5 flex-shrink-0" />
                <span className="text-sm text-slate-700">{p}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </SectionCard>
  )
}
