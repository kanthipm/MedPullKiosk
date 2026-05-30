import type { SlidingFeeTier } from "../lib/slidingScale";

interface FeeTierBadgeProps {
  tier: SlidingFeeTier;
  /** When set, appends the patient's % of FPL after the tier label. */
  percentOfFpl?: number;
  size?: "sm" | "md";
}

export function FeeTierBadge({ tier, percentOfFpl, size = "md" }: FeeTierBadgeProps) {
  const padding = size === "sm" ? "px-2 py-0.5" : "px-2.5 py-1";
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full ${padding} text-xs font-medium ${tier.badgeClass}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${tier.dotClass}`} />
      {tier.shortLabel}
      <span className="opacity-60">·</span>
      {tier.id === "FULL" ? "no discount" : `${tier.discountPercent}% off`}
      {percentOfFpl !== undefined && (
        <span className="opacity-60">({percentOfFpl}% FPL)</span>
      )}
    </span>
  );
}
