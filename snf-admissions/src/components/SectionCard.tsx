import { cn } from '@/lib/utils'
import type { ReactNode } from 'react'

interface Props {
  title: string
  icon?: ReactNode
  children: ReactNode
  className?: string
  headerExtra?: ReactNode
}

export function SectionCard({ title, icon, children, className, headerExtra }: Props) {
  return (
    <div className={cn('bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden', className)}>
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100 bg-slate-50">
        <div className="flex items-center gap-2">
          {icon && <span className="text-slate-500">{icon}</span>}
          <h2 className="text-xs font-semibold text-slate-600 uppercase tracking-wider">{title}</h2>
        </div>
        {headerExtra && <div>{headerExtra}</div>}
      </div>
      <div className="p-4">{children}</div>
    </div>
  )
}
