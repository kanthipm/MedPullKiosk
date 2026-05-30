// Sliding fee discount calculation for community health centers.
//
// Based on the HRSA sliding fee discount program model: a patient's annual
// household income is compared against the Federal Poverty Level (FPL) for their
// household size, and the resulting "% of FPL" maps to a discount pay-class.

// 2025 HHS Federal Poverty Guidelines — 48 contiguous states + DC.
// https://aspe.hhs.gov/topics/poverty-economic-mobility/poverty-guidelines
export const FPL_GUIDELINE_YEAR = 2025;
const FPL_BASE = 15_650; // one-person household
const FPL_PER_ADDITIONAL_PERSON = 5_500;

/** Annual Federal Poverty Level threshold for a given household size. */
export function federalPovertyLine(householdSize: number): number {
  const size = Math.max(1, Math.floor(householdSize || 1));
  return FPL_BASE + (size - 1) * FPL_PER_ADDITIONAL_PERSON;
}

export type SlidingFeeTierId = "A" | "B" | "C" | "D" | "E" | "FULL";

export interface SlidingFeeTier {
  id: SlidingFeeTierId;
  label: string;
  shortLabel: string;
  /** Inclusive upper bound of "% of FPL" for this tier (FULL = Infinity). */
  maxFplPercent: number;
  /** Percent discount off the clinic's standard fees. */
  discountPercent: number;
  /** Percent of the standard fee the patient is responsible for. */
  patientResponsibility: number;
  /** Flat nominal charge for the lowest tier (HRSA "nominal fee"). */
  nominalFee?: number;
  badgeClass: string;
  dotClass: string;
  barClass: string;
}

// Standard 6-class schedule: nominal fee at/below poverty, full fee above 200%.
export const SLIDING_FEE_TIERS: SlidingFeeTier[] = [
  {
    id: "A",
    label: "Nominal Fee",
    shortLabel: "Tier A",
    maxFplPercent: 100,
    discountPercent: 100,
    patientResponsibility: 0,
    nominalFee: 25,
    badgeClass: "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200",
    dotClass: "bg-emerald-500",
    barClass: "bg-emerald-500",
  },
  {
    id: "B",
    label: "80% Discount",
    shortLabel: "Tier B",
    maxFplPercent: 125,
    discountPercent: 80,
    patientResponsibility: 20,
    badgeClass: "bg-teal-50 text-teal-700 ring-1 ring-teal-200",
    dotClass: "bg-teal-500",
    barClass: "bg-teal-500",
  },
  {
    id: "C",
    label: "60% Discount",
    shortLabel: "Tier C",
    maxFplPercent: 150,
    discountPercent: 60,
    patientResponsibility: 40,
    badgeClass: "bg-sky-50 text-sky-700 ring-1 ring-sky-200",
    dotClass: "bg-sky-500",
    barClass: "bg-sky-500",
  },
  {
    id: "D",
    label: "40% Discount",
    shortLabel: "Tier D",
    maxFplPercent: 175,
    discountPercent: 40,
    patientResponsibility: 60,
    badgeClass: "bg-amber-50 text-amber-700 ring-1 ring-amber-200",
    dotClass: "bg-amber-500",
    barClass: "bg-amber-500",
  },
  {
    id: "E",
    label: "20% Discount",
    shortLabel: "Tier E",
    maxFplPercent: 200,
    discountPercent: 20,
    patientResponsibility: 80,
    badgeClass: "bg-orange-50 text-orange-700 ring-1 ring-orange-200",
    dotClass: "bg-orange-500",
    barClass: "bg-orange-500",
  },
  {
    id: "FULL",
    label: "Full Fee",
    shortLabel: "Full Fee",
    maxFplPercent: Infinity,
    discountPercent: 0,
    patientResponsibility: 100,
    badgeClass: "bg-slate-100 text-slate-600 ring-1 ring-slate-300",
    dotClass: "bg-slate-400",
    barClass: "bg-slate-400",
  },
];

export interface SlidingScaleResult {
  monthlyIncome: number;
  annualIncome: number;
  householdSize: number;
  fplThreshold: number;
  /** Annual income as a percent of the FPL threshold, rounded. */
  percentOfFpl: number;
  tier: SlidingFeeTier;
  /** True for any tier that grants a discount (i.e. not full fee). */
  qualifiesForDiscount: boolean;
}

export function tierForFplPercent(percentOfFpl: number): SlidingFeeTier {
  return (
    SLIDING_FEE_TIERS.find((t) => percentOfFpl <= t.maxFplPercent) ??
    SLIDING_FEE_TIERS[SLIDING_FEE_TIERS.length - 1]
  );
}

export function calculateSlidingScale(
  monthlyIncome: number,
  householdSize: number
): SlidingScaleResult {
  const safeMonthly = Math.max(0, monthlyIncome || 0);
  const annualIncome = safeMonthly * 12;
  const fplThreshold = federalPovertyLine(householdSize);
  const percentOfFpl =
    fplThreshold > 0 ? Math.round((annualIncome / fplThreshold) * 100) : 0;
  const tier = tierForFplPercent(percentOfFpl);
  return {
    monthlyIncome: safeMonthly,
    annualIncome,
    householdSize: Math.max(1, Math.floor(householdSize || 1)),
    fplThreshold,
    percentOfFpl,
    tier,
    qualifiesForDiscount: tier.id !== "FULL",
  };
}

export function formatCurrency(amount: number, maximumFractionDigits = 0): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits,
  }).format(amount);
}
