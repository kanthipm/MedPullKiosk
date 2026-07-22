import { ChevronRight, MessageSquareText, Smartphone } from 'lucide-react'
import { useState } from 'react'
import { usePatientCheckins } from '../../api/queries'
import SectionCard from '../../components/SectionCard'
import { relativeTime } from '../../lib/format'
import type { Checkin } from '../../api/types'

const chip = 'rounded-full px-2 py-0.5 text-[10px] font-black uppercase leading-none'

const TONE: Record<string, { label: string; cls: string }> = {
  worse: { label: 'Reported worse', cls: 'bg-risk-high-bg text-risk-high' },
  better: { label: 'Reported better', cls: 'bg-risk-low-bg text-risk-low' },
  steady: { label: 'About the same', cls: 'bg-line/60 text-muted' },
}

function Transcript({ checkin }: { checkin: Checkin }) {
  return (
    <div className="space-y-2 pt-3">
      {checkin.messages.map((m, i) => (
        <div key={i} className={`flex ${m.who === 'patient' ? 'justify-end' : 'justify-start'}`}>
          <div
            className={`max-w-[85%] rounded-2xl px-3.5 py-2 text-[13px] font-semibold leading-relaxed ${
              m.who === 'patient'
                ? 'rounded-br-md bg-gradient-to-br from-oxy to-oxy-light text-white shadow-[0_6px_16px_rgba(47,128,237,.25)]'
                : 'rounded-bl-md bg-soft text-body'
            }`}
          >
            {m.text}
          </div>
        </div>
      ))}
    </div>
  )
}

function CheckinRow({ checkin }: { checkin: Checkin }) {
  const [open, setOpen] = useState(false)
  const tone = checkin.digest.tone ? TONE[checkin.digest.tone] : null

  return (
    <li className="py-3 first:pt-0 last:pb-0">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="group flex w-full cursor-pointer items-start gap-2.5 rounded-lg text-left focus-visible:outline focus-visible:outline-2 focus-visible:outline-oxy"
      >
        <ChevronRight
          size={15}
          className={`mt-1 shrink-0 text-faint transition-transform duration-200 ${open ? 'rotate-90' : ''}`}
        />
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-1.5">
            <span className="text-[12px] font-black tabular-nums text-ink">
              {relativeTime(checkin.occurred_at)}
            </span>
            <span className="inline-flex items-center gap-1 text-[10.5px] font-bold uppercase tracking-[.05em] text-faint">
              {checkin.channel === 'sms' ? (
                <MessageSquareText size={11} />
              ) : (
                <Smartphone size={11} />
              )}
              {checkin.channel}
            </span>
            {tone && <span className={`${chip} ${tone.cls}`}>{tone.label}</span>}
            {checkin.digest.topics.map((topic) => (
              <span key={topic} className={`${chip} bg-line/50 text-muted`}>
                {topic}
              </span>
            ))}
          </span>
          {checkin.digest.highlight && (
            <span className="mt-1 block border-l-[3px] border-oxy/30 pl-2.5 text-[13px] font-semibold italic leading-relaxed text-body">
              “{checkin.digest.highlight}”
            </span>
          )}
        </span>
      </button>
      <div
        className="grid transition-[grid-template-rows,opacity] duration-300 ease-out"
        style={{ gridTemplateRows: open ? '1fr' : '0fr', opacity: open ? 1 : 0 }}
      >
        <div className="overflow-hidden">
          <div className="pl-[25px]">
            <Transcript checkin={checkin} />
          </div>
        </div>
      </div>
    </li>
  )
}

export default function CheckinHistory({ patientId }: { patientId: string }) {
  const { data } = usePatientCheckins(patientId)
  const [showAll, setShowAll] = useState(false)
  const checkins = data?.checkins ?? []
  const visible = showAll ? checkins : checkins.slice(0, 4)

  return (
    <SectionCard
      title="Check-ins"
      aside={
        checkins.length > 0 && (
          <span className="text-xs font-bold tabular-nums text-faint">
            {checkins.length} total · last {relativeTime(checkins[0].occurred_at).toLowerCase()}
          </span>
        )
      }
    >
      {checkins.length === 0 ? (
        <p className="text-[13px] font-semibold text-faint">No recovery conversations yet.</p>
      ) : (
        <>
          <ul className="divide-y divide-line">
            {visible.map((c) => (
              <CheckinRow key={c.id} checkin={c} />
            ))}
          </ul>
          {checkins.length > 4 && (
            <button
              type="button"
              onClick={() => setShowAll((s) => !s)}
              className="mt-2 cursor-pointer rounded-lg px-1 py-1 text-[12px] font-black text-oxy transition-colors duration-150 hover:bg-[#e8f1ff]"
            >
              {showAll ? 'Show recent only' : `Show all ${checkins.length} check-ins`}
            </button>
          )}
          <p className="mt-2.5 text-[11px] font-semibold text-faint">
            Quotes are the patient's own words, selected from each conversation. Expand a
            check-in for the full transcript.
          </p>
        </>
      )}
    </SectionCard>
  )
}
