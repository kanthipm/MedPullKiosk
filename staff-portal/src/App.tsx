import { BrowserRouter, Routes, Route } from "react-router-dom";
import { HomePage } from "./pages/HomePage";
import { StaffDashboardPage } from "./pages/StaffDashboardPage";
import { PatientReviewPage } from "./pages/PatientReviewPage";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/staff" element={<StaffDashboardPage />} />
        <Route path="/staff/review/:id" element={<PatientReviewPage />} />
      </Routes>
    </BrowserRouter>
  );
}
