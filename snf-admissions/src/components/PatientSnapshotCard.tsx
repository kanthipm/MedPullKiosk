import { User, Stethoscope, MapPin, Activity } from 'lucide-react'
import { SectionCard } from './SectionCard'
import { ConfidenceBadge } from './ConfidenceBadge'
import type { PatientSnapshot, ProcessingMeta } from '@/lib/types'

function Row({ label, value, confidence }: { label: string; value: string | null; confidence?: number }) {
  return (
    <div className="flex gap-3 py-1.5 border-b border-slate-50 last:border-0">
      <span className="text-xs text-slate-400 w-32 flex-shrink-0 pt-0.5">{label}</span>
      <div className="flex items-start gap-1.5 flex-1 min-w-0">
        <span className="text-sm text-slate-800 leading-snug">{value ?? '—'}</span>
        {confidence != null && <ConfidenceBadge score={confidence} />}
      </div>
    </div>
  )
}

export function PatientSnapshotCard({ snapshot, meta }: { snapshot: PatientSnapshot; meta: ProcessingMeta }) {
  const c = meta.confidence
  return (
    <SectionCard title="Patient Snapshot" icon={<User className="h-3.5 w-3.5" />}>
      <Row label="Full Name" value={snapshot.name} confidence={c['name']} />
      <Row label="Date of Birth" value={snapshot.dob ? `${snapshot.dob} (age ${snapshot.age ?? '?'})` : snapshot.age ? `Age ${snapshot.age}` : null} confidence={c['dob']} />
      <Row label="MRN" value={snapshot.mrn} confidence={c['mrn']} />
      <Row label="Primary Diagnosis" value={snapshot.admittingDiagnosis} />
      <Row label="Attending Provider" value={snapshot.attendingProvider} />

      <div className="flex gap-3 pt-2">
        <span className="text-xs text-slate-400 w-32 flex-shrink-0 pt-0.5 flex items-center gap-1"><Stethoscope className="h-3 w-3" />Diagnoses</span>
        <div className="flex flex-wrap gap-1 flex-1">
          {snapshot.diagnoses.length > 0 ? snapshot.diagnoses.map((d, i) => (
            <span key={i} className="text-xs px-2 py-0.5 bg-slate-100 text-slate-700 rounded-full">{d}</span>
          )) : <span className="text-sm text-slate-400">—</span>}
        </div>
      </div>

      <div className="flex gap-3 pt-2">
        <span className="text-xs text-slate-400 w-32 flex-shrink-0 pt-0.5 flex items-center gap-1"><MapPin className="h-3 w-3" />Discharge To</span>
        <span className="text-sm text-slate-800">{snapshot.dischargeDestination ?? '—'}</span>
      </div>

      <div className="flex gap-3 pt-2">
        <span className="text-xs text-slate-400 w-32 flex-shrink-0 pt-0.5 flex items-center gap-1"><Activity className="h-3 w-3" />ADL Status</span>
        <span className="text-sm text-slate-800 leading-snug">{snapshot.adlStatus ?? '—'}</span>
      </div>
    </SectionCard>
  )
}
