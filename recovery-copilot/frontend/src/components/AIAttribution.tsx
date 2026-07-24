import { Sparkles } from 'lucide-react'
import { relativeTime } from '../lib/format'

/** Quiet microlabel marking AI-generated narrative. */
export default function AIAttribution({
  label = 'AI summary',
  generatedAt,
  provider,
}: {
  label?: string
  generatedAt?: string
  provider?: string
}) {
  return (
    <span className="inline-flex items-center gap-1.5 text-[10.5px] font-medium uppercase tracking-[.1em] text-faint">
      <Sparkles size={11} className="text-brand" aria-hidden />
      <span className="text-brand">{label}</span>
      {generatedAt && (
        <span className="font-mono normal-case tracking-normal text-faint">
          · {relativeTime(generatedAt)}
        </span>
      )}
      {provider === 'fallback' && (
        <span className="normal-case tracking-normal">· rules-based</span>
      )}
    </span>
  )
}
