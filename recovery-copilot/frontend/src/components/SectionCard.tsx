import type { CSSProperties, ReactNode } from 'react'

/** White card in the demo house style: 22px radius, soft layered shadow, and
 *  an optional colored spine — the one place color enters a card. `sum` swaps
 *  to the demo's gradient "quick summary" treatment for AI narrative cards. */
export default function SectionCard({
  spine,
  title,
  eyebrow,
  aside,
  sum = false,
  children,
  className = '',
  style,
}: {
  spine?: string // tailwind bg-* class
  title?: string
  eyebrow?: ReactNode
  aside?: ReactNode
  sum?: boolean
  children: ReactNode
  className?: string
  style?: CSSProperties
}) {
  const surface = sum
    ? 'bg-gradient-to-br from-white to-[#f3f7ff] shadow-[0_12px_30px_rgba(20,30,60,.09)]'
    : 'bg-white shadow-card'
  return (
    <section
      style={style}
      className={`relative overflow-hidden rounded-card border border-ink/[.04] ${surface} ${className}`}
    >
      {spine && <span aria-hidden className={`absolute inset-y-0 left-0 w-[5px] ${spine}`} />}
      <div className={`p-5 ${spine ? 'pl-6' : ''}`}>
        {(title || eyebrow || aside) && (
          <div className="mb-3 flex items-baseline justify-between gap-3">
            <div>
              {eyebrow}
              {title && (
                <h2 className="text-[15px] font-black tracking-tight text-ink">{title}</h2>
              )}
            </div>
            {aside}
          </div>
        )}
        {children}
      </div>
    </section>
  )
}
