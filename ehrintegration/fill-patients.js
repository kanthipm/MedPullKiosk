// fill-patients.js
//
// Starter for an attended EHR form-filler:
//   - launches a VISIBLE browser with a persistent profile (session survives between runs)
//   - lets a human log in manually, then takes over the same authenticated session
//   - fills each patient using LABEL-based locators (survive layout changes)
//   - guards every write with before/after assertions (catch a wrong/failed fill loudly)
//   - records a Playwright trace you can replay to audit exactly what happened
//
// Run against the bundled mock form (no login):   node fill-patients.js
// Run against a real EHR (manual login first):     REQUIRE_LOGIN=1 TARGET_URL="https://your-ehr/intake" node fill-patients.js
//
// Install once:  npm i playwright && npx playwright install chromium

const { chromium } = require('playwright');
const path = require('path');
const readline = require('readline');

// ---- Config ----------------------------------------------------------------
const TARGET_URL =
  process.env.TARGET_URL || `file://${path.resolve(__dirname, 'mock-ehr-form.html')}`;
const USER_DATA_DIR = path.resolve(__dirname, '.browser-profile'); // persists cookies/session
const REQUIRE_LOGIN = process.env.REQUIRE_LOGIN === '1';          // set to 1 for a real EHR
const HALT_ON_ERROR = process.env.HALT_ON_ERROR === '1';         // stop whole run on any failure

// ---- Mock patients ---------------------------------------------------------
// For realistic volume, generate these with Synthea (synthetic, no PHI) and load from JSON:
//   const patients = require('./patients.json');
const patients = [
  { firstName: 'Aria',   lastName: 'Nakamura', dob: '1984-03-12', mrn: 'MRN001', sex: 'Female', phone: '555-0142', hasInsurance: true,  insurer: 'Blue Shield' },
  { firstName: 'Marcus', lastName: 'OBrien',   dob: '1991-11-02', mrn: 'MRN002', sex: 'Male',   phone: '555-0177', hasInsurance: false, insurer: '' },
];

// ---- Helpers ---------------------------------------------------------------
function waitForEnter(promptText) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => rl.question(promptText, () => { rl.close(); resolve(); }));
}

// Loud assertion: throws if the locator isn't visible within the timeout.
async function assertVisible(locator, message, timeout = 5000) {
  try {
    await locator.waitFor({ state: 'visible', timeout });
  } catch {
    throw new Error(`Guardrail failed: ${message}`);
  }
}

async function fillPatient(page, p) {
  await page.goto(TARGET_URL); // fresh form per patient

  // Locate by LABEL, not position — these survive the form being rearranged.
  await page.getByLabel('First name').fill(p.firstName);
  await page.getByLabel('Last name').fill(p.lastName);
  await page.getByLabel('Date of birth').fill(p.dob);
  await page.getByLabel('MRN').fill(p.mrn);
  await page.getByLabel('Sex').selectOption(p.sex);
  await page.getByLabel('Phone').fill(p.phone);

  // Conditional field: only present once insurance is indicated.
  if (p.hasInsurance) {
    await page.getByLabel('Has insurance').check();
    await page.getByLabel('Insurance provider').fill(p.insurer);
  }

  // GUARDRAIL (before submit): the field must actually hold what we intended.
  const dobValue = await page.getByLabel('Date of birth').inputValue();
  if (dobValue !== p.dob) {
    throw new Error(`Pre-submit check for ${p.mrn}: DOB is "${dobValue}", expected "${p.dob}"`);
  }

  await page.getByRole('button', { name: 'Save' }).click();

  // GUARDRAIL (after submit): the save must produce the expected confirmation.
  await assertVisible(page.getByText('Patient record saved'), `no save confirmation for ${p.mrn}`);
  console.log(`  ok  Saved ${p.firstName} ${p.lastName} (${p.mrn})`);
}

// ---- Main ------------------------------------------------------------------
(async () => {
  const context = await chromium.launchPersistentContext(USER_DATA_DIR, {
    headless: false, // MUST be visible so the clinician can log in
  });
  await context.tracing.start({ screenshots: true, snapshots: true });
  const page = context.pages()[0] || (await context.newPage());

  let failures = 0;
  try {
    if (REQUIRE_LOGIN) {
      await page.goto(TARGET_URL);
      // Production: instead of the prompt below, wait for a known post-login element, e.g.
      //   await page.getByRole('button', { name: 'Log out' }).waitFor({ timeout: 0 });
      await waitForEnter('\n>> Log in manually in the browser window, then press Enter to start...\n');
    }

    for (const p of patients) {
      try {
        await fillPatient(page, p);
      } catch (err) {
        failures++;
        const shot = path.resolve(__dirname, `error-${p.mrn}-${Date.now()}.png`);
        await page.screenshot({ path: shot });
        console.error(`  ERR ${p.mrn} failed: ${err.message}\n      screenshot: ${shot}`);
        if (HALT_ON_ERROR) throw err; // patient-data writes: often safer to stop than continue
      }
    }
  } finally {
    await context.tracing.stop({ path: path.resolve(__dirname, 'trace.zip') });
    await context.close();
    console.log(`\nDone. ${patients.length - failures}/${patients.length} succeeded.`);
    console.log('Replay the run with:  npx playwright show-trace trace.zip');
  }
})();
