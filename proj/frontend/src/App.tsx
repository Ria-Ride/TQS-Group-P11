import { Navigate, Route, Routes } from "react-router";
import { AdminUsersPage } from "./pages/AdminUsersPage";
import { BuyPassPage } from "./pages/BuyPassPage";
import { FleetPage } from "./pages/FleetPage";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { SearchPage } from "./pages/SearchPage";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/vehicles/search" element={<SearchPage />} />
      <Route path="/passes/buy" element={<BuyPassPage />} />
      <Route path="/admin/users" element={<AdminUsersPage />} />
      <Route path="/operator/fleet" element={<FleetPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}