import type { ConfidenceLevel, MetricStatus, Priority, TrajectoryState, Urgency } from '../lib/risk'

export interface DataConfidence {
  score: number
  level: ConfidenceLevel
  days_with_data?: number
}

export interface Trajectory {
  state: TrajectoryState
  pct: number | null
}

export interface WorklistPatient {
  id: string
  name: string
  initials: string
  priority: Priority
  reason: string
  procedure_display: string
  postop_day: number
  days_since_discharge: number
  last_checkin_at: string | null
  assigned_provider: { name: string; role: string }
  data_confidence: DataConfidence
  trajectory: Trajectory
}

export interface WorklistResponse {
  as_of: string
  stats: { total: number; high: number; medium: number; missing: number; low: number }
  briefing: { text: string; generated_at: string; provider: string }
  patients: WorklistPatient[]
}

export interface RiskReason {
  code: string
  text: string
  severity: number
}

export interface SuggestedAction {
  title: string
  detail: string
  urgency: Urgency
}

export interface PatientDetail {
  id: string
  name: string
  initials: string
  age: number
  sex: string
  procedure_display: string
  postop_day: number
  surgery_date: string
  discharge_date: string
  surgeon: string
  assigned_provider: string
  device: { provider: string; model: string; last_sync_at: string | null } | null
  risk: { level: Priority; score: number; reasons: RiskReason[]; computed_at: string }
  data_confidence: DataConfidence
  trajectory: Trajectory
  summary: { text: string; generated_at: string; provider: string }
  actions: SuggestedAction[]
  rtm: { days_with_data: number; window_days: number; qualifies: boolean }
  last_checkin_at: string | null
}

export interface MetricInsight {
  metric_key: string
  name: string
  status: MetricStatus
  status_text: string
  finding: string
  confidence: ConfidenceLevel
  coverage_text: string
  next_step: string | null
  guarded: boolean
  unit: string
  series: { date: string; value: number }[]
  baseline_mean: number | null
}

export interface PatientMetrics {
  data_confidence: DataConfidence
  trajectory: Trajectory & {
    change_point_day: number | null
    actual: { day: number; v: number }[]
    expected: { day: number; lo: number; mid: number; hi: number }[]
  }
  composite: {
    index: number
    level: string
    drivers: { metric_type: string; label: string; contribution: number; direction: string }[]
  }
  metrics: MetricInsight[]
  adherence: { rate: number; verified: number; assigned: number; self_attested: number; days: number[] }
}

export interface TimelineEvent {
  date: string
  kind: 'surgery' | 'discharge' | 'checkin' | 'flag' | 'change_point' | 'today'
  label: string
}

export interface CheckinMessage {
  who: 'patient' | 'copilot'
  text: string
}

export interface Checkin {
  id: number
  occurred_at: string
  channel: string
  messages: CheckinMessage[]
}

export interface AppNotification {
  id: number
  patient_id: string
  patient_name: string
  kind: string
  title: string
  body: string
  channel: string
  status: string
  created_at: string
}

export interface IntegrationProvider {
  key: string
  name: string
  status: 'mock_connected' | 'coming_soon'
  capabilities: string[]
  connected_patients: number
  gait_capable: boolean
}

export interface NotificationPreference {
  channel: string
  enabled: boolean
  min_priority: string
  available: boolean
}
