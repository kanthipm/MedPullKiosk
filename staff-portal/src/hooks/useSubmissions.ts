import { useMemo } from "react";
import type { PatientApplication } from "../types";
import { MOCK_APPLICATIONS } from "../mockData";

declare global {
  interface Window {
    AndroidBridge?: {
      getSubmissions(): string;
    };
  }
}

/**
 * Returns the list of patient applications.
 * When running inside the Android kiosk WebView, live submissions from
 * the device are prepended before the mock seed data.
 * When running in a browser (dev), falls back to mock data only.
 */
export function useSubmissions(): PatientApplication[] {
  return useMemo(() => {
    if (typeof window.AndroidBridge?.getSubmissions === "function") {
      try {
        const json = window.AndroidBridge.getSubmissions();
        const live: PatientApplication[] = JSON.parse(json);
        if (live.length > 0) {
          // Deduplicate: live entries take precedence over mock entries with same id
          const liveIds = new Set(live.map((a) => a.id));
          const filteredMock = MOCK_APPLICATIONS.filter((a) => !liveIds.has(a.id));
          return [...live, ...filteredMock];
        }
      } catch {
        // bridge returned invalid JSON — fall through
      }
    }
    return MOCK_APPLICATIONS;
  }, []);
}
