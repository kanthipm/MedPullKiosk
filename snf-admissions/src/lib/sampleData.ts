import type { PatientProfile, MedReconciliation, FollowUpWorkflow } from './types'

export const SAMPLE_PROFILE: PatientProfile = {
  snapshot: {
    name: 'Dorothy M. Harrington',
    dob: '1942-03-14',
    age: 82,
    mrn: 'MRN-0047821',
    admittingDiagnosis: 'Hip fracture (right femoral neck), status post ORIF',
    diagnoses: [
      'Right femoral neck fracture, s/p ORIF (2024-10-28)',
      'Type 2 Diabetes Mellitus, insulin-dependent',
      'Hypertensive heart disease',
      'Moderate vascular dementia',
      'Chronic kidney disease, Stage 3a',
      'Osteoporosis',
    ],
    dischargeDestination: 'Skilled Nursing Facility — subacute rehab',
    adlStatus: 'Maximum assist ×2 for transfers; dependent for bathing, dressing lower extremities; moderate assist for grooming',
    attendingProvider: 'Dr. Renata Solberg, MD — Orthopedic Surgery',
  },

  clinical: {
    summary: '82-year-old female admitted 10/28/2024 following a mechanical fall at home resulting in a right femoral neck fracture. Patient underwent successful ORIF on 10/29/2024. Post-op course complicated by mild delirium (resolved) and urinary tract infection (treated with Macrobid). Patient cleared for SNF placement by orthopedics and PT. She requires significant ADL assistance, daily wound care, and continuation of PT/OT services. Cognitive baseline is moderate vascular dementia per family; MMSE 16/30 on discharge.',
    mobilityStatus: 'WBAT right lower extremity with front-wheeled walker. Requires maximum assist ×2 for bed-to-chair transfers and ambulation >10 feet. PT recommends 5×/week therapeutic exercises.',
    rehabNeeds: 'PT daily for gait training, strengthening, and fall prevention. OT for ADL retraining and home safety planning. Speech therapy consult recommended (swallowing concern noted by nursing).',
    psychiatricRisks: 'Moderate vascular dementia with baseline anxiety. Episode of acute delirium during hospitalization (resolved). Family reports occasional sundowning behavior. No formal psychiatric diagnoses on record. Consider geropsychiatry consult.',
    fallRisk: 'high',
    medicationAdherenceConcerns: 'Complex regimen (11 medications). Insulin dosing requires daily nurse administration. Family reports inconsistent outpatient med adherence prior to admission. Pill organizer was not used at home.',
    precautions: [
      'Fall precautions',
      'DVT prophylaxis (enoxaparin — see medication list)',
      'Hip precautions: no hip flexion >90°, no adduction past midline',
      'Aspiration precautions — soft mechanical diet, thickened liquids (nectar-thick)',
      'Contact precautions: MRSA nasal screen pending',
    ],
  },

  medications: [
    { id: 'm1', name: 'Enoxaparin (Lovenox)', dosage: '40 mg', frequency: 'Once daily', route: 'Subcutaneous', indication: 'DVT prophylaxis post-ORIF', alerts: ['High-alert medication', 'Renal dose adjustment may be needed (CKD Stage 3)'], source: 'Discharge Summary' },
    { id: 'm2', name: 'Metformin', dosage: '500 mg', frequency: 'Twice daily with meals', route: 'Oral', indication: 'Type 2 Diabetes', alerts: ['HOLD — CKD Stage 3, eGFR 42. Nephrology review recommended'], source: 'MAR' },
    { id: 'm3', name: 'Insulin Glargine (Lantus)', dosage: '18 units', frequency: 'Once daily at bedtime', route: 'Subcutaneous', indication: 'Type 2 Diabetes — basal insulin', alerts: ['Requires daily nursing administration', 'Monitor for hypoglycemia'], source: 'MAR' },
    { id: 'm4', name: 'Lisinopril', dosage: '10 mg', frequency: 'Once daily', route: 'Oral', indication: 'Hypertension', alerts: ['Monitor renal function and potassium given CKD'], source: 'Discharge Summary' },
    { id: 'm5', name: 'Amlodipine', dosage: '5 mg', frequency: 'Once daily', route: 'Oral', indication: 'Hypertension', alerts: [], source: 'Discharge Summary' },
    { id: 'm6', name: 'Atorvastatin', dosage: '40 mg', frequency: 'Once daily at bedtime', route: 'Oral', indication: 'Hyperlipidemia', alerts: [], source: 'MAR' },
    { id: 'm7', name: 'Donepezil (Aricept)', dosage: '10 mg', frequency: 'Once daily at bedtime', route: 'Oral', indication: 'Vascular dementia', alerts: ['May worsen bradycardia — monitor HR'], source: 'Discharge Summary' },
    { id: 'm8', name: 'Sertraline', dosage: '50 mg', frequency: 'Once daily', route: 'Oral', indication: 'Anxiety / dementia-associated depression', alerts: [], source: 'Discharge Summary' },
    { id: 'm9', name: 'Calcium Carbonate + Vitamin D3', dosage: '600 mg / 400 IU', frequency: 'Twice daily', route: 'Oral', indication: 'Osteoporosis prevention', alerts: [], source: 'MAR' },
    { id: 'm10', name: 'Nitrofurantoin (Macrobid)', dosage: '100 mg', frequency: 'Twice daily ×5 days', route: 'Oral', indication: 'UTI (E. coli)', alerts: ['Course ends 11/05/2024', 'AVOID if eGFR <30 — monitor'], source: 'Discharge Summary' },
    { id: 'm11', name: 'Omeprazole', dosage: '20 mg', frequency: 'Once daily before breakfast', route: 'Oral', indication: 'GI prophylaxis', alerts: [], source: 'MAR' },
  ],

  insurance: {
    payerSource: 'Medicare Part A (primary) / UnitedHealthcare MedSupp Plan G (secondary)',
    memberId: '1EG4-TE5-MK72',
    groupNumber: 'GRP-00847',
    authorizationStatus: 'authorized',
    authorizationNumber: 'AUTH-2024-118847',
    coveredDays: 20,
    missingInfo: [
      'Secondary payer prior auth for SNF not confirmed',
      'Medicare benefit period start date needs verification',
    ],
    reimbursementConcerns: 'Patient has 20 Medicare-covered days at 100% (days 1–20). Days 21–100 require a daily copay (~$200/day). Family has not been counseled on cost projection. Ensure financial counselor contact within 48 hours of admission.',
  },

  issues: [
    { id: 'i1', title: 'Primary Care Physician Not Identified', description: 'No PCP listed in discharge documents. Required for care continuity and medication reconciliation sign-off.', severity: 'error', field: null },
    { id: 'i2', title: 'MRSA Screen Pending', description: 'Nasal MRSA screen collected 10/31 — results not in discharge packet. Contact precautions should remain until result received.', severity: 'warning', field: null },
    { id: 'i3', title: 'Metformin Hold Not on MAR', description: 'Discharge summary recommends holding Metformin due to CKD Stage 3 (eGFR 42), but MAR still lists it as active. Reconciliation required.', severity: 'error', field: 'medications' },
    { id: 'i4', title: 'Speech Therapy Order Missing', description: 'Nursing noted aspiration concern; thickened liquids ordered, but no formal speech therapy order found in transfer documents.', severity: 'warning', field: null },
    { id: 'i5', title: 'Advance Directive / POLST Not Included', description: 'No Advance Directive or POLST form present in transfer packet. Family reports DNR status — must be documented within 24 hours.', severity: 'error', field: null },
    { id: 'i6', title: 'Secondary Insurance Prior Auth Unconfirmed', description: 'UnitedHealthcare MedSupp Plan G secondary authorization for SNF stay not documented. Verify before day 21 cost-sharing begins.', severity: 'warning', field: 'insurance' },
    { id: 'i7', title: 'Allergy Documentation Conflict', description: 'MAR lists NKDA but discharge summary notes a reported sulfa drug reaction. Allergy list must be reconciled and documented.', severity: 'error', field: null },
  ],

  risks: {
    fallRisk: 'high',
    behavioralRisk: 'moderate',
    medicationNoncompliance: 'high',
    housingInstability: 'low',
    readmissionRisk: 'moderate',
  },

  timeline: [
    { date: '2024-10-27', event: 'Mechanical fall at home — found by daughter on kitchen floor', facility: 'Home' },
    { date: '2024-10-28', event: 'EMS transport to Mercy General Hospital ED; right femoral neck fracture confirmed on X-ray', facility: 'Mercy General Hospital' },
    { date: '2024-10-29', event: 'ORIF right femoral neck performed by Dr. Solberg; uneventful post-op course', facility: 'Mercy General Hospital — OR' },
    { date: '2024-10-30', event: 'Acute delirium onset — reorientation protocols initiated; UTI detected on urinalysis', facility: 'Mercy General Hospital' },
    { date: '2024-10-31', event: 'Macrobid started for UTI; MRSA nasal screen collected; PT/OT evaluation completed', facility: 'Mercy General Hospital' },
    { date: '2024-11-02', event: 'Delirium resolved; patient cleared for SNF placement by surgical team', facility: 'Mercy General Hospital' },
    { date: '2024-11-03', event: 'Discharge to SNF — transfer documents prepared', facility: 'Mercy General Hospital → SNF' },
  ],

  reconciliation: {
    summary: {
      totalReviewed: 11,
      discrepanciesDetected: 3,
      highRiskMedications: 3,
      unresolvedAllergyConflicts: 1,
    },
    medications: [
      {
        id: 'r1', name: 'Enoxaparin (Lovenox)', dose: '40 mg', frequency: 'Once daily',
        sources: ['Discharge Summary', 'MAR'],
        status: 'verified', riskLevel: 'high', confidence: 0.96,
        highRiskCategory: 'anticoagulant',
        highRiskReason: 'High-alert anticoagulant. Renal dose adjustment may be required (CKD Stage 3, eGFR 42). Bleeding risk elevated post-ORIF.',
        sourceSnippets: [
          { document: 'Discharge Summary', text: 'Enoxaparin 40 mg SQ daily — DVT prophylaxis, continue at SNF. Reassess renal function at 72 hours post-admission.' },
          { document: 'MAR (10/28–11/02)', text: 'Lovenox 40 mg SubQ QDay — administered 2100 daily. No missed doses documented.' },
        ],
      },
      {
        id: 'r2', name: 'Metformin', dose: '500 mg', frequency: 'Twice daily with meals',
        sources: ['Discharge Summary', 'MAR'],
        status: 'conflict', riskLevel: 'high', confidence: 0.99,
        highRiskCategory: null, highRiskReason: null,
        sourceSnippets: [
          { document: 'Discharge Summary', text: 'HOLD Metformin — CKD Stage 3, eGFR 42. Do not restart until nephrology clears. Transition to insulin-only diabetes management.' },
          { document: 'MAR (10/28–11/02)', text: 'Metformin 500 mg PO BID — ACTIVE. Administered with breakfast and dinner throughout stay.' },
        ],
      },
      {
        id: 'r3', name: 'Insulin Glargine (Lantus)', dose: '18 units', frequency: 'Once daily at bedtime',
        sources: ['MAR', 'Discharge Summary'],
        status: 'needs_review', riskLevel: 'high', confidence: 0.91,
        highRiskCategory: 'insulin',
        highRiskReason: 'High-alert medication. Requires daily nursing administration. Hypoglycemia risk; glucose monitoring protocol must be in place before administration.',
        sourceSnippets: [
          { document: 'Discharge Summary', text: 'Insulin Glargine 18 units SQ QHS — basal insulin for T2DM. Adjust per sliding scale if glucose >250 or <80.' },
          { document: 'MAR (10/28–11/02)', text: 'Lantus 18 units SubQ QHS — nursing admin. Sliding scale insulin coverage per protocol not documented in transfer packet.' },
        ],
      },
      {
        id: 'r4', name: 'Lisinopril', dose: '10 mg', frequency: 'Once daily',
        sources: ['Discharge Summary'],
        status: 'missing_from_mar', riskLevel: 'moderate', confidence: 0.88,
        highRiskCategory: null, highRiskReason: null,
        sourceSnippets: [
          { document: 'Discharge Summary', text: 'Lisinopril 10 mg PO daily — continue for hypertension management. Monitor BMP for renal function and potassium monthly.' },
        ],
      },
      {
        id: 'r5', name: 'Amlodipine', dose: '5 mg', frequency: 'Once daily',
        sources: ['Discharge Summary', 'MAR'],
        status: 'verified', riskLevel: 'standard', confidence: 0.97,
        highRiskCategory: null, highRiskReason: null,
        sourceSnippets: [
          { document: 'MAR (10/28–11/02)', text: 'Amlodipine 5 mg PO QDay — administered 0800 daily. No holds or missed doses.' },
        ],
      },
      {
        id: 'r6', name: 'Atorvastatin', dose: '40 mg', frequency: 'Once daily at bedtime',
        sources: ['MAR'],
        status: 'missing_from_mar', riskLevel: 'standard', confidence: 0.85,
        highRiskCategory: null, highRiskReason: null,
        sourceSnippets: [
          { document: 'MAR (10/28–11/02)', text: 'Atorvastatin 40 mg PO QHS. Not found in discharge medication reconciliation list — source discrepancy.' },
        ],
      },
      {
        id: 'r7', name: 'Donepezil (Aricept)', dose: '10 mg', frequency: 'Once daily at bedtime',
        sources: ['Discharge Summary'],
        status: 'needs_review', riskLevel: 'moderate', confidence: 0.93,
        highRiskCategory: 'anticholinergic',
        highRiskReason: 'May worsen bradycardia. Monitor heart rate. Consider Beers Criteria review in elderly patient with dementia.',
        sourceSnippets: [
          { document: 'Discharge Summary', text: 'Donepezil 10 mg PO QHS — continue for vascular dementia. Family reports consistent adherence at home.' },
        ],
      },
      {
        id: 'r8', name: 'Sertraline', dose: '50 mg', frequency: 'Once daily',
        sources: ['Discharge Summary', 'MAR'],
        status: 'verified', riskLevel: 'standard', confidence: 0.95,
        highRiskCategory: null, highRiskReason: null,
        sourceSnippets: [
          { document: 'Discharge Summary', text: 'Sertraline 50 mg PO QDay — continue for anxiety and dementia-associated depression.' },
        ],
      },
      {
        id: 'r9', name: 'Calcium Carbonate + Vitamin D3', dose: '600 mg / 400 IU', frequency: 'Twice daily',
        sources: ['MAR'],
        status: 'verified', riskLevel: 'standard', confidence: 0.98,
        highRiskCategory: null, highRiskReason: null,
        sourceSnippets: [
          { document: 'MAR (10/28–11/02)', text: 'Calcium Carbonate 600 mg + Vit D3 400 IU PO BID — administered with meals.' },
        ],
      },
      {
        id: 'r10', name: 'Nitrofurantoin (Macrobid)', dose: '100 mg', frequency: 'Twice daily ×5 days',
        sources: ['Discharge Summary'],
        status: 'discontinued', riskLevel: 'moderate', confidence: 0.97,
        highRiskCategory: null,
        highRiskReason: 'Course ends 11/05/2024. Avoid if eGFR <30. Confirm completion at SNF admission.',
        sourceSnippets: [
          { document: 'Discharge Summary', text: 'Nitrofurantoin (Macrobid) 100 mg BID ×5 days for UTI (E. coli). Course ends 11/05/2024. Do not renew — avoid with eGFR <30.' },
        ],
      },
      {
        id: 'r11', name: 'Omeprazole', dose: '20 mg', frequency: 'Once daily before breakfast',
        sources: ['MAR', 'Discharge Summary'],
        status: 'verified', riskLevel: 'standard', confidence: 0.99,
        highRiskCategory: null, highRiskReason: null,
        sourceSnippets: [
          { document: 'MAR (10/28–11/02)', text: 'Omeprazole 20 mg PO QAM — GI prophylaxis. Administered before breakfast daily.' },
        ],
      },
    ],
    alerts: [
      {
        id: 'ra1', severity: 'critical',
        message: 'Metformin marked ACTIVE in MAR but discharge summary recommends HOLD due to CKD Stage 3 (eGFR 42). Do not administer until nephrology clears.',
        medications: ['Metformin'],
      },
      {
        id: 'ra2', severity: 'critical',
        message: 'Sulfa allergy conflict: MAR documents NKDA but discharge summary notes a reported sulfa drug reaction. Allergy history must be reconciled before any sulfonamide-class medications are ordered.',
        medications: [],
      },
      {
        id: 'ra3', severity: 'critical',
        message: 'Insulin sliding scale protocol referenced in discharge summary but not included in transfer documents. Sliding scale must be established before first Lantus administration at SNF.',
        medications: ['Insulin Glargine (Lantus)'],
      },
      {
        id: 'ra4', severity: 'warning',
        message: 'Lisinopril appears in discharge summary but is missing from MAR. Verify if intentionally held during hospitalization or omission error.',
        medications: ['Lisinopril'],
      },
      {
        id: 'ra5', severity: 'warning',
        message: 'Nitrofurantoin course ends 11/05/2024. Confirm completion at admission. Do not renew — avoid if eGFR falls below 30.',
        medications: ['Nitrofurantoin (Macrobid)'],
      },
      {
        id: 'ra6', severity: 'info',
        message: 'Enoxaparin renal dose check recommended at 72 hours post-admission. eGFR 42 is borderline for standard prophylactic dosing.',
        medications: ['Enoxaparin (Lovenox)'],
      },
    ],
    recommendedActions: [
      { id: 'ac1', action: 'Clarify Metformin hold order with discharging provider (Dr. Solberg) before SNF admission. Do not administer until written hold order is obtained.', priority: 'urgent' },
      { id: 'ac2', action: 'Reconcile allergy discrepancy: confirm sulfa drug reaction history with patient family and update allergy list in SNF chart.', priority: 'urgent' },
      { id: 'ac3', action: 'Obtain insulin sliding scale protocol from discharging hospital before first Lantus dose at SNF.', priority: 'urgent' },
      { id: 'ac4', action: 'Verify Lisinopril status — confirm whether hold during hospitalization was intentional or a documentation gap.', priority: 'routine' },
      { id: 'ac5', action: 'Confirm Nitrofurantoin course completion date (11/05/2024) and do not renew at SNF.', priority: 'routine' },
      { id: 'ac6', action: 'Recheck eGFR at 72 hours post-admission and reassess Enoxaparin dosing with attending physician.', priority: 'routine' },
    ],
  } satisfies MedReconciliation,

  followUp: {
    admissionReadiness: 'pending_critical',
    admissionReadinessReason: '3 critical blockers unresolved: advance directive missing, allergy conflict undocumented, Metformin hold order not reconciled.',
    actions: [
      {
        id: 'f1',
        issueTitle: 'Advance Directive / POLST Not Included',
        description: 'No POLST or Advance Directive present in transfer packet. Family reports DNR status — must be documented within 24 hours of admission.',
        owner: 'admissions',
        urgency: 'critical',
        suggestedAction: 'Request POLST/DNR documentation from hospital discharge planner before admission finalization. If unavailable, initiate facility POLST intake process with patient family on day of admission.',
        communicationAction: 'Contact hospital discharge planner — request POLST or Advance Directive fax',
        status: 'admission_blocker',
        activityLog: [
          { timestamp: 'Today 9:14 AM', event: 'Issue detected during document review' },
          { timestamp: 'Today 9:14 AM', event: 'Flagged as admission blocker — follow-up required before finalization' },
        ],
      },
      {
        id: 'f2',
        issueTitle: 'Allergy Documentation Conflict',
        description: 'MAR lists NKDA but discharge summary notes a reported sulfa drug reaction. Allergy list must be reconciled and documented before any medications are ordered.',
        owner: 'nursing',
        urgency: 'critical',
        suggestedAction: 'Confirm allergy history with patient family and discharging nurse before admission. Update allergy list in SNF chart immediately. Notify pharmacy of sulfa allergy flag.',
        communicationAction: 'Contact discharging unit nurse — verify allergy history',
        status: 'needs_clinical_review',
        activityLog: [
          { timestamp: 'Today 9:14 AM', event: 'Conflict detected: MAR documents NKDA, discharge summary notes sulfa reaction' },
          { timestamp: 'Today 9:14 AM', event: 'Assigned to Nursing Review — clinical verification required' },
        ],
      },
      {
        id: 'f3',
        issueTitle: 'Metformin Hold Not on MAR',
        description: 'Discharge summary recommends holding Metformin due to CKD Stage 3 (eGFR 42), but MAR still lists it as active. Risk of renal complications if administered.',
        owner: 'nursing',
        urgency: 'critical',
        suggestedAction: 'Confirm Metformin hold order with discharging provider prior to any medication administration. Obtain written hold order and update MAR at SNF before first medication pass.',
        communicationAction: 'Contact discharging physician — obtain written Metformin hold order',
        status: 'needs_clinical_review',
        activityLog: [
          { timestamp: 'Today 9:14 AM', event: 'Discrepancy identified: Metformin active in MAR, hold ordered in discharge summary' },
          { timestamp: 'Today 9:14 AM', event: 'Flagged for nursing review — do not administer until resolved' },
        ],
      },
      {
        id: 'f4',
        issueTitle: 'Primary Care Physician Not Identified',
        description: 'No PCP listed in discharge documents. Required for care continuity and medication reconciliation sign-off within 30 days of admission.',
        owner: 'case_management',
        urgency: 'high',
        suggestedAction: 'Contact patient family to identify PCP. If no established PCP, assign SNF medical director as interim attending and initiate PCP referral within 30 days.',
        communicationAction: 'Contact patient family — request PCP information',
        status: 'pending',
        activityLog: [
          { timestamp: 'Today 9:14 AM', event: 'No PCP identified in transfer documents' },
          { timestamp: 'Today 9:14 AM', event: 'Assigned to Case Management for follow-up' },
        ],
      },
      {
        id: 'f5',
        issueTitle: 'MRSA Screen Result Pending',
        description: 'MRSA nasal screen collected 10/31 — result not in discharge packet. Contact precautions must remain in place until result received.',
        owner: 'infection_control',
        urgency: 'high',
        suggestedAction: 'Request MRSA screen result from hospital lab or infection control. Maintain contact precautions on admission until negative result confirmed. Notify admissions team of room assignment implications.',
        communicationAction: 'Request MRSA screen result from Mercy General lab/infection control',
        status: 'awaiting_response',
        activityLog: [
          { timestamp: 'Today 9:14 AM', event: 'MRSA screen result not included in transfer packet' },
          { timestamp: 'Today 9:14 AM', event: 'Contact precautions recommended pending result' },
          { timestamp: 'Today 9:22 AM', event: 'Follow-up initiated — awaiting result from Mercy General' },
        ],
      },
      {
        id: 'f6',
        issueTitle: 'Speech Therapy Order Missing',
        description: 'Nursing noted aspiration concern; thickened liquids ordered, but no formal speech therapy order found in transfer documents.',
        owner: 'admissions',
        urgency: 'medium',
        suggestedAction: 'Obtain speech therapy order from discharging physician or SNF attending. Initiate soft mechanical diet and thickened liquids per nursing note until formal SLP evaluation is completed.',
        communicationAction: 'Request speech therapy order from discharging provider',
        status: 'pending',
        activityLog: [
          { timestamp: 'Today 9:14 AM', event: 'Aspiration precautions noted but no SLP order found in documents' },
        ],
      },
      {
        id: 'f7',
        issueTitle: 'Secondary Insurance Prior Auth Unconfirmed',
        description: 'UnitedHealthcare MedSupp Plan G secondary authorization for SNF stay not documented. Verify before day 21 cost-sharing begins.',
        owner: 'billing',
        urgency: 'medium',
        suggestedAction: 'Contact UnitedHealthcare to verify SNF secondary authorization. Confirm benefit period and document authorization number in billing system. Alert financial counselor to schedule family cost projection meeting.',
        communicationAction: 'Call UnitedHealthcare — verify secondary SNF authorization',
        status: 'pending',
        activityLog: [
          { timestamp: 'Today 9:14 AM', event: 'Secondary prior auth not confirmed in eligibility documents' },
          { timestamp: 'Today 9:14 AM', event: 'Assigned to Billing — follow up before day 21' },
        ],
      },
    ],
  } satisfies FollowUpWorkflow,

  meta: {
    filesProcessed: ['harrington_discharge_summary.pdf', 'harrington_MAR_10282024.pdf', 'harrington_insurance_eligibility.pdf'],
    estimatedTimeSavedMinutes: 22,
    actualProcessingSeconds: 18,
    confidence: { name: 0.99, dob: 0.99, mrn: 0.97, diagnoses: 0.94, medications: 0.91, insurance: 0.88, adlStatus: 0.85, fallRisk: 0.9 },
  },
}
