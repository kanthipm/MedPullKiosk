import { Bell, Mail, MessageSquare } from 'lucide-react'
import { useNotificationPreferences, useUpdateNotificationPreferences } from '../../api/queries'
import EmptyState from '../../components/EmptyState'
import SectionCard from '../../components/SectionCard'
import { SkeletonCard } from '../../components/Skeleton'

const CHANNEL_META: Record<string, { label: string; description: string; icon: typeof Bell }> = {
  in_app: {
    label: 'In-app',
    description: 'Shows in the notification bell when a patient reaches high priority.',
    icon: Bell,
  },
  sms: {
    label: 'Text message',
    description: 'Texts the assigned provider. Available once an SMS provider is connected.',
    icon: MessageSquare,
  },
  email: {
    label: 'Email',
    description: 'Emails the assigned provider. Available once an email provider is connected.',
    icon: Mail,
  },
}

export default function NotificationSettingsPage() {
  const { data: prefs, isLoading, isError } = useNotificationPreferences()
  const update = useUpdateNotificationPreferences()

  if (isLoading) return <SkeletonCard lines={4} />
  if (isError || !prefs) return <EmptyState title="Settings couldn't be loaded." />

  return (
    <div>
      <h1 className="text-2xl font-black tracking-tight text-ink">Notifications</h1>
      <p className="mt-1 text-sm text-faint">
        How the care team is alerted when a patient reaches high recovery priority.
      </p>

      <SectionCard className="mt-6">
        <ul className="divide-y divide-line">
          {prefs.map((pref) => {
            const meta = CHANNEL_META[pref.channel] ?? {
              label: pref.channel,
              description: '',
              icon: Bell,
            }
            const Icon = meta.icon
            return (
              <li key={pref.channel} className="flex items-center gap-4 py-4 first:pt-0 last:pb-0">
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-soft text-muted">
                  <Icon size={16} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-medium text-ink">
                    {meta.label}
                    {!pref.available && (
                      <span className="ml-2 rounded-full bg-soft px-2 py-0.5 text-[11px] font-medium text-faint ring-1 ring-inset ring-ink/[.06]">
                        Coming soon
                      </span>
                    )}
                  </span>
                  <span className="block text-sm text-faint">{meta.description}</span>
                </span>
                <button
                  type="button"
                  role="switch"
                  aria-checked={pref.enabled && pref.available}
                  disabled={!pref.available || update.isPending}
                  onClick={() =>
                    update.mutate([
                      {
                        channel: pref.channel,
                        enabled: !pref.enabled,
                        min_priority: pref.min_priority,
                      },
                    ])
                  }
                  className={`relative h-6 w-10 shrink-0 rounded-full transition-colors duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-oxy ${
                    pref.enabled && pref.available ? 'bg-oxy' : 'bg-[#dbe1ee]'
                  } ${!pref.available ? 'cursor-not-allowed opacity-50' : ''}`}
                >
                  <span
                    className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow-card transition-all duration-150 ${
                      pref.enabled && pref.available ? 'left-[18px]' : 'left-0.5'
                    }`}
                  />
                </button>
              </li>
            )
          })}
        </ul>
      </SectionCard>

      <p className="mt-4 text-xs text-faint">
        Alerts include the patient, the new priority, and the most important reason — with a
        link straight to their record.
      </p>
    </div>
  )
}
