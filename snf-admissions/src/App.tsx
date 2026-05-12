import { useCallback, useReducer, useRef, useState } from 'react'
import {
  Building2, Upload, FilePlus, X, ChevronRight, Loader2,
  RefreshCw, Download, FileText, Settings, Eye, EyeOff, Cpu,
} from 'lucide-react'
import type { AppState, PatientProfile, UploadedFile } from '@/lib/types'
import { cn, formatFileSize, inferDocType, DOC_TYPE_LABELS } from '@/lib/utils'
import { SAMPLE_PROFILE } from '@/lib/sampleData'
import { extractTextFromPDF } from '@/lib/pdfExtract'
import { analyzeDocuments, hasBridge, getApiConfig, getStoredApiKey, saveApiKey } from '@/lib/openaiClient'
import { FileSidebar } from '@/components/FileSidebar'
import { ProcessingOverlay } from '@/components/ProcessingOverlay'
import { Dashboard } from '@/components/Dashboard'

// ─── Reducer ────────────────────────────────────────────────────────────────

type Action =
  | { type: 'ADD_FILES'; files: UploadedFile[] }
  | { type: 'SET_PROCESSING'; step: string }
  | { type: 'SET_PROFILE'; profile: PatientProfile }
  | { type: 'SET_ERROR'; error: string }
  | { type: 'RESET' }

const init: AppState = { phase: 'upload', files: [], profile: null, error: null, processingStep: null }

function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case 'ADD_FILES':    return { ...state, files: [...state.files, ...action.files], error: null }
    case 'SET_PROCESSING': return { ...state, phase: 'processing', processingStep: action.step, error: null }
    case 'SET_PROFILE':  return { ...state, phase: 'dashboard', profile: action.profile, processingStep: null }
    case 'SET_ERROR':    return { ...state, phase: 'upload', error: action.error, processingStep: null }
    case 'RESET':        return init
    default:             return state
  }
}

// ─── API Key dialog ──────────────────────────────────────────────────────────

function ApiKeyDialog({ onClose }: { onClose: () => void }) {
  const [key, setKey] = useState(getStoredApiKey)
  const [show, setShow] = useState(false)
  const save = () => { saveApiKey(key.trim()); onClose() }
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-6">
        <h2 className="text-base font-bold text-slate-900 mb-1">OpenAI API Key</h2>
        <p className="text-sm text-slate-500 mb-4">Required for live document analysis. Stored locally in your browser. Leave blank to use demo mode.</p>
        <div className="relative">
          <input
            type={show ? 'text' : 'password'}
            value={key}
            onChange={(e) => setKey(e.target.value)}
            placeholder="sk-..."
            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm pr-10 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button onClick={() => setShow(!show)} className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
            {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>
        <div className="flex gap-2 mt-4">
          <button onClick={save} className="flex-1 bg-blue-700 text-white text-sm font-semibold py-2 rounded-lg hover:bg-blue-800">Save</button>
          <button onClick={onClose} className="flex-1 border border-slate-300 text-slate-700 text-sm font-semibold py-2 rounded-lg hover:bg-slate-50">Cancel</button>
        </div>
      </div>
    </div>
  )
}

// ─── Upload dropzone ─────────────────────────────────────────────────────────

