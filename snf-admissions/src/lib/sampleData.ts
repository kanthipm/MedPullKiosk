import type { PatientProfile } from './types'

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

  meta: {
    filesProcessed: ['harrington_discharge_summary.pdf', 'harrington_MAR_10282024.pdf', 'harrington_insurance_eligibility.pdf'],
    estimatedTimeSavedMinutes: 22,
    actualProcessingSeconds: 18,
    confidence: { name: 0.99, dob: 0.99, mrn: 0.97, diagnoses: 0.94, medications: 0.91, insurance: 0.88, adlStatus: 0.85, fallRisk: 0.9 },
  },
}
