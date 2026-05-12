import { HashRouter, Routes, Route, Navigate } from "react-router-dom";
import { StaffDashboardPage } from "./pages/StaffDashboardPage";
import { PatientReviewPage } from "./pages/PatientReviewPage";

export default function App() {
  return (
    <HashRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/staff" replace />} />
        <Route path="/staff" element={<StaffDashboardPage />} />
        <Route path="/staff/review/:id" element={<PatientReviewPage />} />
      </Routes>
    </HashRouter>
  );
}
