import { useState } from "react";
import { useNavigate } from "react-router-dom";
import type { ApplicationStatus, PatientApplication } from "../types";
import { StatusBadge } from "./StatusBadge";

interface ApplicationTableProps {
  applications: PatientApplication[];
}

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 0,
  }).format(amount);
}

const STATUS_OPTIONS: { value: ApplicationStatus | "ALL"; label: string }[] = [
  { value: "ALL", label: "All Statuses" },
  { value: "READY_FOR_REVIEW", label: "Ready for Review" },
  { value: "INCOMPLETE_APPLICATION", label: "Incomplete Application" },
  { value: "NEEDS_FOLLOW_UP", label: "Needs Follow-Up" },
];

export function ApplicationTable({ applications }: ApplicationTableProps) {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<ApplicationStatus | "ALL">("ALL");

  const filtered = applications.filter((app) => {
    const matchesSearch = app.personal.fullName
      .toLowerCase()
      .includes(search.toLowerCase().trim());
    const matchesStatus =
      statusFilter === "ALL" || app.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  return (
    <div className="space-y-4">
      {/* Search + Filter row */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <svg
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z"
            />
          </svg>
          <input
            type="text"
            placeholder="Search by patient name…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full rounded-lg border border-slate-200 bg-white pl-9 pr-3 py-2 text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) =>
            setStatusFilter(e.target.value as ApplicationStatus | "ALL")
          }
          className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent"
        >
          {STATUS_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      {/* Result count */}
      <p className="text-xs text-slate-500">
        Showing {filtered.length} of {applications.length} applications
      </p>

      {/* Table — desktop */}
      <div className="hidden md:block rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
        <table className="min-w-full divide-y divide-slate-100">
          <thead className="bg-slate-50">
            <tr>
              {["Patient Name", "Submitted", "Status", "Missing Docs", "Household", "Monthly Income"].map(
                (col) => (
                  <th
                    key={col}
                    className="px-5 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider"
                  >
                    {col}
                  </th>
                )
              )}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {filtered.length === 0 && (
              <tr>
                <td
                  colSpan={6}
                  className="px-5 py-10 text-center text-sm text-slate-400"
                >
                  No applications match your search.
                </td>
              </tr>
            )}
            {filtered.map((app) => (
              <tr
                key={app.id}
                onClick={() => navigate(`/staff/review/${app.id}`)}
                className="cursor-pointer hover:bg-blue-50 transition-colors"
              >
                <td className="px-5 py-4">
                  <span className="text-sm font-medium text-slate-800">
                    {app.personal.fullName}
                  </span>
                </td>
                <td className="px-5 py-4 text-sm text-slate-500 whitespace-nowrap">
                  {formatDate(app.submittedAt)}
                </td>
                <td className="px-5 py-4">
                  <StatusBadge status={app.status} />
                </td>
                <td className="px-5 py-4 text-sm text-center">
                  {app.missingDocumentsCount > 0 ? (
                    <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-red-100 text-red-700 text-xs font-semibold">
                      {app.missingDocumentsCount}
                    </span>
                  ) : (
                    <span className="text-emerald-600 text-xs font-medium">All present</span>
                  )}
                </td>
                <td className="px-5 py-4 text-sm text-slate-600 text-center">
                  {app.household.householdSize}
                </td>
                <td className="px-5 py-4 text-sm text-slate-600 font-medium">
                  {formatCurrency(app.financial.monthlyIncome)}/mo
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Card list — mobile/tablet */}
      <div className="md:hidden space-y-3">
        {filtered.length === 0 && (
          <p className="py-8 text-center text-sm text-slate-400">
            No applications match your search.
          </p>
        )}
        {filtered.map((app) => (
          <div
            key={app.id}
            onClick={() => navigate(`/staff/review/${app.id}`)}
            className="cursor-pointer rounded-xl border border-slate-200 bg-white p-4 shadow-sm hover:border-brand-500 transition-colors"
          >
            <div className="flex items-start justify-between gap-2 mb-2">
              <span className="text-sm font-semibold text-slate-800">
                {app.personal.fullName}
              </span>
              <StatusBadge status={app.status} size="sm" />
            </div>
            <p className="text-xs text-slate-400 mb-3">
              {formatDate(app.submittedAt)}
            </p>
            <div className="flex gap-4 text-xs text-slate-500">
              <span>HH size: <strong className="text-slate-700">{app.household.householdSize}</strong></span>
              <span>Income: <strong className="text-slate-700">{formatCurrency(app.financial.monthlyIncome)}/mo</strong></span>
              {app.missingDocumentsCount > 0 && (
                <span className="text-red-600 font-medium">
                  {app.missingDocumentsCount} missing doc{app.missingDocumentsCount > 1 ? "s" : ""}
                </span>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
