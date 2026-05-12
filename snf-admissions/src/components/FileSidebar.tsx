import { FileText, CheckCircle2, Loader2, AlertCircle, Clock } from 'lucide-react'
import { cn, formatFileSize, DOC_TYPE_LABELS } from '@/lib/utils'
import type { UploadedFile } from '@/lib/types'

const STATUS_ICON = {
  queued:     <Clock className="h-3.5 w-3.5 text-slate-400" />,
  processing: <Loader2 className="h-3.5 w-3.5 text-blue-500 animate-spin" />,
  done:       <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />,
  error:      <AlertCircle className="h-3.5 w-3.5 text-red-500" />,
}

export function FileSidebar({ files }: { files: UploadedFile[] }) {
  return (
    <aside className="w-56 flex-shrink-0 bg-white border-r border-slate-200 flex flex-col overflow-hidden">
      <div className="px-4 py-3 border-b border-slate-100">
        <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Documents ({files.length})</p>
      </div>
      <div className="flex-1 overflow-y-auto scrollbar-thin p-2 space-y-1">
        {files.map((f) => (
          <div key={f.id} className={cn('flex items-start gap-2.5 px-3 py-2.5 rounded-lg', f.status === 'done' ? 'bg-emerald-50' : 'bg-slate-50')}>
            <div className="h-7 w-7 rounded-md bg-blue-100 flex items-center justify-center flex-shrink-0 mt-0.5">
              <FileText className="h-3.5 w-3.5 text-blue-600" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-medium text-slate-800 truncate leading-tight">{f.name}</p>
              <p className="text-xs text-slate-400 mt-0.5">{DOC_TYPE_LABELS[f.docType]}</p>
              <p className="text-xs text-slate-400">{formatFileSize(f.size)}</p>
            </div>
            <div className="flex-shrink-0 mt-0.5">{STATUS_ICON[f.status]}</div>
          </div>
        ))}
      </div>
      <div className="p-3 border-t border-slate-100">
        <p className="text-xs text-slate-400 leading-snug">Documents are analyzed and combined into a unified patient profile.</p>
      </div>
    </aside>
  )
}
