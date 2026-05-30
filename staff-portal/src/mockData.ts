import type { PatientApplication, UploadedDocument, DocumentUploadStatus } from "./types";

// ── Document helper ───────────────────────────────────────────────────────────

const THUMB_ID = "https://placehold.co/80x60/e2e8f0/64748b?text=ID";
const THUMB_PDF = "https://placehold.co/80x60/e2e8f0/64748b?text=PDF";

function docSlot(
  id: string,
  type: string,
  status: DocumentUploadStatus,
  thumb: string,
  fileName: string,
  uploadedAt: string
): UploadedDocument {
  if (status === "uploaded") {
    return { id, type, fileName, uploadStatus: "uploaded", thumbnailUrl: thumb, uploadedAt };
  }
  return { id, type, fileName: "", uploadStatus: status };
}

/** Standard 3-document set (Photo ID, Proof of Income, Proof of Residency). */
function docs(
  prefix: string,
  idStatus: DocumentUploadStatus,
  incomeStatus: DocumentUploadStatus,
  residencyStatus: DocumentUploadStatus,
  at: string
): UploadedDocument[] {
  return [
    docSlot(`${prefix}a`, "Photo ID", idStatus, THUMB_ID, "photo_id.jpg", at),
    docSlot(`${prefix}b`, "Proof of Income", incomeStatus, THUMB_PDF, "proof_of_income.pdf", at),
    docSlot(`${prefix}c`, "Proof of Residency", residencyStatus, THUMB_PDF, "proof_of_residency.pdf", at),
  ];
}

// ── Mock applications ─────────────────────────────────────────────────────────
// Crafted to span every sliding-fee tier (A–FULL) and every care-priority level
// (High / Medium / Low) across a range of composite scores.

