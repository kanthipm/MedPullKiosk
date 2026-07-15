import { CircleCheck, Footprints } from 'lucide-react'
import { useIntegrations } from '../../api/queries'
import type { IntegrationProvider } from '../../api/types'
import EmptyState from '../../components/EmptyState'
import SectionCard from '../../components/SectionCard'
import { SkeletonCard } from '../../components/Skeleton'

/** Friendly labels for capability chips; gait metrics collapse into one chip. */
const CAPABILITY_LABELS: [string, string[]][] = [
  ['Steps', ['steps']],
  ['Heart rate', ['resting_hr', 'hr_sample']],
  ['HRV', ['hrv_rmssd']],
  ['Sleep', ['sleep_duration', 'sleep_stages']],
  ['SpO₂', ['spo2']],
  ['Respiration', ['respiratory_rate']],
  ['Temperature', ['skin_temp']],
  ['Workouts', ['exercise_session']],
]

function capabilityChips(p: IntegrationProvider): string[] {
  const chips = CAPABILITY_LABELS.filter(([, keys]) =>
    keys.some((k) => p.capabilities.includes(k)),
  ).map(([label]) => label)
  return chips
}

function ProviderCard({ p }: { p: IntegrationProvider }) {
  const connected = p.status === 'mock_connected'
  return (
    <div className="flex flex-col rounded-card border border-ink/[.04] bg-white p-5 shadow-card">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold text-ink">{p.name}</h3>
          {p.connected_patients > 0 && (
            <p className="mt-0.5 text-xs text-faint">
              {p.connected_patients} patient{p.connected_patients === 1 ? '' : 's'} using this
              device type
            </p>
          )}
        </div>
        {connected ? (
          <span className="inline-flex items-center gap-1 rounded-full bg-risk-low-bg px-2 py-0.5 text-[11px] font-medium text-risk-low ring-1 ring-inset ring-risk-low/25">
            <CircleCheck size={11} /> Connected (demo)
          </span>
        ) : (
          <span className="rounded-full bg-soft px-2 py-0.5 text-[11px] font-medium text-muted ring-1 ring-inset ring-ink/[.06]">
            Coming soon
          </span>
        )}
      </div>

      <div className="mt-3 flex flex-wrap gap-1.5">
        {capabilityChips(p).map((label) => (
          <span
            key={label}
            className="rounded-md bg-soft px-1.5 py-0.5 text-[11px] text-muted ring-1 ring-inset ring-ink/[.06]"
          >
            {label}
          </span>
        ))}
        {p.gait_capable && (
          <span className="inline-flex items-center gap-1 rounded-md bg-[#e8f1ff] px-1.5 py-0.5 text-[11px] font-medium text-oxy ring-1 ring-inset ring-oxy/20">
            <Footprints size={11} /> Gait & mobility
          </span>
        )}
      </div>

      <div className="mt-auto pt-4">
        <button
          type="button"
          disabled={!connected}
          className={`w-full rounded-lg px-3 py-1.5 text-xs font-medium transition-colors duration-150 ${
            connected
              ? 'bg-soft text-muted'
              : 'cursor-not-allowed bg-soft text-faint'
          }`}
        >
          {connected ? 'Manage connection' : 'Connect'}
        </button>
      </div>
    </div>
  )
}

export default function IntegrationsPage() {
  const { data, isLoading, isError } = useIntegrations()

  if (isLoading) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <SkeletonCard lines={3} />
        <SkeletonCard lines={3} />
        <SkeletonCard lines={3} />
      </div>
    )
  }
  if (isError || !data) {
    return <EmptyState title="Integrations couldn't be loaded." />
  }

  const providers = [...data.providers].sort(
    (a, b) => (a.status === 'mock_connected' ? -1 : 0) - (b.status === 'mock_connected' ? -1 : 0),
  )

  return (
    <div>
      <h1 className="text-2xl font-black tracking-tight text-ink">Integrations</h1>
      <p className="mt-1 max-w-2xl text-sm text-faint">
        Every source feeds the same Recovery Intelligence Engine through one normalized data
        store — connecting a new provider never changes what you see on the worklist.
      </p>

      <SectionCard sum spine="bg-oxy" className="mt-6">
        <p className="text-sm leading-relaxed text-body">
          This workspace is running on the <span className="font-medium text-ink">demo
          data source</span>. Production connections go live through a wearable aggregator
          (Terra or Junction) plus Apple Health for gait metrics — the connector scaffolding,
          webhook endpoint, and normalized observation store are already in place.
        </p>
      </SectionCard>

      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {providers.map((p) => (
          <ProviderCard key={p.key} p={p} />
        ))}
      </div>

      <p className="mt-6 text-xs text-faint">
        Gait &amp; mobility metrics (walking speed, asymmetry, steadiness) are measured only by
        Apple devices — patient charts adapt automatically to what each device can provide.
      </p>
    </div>
  )
}
