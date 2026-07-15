import { PRIORITY, type Priority } from '../lib/risk'

/** Demo-style risk pill (.rpill) — high carries the glow ring. */
export default function PriorityBadge({ priority, className = '' }: { priority: Priority; className?: string }) {
  const p = PRIORITY[priority]
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1 text-[11px] font-black leading-none tracking-tight ${p.pill} ${className}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${p.dot}`} />
      {p.label}
    </span>
  )
}
