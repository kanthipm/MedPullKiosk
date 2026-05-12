import { Loader2, FileSearch } from 'lucide-react'

export function ProcessingOverlay({ step, fileCount }: { step: string; fileCount: number }) {
  return (
    <div className="flex flex-col items-center justify-center min-h-full p-8">
      <div className="w-full max-w-md bg-white rounded-2xl border border-slate-200 shadow-sm p-10 flex flex-col items-center gap-6 text-center">
        <div className="relative h-20 w-20">
          <div className="absolute inset-0 rounded-full bg-blue-100 animate-ping opacity-40" />
          <div className="relative h-20 w-20 rounded-full bg-blue-700 flex items-center justify-center">
            <FileSearch className="h-9 w-9 text-white" />
          </div>
        </div>
        <div>
          <p className="text-lg font-semibold text-slate-900">Analyzing {fileCount} document{fileCount !== 1 ? 's' : ''}</p>
          <p className="text-sm text-slate-500 mt-1">AI is extracting and structuring patient information</p>
        </div>
        <div className="w-full flex items-center gap-3 px-4 py-3 bg-slate-50 rounded-lg border border-slate-200">
          <Loader2 className="h-4 w-4 text-blue-600 animate-spin flex-shrink-0" />
          <p className="text-sm text-slate-700 text-left">{step}</p>
        </div>
        <div className="flex gap-1.5">
          {[0, 1, 2, 3, 4].map((i) => (
            <div key={i} className="h-1.5 w-1.5 rounded-full bg-blue-600 animate-bounce" style={{ animationDelay: `${i * 0.15}s` }} />
          ))}
        </div>
        <p className="text-xs text-slate-400">This usually takes 15–30 seconds</p>
      </div>
    </div>
  )
}
