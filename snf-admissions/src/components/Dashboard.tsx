import type { PatientProfile, RiskFlags, RiskLevel } from '@/lib/types'
import { cn, riskColor, riskDot } from '@/lib/utils'
import { PatientSnapshotCard } from './PatientSnapshotCard'
import { ClinicalSummaryCard } from './ClinicalSummaryCard'
import { MedicationTable } from './MedicationTable'
import { InsuranceCard } from './InsuranceCard'
import { MissingInfoPanel, IntakeIssuesPanel } from './MissingInfoPanel'
import { RiskFlagsPanel } from './RiskFlagsPanel'
import { HospitalizationTimeline } from './HospitalizationTimeline'
import { MedicationReconciliation } from './MedicationReconciliation'

const RISK_LABELS: Record<keyof RiskFlags, string> = {
  fallRisk: 'Fall', behavioralRisk: 'Behavioral', medicationNoncompliance: 'Med Compliance',
  housingInstability: 'Housing', readmissionRisk: 'Readmission',
}

export function Dashboard({ profile }: { profile: PatientProfile }) {
  const { snapshot, clinical, medications, insurance, issues, risks, timeline, meta: _meta } = profile
  const highRisks = (Object.entries(risks) as [keyof RiskFlags, RiskLevel][]).filter(([, v]) => v === 'high')

  return (
    <div className="p-4 sm:p-6 space-y-4 max-w-[1400px] mx-auto">
      {/* Patient header */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm px-5 py-4">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h1 className="text-xl font-bold text-slate-900">{snapshot.name ?? 'Unknown Patient'}</h1>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-1 text-sm text-slate-500">
              {snapshot.dob && <span>DOB {snapshot.dob}</span>}
              {snapshot.age && <span>Age {snapshot.age}</span>}
              {snapshot.mrn && <span className="font-mono text-xs bg-slate-100 px-1.5 py-0.5 rounded text-slate-600">{snapshot.mrn}</span>}
              {snapshot.admittingDiagnosis && <span className="text-slate-700">{snapshot.admittingDiagnosis}</span>}
            </div>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {(Object.entries(risks) as [keyof RiskFlags, RiskLevel][]).map(([key, level]) => (
              <span key={key} className={cn('inline-flex items-center gap-1 text-xs font-semibold px-2 py-1 rounded-md border', riskColor(level))}>
                <span className={cn('h-1.5 w-1.5 rounded-full', riskDot(level))} />{RISK_LABELS[key]}
              </span>
            ))}
          </div>
        </div>
        {highRisks.length > 0 && (
          <div className="mt-3 flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg">
            <span className="h-2 w-2 rounded-full bg-red-500 animate-pulse flex-shrink-0" />
            <p className="text-xs font-semibold text-red-700">High-risk patient: {highRisks.map(([k]) => RISK_LABELS[k]).join(', ')}</p>
          </div>
        )}
      </div>

      {/* Critical issues banner */}
      {issues.filter((i) => i.severity === 'error').length > 0 && (
        <div className="bg-red-50 border border-red-200 rounded-xl px-5 py-3 flex items-center gap-3">
          <span className="h-2.5 w-2.5 rounded-full bg-red-500 animate-pulse flex-shrink-0" />
          <p className="text-sm font-semibold text-red-800">
            {issues.filter((i) => i.severity === 'error').length} critical intake issue{issues.filter((i) => i.severity === 'error').length !== 1 ? 's' : ''} require attention before admission can proceed.
          </p>
        </div>
      )}

      {/* 3-column grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">
        <div className="space-y-4">
          <PatientSnapshotCard snapshot={snapshot} meta={_meta} />
          <RiskFlagsPanel risks={risks} />
        </div>
        <div className="space-y-4">
          <ClinicalSummaryCard clinical={clinical} />
        </div>
        <div className="space-y-4">
          <InsuranceCard insurance={insurance} />
          {timeline.length > 0 && <HospitalizationTimeline events={timeline} />}
        </div>
      </div>

      {/* Missing info + intake issues side by side */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <IntakeIssuesPanel issues={issues} />
        <MissingInfoPanel issues={issues} />
      </div>

      {/* Full-width medications */}
      <MedicationTable medications={medications} />

      {/* Medication reconciliation */}
      <MedicationReconciliation reconciliation={profile.reconciliation} />

      <div className="h-4" />
    </div>
  )
}
