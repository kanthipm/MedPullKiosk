import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { MOCK_APPLICATIONS } from "../mockData";
import { SectionCard } from "../components/SectionCard";
import { StatusBadge } from "../components/StatusBadge";
import type { ApplicationStatus, UploadedDocument } from "../types";

// ── helpers ──────────────────────────────────────────────────────────────────

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleString("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

function formatDOB(dob: string): string {
  const d = new Date(dob + "T00:00:00");
  return d.toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" });
}

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 0,
  }).format(amount);
}

// ── Review Badge (overall) ───────────────────────────────────────────────────

type ReviewBadge = "READY_FOR_REVIEW" | "NEEDS_FOLLOW_UP" | "INCOMPLETE";

function overallBadge(status: ApplicationStatus): ReviewBadge {
  if (status === "READY_FOR_REVIEW") return "READY_FOR_REVIEW";
  if (status === "NEEDS_FOLLOW_UP") return "NEEDS_FOLLOW_UP";
  return "INCOMPLETE";
}

const REVIEW_BADGE_CONFIG: Record<
  ReviewBadge,
  { label: string; className: string }
> = {
  READY_FOR_REVIEW: {
    label: "Ready for Review",
    className: "bg-emerald-100 text-emerald-800 ring-1 ring-emerald-300",
  },
  NEEDS_FOLLOW_UP: {
    label: "Needs Follow-Up",
    className: "bg-red-100 text-red-800 ring-1 ring-red-300",
  },
  INCOMPLETE: {
    label: "Incomplete",
    className: "bg-amber-100 text-amber-800 ring-1 ring-amber-300",
  },
};

function OverallBadge({ badge }: { badge: ReviewBadge }) {
  const cfg = REVIEW_BADGE_CONFIG[badge];
  return (
    <span className={`inline-block rounded-lg px-3 py-1 text-sm font-semibold ${cfg.className}`}>
      {cfg.label}
    </span>
  );
}

// ── Field row inside a section ───────────────────────────────────────────────

function FieldRow({
  label,
  value,
  missing,
}: {
  label: string;
  value?: string | number | null;
  missing?: boolean;
}) {
  const isEmpty = value === undefined || value === null || value === "";
  return (
    <div className="flex flex-col sm:flex-row sm:gap-4 py-2.5 border-b border-slate-100 last:border-0">
      <dt className="w-44 flex-shrink-0 text-xs font-medium text-slate-500 uppercase tracking-wide pt-0.5">
        {label}
      </dt>
      <dd className={`text-sm mt-0.5 sm:mt-0 ${isEmpty || missing ? "text-red-500 italic" : "text-slate-800 font-medium"}`}>
        {isEmpty || missing ? (
          <span className="flex items-center gap-1">
            <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20">
              <path
                fillRule="evenodd"
                d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z"
                clipRule="evenodd"
              />
            </svg>
            Missing
          </span>
        ) : (
          String(value)
        )}
      </dd>
    </div>
  );
}

// ── Document card ────────────────────────────────────────────────────────────

function DocumentCard({ doc }: { doc: UploadedDocument }) {
  const isUploaded = doc.uploadStatus === "uploaded";

  return (
    <div
      className={`rounded-lg border p-3 flex gap-3 items-start ${
        isUploaded ? "border-slate-200 bg-white" : "border-red-200 bg-red-50"
      }`}
    >
      {/* Thumbnail / placeholder */}
      <div className="flex-shrink-0 w-16 h-12 rounded overflow-hidden bg-slate-100 flex items-center justify-center border border-slate-200">
        {isUploaded && doc.thumbnailUrl ? (
          <img
            src={doc.thumbnailUrl}
            alt={doc.type}
            className="w-full h-full object-cover"
          />
        ) : (
          <svg
            className={`w-6 h-6 ${isUploaded ? "text-slate-400" : "text-red-400"}`}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
            />
          </svg>
        )}
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-slate-800 truncate">{doc.type}</p>
        {isUploaded ? (
          <>
            <p className="text-xs text-slate-400 truncate mt-0.5">{doc.fileName}</p>
            <span className="inline-flex items-center gap-1 mt-1 text-xs text-emerald-700 font-medium">
              <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                <path
                  fillRule="evenodd"
                  d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                  clipRule="evenodd"
                />
              </svg>
              Uploaded
            </span>
          </>
        ) : (
          <span className="inline-flex items-center gap-1 mt-1 text-xs text-red-600 font-medium">
            <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
              <path
                fillRule="evenodd"
                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                clipRule="evenodd"
              />
            </svg>
            Not submitted
          </span>
        )}
      </div>

      {/* View button */}
      {isUploaded && (
        <button
          className="flex-shrink-0 text-xs font-medium text-brand-600 hover:text-brand-800 hover:underline"
          onClick={() => alert(`Viewing: ${doc.fileName}`)}
        >
          View
        </button>
      )}
    </div>
  );
}

// ── Main page ────────────────────────────────────────────────────────────────

