// Composite care-priority assessment for a patient application.
//
// We have no clinical urgency field at intake, so "how in need of treatment"
// is approximated from the financial + coverage signals we DO collect:
//   1. Ability to pay        — % of Federal Poverty Level (lower = higher need)
//   2. Insurance gap         — uninsured / pending coverage raises need
//   3. Household burden       — minors and large dependent counts raise need
//
// These weights are intentionally simple and centralized here so a clinic can
// tune them as real intake data accumulates.

import type { PatientApplication } from "../types";
import { calculateSlidingScale, type SlidingScaleResult } from "./slidingScale";

export type PriorityLevel = "HIGH" | "MEDIUM" | "LOW";

export type FactorSeverity = "high" | "medium" | "low";

export interface RiskFactor {
  label: string;
  detail?: string;
  severity: FactorSeverity;
}

export interface RiskAssessment {
  level: PriorityLevel;
  score: number;
  factors: RiskFactor[];
  slidingScale: SlidingScaleResult;
}

const HIGH_THRESHOLD = 5;
const MEDIUM_THRESHOLD = 3;

export const PRIORITY_CONFIG: Record<
  PriorityLevel,
  { label: string; badgeClass: string; dotClass: string; barClass: string }
> = {
  HIGH: {
    label: "High",
    badgeClass: "bg-red-50 text-red-700 ring-1 ring-red-200",
    dotClass: "bg-red-500",
    barClass: "bg-red-500",
  },
  MEDIUM: {
    label: "Medium",
    badgeClass: "bg-amber-50 text-amber-700 ring-1 ring-amber-200",
    dotClass: "bg-amber-500",
    barClass: "bg-amber-500",
  },
  LOW: {
    label: "Low",
    badgeClass: "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200",
    dotClass: "bg-emerald-500",
    barClass: "bg-emerald-500",
  },
};

export function isUninsured(insuranceStatus: string): boolean {
  return /uninsured|no insurance|none|self[- ]?pay/i.test(insuranceStatus);
}

export function assessRisk(app: PatientApplication): RiskAssessment {
  const slidingScale = calculateSlidingScale(
    app.financial.monthlyIncome,
    app.household.householdSize
  );
  const factors: RiskFactor[] = [];
  let score = 0;

  // 1. Ability to pay — based on % of FPL.
  const pct = slidingScale.percentOfFpl;
  if (pct <= 100) {
    score += 3;
    factors.push({
      label: "Below poverty line",
      detail: `Income at ${pct}% of FPL`,
      severity: "high",
    });
  } else if (pct <= 150) {
    score += 2;
    factors.push({ label: "Low income", detail: `${pct}% of FPL`, severity: "medium" });
  } else if (pct <= 200) {
    score += 1;
    factors.push({ label: "Limited income", detail: `${pct}% of FPL`, severity: "low" });
  }

  // 2. Insurance gap.
  const ins = app.financial.insuranceStatus ?? "";
  if (isUninsured(ins)) {
    score += 2;
    factors.push({ label: "Uninsured", severity: "high" });
  } else if (/pending/i.test(ins)) {
    score += 1;
    factors.push({ label: "Coverage pending", detail: ins, severity: "medium" });
  } else if (/chip/i.test(ins) && /child/i.test(ins)) {
    score += 1;
    factors.push({
      label: "Adult coverage gap",
      detail: "Children covered, adult uninsured",
      severity: "medium",
    });
  }

  // 3. Household burden.
  const minors = app.household.minors ?? 0;
  if (minors > 0) {
    score += 1;
    factors.push({
      label: `${minors} minor${minors > 1 ? "s" : ""} in household`,
      severity: "medium",
    });
  }
  if (app.household.dependents >= 3) {
    score += 1;
    factors.push({
      label: "Large household",
      detail: `${app.household.dependents} dependents`,
      severity: "low",
    });
  }

  const level: PriorityLevel =
    score >= HIGH_THRESHOLD ? "HIGH" : score >= MEDIUM_THRESHOLD ? "MEDIUM" : "LOW";

  return { level, score, factors, slidingScale };
}
