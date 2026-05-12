import { useSubmissions } from "../hooks/useSubmissions";
import { ApplicationTable } from "../components/ApplicationTable";
import type { ApplicationStatus } from "../types";

interface StatCardProps {
  label: string;
  value: number;
  colorClass: string;
  bgClass: string;
}

function StatCard({ label, value, colorClass, bgClass }: StatCardProps) {
  return (
    <div className={`rounded-xl border ${bgClass} px-5 py-4 shadow-sm`}>
      <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">{label}</p>
      <p className={`text-3xl font-bold ${colorClass}`}>{value}</p>
    </div>
  );
}

export function StaffDashboardPage() {
  const handleBackToKiosk = () => window.history.go(-window.history.length);
  const applications = useSubmissions();

  const statusCounts: Record<ApplicationStatus, number> = {
    READY_FOR_REVIEW: 0,
    INCOMPLETE_APPLICATION: 0,
    NEEDS_FOLLOW_UP: 0,
  };
  applications.forEach((app) => {
    statusCounts[app.status]++;
  });

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Top nav */}
      <header className="bg-white border-b border-slate-200 shadow-sm">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="h-8 w-8 rounded-lg bg-brand-600 flex items-center justify-center">
              <svg className="h-4 w-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
            </div>
            <div>
              <h1 className="text-base font-bold text-slate-900 leading-tight">
                MedPull Staff Portal
              </h1>
              <p className="text-xs text-slate-400">Coastal Gateway Community Health Center</p>
            </div>
          </div>
          <button
            onClick={handleBackToKiosk}
            className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 transition-colors"
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            Back to Kiosk
          </button>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-4 sm:px-6 py-8 space-y-8">
        {/* Page heading */}
        <div>
          <h2 className="text-xl font-bold text-slate-900">Application Review</h2>
          <p className="text-sm text-slate-500 mt-0.5">
            {applications.length} submitted applications · Today
          </p>
        </div>

        {/* Summary stat cards */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <StatCard
            label="Total"
            value={applications.length}
            colorClass="text-slate-800"
            bgClass="border-slate-200 bg-white"
          />
          <StatCard
            label="Ready for Review"
            value={statusCounts.READY_FOR_REVIEW}
            colorClass="text-emerald-700"
            bgClass="border-emerald-100 bg-emerald-50"
          />
          <StatCard
            label="Incomplete"
            value={statusCounts.INCOMPLETE_APPLICATION}
            colorClass="text-amber-700"
            bgClass="border-amber-100 bg-amber-50"
          />
          <StatCard
            label="Needs Follow-Up"
            value={statusCounts.NEEDS_FOLLOW_UP}
            colorClass="text-red-700"
            bgClass="border-red-100 bg-red-50"
          />
        </div>

        {/* Applications table */}
        <section>
          <ApplicationTable applications={applications} />
        </section>
      </main>
    </div>
  );
}
