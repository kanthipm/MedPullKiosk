import { Clock, TrendingDown, Zap } from 'lucide-react'
import { SectionCard } from './SectionCard'
import type { ProcessingMeta } from '@/lib/types'

export function TimeSavedWidget({ meta }: { meta: ProcessingMeta }) {
  const manualMinutes = meta.estimatedTimeSavedMinutes + 5
  const aiMinutes = Math.ceil(meta.actualProcessingSeconds / 60) || 1
  const pctSaved = Math.round(((manualMinutes - aiMinutes) / manualMinutes) * 100)
  return (
    <SectionCard title="Workflow Time Saved" icon={<Zap className="h-3.5 w-3.5" />}>
      <div className="flex items-center justify-around gap-4 mb-4">
        <div className="text-center">
          <p className="text-xs text-slate-400 mb-1">Manual review</p>
          <p className="text-2xl font-bold text-slate-500 line-through decoration-red-400">{manualMinutes} min</p>
        </div>
        <div className="flex flex-col items-center text-emerald-600">
          <TrendingDown className="h-5 w-5" />
          <span className="text-xs font-bold">{pctSaved}% faster</span>
        </div>
        <div className="text-center">
          <p className="text-xs text-slate-400 mb-1">With AI copilot</p>
          <p className="text-2xl font-bold text-emerald-600">{aiMinutes} min</p>
        </div>
      </div>
      <div className="mb-4">
        <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
          <div className="h-full bg-emerald-500 rounded-full" style={{ width: `${pctSaved}%` }} />
        </div>
      </div>
      <div className="border-t border-slate-100 pt-3 space-y-1">
        <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Documents Processed</p>
        {meta.filesProcessed.map((f, i) => (
          <div key={i} className="flex items-center gap-2">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 flex-shrink-0" />
            <span className="text-xs text-slate-600 truncate">{f}</span>
          </div>
        ))}
      </div>
      <div className="flex items-center gap-1.5 text-xs text-slate-400 border-t border-slate-100 pt-3 mt-3">
        <Clock className="h-3 w-3" /><span>AI processing: {meta.actualProcessingSeconds}s</span>
      </div>
    </SectionCard>
  )
}
