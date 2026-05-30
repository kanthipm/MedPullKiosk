// ── Application Status ──────────────────────────────────────────────────────

export type ApplicationStatus =
  | "READY_FOR_REVIEW"
  | "INCOMPLETE_APPLICATION"
  | "NEEDS_FOLLOW_UP";

// ── Intake Program / Form ───────────────────────────────────────────────────

export type IntakeProgram =
  | "SLIDING_FEE"
  | "MEDICAL_INTAKE"
  | "NEW_PATIENT"
  | "MEDICAID_RENEWAL";

export const PROGRAM_LABELS: Record<IntakeProgram, string> = {
  SLIDING_FEE: "Sliding Fee Eligibility",
  MEDICAL_INTAKE: "Medical Intake",
  NEW_PATIENT: "New Patient Intake",
  MEDICAID_RENEWAL: "Medicaid Renewal",
};

// Submissions coming from the kiosk bridge don't yet carry a program; they are
// all produced by the sliding-fee flow today.
export const DEFAULT_PROGRAM: IntakeProgram = "SLIDING_FEE";

export function programLabel(program: IntakeProgram | undefined): string {
  return PROGRAM_LABELS[program ?? DEFAULT_PROGRAM];
}

// ── Document ────────────────────────────────────────────────────────────────

export type DocumentUploadStatus = "uploaded" | "missing" | "skipped" | "processing";

export interface UploadedDocument {
  id: string;
  type: string;              // e.g. "Proof of Income", "Photo ID"
  fileName: string;
  uploadStatus: DocumentUploadStatus;
  thumbnailUrl?: string;     // placeholder/preview image URL
  uploadedAt?: string;       // ISO string
}

// ── Patient Application ─────────────────────────────────────────────────────

export interface PersonalInfo {
  fullName: string;
  dob: string;               // e.g. "1985-03-14"
  address: string;
  phone?: string;
  email?: string;
}

export interface HouseholdInfo {
  householdSize: number;
  dependents: number;
  minors?: number;
}

export interface FinancialInfo {
  incomeSources: string[];   // e.g. ["Employment", "Child Support"]
  monthlyIncome: number;     // USD
  employmentStatus: string;  // e.g. "Part-time", "Unemployed"
  insuranceStatus: string;   // e.g. "Uninsured", "Medicaid"
}

export interface PatientApplication {
  id: string;
  submittedAt: string;       // ISO string
  status: ApplicationStatus;
  program?: IntakeProgram;   // which intake form the patient completed

  personal: PersonalInfo;
  household: HouseholdInfo;
  financial: FinancialInfo;
  documents: UploadedDocument[];

  missingDocumentsCount: number;
  missingRequiredFields: string[];

  staffNotes?: string;       // local state only — no persistence
}
