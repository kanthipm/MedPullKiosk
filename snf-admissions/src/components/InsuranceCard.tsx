import { CreditCard, AlertTriangle } from 'lucide-react'
import { SectionCard } from './SectionCard'
import { authStatusLabel, cn } from '@/lib/utils'
import type { InsurancePanel } from '@/lib/types'

function Row({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="flex gap-3 py-1.5 border-b border-slate-50 last:border-0">
      <span className="text-xs text-slate-400 w-36 flex-shrink-0 pt-0.5">{label}</span>
      <span className="text-sm text-slate-800">{value ?? '—'}</span>
    </div>
  )
}

export function InsuranceCard({ insurance }: { insurance: InsurancePanel }) {
  const { label: authLabel, cls: authCls } = authStatusLabel(insurance.authorizationStatus)
  return (
    <SectionCard title="Insurance / Eligibility" icon={<CreditCard className="h-3.5 w-3.5" />}>
      <Row label="Payer Source" value={insurance.payerSource} />
      <Row label="Member ID" value={insurance.memberId} />
      <Row label="Group Number" value={insurance.groupNumber} />
      <div className="flex gap-3 py-1.5 border-b border-slate-50">
        <span className="text-xs text-slate-400 w-36 flex-shrink-0 pt-0.5">Authorization</span>
        <div className="flex items-center gap-2">
          <span className={cn('text-xs font-semibold px-2.5 py-0.5 rounded-full border', authCls)}>{authLabel}</span>
          {insurance.authorizationNumber && <span className="text-xs text-slate-500">#{insurance.authorizationNumber}</span>}
        </div>
      </div>
      <Row label="Covered Days" value={insurance.coveredDays != null ? `${insurance.coveredDays} days (Medicare Part A)` : null} />
      {insurance.reimbursementConcerns && (
        <div className="mt-3 p-3 bg-amber-50 border border-amber-100 rounded-lg">
          <p className="text-xs font-semibold text-amber-700 mb-1 flex items-center gap-1"><AlertTriangle className="h-3 w-3" />Reimbursement Note</p>
          <p className="text-sm text-slate-700 leading-snug">{insurance.reimbursementConcerns}</p>
        </div>
      )}
      {insurance.missingInfo.length > 0 && (
        <div className="mt-3 space-y-1">
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Missing Information</p>
          {insurance.missingInfo.map((item, i) => (
            <div key={i} className="flex items-start gap-2">
              <AlertTriangle className="h-3.5 w-3.5 text-amber-500 flex-shrink-0 mt-0.5" />
              <span className="text-sm text-slate-700">{item}</span>
            </div>
          ))}
        </div>
      )}
    </SectionCard>
  )
}
