/** 14-day adherence strip: 1 = verified (solid), 0.5 = self-attested (half tone),
 *  0 = missed (outline). Reads left → right, oldest → today. */
export default function AdherenceDots({ days, rate }: { days: number[]; rate: number }) {
  return (
    <div className="flex items-center gap-3">
      <div
        className="flex items-center gap-1"
        role="img"
        aria-label={`Adherence last 14 days, ${Math.round(rate * 100)}%`}
      >
        {days.map((d, i) => (
          <span
            key={i}
            className={
              d >= 1
                ? 'h-2 w-2 rounded-full bg-oxy'
                : d >= 0.5
                  ? 'h-2 w-2 rounded-full bg-oxy/35'
                  : 'h-2 w-2 rounded-full border border-ink/15 bg-transparent'
            }
          />
        ))}
      </div>
      <span className="text-[15px] font-black tabular-nums tracking-tight text-ink">
        {Math.round(rate * 100)}%
      </span>
    </div>
  )
}
