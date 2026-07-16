import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout'
import RequireAuth from './components/RequireAuth'
import { ErrorBoundary } from './components/ErrorBoundary'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import MenuPage from './pages/MenuPage'
import ReportPage from './pages/ReportPage'
import OrdersPage from './pages/OrdersPage'
import UsersPage from './pages/UsersPage'
import SettingsPage from './pages/SettingsPage'
import CRMPage from './pages/CRMPage'
import TablesPage from './pages/TablesPage'
import InventoryPage from './pages/InventoryPage'
import KitchenManagementPage from './pages/KitchenManagementPage'
import KioskManagementPage from './pages/KioskManagementPage'
import PadConfigPage from './pages/PadConfigPage'
import CdsManagementPage from './pages/CdsManagementPage'
import QrOrderingPage from './pages/QrOrderingPage'
import ReservationsPage from './pages/ReservationsPage'
import ReservationSettingsPage from './pages/ReservationSettingsPage'
import AiWorkspacePage from './pages/AiWorkspacePage'
import GroupBuyingRedemptionsPage from './pages/GroupBuyingRedemptionsPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route
          path="/"
          element={
            <RequireAuth>
              <Layout />
            </RequireAuth>
          }
        >
          <Route index element={<ErrorBoundary label="Dashboard"><DashboardPage /></ErrorBoundary>} />
          <Route path="ai" element={<ErrorBoundary label="AI Workspace"><AiWorkspacePage /></ErrorBoundary>} />
          <Route path="menu" element={<ErrorBoundary label="Menu"><MenuPage /></ErrorBoundary>} />
          <Route path="crm" element={<ErrorBoundary label="CRM"><CRMPage /></ErrorBoundary>} />
          <Route path="reservations" element={<ErrorBoundary label="Reservations"><ReservationsPage /></ErrorBoundary>} />
          <Route path="reservations/settings" element={<ErrorBoundary label="ReservationSettings"><ReservationSettingsPage /></ErrorBoundary>} />
          <Route path="tables" element={<ErrorBoundary label="Tables"><TablesPage /></ErrorBoundary>} />
          <Route path="kitchen" element={<ErrorBoundary label="Kitchen"><KitchenManagementPage /></ErrorBoundary>} />
          <Route path="kiosk" element={<ErrorBoundary label="Kiosk"><KioskManagementPage /></ErrorBoundary>} />
          <Route path="pad" element={<ErrorBoundary label="Pad"><PadConfigPage /></ErrorBoundary>} />
          <Route path="cds" element={<ErrorBoundary label="CDS"><CdsManagementPage /></ErrorBoundary>} />
          <Route path="qr-ordering" element={<ErrorBoundary label="QrOrdering"><QrOrderingPage /></ErrorBoundary>} />
          <Route path="inventory" element={<ErrorBoundary label="Inventory"><InventoryPage /></ErrorBoundary>} />
          <Route path="reports" element={<ErrorBoundary label="Reports"><ReportPage /></ErrorBoundary>} />
          <Route path="orders" element={<ErrorBoundary label="Orders"><OrdersPage /></ErrorBoundary>} />
          <Route path="group-buying" element={<ErrorBoundary label="GroupBuying"><GroupBuyingRedemptionsPage /></ErrorBoundary>} />
          <Route path="users" element={<ErrorBoundary label="Users"><UsersPage /></ErrorBoundary>} />
          <Route path="settings" element={<ErrorBoundary label="Settings"><SettingsPage /></ErrorBoundary>} />
        </Route>

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
