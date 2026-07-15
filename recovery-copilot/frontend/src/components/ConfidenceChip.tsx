import { CONFIDENCE_LABEL, type ConfidenceLevel } from '../lib/risk'

const STYLES: Record<ConfidenceLevel, string> = {
  high: 'bg-risk-low-bg text-risk-low',
  med: 'bg-risk-med-bg text-risk-med',
  low: 'bg-risk-missing-bg text-risk-missing',
}

/** Demo-style confidence chip (.confchip). High confidence is the norm, so it
 *  renders nothing unless asked — the chip flags reduced trust, not normalcy. */
export default function ConfidenceChip({
  level,
  showHigh = false,
}: {
  level: ConfidenceLevel
  showHigh?: boolean
}) {
  if (level === 'high' && !showHigh) return null
  return (
    <span
      className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-1 text-[10.5px] font-extrabold leading-none ${STYLES[level]}`}
    >
      {CONFIDENCE_LABEL[level]}
    </span>
  )
}
