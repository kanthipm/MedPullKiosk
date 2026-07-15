import { Search, Sparkles, X } from 'lucide-react'
import { useState } from 'react'
import { useAsk, type AskResult } from '../../api/queries'
import AIAttribution from '../../components/AIAttribution'
import SectionCard from '../../components/SectionCard'
import { SkeletonLine } from '../../components/Skeleton'

const SUGGESTIONS = [
  'Who reported fever this week?',
  'Which patients are behind schedule?',
  'Anyone not wearing their device?',
]

/** Natural-language questions over the roster — the answer both explains and
 *  filters (the worklist narrows to the cited patients). */
export default function AskBar({
  result,
  onResult,
  onClear,
}: {
  result: AskResult | null
  onResult: (result: AskResult) => void
  onClear: () => void
}) {
  const [question, setQuestion] = useState('')
  const ask = useAsk()

  const submit = (q: string) => {
    const trimmed = q.trim()
    if (trimmed.length < 3 || ask.isPending) return
    setQuestion(trimmed)
    ask.mutate(trimmed, { onSuccess: onResult })
  }

  return (
    <div>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          submit(question)
        }}
        className="glass flex items-center gap-2 rounded-[18px] p-2 shadow-glass"
      >
        <span className="grid h-8 w-8 shrink-0 place-items-center rounded-[11px] bg-gradient-to-br from-oxy to-oxy-light text-white">
          <Sparkles size={14} />
        </span>
        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Ask about your patients — symptoms, progress, adherence, data gaps…"
          aria-label="Ask about your patients"
          className="min-w-0 flex-1 bg-transparent text-[13.5px] font-semibold text-ink placeholder:text-faint focus:outline-none"
        />
        {(question || result) && (
          <button
            type="button"
            aria-label="Clear question"
            onClick={() => {
              setQuestion('')
              onClear()
            }}
            className="grid h-7 w-7 cursor-pointer place-items-center rounded-lg text-faint transition-colors duration-150 hover:bg-white hover:text-ink"
          >
            <X size={14} />
          </button>
        )}
        <button
          type="submit"
          disabled={question.trim().length < 3 || ask.isPending}
          className="qa-btn !flex-none px-4"
        >
          <Search size={13} className="text-oxy" />
          {ask.isPending ? 'Thinking…' : 'Ask'}
        </button>
      </form>

      {!result && !ask.isPending && (
        <div className="mt-2 flex flex-wrap items-center gap-1.5 px-1">
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => submit(s)}
              className="cursor-pointer rounded-full bg-white/60 px-2.5 py-1 text-[11px] font-bold text-muted ring-1 ring-inset ring-ink/[.05] transition-all duration-150 hover:-translate-y-px hover:bg-white hover:text-ink"
            >
              {s}
            </button>
          ))}
        </div>
      )}

      {ask.isPending && (
        <div className="mt-3 space-y-2.5 rounded-card border border-ink/[.04] bg-white p-5 shadow-card">
          <SkeletonLine className="h-3 w-1/5" />
          <SkeletonLine className="h-3.5 w-full" />
          <SkeletonLine className="h-3.5 w-3/4" />
        </div>
      )}

      {result && !ask.isPending && (
        <SectionCard
          sum
          spine="bg-oxy"
          className="mt-3 animate-fadeIn"
          eyebrow={<AIAttribution label="AI answer" generatedAt={result.generated_at} provider={result.provider} />}
          aside={
            result.patient_ids.length > 0 ? (
              <span className="rounded-full bg-[#e8f1ff] px-2.5 py-1 text-[10.5px] font-black leading-none text-oxy">
                Showing {result.patient_ids.length} match
                {result.patient_ids.length === 1 ? '' : 'es'}
              </span>
            ) : undefined
          }
        >
          <p className="text-[13.5px] font-semibold leading-[1.55] text-body">{result.answer}</p>
        </SectionCard>
      )}
    </div>
  )
}
