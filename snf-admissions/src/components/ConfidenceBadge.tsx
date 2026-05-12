import { cn } from '@/lib/utils'

interface Props { score?: number; className?: string }

export function ConfidenceBadge({ score, className }: Props) {
  if (score == null) return null
  const pct = Math.round(score * 100)
  const color = pct >= 90 ? 'text-emerald-700 bg-emerald-50' : pct >= 75 ? 'text-amber-700 bg-amber-50' : 'text-red-700 bg-red-50'
  return (
    <span className={cn('inline-flex items-center text-[10px] font-semibold px-1.5 py-0.5 rounded', color, className)} title={`AI confidence: ${pct}%`}>
      {pct}%
    </span>
  )
}