export function PatientReviewPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [notes, setNotes] = useState("");

  const app = MOCK_APPLICATIONS.find((a) => a.id === id);

  if (!app) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="text-center">
          <p className="text-slate-500 text-sm mb-3">Application not found.</p>
          <button
            onClick={() => navigate("/staff")}
            className="text-brand-600 text-sm hover:underline"
          >
            ← Back to Dashboard
          </button>
        </div>
      </div>
    );
  }

  const badge = overallBadge(app.status);
  const hasFieldIssues = app.missingRequiredFields.length > 0;
  const hasDocIssues = app.missingDocumentsCount > 0;

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Header */}
      <header className="bg-white border-b border-slate-200 shadow-sm sticky top-0 z-10">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
          <button
            onClick={() => navigate("/staff")}
            className="flex items-center gap-2 text-sm text-slate-500 hover:text-slate-800 transition-colors"
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            All Applications
          </button>
          <div className="flex items-center gap-3">
            <StatusBadge status={app.status} />
          </div>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 sm:px-6 py-8 space-y-6">

        {/* Patient header card */}
        <div className="rounded-xl border border-slate-200 bg-white shadow-sm p-5">
          <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
            <div>
              <div className="flex items-center gap-3 mb-1">
                <div className="h-10 w-10 rounded-full bg-brand-100 flex items-center justify-center text-brand-700 font-semibold text-sm">
                  {app.personal.fullName.split(" ").map((n) => n[0]).slice(0, 2).join("")}
                </div>
                <h1 className="text-xl font-bold text-slate-900">{app.personal.fullName}</h1>
              </div>
              <p className="text-xs text-slate-400 ml-13 pl-[52px]">
                Submitted {formatDate(app.submittedAt)} · App #{app.id}
              </p>
            </div>
            <div className="flex flex-col items-start sm:items-end gap-2">
              <OverallBadge badge={badge} />
              {(hasFieldIssues || hasDocIssues) && (
                <div className="text-right">
                  {hasFieldIssues && (
                    <p className="text-xs text-red-500 font-medium">
                      {app.missingRequiredFields.length} required field{app.missingRequiredFields.length > 1 ? "s" : ""} missing
                    </p>
                  )}
                  {hasDocIssues && (
                    <p className="text-xs text-red-500 font-medium">
                      {app.missingDocumentsCount} required document{app.missingDocumentsCount > 1 ? "s" : ""} missing
                    </p>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Validation alert */}
        {(hasFieldIssues || hasDocIssues) && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 flex gap-3">
            <svg className="w-5 h-5 text-amber-500 flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd"
                d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z"
                clipRule="evenodd"
              />
            </svg>
            <div>
              <p className="text-sm font-semibold text-amber-800 mb-1">Action required before processing</p>
              <ul className="list-disc list-inside text-xs text-amber-700 space-y-0.5">
                {app.missingRequiredFields.map((f) => (
                  <li key={f}>{f}</li>
                ))}
                {hasDocIssues && (
                  <li>{app.missingDocumentsCount} document{app.missingDocumentsCount > 1 ? "s" : ""} not uploaded</li>
                )}
              </ul>
            </div>
          </div>
        )}

        {/* Personal Information */}
        <SectionCard
          title="Personal Information"
          icon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
          }
        >
          <dl>
            <FieldRow label="Full Name" value={app.personal.fullName} />
            <FieldRow label="Date of Birth" value={formatDOB(app.personal.dob)} />
            <FieldRow
              label="Address"
              value={app.personal.address}
              missing={!app.personal.address}
            />
            <FieldRow label="Phone" value={app.personal.phone} />
            <FieldRow label="Email" value={app.personal.email} />
          </dl>
        </SectionCard>

        {/* Household Information */}
        <SectionCard
          title="Household Information"
          icon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
            </svg>
          }
        >
          <dl>
            <FieldRow label="Household Size" value={`${app.household.householdSize} people`} />
            <FieldRow label="Dependents" value={app.household.dependents} />
            {app.household.minors !== undefined && (
              <FieldRow label="Minors" value={app.household.minors} />
            )}
          </dl>
        </SectionCard>

        {/* Financial Information */}
        <SectionCard
          title="Financial Information"
          icon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        >
          <dl>
            <FieldRow
              label="Income Sources"
              value={app.financial.incomeSources.join(", ")}
            />
            <FieldRow
              label="Monthly Income"
              value={`${formatCurrency(app.financial.monthlyIncome)} / month`}
            />
            <FieldRow label="Employment Status" value={app.financial.employmentStatus} />
            <FieldRow label="Insurance Status" value={app.financial.insuranceStatus} />
          </dl>
        </SectionCard>

        {/* Documents */}
        <SectionCard
          title="Documents"
          icon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          }
          alert={app.missingDocumentsCount > 0 ? `${app.missingDocumentsCount} missing` : undefined}
        >
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {app.documents.map((doc) => (
              <DocumentCard key={doc.id} doc={doc} />
            ))}
          </div>
        </SectionCard>

        {/* Internal Notes */}
        <SectionCard
          title="Internal Notes"
          icon={
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          }
        >
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Add internal staff notes here…  (not saved or shared with patient)"
            rows={4}
            className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent resize-y"
          />
          <p className="mt-1.5 text-xs text-slate-400 italic">
            Notes are session-only and not persisted.
          </p>
        </SectionCard>

        {/* Bottom action row */}
        <div className="flex justify-between items-center pb-8">
          <button
            onClick={() => navigate("/staff")}
            className="text-sm text-slate-500 hover:text-slate-700 hover:underline"
          >
            ← Back to dashboard
          </button>
          <button
            onClick={() => alert("Mark as reviewed (not yet wired to backend)")}
            className="rounded-lg bg-brand-600 hover:bg-brand-700 text-white text-sm font-semibold px-5 py-2.5 transition-colors"
          >
            Mark as Reviewed
          </button>
        </div>
      </main>
    </div>
  );
}
