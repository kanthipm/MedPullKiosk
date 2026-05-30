import type { IntakeProgram } from "../types";
import { programLabel, DEFAULT_PROGRAM } from "../types";

const PROGRAM_STYLE: Record<IntakeProgram, string> = {
  SLIDING_FEE: "bg-brand-50 text-brand-700 ring-1 ring-brand-200",
  MEDICAL_INTAKE: "bg-violet-50 text-violet-700 ring-1 ring-violet-200",
  NEW_PATIENT: "bg-cyan-50 text-cyan-700 ring-1 ring-cyan-200",
  MEDICAID_RENEWAL: "bg-indigo-50 text-indigo-700 ring-1 ring-indigo-200",
};

export function ProgramBadge({ program }: { program: IntakeProgram | undefined }) {
  const p = program ?? DEFAULT_PROGRAM;
  return (
    <span
      className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium ${PROGRAM_STYLE[p]}`}
    >
      {programLabel(p)}
    </span>
  );
}
