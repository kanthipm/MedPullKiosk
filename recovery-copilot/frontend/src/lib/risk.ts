export type Priority = 'high' | 'medium' | 'low' | 'missing_data'
export type TrajectoryState = 'behind' | 'on' | 'ahead' | 'unknown'
export type ConfidenceLevel = 'high' | 'med' | 'low'
export type Urgency = 'today' | 'this_week' | 'routine'

/** Risk tokens ported from the orthopedic-demo design system. Color appears
 *  only in pills, spines, and status accents — everything else stays ink. */
export const PRIORITY = {
  high: {
    label: 'Needs review',
    dot: 'bg-risk-high',
    pill: 'bg-risk-high-bg text-risk-high shadow-[0_0_0_1.5px_rgba(229,72,77,.28),0_6px_16px_rgba(229,72,77,.2)]',
    spine: 'bg-risk-high',
    order: 0,
  },
  medium: {
    label: 'Needs attention',
    dot: 'bg-risk-med',
    pill: 'bg-risk-med-bg text-risk-med',
    spine: 'bg-risk-med',
    order: 1,
  },
  missing_data: {
    label: 'Missing data',
    dot: 'bg-risk-missing',
    pill: 'bg-risk-missing-bg text-risk-missing',
    spine: 'bg-risk-missing',
    order: 2,
  },
  low: {
    label: 'Stable',
    dot: 'bg-risk-low',
    pill: 'bg-risk-low-bg text-risk-low',
    spine: 'bg-risk-low',
    order: 3,
  },
} as const satisfies Record<Priority, unknown>

export const TRAJECTORY_LABEL: Record<TrajectoryState, string> = {
  behind: 'Behind expected curve',
  on: 'On expected curve',
  ahead: 'Ahead of expected curve',
  unknown: 'Trajectory not yet established',
}

export const URGENCY = {
  today: { label: 'Today', pill: 'bg-risk-high-bg text-risk-high' },
  this_week: { label: 'This week', pill: 'bg-risk-med-bg text-risk-med' },
  routine: { label: 'Routine', pill: 'bg-risk-missing-bg text-muted' },
} as const satisfies Record<Urgency, unknown>

export const CONFIDENCE_LABEL: Record<ConfidenceLevel, string> = {
  high: 'High confidence',
  med: 'Moderate confidence',
  low: 'Low confidence',
}

export type MetricStatus = 'flag' | 'watch' | 'ok' | 'nodata'

export const METRIC_STATUS = {
  flag: { label: 'Flag', pill: 'bg-risk-high-bg text-risk-high', spine: 'bg-risk-high' },
  watch: { label: 'Watch', pill: 'bg-risk-med-bg text-risk-med', spine: 'bg-risk-med' },
  ok: { label: 'OK', pill: 'bg-risk-low-bg text-risk-low', spine: 'bg-risk-low' },
  nodata: { label: 'No data', pill: 'bg-risk-missing-bg text-risk-missing', spine: 'bg-line' },
} as const satisfies Record<MetricStatus, unknown>

/** Signal-depth tiers, ported from the demo's Everyday/Advanced/Clinical toggle. */
export type Tier = 1 | 2 | 3
export const TIERS: { value: Tier; label: string }[] = [
  { value: 1, label: 'Everyday' },
  { value: 2, label: 'Advanced' },
  { value: 3, label: 'Clinical' },
]
