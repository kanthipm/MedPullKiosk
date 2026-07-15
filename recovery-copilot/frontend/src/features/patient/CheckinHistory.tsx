import { usePatientCheckins } from '../../api/queries'
import Disclosure from '../../components/Disclosure'
import SectionCard from '../../components/SectionCard'
import { relativeTime } from '../../lib/format'
import type { Checkin } from '../../api/types'

function Conversation({ checkin }: { checkin: Checkin }) {
  return (
    <div className="space-y-2">
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

export default function CheckinHistory({ patientId }: { patientId: string }) {
  const { data } = usePatientCheckins(patientId)
  const checkins = data?.checkins ?? []
  const latest = checkins[0]

  // The latest patient-voice line is the preview — their own words, on top.
  const lastPatientLine = latest?.messages.filter((m) => m.who === 'patient').at(-1)?.text

  return (
    <SectionCard
      title="Check-ins"
      aside={
        latest && (
          <span className="text-xs font-bold tabular-nums text-faint">
            {relativeTime(latest.occurred_at)}
          </span>
        )
      }
    >
      {!latest ? (
        <p className="text-[13px] font-semibold text-faint">No recovery conversations yet.</p>
      ) : (
        <>
          {lastPatientLine && (
            <blockquote className="border-l-[3px] border-oxy/30 pl-3 text-[13.5px] font-semibold italic leading-relaxed text-body">
              “{lastPatientLine}”
            </blockquote>
          )}
          <div className="mt-3">
            <Disclosure label="Conversation history" hint={`${checkins.length} check-ins`}>
              <div className="space-y-5">
                {checkins.map((c) => (
                  <div key={c.id}>
                    <p className="mb-2 text-[10.5px] font-black uppercase tracking-[.08em] text-faint">
                      {relativeTime(c.occurred_at)}
                    </p>
                    <Conversation checkin={c} />
                  </div>
                ))}
              </div>
            </Disclosure>
          </div>
        </>
      )}
    </SectionCard>
  )
}
