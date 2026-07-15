import { Sparkles } from 'lucide-react'
import { relativeTime } from '../lib/format'

/** Kicker-style microlabel marking AI-generated narrative (demo .kicker). */
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
    <span className="inline-flex items-center gap-1.5 text-[10.5px] font-black uppercase tracking-[.08em] text-faint">
      <Sparkles size={11} className="text-oxy" aria-hidden />
      <span className="text-oxy">{label}</span>
      {generatedAt && (
        <span className="normal-case tracking-normal">· {relativeTime(generatedAt)}</span>
      )}
      {provider === 'fallback' && <span className="normal-case tracking-normal">· rules-based</span>}
    </span>
  )
}
