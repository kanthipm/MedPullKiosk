import { Calendar } from 'lucide-react'
import { SectionCard } from './SectionCard'
import type { TimelineEvent } from '@/lib/types'

export function HospitalizationTimeline({ events }: { events: TimelineEvent[] }) {
  if (!events.length) return null
  return (
    <SectionCard title="Hospitalization Timeline" icon={<Calendar className="h-3.5 w-3.5" />}>
      <div className="relative">
        <div className="absolute left-[6px] top-2 bottom-2 w-px bg-slate-200" />
        <ol className="space-y-4 pl-6">
          {events.map((ev, i) => (
            <li key={i} className="relative">
              <span className="absolute -left-6 top-1 h-3.5 w-3.5 rounded-full border-2 border-white bg-blue-600 shadow" />
              <div className="flex flex-wrap items-baseline gap-2">
                <time className="text-xs font-semibold text-blue-700 whitespace-nowrap">{ev.date}</time>
                {ev.facility && <span className="text-xs text-slate-400">{ev.facility}</span>}
              </div>
              <p className="text-sm text-slate-700 leading-snug mt-0.5">{ev.event}</p>
            </li>
          ))}
        </ol>
      </div>
    </SectionCard>
  )
}
