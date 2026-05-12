import type { ApplicationStatus } from "../types";

interface StatusBadgeProps {
  status: ApplicationStatus;
  size?: "sm" | "md";
}

const STATUS_CONFIG: Record<
  ApplicationStatus,
  { label: string; className: string; dot: string }
> = {
  READY_FOR_REVIEW: {
    label: "Ready for Review",
    className: "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200",
    dot: "bg-emerald-500",
  },
  INCOMPLETE_APPLICATION: {
    label: "Incomplete Application",
    className: "bg-amber-50 text-amber-700 ring-1 ring-amber-200",
    dot: "bg-amber-500",
  },
  NEEDS_FOLLOW_UP: {
    label: "Needs Follow-Up",
    className: "bg-red-50 text-red-700 ring-1 ring-red-200",
    dot: "bg-red-500",
  },
};

export function StatusBadge({ status, size = "md" }: StatusBadgeProps) {
  const cfg = STATUS_CONFIG[status];
  const textSize = size === "sm" ? "text-xs" : "text-xs font-medium";
  const padding = size === "sm" ? "px-2 py-0.5" : "px-2.5 py-1";

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full ${padding} ${textSize} ${cfg.className}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${cfg.dot}`} />
      {cfg.label}
    </span>
  );
}