function UploadZone({
  rawFiles, error, onFilesAdded, onRemove, onAnalyze, onLoadSample, analyzing,
}: {
  rawFiles: File[]
  error: string | null
  onFilesAdded: (f: File[]) => void
  onRemove: (i: number) => void
  onAnalyze: () => void
  onLoadSample: () => void
  analyzing: boolean
}) {
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const accept = (files: File[]) => {
    const valid = files.filter((f) => f.type === 'application/pdf' || f.name.endsWith('.pdf'))
    if (valid.length) onFilesAdded(valid)
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-full p-8 gap-5">
      <div
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => { e.preventDefault(); setDragging(false); accept(Array.from(e.dataTransfer.files)) }}
        onClick={() => inputRef.current?.click()}
        className={cn(
          'w-full max-w-2xl border-2 border-dashed rounded-xl p-10 flex flex-col items-center gap-4 cursor-pointer transition-colors select-none',
          dragging ? 'border-blue-600 bg-blue-50' : 'border-slate-300 bg-white hover:border-slate-400 hover:bg-slate-50',
        )}
      >
        <div className={cn('h-14 w-14 rounded-full flex items-center justify-center', dragging ? 'bg-blue-100' : 'bg-slate-100')}>
          <Upload className={cn('h-7 w-7', dragging ? 'text-blue-600' : 'text-slate-400')} />
        </div>
        <div className="text-center">
          <p className="text-base font-semibold text-slate-800">Drop hospital transfer documents here</p>
          <p className="text-sm text-slate-500 mt-1">or <span className="text-blue-600 font-medium underline underline-offset-2">click to browse</span></p>
          <p className="text-xs text-slate-400 mt-2">Discharge summaries · medication records · insurance forms · faxed PDFs</p>
        </div>
        <div className="flex flex-wrap gap-2 justify-center">
          {['Discharge Summary', 'MAR / Medication List', 'Insurance / Eligibility', 'Fax / Scanned Notes'].map((l) => (
            <span key={l} className="text-xs px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 font-medium">{l}</span>
          ))}
        </div>
        <input ref={inputRef} type="file" multiple accept=".pdf,application/pdf" className="hidden" onChange={(e) => { accept(Array.from(e.target.files ?? [])); e.target.value = '' }} />
      </div>

      {rawFiles.length > 0 && (
        <div className="w-full max-w-2xl space-y-2">
          <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Queued ({rawFiles.length})</p>
          {rawFiles.map((f, i) => (
            <div key={i} className="flex items-center gap-3 px-4 py-3 bg-white rounded-lg border border-slate-200 shadow-sm">
              <div className="h-8 w-8 rounded-md bg-blue-50 flex items-center justify-center flex-shrink-0">
                <FilePlus className="h-4 w-4 text-blue-600" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-slate-800 truncate">{f.name}</p>
                <p className="text-xs text-slate-400">{DOC_TYPE_LABELS[inferDocType(f.name)]} · {formatFileSize(f.size)}</p>
              </div>
              <button onClick={() => onRemove(i)} className="text-slate-400 hover:text-slate-600 p-1 rounded"><X className="h-4 w-4" /></button>
            </div>
          ))}
        </div>
      )}

      {error && <div className="w-full max-w-2xl px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>}

      <div className="flex flex-col items-center gap-3 w-full max-w-2xl">
        <button
          onClick={onAnalyze}
          disabled={rawFiles.length === 0 || analyzing}
          className={cn('w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-semibold transition-colors',
            rawFiles.length > 0 && !analyzing ? 'bg-blue-700 text-white hover:bg-blue-800 shadow-sm' : 'bg-slate-100 text-slate-400 cursor-not-allowed')}
        >
          {analyzing ? <><Loader2 className="h-4 w-4 animate-spin" />Analyzing…</> : <>Analyze Documents<ChevronRight className="h-4 w-4" /></>}
        </button>
        <div className="flex items-center gap-3 w-full">
          <div className="flex-1 h-px bg-slate-200" /><span className="text-xs text-slate-400">or</span><div className="flex-1 h-px bg-slate-200" />
        </div>
        <button onClick={onLoadSample} className="w-full py-2.5 rounded-xl text-sm font-medium text-slate-600 border border-slate-300 bg-white hover:bg-slate-50 transition-colors">
          Load sample patient (demo mode)
        </button>
      </div>
    </div>
  )
}

// ─── Root App ────────────────────────────────────────────────────────────────

