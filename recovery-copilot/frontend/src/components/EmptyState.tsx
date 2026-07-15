import type { ReactNode } from 'react'

export default function EmptyState({
  title,
  children,
}: {
  title: string
  children?: ReactNode
}) {
  return (
    <div className="rounded-card border border-dashed border-ink/10 bg-white/50 px-6 py-10 text-center">
      <p className="text-[13.5px] font-extrabold text-body">{title}</p>
      {children && <p className="mt-1 text-[13px] font-semibold text-faint">{children}</p>}
    </div>
  )
}
