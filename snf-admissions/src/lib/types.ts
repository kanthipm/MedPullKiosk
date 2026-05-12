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

export interface PatientProfile {
  snapshot: PatientSnapshot
  clinical: ClinicalSummary
  medications: Medication[]
  insurance: InsurancePanel
  issues: IntakeIssue[]
  risks: RiskFlags
  timeline: TimelineEvent[]
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