export default function App() {
  const [state, dispatch] = useReducer(reducer, init)
  const [rawFiles, setRawFiles] = useState<File[]>([])
  const [analyzing, setAnalyzing] = useState(false)
  const [showSettings, setShowSettings] = useState(false)
  const apiConfig = getApiConfig()
  const inKiosk = hasBridge()

  const handleFilesAdded = useCallback((files: File[]) => {
    const newUploaded: UploadedFile[] = files.map((f) => ({
      id: crypto.randomUUID(), name: f.name, size: f.size, docType: inferDocType(f.name), status: 'queued',
    }))
    setRawFiles((prev) => [...prev, ...files])
    dispatch({ type: 'ADD_FILES', files: newUploaded })
  }, [])

  const handleRemove = useCallback((i: number) => {
    setRawFiles((prev) => prev.filter((_, idx) => idx !== i))
  }, [])

  const handleAnalyze = useCallback(async () => {
    if (!rawFiles.length) return
    setAnalyzing(true)
    const STEPS = [
      'Reading uploaded files…',
      'Extracting text from PDFs…',
      'Parsing medication records…',
      'Analyzing clinical notes…',
      'Reviewing insurance documents…',
      'Generating patient profile…',
      'Flagging intake issues…',
    ]
    let stepIdx = 0
    dispatch({ type: 'SET_PROCESSING', step: STEPS[0] })
    const interval = setInterval(() => {
      stepIdx = Math.min(stepIdx + 1, STEPS.length - 1)
      dispatch({ type: 'SET_PROCESSING', step: STEPS[stepIdx] })
    }, 1800)
    const start = Date.now()
    try {
      // Extract PDF text in the browser (works both in kiosk and dev)
      dispatch({ type: 'SET_PROCESSING', step: 'Extracting text from PDFs…' })
      const fileTexts = await Promise.all(
        rawFiles.map(async (f) => ({ name: f.name, text: await extractTextFromPDF(f) }))
      )

      // analyzeDocuments picks the right path:
      //   - kiosk: calls AndroidBridge → Grok via BuildConfig key
      //   - dev browser: calls OpenAI directly via localStorage key
      //   - no key: returns demo:true
      const result = await analyzeDocuments(fileTexts, (step) => dispatch({ type: 'SET_PROCESSING', step }))

      if (result.demo) {
        // No key available — fall back to sample data
        await new Promise((r) => setTimeout(r, 800))
        dispatch({
          type: 'SET_PROFILE',
          profile: { ...SAMPLE_PROFILE, meta: { ...SAMPLE_PROFILE.meta, filesProcessed: rawFiles.map((f) => f.name), actualProcessingSeconds: 2 } },
        })
        return
      }

      const elapsed = Math.round((Date.now() - start) / 1000)
      const profile: PatientProfile = {
        ...result.profile,
        meta: {
          filesProcessed: rawFiles.map((f) => f.name),
          estimatedTimeSavedMinutes: 20 + rawFiles.length * 2,
          actualProcessingSeconds: elapsed,
          confidence: {},
        },
      }
      dispatch({ type: 'SET_PROFILE', profile })
    } catch (err: unknown) {
      dispatch({ type: 'SET_ERROR', error: err instanceof Error ? err.message : 'Analysis failed. Try demo mode.' })
    } finally {
      clearInterval(interval)
      setAnalyzing(false)
    }
  }, [rawFiles])

  const handleLoadSample = useCallback(async () => {
    dispatch({ type: 'SET_PROCESSING', step: 'Loading sample patient…' })
    await new Promise((r) => setTimeout(r, 1000))
    dispatch({ type: 'SET_PROFILE', profile: SAMPLE_PROFILE })
  }, [])

  const handleExport = () => {
    if (!state.profile) return
    const p = state.profile
    const lines = [
      'SNF ADMISSIONS SUMMARY', '======================',
      `Patient: ${p.snapshot.name ?? 'Unknown'}`, `DOB: ${p.snapshot.dob ?? '—'}  |  MRN: ${p.snapshot.mrn ?? '—'}`,
      `Diagnoses: ${p.snapshot.diagnoses.join('; ')}`, '',
      'CLINICAL SUMMARY', p.clinical.summary ?? 'N/A', '',
      'MEDICATIONS',
      ...p.medications.map((m) => `• ${m.name} ${m.dosage} ${m.frequency} (${m.route})${m.alerts.length ? ' [ALERT: ' + m.alerts.join(', ') + ']' : ''}`),
      '', 'INTAKE ISSUES',
      ...p.issues.map((i) => `[${i.severity.toUpperCase()}] ${i.title}: ${i.description}`),
      '', 'Generated by MedPull SNF Admissions Copilot',
    ]
    const blob = new Blob([lines.join('\n')], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `SNF_Intake_${(p.snapshot.name ?? 'Patient').replace(/\s+/g, '_')}.txt`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="flex flex-col h-screen overflow-hidden">
      {/* Header */}
      <header className="bg-white border-b border-slate-200 shadow-sm flex-shrink-0 z-10">
        <div className="px-4 sm:px-6 h-14 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="h-8 w-8 rounded-lg bg-blue-700 flex items-center justify-center flex-shrink-0">
              <Building2 className="h-4 w-4 text-white" />
            </div>
            <div>
              <span className="text-sm font-bold text-slate-900">SNF Admissions Copilot</span>
              <span className="hidden sm:inline text-xs text-slate-400 font-normal"> — MedPull</span>
              {state.profile?.snapshot.name && state.phase === 'dashboard' && (
                <p className="text-xs text-slate-500 leading-tight">{state.profile.snapshot.name}</p>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2">
            {state.phase === 'dashboard' && state.profile && (
              <>
                <button onClick={handleExport} className="flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-md border border-slate-300 text-slate-700 bg-white hover:bg-slate-50">
                  <Download className="h-3.5 w-3.5" />Export Summary
                </button>
                <button onClick={() => alert('Generate SNF Intake Packet — coming soon')} className="flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-md border border-blue-600 text-blue-700 bg-blue-50 hover:bg-blue-100">
                  <FileText className="h-3.5 w-3.5" />Generate Intake Packet
                </button>
              </>
            )}
            {/* Show model badge in kiosk; show settings gear in browser dev mode */}
            {inKiosk ? (
              <span className="flex items-center gap-1 text-xs text-slate-400 px-2 py-1 rounded-md bg-slate-50 border border-slate-200">
                <Cpu className="h-3 w-3" />{apiConfig.provider}
              </span>
            ) : (
              <button onClick={() => setShowSettings(true)} className="p-1.5 rounded-md text-slate-400 hover:text-slate-600 hover:bg-slate-100" title="API Key Settings">
                <Settings className="h-4 w-4" />
              </button>
            )}
            {state.phase !== 'upload' && (
              <button onClick={() => { dispatch({ type: 'RESET' }); setRawFiles([]) }} className="flex items-center gap-1.5 text-xs font-medium px-2.5 py-1.5 rounded-md text-slate-500 hover:text-slate-700 hover:bg-slate-100">
                <RefreshCw className="h-3.5 w-3.5" />New Patient
              </button>
            )}
          </div>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {state.files.length > 0 && <FileSidebar files={state.files} />}
        <main className="flex-1 overflow-y-auto scrollbar-thin">
          {state.phase === 'upload' && (
            <UploadZone rawFiles={rawFiles} error={state.error} onFilesAdded={handleFilesAdded} onRemove={handleRemove} onAnalyze={handleAnalyze} onLoadSample={handleLoadSample} analyzing={analyzing} />
          )}
          {state.phase === 'processing' && <ProcessingOverlay step={state.processingStep ?? 'Analyzing…'} fileCount={state.files.length || rawFiles.length} />}
          {state.phase === 'dashboard' && state.profile && <Dashboard profile={state.profile} />}
        </main>
      </div>

      {showSettings && <ApiKeyDialog onClose={() => setShowSettings(false)} />}
    </div>
  )
}
