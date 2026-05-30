import { useState } from "react";
import {
  calculateSlidingScale,
  federalPovertyLine,
  formatCurrency,
  FPL_GUIDELINE_YEAR,
  SLIDING_FEE_TIERS,
} from "../lib/slidingScale";
import { FeeTierBadge } from "./FeeTierBadge";

// Width of each tier on the visual scale (in "% of FPL" units). The full-fee
// tier is open-ended, so we cap the rendered scale at 250% FPL.
const SCALE_MAX = 250;
const SEGMENTS = [
  { id: "A", from: 0, to: 100 },
  { id: "B", from: 100, to: 125 },
  { id: "C", from: 125, to: 150 },
  { id: "D", from: 150, to: 175 },
  { id: "E", from: 175, to: 200 },
  { id: "FULL", from: 200, to: SCALE_MAX },
] as const;

interface SlidingScaleCalculatorProps {
  /** Optional starting values (e.g. prefilled from a patient). */
  initialMonthlyIncome?: number;
  initialHouseholdSize?: number;
}

export function SlidingScaleCalculator({
  initialMonthlyIncome = 2000,
  initialHouseholdSize = 3,
}: SlidingScaleCalculatorProps) {
  const [incomeInput, setIncomeInput] = useState(String(initialMonthlyIncome));
  const [householdSize, setHouseholdSize] = useState(initialHouseholdSize);

  const monthlyIncome = Math.max(0, Number(incomeInput.replace(/[^0-9.]/g, "")) || 0);
  const result = calculateSlidingScale(monthlyIncome, householdSize);
  const { tier } = result;

  const markerLeft = `${(Math.min(result.percentOfFpl, SCALE_MAX) / SCALE_MAX) * 100}%`;

  const adjustHousehold = (delta: number) =>
    setHouseholdSize((s) => Math.min(12, Math.max(1, s + delta)));

  return (
    <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
      <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-100 bg-slate-50">
        <div className="flex items-center gap-2.5">
          <span className="text-brand-600">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M9 7h6m-6 4h6m-6 4h4M5 3h14a2 2 0 012 2v14a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2z" />
            </svg>
          </span>
          <h3 className="text-sm font-semibold text-slate-700 uppercase tracking-wider">
            Sliding Fee Calculator
          </h3>
        </div>
        <span className="text-xs text-slate-400">{FPL_GUIDELINE_YEAR} FPL</span>
      </div>

      <div className="px-5 py-5 space-y-5">
        {/* Inputs */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <label className="block">
            <span className="text-xs font-medium text-slate-500 uppercase tracking-wide">
              Monthly household income
            </span>
            <div className="relative mt-1.5">
              <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-sm">
                $
              </span>
              <input
                type="text"
                inputMode="numeric"
                value={incomeInput}
                onChange={(e) => setIncomeInput(e.target.value)}
                className="w-full rounded-lg border border-slate-200 bg-white pl-7 pr-3 py-2 text-sm text-slate-800 font-medium focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent"
              />
            </div>
            <span className="mt-1 block text-xs text-slate-400">
              {formatCurrency(result.annualIncome)} / year
            </span>
          </label>

          <label className="block">
            <span className="text-xs font-medium text-slate-500 uppercase tracking-wide">
              Household size
            </span>
            <div className="mt-1.5 flex items-center rounded-lg border border-slate-200 bg-white">
              <button
                type="button"
                onClick={() => adjustHousehold(-1)}
                className="px-3 py-2 text-slate-500 hover:text-brand-600 hover:bg-slate-50 rounded-l-lg transition-colors"
                aria-label="Decrease household size"
              >
                −
              </button>
              <span className="flex-1 text-center text-sm font-semibold text-slate-800">
                {householdSize}
              </span>
              <button
                type="button"
                onClick={() => adjustHousehold(1)}
                className="px-3 py-2 text-slate-500 hover:text-brand-600 hover:bg-slate-50 rounded-r-lg transition-colors"
                aria-label="Increase household size"
              >
                +
              </button>
            </div>
            <span className="mt-1 block text-xs text-slate-400">
              FPL: {formatCurrency(federalPovertyLine(householdSize))} / year
            </span>
          </label>
        </div>

        {/* Result */}
        <div className="rounded-lg bg-slate-50 border border-slate-100 p-4">
          <div className="flex items-center justify-between gap-3 flex-wrap">
            <div>
              <p className="text-xs text-slate-500 uppercase tracking-wide">Eligibility</p>
              <p className="text-2xl font-bold text-slate-900 leading-tight">
                {result.percentOfFpl}%
                <span className="text-sm font-medium text-slate-400 ml-1">of FPL</span>
              </p>
            </div>
            <FeeTierBadge tier={tier} />
          </div>

          {/* Tiered scale */}
          <div className="mt-4">
            <div className="relative">
              <div className="flex h-2.5 rounded-full overflow-hidden">
                {SEGMENTS.map((seg) => {
                  const t = SLIDING_FEE_TIERS.find((x) => x.id === seg.id)!;
                  const widthPct = ((seg.to - seg.from) / SCALE_MAX) * 100;
                  const active = t.id === tier.id;
                  return (
                    <div
                      key={seg.id}
                      style={{ width: `${widthPct}%` }}
                      className={`${t.barClass} ${active ? "opacity-100" : "opacity-30"} transition-opacity`}
                    />
                  );
                })}
              </div>
              {/* Marker */}
              <div
                className="absolute -top-1 h-4 w-0.5 bg-slate-900"
                style={{ left: markerLeft }}
              >
                <span className="absolute -top-1 left-1/2 -translate-x-1/2 h-2 w-2 rounded-full bg-slate-900 ring-2 ring-white" />
              </div>
            </div>
            <div className="mt-1.5 flex justify-between text-[10px] text-slate-400">
              <span>0%</span>
              <span>100%</span>
              <span>200%</span>
              <span>{SCALE_MAX}%+</span>
            </div>
          </div>

          {/* Breakdown */}
          <dl className="mt-4 grid grid-cols-3 gap-3 text-center">
            <div className="rounded-lg bg-white border border-slate-100 py-2.5">
              <dt className="text-[11px] text-slate-400 uppercase tracking-wide">Discount</dt>
              <dd className="text-base font-bold text-slate-800">{tier.discountPercent}%</dd>
            </div>
            <div className="rounded-lg bg-white border border-slate-100 py-2.5">
              <dt className="text-[11px] text-slate-400 uppercase tracking-wide">Patient pays</dt>
              <dd className="text-base font-bold text-slate-800">{tier.patientResponsibility}%</dd>
            </div>
            <div className="rounded-lg bg-white border border-slate-100 py-2.5">
              <dt className="text-[11px] text-slate-400 uppercase tracking-wide">Visit fee</dt>
              <dd className="text-base font-bold text-slate-800">
                {tier.nominalFee !== undefined ? formatCurrency(tier.nominalFee) : tier.id === "FULL" ? "Full" : `${tier.patientResponsibility}%`}
              </dd>
            </div>
          </dl>

          <p className="mt-3 text-xs text-slate-500">
            {tier.id === "FULL" ? (
              <>Income exceeds 200% of the Federal Poverty Level — does not qualify for a sliding fee discount.</>
            ) : tier.nominalFee !== undefined ? (
              <>Qualifies for the nominal {formatCurrency(tier.nominalFee)} visit fee (≤100% FPL).</>
            ) : (
              <>Qualifies for a {tier.discountPercent}% discount on standard clinic fees.</>
            )}
          </p>
        </div>

        <p className="text-[11px] text-slate-400 leading-relaxed">
          Estimate based on {FPL_GUIDELINE_YEAR} HHS Federal Poverty Guidelines (48 contiguous
          states). Final eligibility is determined by clinic staff after document verification.
        </p>
      </div>
    </div>
  );
}
