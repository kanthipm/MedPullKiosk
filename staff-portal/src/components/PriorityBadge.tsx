import { PRIORITY_CONFIG, type PriorityLevel } from "../lib/risk";

interface PriorityBadgeProps {
  level: PriorityLevel;
  size?: "sm" | "md";
}

export function PriorityBadge({ level, size = "md" }: PriorityBadgeProps) {
  const cfg = PRIORITY_CONFIG[level];
  const padding = size === "sm" ? "px-2 py-0.5" : "px-2.5 py-1";
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full ${padding} text-xs font-medium ${cfg.badgeClass}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${cfg.dotClass}`} />
      {cfg.label} Priority
    </span>
  );
}
