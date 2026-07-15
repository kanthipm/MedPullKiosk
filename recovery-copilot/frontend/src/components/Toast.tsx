import { CircleCheck, Info, TriangleAlert } from 'lucide-react'
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'

type ToastKind = 'success' | 'info' | 'warning'

interface ToastItem {
  id: number
  kind: ToastKind
  text: string
}

const ToastContext = createContext<(text: string, kind?: ToastKind) => void>(() => {})

export function useToast() {
  return useContext(ToastContext)
}

const ICON: Record<ToastKind, ReactNode> = {
  success: <CircleCheck size={15} className="text-risk-low" />,
  info: <Info size={15} className="text-oxy" />,
  warning: <TriangleAlert size={15} className="text-risk-med" />,
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const nextId = useRef(1)

  const push = useCallback((text: string, kind: ToastKind = 'success') => {
    const id = nextId.current++
    setToasts((t) => [...t, { id, kind, text }])
    window.setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3800)
  }, [])

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed bottom-6 left-1/2 z-[100] flex w-full max-w-sm -translate-x-1/2 flex-col items-center gap-2 px-4"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className="glass pointer-events-auto flex w-auto animate-toastIn items-center gap-2.5 rounded-2xl px-4 py-2.5 text-[13px] font-bold text-ink shadow-glass"
          >
            {ICON[toast.kind]}
            {toast.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
