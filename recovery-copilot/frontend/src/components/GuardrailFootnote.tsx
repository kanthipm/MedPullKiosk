export default function GuardrailFootnote({ className = '' }: { className?: string }) {
  return (
    <p className={`text-[11px] font-bold text-faint ${className}`}>
      Monitoring signals for clinician review — not a diagnosis.
    </p>
  )
}
