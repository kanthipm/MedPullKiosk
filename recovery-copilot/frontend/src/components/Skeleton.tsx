/** Silver shimmer loading bars — the app's only loading treatment. */

export function SkeletonLine({ className = '' }: { className?: string }) {
  return <div className={`shimmer rounded-lg ${className}`} />
}

export function SkeletonCard({ lines = 3 }: { lines?: number }) {
  return (
    <div className="rounded-card border border-ink/[.04] bg-white p-5 shadow-card">
      <div className="space-y-3">
        {Array.from({ length: lines }).map((_, i) => (
          <SkeletonLine key={i} className={`h-3.5 ${i === 0 ? 'w-1/4' : i % 2 ? 'w-full' : 'w-2/3'}`} />
        ))}
      </div>
    </div>
  )
}

/** Absolute overlay dropped over a live card during a full refresh: one
 *  conjoined silver rectangle shimmering as a single block, fully opaque so
 *  nothing underneath shows through, z-raised above any in-card layering. */
export function RefreshOverlay({ show }: { show: boolean }) {
  if (!show) return null
  return (
    <div aria-hidden className="absolute inset-0 z-20 animate-fadeIn">
      <div className="shimmer h-full w-full rounded-card" />
    </div>
  )
}
