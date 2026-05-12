import { useNavigate } from "react-router-dom";
import { MOCK_APPLICATIONS } from "../mockData";

export function HomePage() {
  const navigate = useNavigate();

  const pendingCount = MOCK_APPLICATIONS.filter(
    (a) => a.status === "READY_FOR_REVIEW"
  ).length;

  return (
    <div className="min-h-screen bg-gradient-to-br from-brand-800 to-brand-900 flex flex-col items-center justify-center px-4">
      {/* Kiosk card */}
      <div className="w-full max-w-md bg-white rounded-2xl shadow-2xl p-8 text-center">
        {/* Logo / icon */}
        <div className="mx-auto mb-5 h-16 w-16 rounded-2xl bg-brand-600 flex items-center justify-center shadow-lg">
          <svg className="h-8 w-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
          </svg>
        </div>

        <h1 className="text-2xl font-bold text-slate-900 mb-1">
          MedPull Kiosk
        </h1>
        <p className="text-sm text-slate-500 mb-8">
          Coastal Gateway Community Health Center
        </p>

        {/* Main patient CTA */}
        <button
          className="w-full rounded-xl bg-brand-600 hover:bg-brand-700 text-white text-base font-semibold py-4 mb-4 transition-colors shadow-md"
          onClick={() => alert("Patient intake flow would start here.")}
        >
          Start Patient Intake
        </button>

        <button
          className="w-full rounded-xl border border-slate-200 bg-slate-50 hover:bg-slate-100 text-slate-600 text-base font-semibold py-4 mb-6 transition-colors"
          onClick={() => alert("Continue existing intake would open here.")}
        >
          Continue Where I Left Off
        </button>

        <hr className="border-slate-100 mb-5" />

        {/* Staff View — subtle secondary */}
        <div className="flex items-center justify-between">
          <p className="text-xs text-slate-400">Staff access</p>
          <button
            onClick={() => navigate("/staff")}
            className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-brand-700 border border-slate-200 hover:border-brand-300 rounded-lg px-3 py-1.5 transition-colors"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
            Staff View
            {pendingCount > 0 && (
              <span className="ml-1 inline-flex items-center justify-center h-4 min-w-4 px-1 rounded-full bg-brand-600 text-white text-[10px] font-bold">
                {pendingCount}
              </span>
            )}
          </button>
        </div>
      </div>

      <p className="mt-6 text-xs text-brand-200 opacity-60">
        MedPull · Sliding Fee &amp; Medical Intake
      </p>
    </div>
  );
}
