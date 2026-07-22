# SPEC.md — P1: Remote Therapeutic Monitoring (RTM)

> Product spec for the P1 Orthopedic RTM track. The existing Recovery Copilot
> console (see [README.md](README.md)) is the foundation this builds on.

## Overview

P1 expands the existing MedPull Recovery Copilot into an AI-powered Remote
Therapeutic Monitoring (RTM) platform for orthopedic recovery.

The current Recovery Copilot remains the foundation. Patients continue
receiving AI recovery check-ins while providers use the same dashboard and
patient views. P1 adds RTM-specific workflows including patient onboarding,
therapeutic monitoring, provider treatment management, AI documentation,
compliance tracking, and billing readiness.

## CPT code reference

Source: Centers for Medicare & Medicaid Services.

| CPT code | Covers | MedPull role |
| --- | --- | --- |
| 98975 | Initial setup & patient education | AI onboarding, consent, patient education, enrollment tracking |
| 98985 | Musculoskeletal monitoring, 2–15 days | Daily recovery check-ins, adherence tracking, monitoring-day counter |
| 98977 | Musculoskeletal monitoring, 16–30 days | Same as above for longer monitoring periods |
| 98979 | First 10 minutes of treatment management (new in 2026) | Automatically track provider review time + require one live interaction |
| 98980 | First 20 minutes of treatment management | Time tracking, documentation, communication logging |
| 98981 | Each additional 20 minutes | Additional provider time tracking |

## Goals

- Monitor orthopedic recovery remotely
- Improve therapy adherence
- Detect at-risk patients earlier
- Reduce provider administrative burden
- Simplify CMS RTM workflows
- Increase RTM reimbursement

## Design philosophy

P1 should feel like a natural extension of the existing MedPull application.

Reuse the current UI, colors, typography, spacing, components, and navigation.
The interface should remain clean, minimal, and AI-first.

Providers should never be overwhelmed with dashboards or raw patient data.
Every screen should answer three questions:

1. Who needs my attention?
2. Why?
3. What should I do next?

Display concise AI summaries first, with detailed information available
through expandable sections only when needed.

## RTM workflow

### 1. Patient enrollment (CPT 98975)

Using the existing chatbot, AI automatically guides patients through:

- RTM education
- Consent
- Baseline assessment
- Pain and mobility baseline
- Surgery or injury details
- Recovery pathway assignment

Provider status:

- Enrollment Complete
- Education Complete
- Consent Complete
- Baseline Complete

### 2. Daily therapeutic monitoring (CPT 98985 / 98977)

Patients receive scheduled conversational recovery check-ins through SMS or
the MedPull app.

The AI dynamically asks follow-up questions based on recovery stage and
previous responses while monitoring:

- Pain
- Mobility
- Swelling
- Medication adherence
- Physical therapy adherence
- Home exercise completion
- Daily activity
- Sleep
- New symptoms

Each patient includes a simple monitoring card:

> **Monitoring Progress**
> 14 / 16 Days
> Monitoring Eligible

### 3. Recovery intelligence

After every conversation, AI converts patient responses into concise clinical
insights.

Each patient receives a Recovery Status:

- On Track
- Needs Review
- High Risk

Along with:

- AI recovery summary
- Clinical flags
- Recovery trends
- Recommended next action

The goal is to summarize rather than display raw questionnaire data.

### 4. Provider worklist

The homepage functions as an intelligent worklist rather than an EHR
dashboard.

Display only: patient, recovery status, one-line AI summary, monitoring
progress, and suggested action.

| Patient | Status | Summary | Action |
| --- | --- | --- | --- |
| Jane Doe | High Risk | Pain increasing, missed PT | Review today |
| John Smith | Needs Review | Missed check-ins | Message patient |
| Emily Chen | On Track | Recovery progressing normally | No action |

### 5. Patient detail

Each patient page begins with an AI-generated recovery summary.

Expandable sections include:

- Recovery timeline
- Conversation history
- Trend graphs
- Documentation
- Provider notes
- Future wearable data

The AI summary should remain the primary focus.

### 6. Provider treatment management (CPT 98979 / 98980 / 98981)

Providers can:

- Message patient
- Call patient
- Schedule follow-up
- Escalate to nurse
- Update treatment plan

All interactions are automatically logged while provider review time is
tracked in the background.

### 7. AI documentation

Automatically generate:

- Recovery summaries
- Encounter notes
- Monthly RTM summaries
- Outreach documentation
- Treatment management documentation

Providers simply review and approve.

### 8. RTM compliance & billing

Every patient includes an RTM Readiness Card:

> **RTM Readiness**
> Enrollment Complete
> Education Complete
> Baseline Complete
> Monitoring Days: 14 / 16
> Provider Review: 14 minutes
> Interactive Communication: Required
> Documentation: Ready
>
> **Billing Eligibility**
> CPT 98975
> CPT 98985
> CPT 98980 (6 minutes remaining)
>
> **Suggested Next Action**
> Call patient to complete RTM requirements.

Once all requirements are satisfied, MedPull automatically marks the patient
as **Ready to Bill**.

The Compliance Engine continuously tracks CMS requirements, identifies
missing steps, and recommends the next action without requiring providers to
understand billing rules.

### 9. Practice overview

A lightweight overview displaying:

- RTM patients
- Patients needing review
- Patients ready for billing
- Therapy adherence
- Estimated RTM revenue opportunity

Avoid complex dashboards or unnecessary analytics.

## Future integrations

Design interfaces now using mocked data.

Future integrations include:

- Apple Health
- Wearables
- Physical therapy platforms
- Electronic Health Records
- Patient portals

All external data should flow through the Recovery Intelligence Engine so
providers receive concise AI summaries rather than raw data streams.

## Product philosophy

MedPull is an AI RTM Copilot, not another patient monitoring dashboard.

Patients recover through conversational AI while providers receive concise,
actionable insights instead of overwhelming data. By combining intelligent
monitoring, provider prioritization, automated documentation, compliance
tracking, and billing readiness into a single workflow, MedPull enables
practices to deliver better patient care while making RTM significantly
easier to operate and reimburse.
