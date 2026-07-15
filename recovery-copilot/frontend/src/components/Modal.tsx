import { X } from 'lucide-react'
import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

export default function Modal({
  title,
  onClose,
  children,
}: {
  title: string
  onClose: () => void
  children: ReactNode
}) {
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    // move focus into the dialog
    panelRef.current?.querySelector<HTMLElement>('input, textarea, button')?.focus()
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  // Portal to <body>: position:fixed resolves against any transformed ancestor
  // (e.g. a card mid-entrance-animation), which would pin the dialog to that
  // card instead of the viewport.
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        aria-hidden
        className="absolute inset-0 animate-fadeIn bg-ink/30 backdrop-blur-sm"
        onClick={onClose}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="glass-strong relative w-full max-w-md animate-modalIn rounded-card p-5 shadow-glass"
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-[15px] font-black tracking-tight text-ink">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="cursor-pointer rounded-lg p-1.5 text-muted transition-colors duration-150 hover:bg-white hover:text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-oxy"
          >
            <X size={16} />
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  )
}
