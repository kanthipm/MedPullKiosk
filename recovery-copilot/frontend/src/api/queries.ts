import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchJson } from './client'
import type {
  AppNotification,
  Checkin,
  IntegrationProvider,
  NotificationPreference,
  PatientDetail,
  PatientMetrics,
  TimelineEvent,
  WorklistResponse,
} from './types'

export function useWorklist() {
  return useQuery({
    queryKey: ['worklist'],
    queryFn: () => fetchJson<WorklistResponse>('/api/worklist'),
  })
}

export function usePatient(id: string) {
  return useQuery({
    queryKey: ['patient', id],
    queryFn: () => fetchJson<PatientDetail>(`/api/patients/${id}`),
  })
}

export function usePatientMetrics(id: string, enabled = true) {
  return useQuery({
    queryKey: ['patient', id, 'metrics'],
    queryFn: () => fetchJson<PatientMetrics>(`/api/patients/${id}/metrics`),
    enabled,
  })
}

export function usePatientTimeline(id: string) {
  return useQuery({
    queryKey: ['patient', id, 'timeline'],
    queryFn: () => fetchJson<{ events: TimelineEvent[] }>(`/api/patients/${id}/timeline`),
  })
}

export function usePatientCheckins(id: string) {
  return useQuery({
    queryKey: ['patient', id, 'checkins'],
    queryFn: () => fetchJson<{ checkins: Checkin[] }>(`/api/patients/${id}/checkins`),
  })
}

export function useNotifications() {
  return useQuery({
    queryKey: ['notifications'],
    queryFn: () => fetchJson<{ notifications: AppNotification[] }>('/api/notifications?status=all'),
    refetchInterval: 60_000,
  })
}

export function useMarkNotificationRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => fetchJson(`/api/notifications/${id}/read`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })
}

export function useMarkAllNotificationsRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => fetchJson('/api/notifications/read-all', { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })
}

export function useIntegrations() {
  return useQuery({
    queryKey: ['integrations'],
    queryFn: () => fetchJson<{ providers: IntegrationProvider[] }>('/api/integrations'),
  })
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: ['notification-preferences'],
    queryFn: () => fetchJson<NotificationPreference[]>('/api/notification-preferences'),
  })
}

export function useUpdateNotificationPreferences() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (prefs: { channel: string; enabled: boolean; min_priority: string }[]) =>
      fetchJson<NotificationPreference[]>('/api/notification-preferences', {
        method: 'PUT',
        body: JSON.stringify(prefs),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notification-preferences'] }),
  })
}

export interface AskResult {
  answer: string
  patient_ids: string[]
  provider: string
  generated_at: string
}

export function useAsk() {
  return useMutation({
    mutationFn: (question: string) =>
      fetchJson<AskResult>('/api/ask', { method: 'POST', body: JSON.stringify({ question }) }),
  })
}

export function useDraftMessage(id: string) {
  return useMutation({
    mutationFn: () =>
      fetchJson<{ message: string; provider: string }>(
        `/api/patients/${id}/actions/draft-message`,
        { method: 'POST' },
      ),
  })
}

export function useAssignTask(id: string) {
  return useMutation({
    mutationFn: (task: { title: string; why: string }) =>
      fetchJson<{ ok: boolean }>(`/api/patients/${id}/actions/assign-task`, {
        method: 'POST',
        body: JSON.stringify(task),
      }),
  })
}

export function useMessagePatient(id: string) {
  return useMutation({
    mutationFn: (text: string) =>
      fetchJson<{ status: string }>(`/api/patients/${id}/actions/message`, {
        method: 'POST',
        body: JSON.stringify({ text }),
      }),
  })
}

export function useEscalate(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      fetchJson<{ ok: boolean }>(`/api/patients/${id}/actions/escalate`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })
}

export function useRecompute(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => fetchJson(`/api/patients/${id}/recompute`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['patient', id] })
      qc.invalidateQueries({ queryKey: ['worklist'] })
      // recompute can flip a patient to high priority, which creates a bell
      // notification server-side
      qc.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}
