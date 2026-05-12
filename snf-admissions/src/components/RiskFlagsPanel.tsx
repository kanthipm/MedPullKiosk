import { ShieldAlert } from 'lucide-react'
import { SectionCard } from './SectionCard'
import { cn, riskColor, riskDot } from '@/lib/utils'
import type { RiskFlags, RiskLevel } from '@/lib/types'

const LABELS: Record<keyof RiskFlags, string> = {
  fallRisk: 'Fall Risk',
  behavioralRisk: 'Behavioral Risk',
  medicationNoncompliance: 'Med Noncompliance',
  housingInstability: 'Housing Instability',
  readmissionRisk: 'Readmission Risk',
}

export function RiskFlagsPanel({ risks }: { risks: RiskFlags }) {
  const highCount = Object.values(risks).filter((v) => v === 'high').length
  return (
    <SectionCard title="Operational Risk Flags" icon={<ShieldAlert className="h-3.5 w-3.5" />}
      headerExtra={highCount > 0 ? <span className="text-xs font-semibold text-red-700 bg-red-100 border border-red-200 px-2 py-0.5 rounded-full">{highCount} high risk</span> : undefined}>
      <div className="space-y-2">
        {(Object.entries(risks) as [keyof RiskFlags, RiskLevel][]).map(([key, level]) => (
          <div key={key} className={cn('flex items-center gap-2 px-3 py-2 rounded-lg border text-sm font-medium', riskColor(level))}>
            <span className={cn('h-2 w-2 rounded-full flex-shrink-0', riskDot(level))} />
            <span className="flex-1">{LABELS[key]}</span>
            <span className="text-xs font-semibold opacity-80">{level === 'unknown' ? 'Unknown' : level.charAt(0).toUpperCase() + level.slice(1)}</span>
          </div>
        ))}
      </div>
    </SectionCard>
  )
}