export const MOCK_APPLICATIONS: PatientApplication[] = [
  {
    // Tier A · HIGH (score ~7): 53% FPL, uninsured, 3 minors, large household
    id: "app-001",
    submittedAt: "2026-05-29T09:14:00Z",
    status: "INCOMPLETE_APPLICATION",
    program: "NEW_PATIENT",
    personal: {
      fullName: "Rosa Carmen Delgado",
      dob: "1968-09-22",
      address: "",
      phone: "(864) 555-0223",
    },
    household: { householdSize: 5, dependents: 3, minors: 3 },
    financial: {
      incomeSources: ["Seasonal Employment", "SNAP Benefits"],
      monthlyIncome: 1650,
      employmentStatus: "Seasonal",
      insuranceStatus: "Uninsured",
    },
    documents: docs("doc-001", "missing", "missing", "missing", "2026-05-29T09:14:00Z"),
    missingDocumentsCount: 3,
    missingRequiredFields: ["Address", "Photo ID", "Proof of Income", "Proof of Residency"],
  },
  {
    // Tier A · MEDIUM (score 3): 84% FPL, Medicare, single senior
    id: "app-002",
    submittedAt: "2026-05-29T08:40:00Z",
    status: "READY_FOR_REVIEW",
    program: "MEDICAID_RENEWAL",
    personal: {
      fullName: "Walter Boyd",
      dob: "1951-02-11",
      address: "77 Sycamore Ln, Greenville, SC 29605",
      phone: "(864) 555-0455",
      email: "w.boyd@email.com",
    },
    household: { householdSize: 1, dependents: 0 },
    financial: {
      incomeSources: ["Social Security/SSI"],
      monthlyIncome: 1100,
      employmentStatus: "Retired",
      insuranceStatus: "Medicare",
    },
    documents: docs("doc-002", "uploaded", "uploaded", "uploaded", "2026-05-29T08:35:00Z"),
    missingDocumentsCount: 0,
    missingRequiredFields: [],
  },
  {
    // Tier B · HIGH (score 5): 110% FPL, uninsured, 2 minors
    id: "app-003",
    submittedAt: "2026-05-29T09:50:00Z",
    status: "READY_FOR_REVIEW",
    program: "SLIDING_FEE",
    personal: {
      fullName: "Maria Elena Gutierrez",
      dob: "1985-03-14",
      address: "1421 Maple St, Greenville, SC 29601",
      phone: "(864) 555-0182",
      email: "m.gutierrez@email.com",
    },
    household: { householdSize: 4, dependents: 2, minors: 2 },
    financial: {
      incomeSources: ["Employment"],
      monthlyIncome: 2950,
      employmentStatus: "Full-time",
      insuranceStatus: "Uninsured",
    },
    documents: docs("doc-003", "uploaded", "uploaded", "uploaded", "2026-05-29T09:45:00Z"),
    missingDocumentsCount: 0,
    missingRequiredFields: [],
  },
  {
    // Tier B · MEDIUM (score 3): 120% FPL, coverage pending
    id: "app-004",
    submittedAt: "2026-05-28T15:20:00Z",
    status: "INCOMPLETE_APPLICATION",
    program: "MEDICAID_RENEWAL",
    personal: {
      fullName: "Tanya Brooks",
      dob: "1979-12-03",
      address: "210 Elm Ct, Greenville, SC 29607",
      phone: "(864) 555-0398",
    },
    household: { householdSize: 2, dependents: 1, minors: 0 },
    financial: {
      incomeSources: ["Part-time Employment"],
      monthlyIncome: 2115,
      employmentStatus: "Part-time",
      insuranceStatus: "Medicaid (Pending)",
    },
    documents: docs("doc-004", "uploaded", "missing", "uploaded", "2026-05-28T15:15:00Z"),
    missingDocumentsCount: 1,
    missingRequiredFields: ["Proof of Income"],
  },
  {
    // Tier B · LOW (score 2): 115% FPL, insured single adult
    id: "app-005",
    submittedAt: "2026-05-28T13:05:00Z",
    status: "READY_FOR_REVIEW",
    program: "SLIDING_FEE",
    personal: {
      fullName: "Gregory Nash",
      dob: "1990-06-19",
      address: "503 Pine St, Greenville, SC 29609",
      phone: "(864) 555-0511",
      email: "g.nash@email.com",
    },
    household: { householdSize: 1, dependents: 0 },
    financial: {
      incomeSources: ["Employment"],
      monthlyIncome: 1500,
      employmentStatus: "Full-time",
      insuranceStatus: "Private/Employer",
    },
    documents: docs("doc-005", "uploaded", "uploaded", "uploaded", "2026-05-28T13:00:00Z"),
    missingDocumentsCount: 0,
    missingRequiredFields: [],
  },
  {
    // Tier C · HIGH (score 6): 140% FPL, uninsured, 4 minors, large household
    id: "app-006",
    submittedAt: "2026-05-29T10:30:00Z",
    status: "NEEDS_FOLLOW_UP",
    program: "SLIDING_FEE",
    personal: {
      fullName: "Amara Okafor",
      dob: "1982-04-27",
      address: "88 Cedar Ave, Greenville, SC 29611",
      phone: "(864) 555-0744",
      email: "a.okafor@email.com",
    },
    household: { householdSize: 6, dependents: 4, minors: 4 },
    financial: {
      incomeSources: ["Employment", "Child Support/Alimony"],
      monthlyIncome: 5034,
      employmentStatus: "Full-time",
      insuranceStatus: "Uninsured",
    },
    documents: docs("doc-006", "uploaded", "uploaded", "missing", "2026-05-29T10:25:00Z"),
    missingDocumentsCount: 1,
    missingRequiredFields: ["Proof of Residency"],
  },
  {
    // Tier C · MEDIUM (score 4): 135% FPL, coverage pending, 1 minor
    id: "app-007",
    submittedAt: "2026-05-28T11:48:00Z",
    status: "INCOMPLETE_APPLICATION",
    program: "NEW_PATIENT",
    personal: {
      fullName: "Linda Tran",
      dob: "1988-10-09",
      address: "146 Walnut Dr, Greenville, SC 29615",
      phone: "(864) 555-0626",
    },
    household: { householdSize: 3, dependents: 1, minors: 1 },
    financial: {
      incomeSources: ["Self-employment"],
      monthlyIncome: 2998,
      employmentStatus: "Self-employed",
      insuranceStatus: "Medicaid (Pending)",
    },
    documents: docs("doc-007", "uploaded", "missing", "missing", "2026-05-28T11:40:00Z"),
    missingDocumentsCount: 2,
    missingRequiredFields: ["Proof of Income", "Proof of Residency"],
  },
  {
    // Tier C · LOW (score 2): 145% FPL, insured couple
    id: "app-008",
    submittedAt: "2026-05-27T16:10:00Z",
    status: "READY_FOR_REVIEW",
    program: "SLIDING_FEE",
    personal: {
      fullName: "Daniel Whitfield",
      dob: "1975-01-30",
      address: "920 Oakhurst Rd, Greenville, SC 29605",
      phone: "(864) 555-0832",
      email: "d.whitfield@email.com",
    },
    household: { householdSize: 2, dependents: 0 },
    financial: {
      incomeSources: ["Employment"],
      monthlyIncome: 2556,
      employmentStatus: "Full-time",
      insuranceStatus: "Private/Employer",
    },
    documents: docs("doc-008", "uploaded", "uploaded", "uploaded", "2026-05-27T16:05:00Z"),
    missingDocumentsCount: 0,
    missingRequiredFields: [],
  },
  {
    // Tier D · HIGH (score 5): 165% FPL, uninsured, 3 minors, large household
    id: "app-009",
    submittedAt: "2026-05-29T11:02:00Z",
    status: "NEEDS_FOLLOW_UP",
    program: "MEDICAL_INTAKE",
    personal: {
      fullName: "Fatima Yusuf",
      dob: "1986-08-15",
      address: "33 Brook Hollow, Greenville, SC 29617",
      phone: "(864) 555-0917",
    },
    household: { householdSize: 5, dependents: 3, minors: 3 },
    financial: {
      incomeSources: ["Employment", "Self-employment"],
      monthlyIncome: 5177,
      employmentStatus: "Full-time",
      insuranceStatus: "Uninsured",
    },
    documents: docs("doc-009", "uploaded", "skipped", "uploaded", "2026-05-29T10:58:00Z"),
    missingDocumentsCount: 1,
    missingRequiredFields: ["Proof of Income"],
  },
  {
    // Tier D · MEDIUM (score 3): 170% FPL, uninsured
    id: "app-010",
    submittedAt: "2026-05-28T09:25:00Z",
    status: "READY_FOR_REVIEW",
    program: "SLIDING_FEE",
    personal: {
      fullName: "Marcus Bell",
      dob: "1980-05-21",
      address: "415 Highland Ave, Greenville, SC 29601",
      phone: "(864) 555-0203",
      email: "m.bell@email.com",
    },
    household: { householdSize: 4, dependents: 2, minors: 0 },
    financial: {
      incomeSources: ["Employment"],
      monthlyIncome: 4555,
      employmentStatus: "Full-time",
      insuranceStatus: "Uninsured",
    },
    documents: docs("doc-010", "uploaded", "uploaded", "uploaded", "2026-05-28T09:20:00Z"),
    missingDocumentsCount: 0,
    missingRequiredFields: [],
  },
  {
    // Tier D · LOW (score 1): 160% FPL, insured single adult
    id: "app-011",
    submittedAt: "2026-05-27T14:00:00Z",
    status: "READY_FOR_REVIEW",
    program: "SLIDING_FEE",
    personal: {
      fullName: "Sofia Reyes",
      dob: "1993-11-12",
      address: "61 Magnolia St, Greenville, SC 29607",
      phone: "(864) 555-0688",
      email: "s.reyes@email.com",
    },
    household: { householdSize: 1, dependents: 0 },
    financial: {
      incomeSources: ["Employment"],
      monthlyIncome: 2087,
      employmentStatus: "Full-time",
      insuranceStatus: "Private/Employer",
    },
    documents: docs("doc-011", "uploaded", "uploaded", "uploaded", "2026-05-27T13:55:00Z"),
    missingDocumentsCount: 0,
    missingRequiredFields: [],
  },
  {
    // Tier E · HIGH (score 5): 190% FPL, uninsured, 4 minors, large household
    id: "app-012",
    submittedAt: "2026-05-29T08:05:00Z",
    status: "INCOMPLETE_APPLICATION",
    program: "NEW_PATIENT",
    personal: {
      fullName: "Joseph Adeyemi",
      dob: "1978-03-08",
      address: "204 Riverbend Dr, Greenville, SC 29611",
      phone: "(864) 555-0145",
    },
    household: { householdSize: 6, dependents: 4, minors: 4 },
    financial: {
      incomeSources: ["Employment", "Self-employment"],
      monthlyIncome: 6832,
      employmentStatus: "Full-time",
      insuranceStatus: "Uninsured",
    },
    documents: docs("doc-012", "uploaded", "missing", "missing", "2026-05-29T08:00:00Z"),
    missingDocumentsCount: 2,
    missingRequiredFields: ["Proof of Income", "Proof of Residency"],
  },
  {
    // Tier E · MEDIUM (score 4): 185% FPL, uninsured, 1 minor
    id: "app-013",
    submittedAt: "2026-05-28T17:30:00Z",
    status: "NEEDS_FOLLOW_UP",
    program: "MEDICAL_INTAKE",
    personal: {
      fullName: "Priya Anand",
      dob: "1991-07-04",
      address: "89 Birchwood Dr, Greenville, SC 29615",
      phone: "(864) 555-0619",
      email: "p.anand@email.com",
    },
    household: { householdSize: 3, dependents: 1, minors: 1 },
    financial: {
      incomeSources: ["Self-employment"],
      monthlyIncome: 4109,
      employmentStatus: "Self-employed",
      insuranceStatus: "Uninsured",
    },
    documents: docs("doc-013", "uploaded", "uploaded", "missing", "2026-05-28T17:25:00Z"),
    missingDocumentsCount: 1,
    missingRequiredFields: ["Proof of Residency"],
  },
  {
    // Tier E · LOW (score 1): 195% FPL, insured couple
    id: "app-014",
    submittedAt: "2026-05-27T10:45:00Z",
    status: "READY_FOR_REVIEW",
    program: "SLIDING_FEE",
    personal: {
      fullName: "Henry Caldwell",
      dob: "1972-09-17",
      address: "350 Crestview Rd, Greenville, SC 29609",
      phone: "(864) 555-0770",
      email: "h.caldwell@email.com",
    },
    household: { householdSize: 2, dependents: 0 },
    financial: {
      incomeSources: ["Employment"],
      monthlyIncome: 3437,
      employmentStatus: "Full-time",
      insuranceStatus: "Private/Employer",
    },
    documents: docs("doc-014", "uploaded", "uploaded", "uploaded", "2026-05-27T10:40:00Z"),
    missingDocumentsCount: 0,
    missingRequiredFields: [],
  },
  {
    // Full Fee · MEDIUM (score 3): 230% FPL, uninsured, 2 minors
    id: "app-015",
    submittedAt: "2026-05-28T12:15:00Z",
    status: "READY_FOR_REVIEW",
    program: "SLIDING_FEE",
    personal: {
      fullName: "Beatriz Santos",
      dob: "1984-02-26",
      address: "712 Lakeshore Dr, Greenville, SC 29615",
      phone: "(864) 555-0294",
      email: "b.santos@email.com",
    },
    household: { householdSize: 4, dependents: 2, minors: 2 },
    financial: {
      incomeSources: ["Employment"],
      monthlyIncome: 6162,
      employmentStatus: "Full-time",
      insuranceStatus: "Uninsured",
    },
    documents: docs("doc-015", "uploaded", "uploaded", "uploaded", "2026-05-28T12:10:00Z"),
    missingDocumentsCount: 0,
    missingRequiredFields: [],
  },
  {
    // Full Fee · LOW (score 0): 260% FPL, insured couple
    id: "app-016",
    submittedAt: "2026-05-27T09:00:00Z",
    status: "READY_FOR_REVIEW",
    program: "MEDICAL_INTAKE",
    personal: {
      fullName: "Kevin O'Brien",
      dob: "1983-07-29",
      address: "1180 Augusta St, Greenville, SC 29605",
      phone: "(864) 555-0860",
      email: "k.obrien@email.com",
    },
    household: { householdSize: 2, dependents: 0 },
    financial: {
      incomeSources: ["Employment"],
      monthlyIncome: 4582,
      employmentStatus: "Full-time",
      insuranceStatus: "Private/Employer",
    },
    documents: docs("doc-016", "uploaded", "uploaded", "uploaded", "2026-05-27T08:55:00Z"),
    missingDocumentsCount: 0,
    missingRequiredFields: [],
  },
];
