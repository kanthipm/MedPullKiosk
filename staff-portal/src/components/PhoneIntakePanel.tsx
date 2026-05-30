import { PHONE_CALLS, DAILY_DIGEST, type PhoneCall } from "../phoneIntake";
import { PriorityBadge } from "./PriorityBadge";

function SparkleIcon({ className = "w-4 h-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
        d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
    </svg>
  );
}

function DigestStat({ value, label, accent }: { value: number; label: string; accent: string }) {
  return (
    <div className="rounded-lg bg-white/70 border border-white/60 px-3 py-2.5 text-center">
      <p className={`text-2xl font-bold leading-none ${accent}`}>{value}</p>
      <p className="mt-1 text-[11px] font-medium text-slate-500 leading-tight">{label}</p>
    </div>
  );
}

function CallCard({ call }: { call: PhoneCall }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-slate-800">{call.callerName}</p>
          <p className="mt-0.5 text-xs text-slate-400">
            {call.phoneMasked} · {call.time} · {call.durationLabel} · {call.language}
          </p>
        </div>
        <PriorityBadge level={call.riskLevel} size="sm" />
      </div>

      <p className="mt-3 text-sm text-slate-600 leading-relaxed">{call.summary}</p>

      <div className="mt-3 flex items-start gap-2 rounded-lg bg-slate-50 border border-slate-100 px-3 py-2">
        <span className="mt-0.5 text-brand-500 flex-shrink-0">
          <SparkleIcon className="w-3.5 h-3.5" />
        </span>
        <p className="text-xs text-slate-600 leading-relaxed">
          <span className="font-semibold text-slate-700">AI risk read · </span>
          {call.riskNote}
        </p>
      </div>

      <div className="mt-3">
        {call.appointment ? (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700 ring-1 ring-emerald-200">
            <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            {call.appointment}
          </span>
        ) : (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700 ring-1 ring-amber-200">
            <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
            </svg>
            Needs follow-up
          </span>
        )}
        {call.followUp && (
          <p className="mt-2 text-xs text-slate-500">
            <span className="font-medium text-slate-600">Next step: </span>
            {call.followUp}
          </p>
        )}
      </div>
    </div>
  );
}

export function PhoneIntakePanel() {
  return (
    <section>
      <div className="flex items-center justify-between mb-4">
        <div>
          <h3 className="text-base font-bold text-slate-900">AI Phone Intake</h3>
          <p className="text-sm text-slate-500 mt-0.5">
            Patients describe their situation by phone; AI summarizes and triages each call.
          </p>
        </div>
        <span className="inline-flex items-center gap-1.5 rounded-full bg-violet-50 px-2.5 py-1 text-xs font-medium text-violet-700 ring-1 ring-violet-200">
          <SparkleIcon className="w-3.5 h-3.5" />
          Simulated demo
        </span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
        {/* Daily AI briefing */}
        <div className="rounded-xl border border-slate-200 bg-gradient-to-br from-brand-50 to-violet-50 shadow-sm overflow-hidden">
          <div className="px-5 py-3.5 border-b border-white/60 flex items-center gap-2.5">
            <span className="text-brand-600">
              <SparkleIcon className="w-4 h-4" />
            </span>
            <div>
              <h4 className="text-sm font-semibold text-slate-700 uppercase tracking-wider leading-tight">
                Today's AI Briefing
              </h4>
              <p className="text-[11px] text-slate-500">{DAILY_DIGEST.date}</p>
            </div>
          </div>
          <div className="px-5 py-5 space-y-4">
            <div className="grid grid-cols-2 gap-2.5">
              <DigestStat value={DAILY_DIGEST.totalCalls} label="Calls today" accent="text-slate-900" />
              <DigestStat value={DAILY_DIGEST.highRisk} label="High priority" accent="text-red-600" />
              <DigestStat
                value={DAILY_DIGEST.appointmentsScheduled}
                label="Appts booked"
                accent="text-emerald-600"
              />
              <DigestStat
                value={DAILY_DIGEST.callbacksNeeded}
                label="Need follow-up"
                accent="text-amber-600"
              />
            </div>

            <p className="text-sm text-slate-600 leading-relaxed">{DAILY_DIGEST.narrative}</p>

            <ul className="space-y-1.5">
              {DAILY_DIGEST.highlights.map((h) => (
                <li key={h} className="flex items-start gap-2 text-xs text-slate-600">
                  <span className="mt-1.5 h-1.5 w-1.5 flex-shrink-0 rounded-full bg-brand-500" />
                  {h}
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Recent calls */}
        <div className="lg:col-span-2 space-y-3">
          <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide">
            Recent Calls · {PHONE_CALLS.length} today
          </p>
          {PHONE_CALLS.map((call) => (
            <CallCard key={call.id} call={call} />
          ))}
        </div>
      </div>
    </section>
  );
}
