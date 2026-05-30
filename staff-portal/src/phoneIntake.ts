// Placeholder data for the AI Phone Intake feature.
//
// In production, patients call a phone line and describe their situation to an
// AI agent. The agent transcribes the call, writes a plain-language summary,
// and assigns a care-priority risk level. A separate end-of-day pass reads all
// of the day's calls and produces the briefing in DAILY_DIGEST.
//
// None of that is built yet — everything below is hand-written demo content so
// the dashboard has something realistic to render.

import type { PriorityLevel } from "./lib/risk";

export interface PhoneCall {
  id: string;
  callerName: string;
  phoneMasked: string;
  time: string;
  durationLabel: string;
  language: string;
  /** AI plain-language summary of what the caller described. */
  summary: string;
  riskLevel: PriorityLevel;
  /** AI's one-line rationale for the assigned risk level. */
  riskNote: string;
  /** Appointment the agent booked, if any. */
  appointment?: string;
  /** Recommended staff follow-up when no appointment was booked. */
  followUp?: string;
}

export interface DailyCallDigest {
  date: string;
  totalCalls: number;
  highRisk: number;
  appointmentsScheduled: number;
  callbacksNeeded: number;
  /** AI narrative covering the day's risk picture and what happened. */
  narrative: string;
  highlights: string[];
}

export const PHONE_CALLS: PhoneCall[] = [
  {
    id: "call-001",
    callerName: "Rosa Delgado",
    phoneMasked: "(•••) •••-4821",
    time: "8:42 AM",
    durationLabel: "5m 18s",
    language: "Spanish",
    summary:
      "Type 2 diabetic, ran out of insulin three days ago and can't afford a refill. Reports blurred vision and feeling shaky. No insurance, works cash jobs, household of four.",
    riskLevel: "HIGH",
    riskNote: "Active medication gap with hyperglycemia symptoms; uninsured and below poverty line.",
    appointment: "Today 11:30 AM · Urgent same-day slot",
  },
  {
    id: "call-002",
    callerName: "Walter Boyd",
    phoneMasked: "(•••) •••-7103",
    time: "9:15 AM",
    durationLabel: "3m 47s",
    language: "English",
    summary:
      "Chest tightness when climbing stairs over the past week, eases with rest. Wants to know whether a visit is covered before coming in. Medicare application still pending.",
    riskLevel: "HIGH",
    riskNote: "Exertional chest symptoms warrant prompt evaluation; coverage uncertainty may delay care.",
    appointment: "Tomorrow 9:00 AM · Cardiac screening",
  },
  {
    id: "call-003",
    callerName: "Amara Okafor",
    phoneMasked: "(•••) •••-2299",
    time: "10:03 AM",
    durationLabel: "6m 02s",
    language: "English",
    summary:
      "Roughly 14 weeks pregnant with no prenatal care so far. Recently moved to the area, uninsured, asking about sliding-fee eligibility and where to start.",
    riskLevel: "HIGH",
    riskNote: "Unestablished prenatal care in second trimester; time-sensitive enrollment needed.",
    appointment: "Fri 2:15 PM · New OB intake",
  },
  {
    id: "call-004",
    callerName: "Marcus Bell",
    phoneMasked: "(•••) •••-5560",
    time: "11:28 AM",
    durationLabel: "4m 11s",
    language: "English",
    summary:
      "Lost his job and employer insurance last month. Needs to reschedule a follow-up and is worried about paying. Mentioned trouble sleeping and ongoing stress.",
    riskLevel: "MEDIUM",
    riskNote: "Coverage loss plus mental-health stressors; no acute symptoms reported.",
    followUp: "Re-screen for sliding fee; offer behavioral-health referral.",
  },
  {
    id: "call-005",
    callerName: "Linda Tran",
    phoneMasked: "(•••) •••-8847",
    time: "1:12 PM",
    durationLabel: "2m 36s",
    language: "Vietnamese",
    summary:
      "Routine refill request for blood-pressure medication. Reports readings have been stable at home and she feels well. Has CHIP coverage for her two children.",
    riskLevel: "LOW",
    riskNote: "Stable chronic condition, routine refill, no red-flag symptoms.",
    appointment: "Jun 3 10:45 AM · Med-check visit",
  },
  {
    id: "call-006",
    callerName: "Henry Caldwell",
    phoneMasked: "(•••) •••-3014",
    time: "2:49 PM",
    durationLabel: "3m 22s",
    language: "English",
    summary:
      "General question about whether he qualifies for the sliding-fee program and what documents to bring. No current symptoms; comparing options for a future physical.",
    riskLevel: "LOW",
    riskNote: "Informational call only; no clinical concern.",
    followUp: "Email eligibility checklist and document list.",
  },
];

export const DAILY_DIGEST: DailyCallDigest = {
  date: "May 29, 2026",
  totalCalls: 6,
  highRisk: 3,
  appointmentsScheduled: 4,
  callbacksNeeded: 2,
  narrative:
    "Six patients called in today. Three were flagged high priority and routed to same-day or next-day slots: an uninsured diabetic out of insulin, a patient with exertional chest pain, and a second-trimester pregnancy with no established prenatal care. Two medium/low callers had coverage or eligibility questions tied to recent job loss, and one was purely informational. Four appointments were booked directly from the line; two callers still need staff follow-up on sliding-fee re-screening and a behavioral-health referral.",
  highlights: [
    "3 high-priority calls all booked within 24 hours",
    "1 medication-access emergency (insulin) seen same day",
    "2 callers pending sliding-fee re-screen after coverage loss",
    "No callers left without a next step",
  ],
};
