// ── Application Status ──────────────────────────────────────────────────────

export type ApplicationStatus =
  | "READY_FOR_REVIEW"
  | "INCOMPLETE_APPLICATION"
  | "NEEDS_FOLLOW_UP";

// ── Document ────────────────────────────────────────────────────────────────

export type DocumentUploadStatus = "uploaded" | "missing" | "processing";

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

  personal: PersonalInfo;
  household: HouseholdInfo;
  financial: FinancialInfo;
  documents: UploadedDocument[];

  missingDocumentsCount: number;
  missingRequiredFields: string[];

  staffNotes?: string;       // local state only — no persistence
}
