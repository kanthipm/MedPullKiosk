import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
import type { FileDocType, RiskLevel, AuthStatus } from './types'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function inferDocType(filename: string): FileDocType {
  const lower = filename.toLowerCase()
  if (lower.includes('discharge') || lower.includes('summary') || lower.includes('d/c'))
    return 'discharge_summary'
  if (lower.includes('mar') || lower.includes('medication') || lower.includes('med'))
    return 'medication_record'
  if (lower.includes('insurance') || lower.includes('eligib') || lower.includes('auth') || lower.includes('coverage'))
    return 'insurance'
  if (lower.includes('fax') || lower.includes('scan') || lower.includes('note'))
    return 'fax'
  return 'unknown'
}

export const DOC_TYPE_LABELS: Record<FileDocType, string> = {
  discharge_summary: 'Discharge Summary',
  medication_record: 'Medication Record',
  insurance: 'Insurance / Eligibility',
  fax: 'Fax / Scan',
  unknown: 'Document',
}

export function riskColor(level: RiskLevel): string {
  switch (level) {
    case 'high':     return 'bg-red-100 text-red-800 border-red-200'
    case 'moderate': return 'bg-amber-100 text-amber-800 border-amber-200'
    case 'low':      return 'bg-emerald-100 text-emerald-800 border-emerald-200'
    default:         return 'bg-slate-100 text-slate-600 border-slate-200'
  }
}

export function riskDot(level: RiskLevel): string {
  switch (level) {
    case 'high':     return 'bg-red-500'
    case 'moderate': return 'bg-amber-500'
    case 'low':      return 'bg-emerald-500'
    default:         return 'bg-slate-400'
  }
}

export function authStatusLabel(status: AuthStatus): { label: string; cls: string } {
  switch (status) {
    case 'authorized': return { label: 'Authorized', cls: 'bg-emerald-100 text-emerald-800 border-emerald-200' }
    case 'pending':    return { label: 'Pending',    cls: 'bg-amber-100 text-amber-800 border-amber-200' }
    case 'denied':     return { label: 'Denied',     cls: 'bg-red-100 text-red-800 border-red-200' }
    default:           return { label: 'Unknown',    cls: 'bg-slate-100 text-slate-600 border-slate-200' }
  }
}
