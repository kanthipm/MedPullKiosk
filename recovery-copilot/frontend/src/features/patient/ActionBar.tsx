import { ClipboardList, MessageSquare, RefreshCw, Sparkles, TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { useAssignTask, useDraftMessage, useEscalate, useMessagePatient } from '../../api/queries'
import Modal from '../../components/Modal'
import { useToast } from '../../components/Toast'

const inputCls =
  'w-full rounded-btn border border-line bg-white px-3 py-2 text-[13px] font-semibold text-ink placeholder:text-faint focus:outline focus:outline-2 focus:outline-oxy'

export default function ActionBar({
  patientId,
  patientName,
  onRefresh,
  refreshing,
}: {
  patientId: string
  patientName: string
  onRefresh: () => void
  refreshing: boolean
}) {
  const toast = useToast()
  const [modal, setModal] = useState<'assign' | 'message' | null>(null)
  const [taskTitle, setTaskTitle] = useState('')
  const [taskWhy, setTaskWhy] = useState('')
  const [messageText, setMessageText] = useState('')

  const assign = useAssignTask(patientId)
  const message = useMessagePatient(patientId)
  const escalate = useEscalate(patientId)
  const draft = useDraftMessage(patientId)

  const firstName = patientName.split(' ')[0]

  const submitTask = () => {
    if (!taskTitle.trim()) return
    assign.mutate(
      { title: taskTitle.trim(), why: taskWhy.trim() },
      {
        onSuccess: () => {
          toast(`Task assigned to ${firstName}`)
          setModal(null)
          setTaskTitle('')
          setTaskWhy('')
        },
        onError: () => toast('Task could not be assigned — try again', 'warning'),
      },
    )
  }

  const submitMessage = () => {
    if (!messageText.trim()) return
    message.mutate(messageText.trim(), {
      onSuccess: () => {
        toast(`Message queued for ${firstName} — sends when SMS goes live`, 'info')
        setModal(null)
        setMessageText('')
      },
      onError: () => toast('Message could not be queued — try again', 'warning'),
    })
  }

  const fireEscalate = () => {
    escalate.mutate(undefined, {
      onSuccess: () => toast('Escalated — care team notified', 'warning'),
      onError: () => toast('Escalation failed — try again', 'warning'),
    })
  }

  return (
    <>
      <div className="glass flex flex-wrap items-center gap-2 rounded-[18px] p-2.5 shadow-glass">
        <button type="button" className="qa-btn flex-1" onClick={() => setModal('assign')}>
          <ClipboardList size={15} className="text-oxy" /> Assign tasks
        </button>
        <button type="button" className="qa-btn flex-1" onClick={() => setModal('message')}>
          <MessageSquare size={15} className="text-oxy" /> Message
        </button>
        <button
          type="button"
          className="qa-btn flex-1 text-risk-high"
          onClick={fireEscalate}
          disabled={escalate.isPending}
        >
          <TriangleAlert size={15} /> Escalate
        </button>
        <button
          type="button"
          className="qa-btn flex-1"
          onClick={onRefresh}
          disabled={refreshing}
          title="Re-run the analysis and regenerate the AI summary"
        >
          <RefreshCw size={15} className={`text-oxy ${refreshing ? 'animate-spin' : ''}`} />
          {refreshing ? 'Refreshing…' : 'Refresh analysis'}
        </button>
      </div>

      {modal === 'assign' && (
        <Modal title={`Assign a task to ${firstName}`} onClose={() => setModal(null)}>
          <div className="space-y-3">
            <div>
              <label htmlFor="task-title" className="mb-1 block text-[11px] font-black uppercase tracking-[.06em] text-faint">
                Task
              </label>
              <input
                id="task-title"
                className={inputCls}
                placeholder="e.g. Walk 10 minutes, twice daily"
                value={taskTitle}
                onChange={(e) => setTaskTitle(e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="task-why" className="mb-1 block text-[11px] font-black uppercase tracking-[.06em] text-faint">
                Why it matters <span className="font-bold normal-case tracking-normal">(shown to the patient)</span>
              </label>
              <input
                id="task-why"
                className={inputCls}
                placeholder="e.g. Restores knee motion and circulation"
                value={taskWhy}
                onChange={(e) => setTaskWhy(e.target.value)}
              />
            </div>
            <button
              type="button"
              onClick={submitTask}
              disabled={!taskTitle.trim() || assign.isPending}
              className="w-full cursor-pointer rounded-btn bg-gradient-to-br from-oxy to-oxy-light px-4 py-2.5 text-[13px] font-extrabold text-white shadow-[0_8px_20px_rgba(47,128,237,.35)] transition-[transform,box-shadow] duration-150 hover:-translate-y-px active:translate-y-0 active:scale-[.98] disabled:pointer-events-none disabled:opacity-50"
            >
              {assign.isPending ? 'Assigning…' : 'Assign task'}
            </button>
          </div>
        </Modal>
      )}

      {modal === 'message' && (
        <Modal title={`Message ${firstName}`} onClose={() => setModal(null)}>
          <div className="space-y-3">
            <div>
              <div className="mb-1 flex items-center justify-between">
                <label htmlFor="message-text" className="block text-[11px] font-black uppercase tracking-[.06em] text-faint">
                  Message
                </label>
                <button
                  type="button"
                  disabled={draft.isPending}
                  onClick={() =>
                    draft.mutate(undefined, {
                      onSuccess: (result) => setMessageText(result.message),
                      onError: () => toast('Drafting failed — try again', 'warning'),
                    })
                  }
                  className="inline-flex cursor-pointer items-center gap-1 rounded-lg px-2 py-1 text-[11px] font-black text-oxy transition-colors duration-150 hover:bg-[#e8f1ff] disabled:opacity-50"
                >
                  <Sparkles size={11} className={draft.isPending ? 'animate-spin' : ''} />
                  {draft.isPending ? 'Drafting…' : 'Draft with AI'}
                </button>
              </div>
              <textarea
                id="message-text"
                rows={4}
                className={`${inputCls} ${draft.isPending ? 'shimmer text-transparent' : ''}`}
                placeholder="e.g. Hi Marcus — please take your temperature this morning and tell the check-in assistant the reading."
                value={messageText}
                onChange={(e) => setMessageText(e.target.value)}
                disabled={draft.isPending}
              />
              <p className="mt-1.5 text-[11px] font-semibold text-faint">
                AI drafts are editable — nothing sends without your review. Delivery is queued
                until the SMS integration is connected.
              </p>
            </div>
            <button
              type="button"
              onClick={submitMessage}
              disabled={!messageText.trim() || message.isPending}
              className="w-full cursor-pointer rounded-btn bg-gradient-to-br from-oxy to-oxy-light px-4 py-2.5 text-[13px] font-extrabold text-white shadow-[0_8px_20px_rgba(47,128,237,.35)] transition-[transform,box-shadow] duration-150 hover:-translate-y-px active:translate-y-0 active:scale-[.98] disabled:pointer-events-none disabled:opacity-50"
            >
              {message.isPending ? 'Queueing…' : 'Queue message'}
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}
