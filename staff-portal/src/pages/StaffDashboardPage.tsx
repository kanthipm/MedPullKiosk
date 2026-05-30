import { useMemo } from "react";
import { useSubmissions } from "../hooks/useSubmissions";
import { ApplicationTable } from "../components/ApplicationTable";
import { SlidingScaleCalculator } from "../components/SlidingScaleCalculator";
import { PhoneIntakePanel } from "../components/PhoneIntakePanel";
import { assessRisk, isUninsured, PRIORITY_CONFIG, type PriorityLevel } from "../lib/risk";
import { SLIDING_FEE_TIERS } from "../lib/slidingScale";

interface MetricCardProps {
  label: string;
  value: string | number;
  sub?: string;
  accent: string;
  icon: React.ReactNode;
}

function MetricCard({ label, value, sub, accent, icon }: MetricCardProps) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
      <div className="flex items-start justify-between">
        <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">{label}</p>
        <span className={accent}>{icon}</span>
      </div>
      <p className="mt-2 text-3xl font-bold text-slate-900 leading-none">{value}</p>
      {sub && <p className="mt-1.5 text-xs text-slate-400">{sub}</p>}
    </div>
  );
}

function DistributionRow({
  label,
  count,
  total,
  colorClass,
}: {
  label: string;
  count: number;
  total: number;
  colorClass: string;
}) {
  const pct = total > 0 ? (count / total) * 100 : 0;
  return (
    <div className="flex items-center gap-3">
      <span className="w-24 flex-shrink-0 text-xs text-slate-500">{label}</span>
      <div className="flex-1 h-2 rounded-full bg-slate-100 overflow-hidden">
        <div className={`h-full rounded-full ${colorClass}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="w-6 flex-shrink-0 text-right text-xs font-semibold text-slate-700">
        {count}
      </span>
    </div>
  );
}

export function StaffDashboardPage() {
  const applications = useSubmissions();

  const stats = useMemo(() => {
    const risks = applications.map((app) => assessRisk(app));
    const priorityCounts: Record<PriorityLevel, number> = { HIGH: 0, MEDIUM: 0, LOW: 0 };
    const tierCounts: Record<string, number> = {};
    let uninsured = 0;
    let qualifyDiscount = 0;

    risks.forEach((r, i) => {
      priorityCounts[r.level]++;
      tierCounts[r.slidingScale.tier.id] = (tierCounts[r.slidingScale.tier.id] ?? 0) + 1;
      if (r.slidingScale.qualifiesForDiscount) qualifyDiscount++;
      if (isUninsured(applications[i].financial.insuranceStatus)) uninsured++;
    });

    const today = new Date().toDateString();
    const processedToday = applications.filter(
      (a) => new Date(a.submittedAt).toDateString() === today
    ).length;

    return {
      total: applications.length,
      processedToday,
      priorityCounts,
      tierCounts,
      uninsured,
      qualifyDiscount,
    };
  }, [applications]);

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Top nav */}
      <header className="bg-white border-b border-slate-200 shadow-sm">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-4 flex items-center">
          <div className="flex items-center gap-3">
            <div className="h-8 w-8 rounded-lg bg-brand-600 flex items-center justify-center">
              <svg className="h-4 w-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
            </div>
            <div>
              <h1 className="text-base font-bold text-slate-900 leading-tight">
                MedPull Clinic Dashboard
              </h1>
              <p className="text-xs text-slate-400">Coastal Gateway Community Health Center</p>
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-4 sm:px-6 py-8 space-y-8">
        {/* Page heading */}
        <div>
          <h2 className="text-xl font-bold text-slate-900">Patients Processed</h2>
          <p className="text-sm text-slate-500 mt-0.5">
            {stats.total} total · {stats.processedToday} today
          </p>
        </div>

        {/* Metric cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <MetricCard
            label="Patients Processed"
            value={stats.total}
            sub={`${stats.processedToday} submitted today`}
            accent="text-brand-500"
            icon={
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M17 20h5v-2a4 4 0 00-3-3.87M9 20H4v-2a4 4 0 013-3.87m6-1.13a4 4 0 10-4-4 4 4 0 004 4z" />
              </svg>
            }
          />
          <MetricCard
            label="High Priority"
            value={stats.priorityCounts.HIGH}
            sub="need staff attention"
            accent="text-red-500"
            icon={
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M12 9v2m0 4h.01M5.07 19h13.86a2 2 0 001.74-3L13.74 4a2 2 0 00-3.48 0L3.34 16a2 2 0 001.73 3z" />
              </svg>
            }
          />
          <MetricCard
            label="Qualify for Discount"
            value={stats.qualifyDiscount}
            sub={`${stats.total - stats.qualifyDiscount} at full fee`}
            accent="text-emerald-500"
            icon={
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            }
          />
          <MetricCard
            label="Uninsured"
            value={stats.uninsured}
            sub="no active coverage"
            accent="text-amber-500"
            icon={
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z" />
              </svg>
            }
          />
        </div>

        {/* Calculator + clinic snapshot */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
          <SlidingScaleCalculator />

          <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
            <div className="px-5 py-3.5 border-b border-slate-100 bg-slate-50">
              <h3 className="text-sm font-semibold text-slate-700 uppercase tracking-wider">
                Clinic Snapshot
              </h3>
            </div>
            <div className="px-5 py-5 space-y-6">
              <div>
                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-3">
                  Care Priority
                </p>
                <div className="space-y-2.5">
                  {(["HIGH", "MEDIUM", "LOW"] as PriorityLevel[]).map((level) => (
                    <DistributionRow
                      key={level}
                      label={`${PRIORITY_CONFIG[level].label} priority`}
                      count={stats.priorityCounts[level]}
                      total={stats.total}
                      colorClass={PRIORITY_CONFIG[level].barClass}
                    />
                  ))}
                </div>
              </div>

              <div>
                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-3">
                  Sliding Fee Tiers
                </p>
                <div className="space-y-2.5">
                  {SLIDING_FEE_TIERS.map((tier) => (
                    <DistributionRow
                      key={tier.id}
                      label={`${tier.shortLabel} · ${tier.id === "FULL" ? "0%" : tier.discountPercent + "%"}`}
                      count={stats.tierCounts[tier.id] ?? 0}
                      total={stats.total}
                      colorClass={tier.barClass}
                    />
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* AI phone intake */}
        <PhoneIntakePanel />

        {/* Applications table */}
        <section>
          <h3 className="text-base font-bold text-slate-900 mb-4">Patient Queue</h3>
          <ApplicationTable applications={applications} />
        </section>
      </main>
    </div>
  );
}
