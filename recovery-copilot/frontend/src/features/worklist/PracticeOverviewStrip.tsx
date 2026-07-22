import { Activity, BadgeDollarSign, ClipboardCheck, Eye, Users } from 'lucide-react'
import type { ReactNode } from 'react'
import { usePracticeOverview } from '../../api/queries'

/** The SPEC §9 practice overview — five numbers in one quiet strip, not a
 *  dashboard. Revenue is a demo estimate from the compliance engine's
 *  eligible-CPT rates, and is labeled as such. */
export default function PracticeOverviewStrip() {
  const { data } = usePracticeOverview()
  if (!data) return null

  return (
    <div className="glass grid grid-cols-2 gap-1 rounded-[18px] p-2.5 shadow-glass sm:grid-cols-5">
      <Stat icon={<Users size={14} />} label="RTM patients" value={String(data.rtm_patients)} />
      <Stat icon={<Eye size={14} />} label="Need review" value={String(data.needs_review)} />
      <Stat
        icon={<ClipboardCheck size={14} />}
        label="Ready to bill"
        value={String(data.ready_to_bill)}
        highlight={data.ready_to_bill > 0}
      />
      <Stat
        icon={<Activity size={14} />}
        label="Therapy adherence"
        value={data.therapy_adherence_pct != null ? `${data.therapy_adherence_pct}%` : '—'}
      />
      <Stat
        icon={<BadgeDollarSign size={14} />}
        label="Est. RTM revenue"
        value={`$${data.estimated_revenue.toLocaleString('en-US', { maximumFractionDigits: 0 })}`}
        hint="estimate"
      />
    </div>
  )
}

function Stat({
  icon,
  label,
  value,
  hint,
  highlight = false,
}: {
  icon: ReactNode
  label: string
  value: string
  hint?: string
  highlight?: boolean
}) {
  return (
    <div className="rounded-[12px] px-3 py-2">
      <span className="flex items-center gap-1.5 text-[10.5px] font-black uppercase tracking-[.06em] text-faint">
        <span className="text-oxy">{icon}</span>
        {label}
      </span>
      <span
        className={`mt-0.5 block text-[19px] font-black tabular-nums tracking-tight ${
          highlight ? 'text-risk-low' : 'text-ink'
        }`}
      >
        {value}
        {hint && (
          <span className="ml-1 align-middle text-[10px] font-bold uppercase tracking-[.04em] text-faint">
            {hint}
          </span>
        )}
      </span>
    </div>
  )
}
