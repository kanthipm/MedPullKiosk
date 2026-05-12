export type FileDocType = 'discharge_summary' | 'medication_record' | 'insurance' | 'fax' | 'unknown'
export type FileStatus = 'queued' | 'processing' | 'done' | 'error'

export interface UploadedFile {
  id: string
  name: string
  size: number
  docType: FileDocType
  status: FileStatus
}

export interface PatientSnapshot {
  name: string | null
  dob: string | null
  age: number | null
  mrn: string | null
  diagnoses: string[]
  dischargeDestination: string | null
  adlStatus: string | null
  attendingProvider: string | null
  admittingDiagnosis: string | null
}

export type RiskLevel = 'low' | 'moderate' | 'high' | 'unknown'

export interface ClinicalSummary {
  summary: string | null
  mobilityStatus: string | null
  rehabNeeds: string | null
  psychiatricRisks: string | null
  fallRisk: RiskLevel
  medicationAdherenceConcerns: string | null
  precautions: string[]
}

export interface Medication {
  id: string
  name: string
  dosage: string
  frequency: string
  route: string
  indication: string | null
  alerts: string[]
  source: string | null
}

export type AuthStatus = 'authorized' | 'pending' | 'denied' | 'unknown'

export interface InsurancePanel {
  payerSource: string | null
  memberId: string | null
  groupNumber: string | null
  authorizationStatus: AuthStatus
  authorizationNumber: string | null
  coveredDays: number | null
  missingInfo: string[]
  reimbursementConcerns: string | null
}

export type IssueSeverity = 'warning' | 'error'

export interface IntakeIssue {
  id: string
  title: string
  description: string
  severity: IssueSeverity
  field: string | null
}

export interface RiskFlags {
  fallRisk: RiskLevel
  behavioralRisk: RiskLevel
  medicationNoncompliance: RiskLevel
  housingInstability: RiskLevel
  readmissionRisk: RiskLevel
}

export interface TimelineEvent {
  date: string
  event: string
  facility: string | null
}

export interface ProcessingMeta {
  filesProcessed: string[]
  estimatedTimeSavedMinutes: number
  actualProcessingSeconds: number
  confidence: Partial<Record<string, number>>
}

// ─── Medication Reconciliation ───────────────────────────────────────────────

export type ReconStatus = 'verified' | 'conflict' | 'missing_from_mar' | 'discontinued' | 'needs_review'
export type MedRiskLevel = 'high' | 'moderate' | 'standard'
export type ReconAlertSeverity = 'critical' | 'warning' | 'info'

export interface ReconMedication {
  id: string
  name: string
  dose: string
  frequency: string
  sources: string[]
  status: ReconStatus
  riskLevel: MedRiskLevel
  confidence: number          // 0–1
  highRiskCategory: string | null   // e.g. "anticoagulant", "insulin", "opioid"
  highRiskReason: string | null     // e.g. "May increase fall risk in elderly patients"
  sourceSnippets: { document: string; text: string }[]
}

export interface ReconAlert {
  id: string
  severity: ReconAlertSeverity
  message: string
  medications: string[]
}

export interface ReconAction {
  id: string
  action: string
  priority: 'urgent' | 'routine'
}

export interface MedReconciliation {
  medications: ReconMedication[]
  alerts: ReconAlert[]
  summary: {
    totalReviewed: number
    discrepanciesDetected: number
    highRiskMedications: number
    unresolvedAllergyConflicts: number
  }
  recommendedActions: ReconAction[]
}

// ─── Patient Profile ─────────────────────────────────────────────────────────

export interface PatientProfile {
  snapshot: PatientSnapshot
  clinical: ClinicalSummary
  medications: Medication[]
  insurance: InsurancePanel
  issues: IntakeIssue[]
  risks: RiskFlags
  timeline: TimelineEvent[]
  reconciliation: MedReconciliation
  meta: ProcessingMeta
}

export type AppPhase = 'upload' | 'processing' | 'dashboard'

export interface AppState {
  phase: AppPhase
  files: UploadedFile[]
  profile: PatientProfile | null
  error: string | null
  processingStep: string | null
}
