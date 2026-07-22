import {
  CalendarClock,
  ClipboardList,
  MessageSquare,
  NotebookPen,
  Phone,
  RefreshCw,
  Sparkles,
  TriangleAlert,
} from 'lucide-react'
import { useState } from 'react'
import {
  useAssignTask,
  useDraftMessage,
  useEscalate,
  useLogCall,
  useMessagePatient,
  useScheduleFollowup,
  useUpdatePlan,
} from '../../api/queries'
import Modal from '../../components/Modal'
import { useToast } from '../../components/Toast'

const inputCls =
  'w-full rounded-btn border border-line bg-white px-3 py-2 text-[13px] font-semibold text-ink placeholder:text-faint focus:outline focus:outline-2 focus:outline-oxy'

const primaryBtnCls =
  'w-full cursor-pointer rounded-btn bg-gradient-to-br from-oxy to-oxy-light px-4 py-2.5 text-[13px] font-extrabold text-white shadow-[0_8px_20px_rgba(47,128,237,.35)] transition-[transform,box-shadow] duration-150 hover:-translate-y-px active:translate-y-0 active:scale-[.98] disabled:pointer-events-none disabled:opacity-50'

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
  const [modal, setModal] = useState<'assign' | 'message' | 'call' | 'followup' | 'plan' | null>(
    null,
  )
  const [taskTitle, setTaskTitle] = useState('')
  const [taskWhy, setTaskWhy] = useState('')
  const [messageText, setMessageText] = useState('')
  const [callMinutes, setCallMinutes] = useState('5')
  const [callNote, setCallNote] = useState('')
  const [followupWhen, setFollowupWhen] = useState('')
  const [followupNote, setFollowupNote] = useState('')
  const [planSummary, setPlanSummary] = useState('')

  const assign = useAssignTask(patientId)
  const message = useMessagePatient(patientId)
  const escalate = useEscalate(patientId)
  const draft = useDraftMessage(patientId)
  const logCall = useLogCall(patientId)
  const followup = useScheduleFollowup(patientId)
  const updatePlan = useUpdatePlan(patientId)

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

  const submitCall = () => {
    const minutes = Math.max(1, Number(callMinutes) || 5)
    logCall.mutate(
      { minutes, note: callNote.trim() },
      {
        onSuccess: () => {
          toast(`Call logged — ${minutes} min counted toward treatment management`)
          setModal(null)
          setCallMinutes('5')
          setCallNote('')
        },
        onError: () => toast('Call could not be logged — try again', 'warning'),
      },
    )
  }

  const submitFollowup = () => {
    if (!followupWhen.trim()) return
    followup.mutate(
      { when: followupWhen.trim(), note: followupNote.trim() },
      {
        onSuccess: () => {
          toast(`Follow-up scheduled for ${firstName}`)
          setModal(null)
          setFollowupWhen('')
          setFollowupNote('')
        },
        onError: () => toast('Follow-up could not be scheduled — try again', 'warning'),
      },
    )
  }

  const submitPlan = () => {
    if (!planSummary.trim()) return
    updatePlan.mutate(planSummary.trim(), {
      onSuccess: () => {
        toast('Treatment plan update logged')
        setModal(null)
        setPlanSummary('')
      },
      onError: () => toast('Plan update failed — try again', 'warning'),
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
        <button type="button" className="qa-btn flex-1" onClick={() => setModal('call')}>
          <Phone size={15} className="text-oxy" /> Call patient
        </button>
        <button type="button" className="qa-btn flex-1" onClick={() => setModal('followup')}>
          <CalendarClock size={15} className="text-oxy" /> Follow-up
        </button>
        <button type="button" className="qa-btn flex-1" onClick={() => setModal('plan')}>
          <NotebookPen size={15} className="text-oxy" /> Update plan
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

      {modal === 'call' && (
        <Modal title={`Log a call with ${firstName}`} onClose={() => setModal(null)}>
          <div className="space-y-3">
            <div>
              <label htmlFor="call-minutes" className="mb-1 block text-[11px] font-black uppercase tracking-[.06em] text-faint">
                Call length (minutes)
              </label>
              <input
                id="call-minutes"
                type="number"
                min={1}
                max={60}
                className={inputCls}
                value={callMinutes}
                onChange={(e) => setCallMinutes(e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="call-note" className="mb-1 block text-[11px] font-black uppercase tracking-[.06em] text-faint">
                Note <span className="font-bold normal-case tracking-normal">(optional)</span>
              </label>
              <input
                id="call-note"
                className={inputCls}
                placeholder="e.g. Pain reviewed, PT plan reinforced"
                value={callNote}
                onChange={(e) => setCallNote(e.target.value)}
              />
            </div>
            <p className="text-[11px] font-semibold text-faint">
              Logged as live interactive communication — counts toward the treatment-management
              requirement (CPT 98980).
            </p>
            <button
              type="button"
              onClick={submitCall}
              disabled={logCall.isPending}
              className={primaryBtnCls}
            >
              {logCall.isPending ? 'Logging…' : 'Log call'}
            </button>
          </div>
        </Modal>
      )}

      {modal === 'followup' && (
        <Modal title={`Schedule a follow-up for ${firstName}`} onClose={() => setModal(null)}>
          <div className="space-y-3">
            <div>
              <label htmlFor="followup-when" className="mb-1 block text-[11px] font-black uppercase tracking-[.06em] text-faint">
                When
              </label>
              <input
                id="followup-when"
                className={inputCls}
                placeholder="e.g. Thursday 2:30 PM"
                value={followupWhen}
                onChange={(e) => setFollowupWhen(e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="followup-note" className="mb-1 block text-[11px] font-black uppercase tracking-[.06em] text-faint">
                Note <span className="font-bold normal-case tracking-normal">(optional)</span>
              </label>
              <input
                id="followup-note"
                className={inputCls}
                placeholder="e.g. Recheck swelling and ROM"
                value={followupNote}
                onChange={(e) => setFollowupNote(e.target.value)}
              />
            </div>
            <button
              type="button"
              onClick={submitFollowup}
              disabled={!followupWhen.trim() || followup.isPending}
              className={primaryBtnCls}
            >
              {followup.isPending ? 'Scheduling…' : 'Schedule follow-up'}
            </button>
          </div>
        </Modal>
      )}

      {modal === 'plan' && (
        <Modal title={`Update ${firstName}'s treatment plan`} onClose={() => setModal(null)}>
          <div className="space-y-3">
            <div>
              <label htmlFor="plan-summary" className="mb-1 block text-[11px] font-black uppercase tracking-[.06em] text-faint">
                What changed
              </label>
              <textarea
                id="plan-summary"
                rows={4}
                className={inputCls}
                placeholder="e.g. Advance to stage-2 exercises; ice protocol after each session"
                value={planSummary}
                onChange={(e) => setPlanSummary(e.target.value)}
              />
            </div>
            <p className="text-[11px] font-semibold text-faint">
              Logged to the RTM record and counted as treatment-management time.
            </p>
            <button
              type="button"
              onClick={submitPlan}
              disabled={!planSummary.trim() || updatePlan.isPending}
              className={primaryBtnCls}
            >
              {updatePlan.isPending ? 'Saving…' : 'Log plan update'}
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}
