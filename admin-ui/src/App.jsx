import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './pages/Login.jsx'
import ProductsPage from './pages/Products.jsx'
import DashboardPage from './pages/Dashboard.jsx'
import OrdersPage from './pages/Orders.jsx'
import TransactionsPage from './pages/Transactions.jsx'
import CategoriesPage from './pages/Categories.jsx'
import InventoryPage from './pages/Inventory.jsx'
import CustomersPage from './pages/Customers.jsx'
import ReviewsPage from './pages/Reviews.jsx'
import VouchersPage from './pages/Vouchers.jsx'
import StaffsPage from './pages/Staffs.jsx'
import RolesPermissionsPage from './pages/RolesPermissions.jsx'
import SettingsPage from './pages/Settings.jsx'
import AdminDataSyncPage from './pages/AdminDataSync.jsx'
import ProfilePage from './pages/Profile.jsx'
import ChangePasswordPage from './pages/ChangePassword.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import DashboardLayout from './layout/DashboardLayout.jsx'
import { isLoggedIn } from './auth/auth.js'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<DashboardLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />

          <Route
            path="/orders"
            element={
              <ProtectedRoute anyPermissions={["ORDER_VIEW_SELF", "ORDER_VIEW_ALL"]}>
                <OrdersPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/transactions"
            element={
              <ProtectedRoute anyPermissions={["STATS_VIEW"]}>
                <TransactionsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/categories"
            element={
              <ProtectedRoute
                anyPermissions={["PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE"]}
              >
                <CategoriesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/products"
            element={
              <ProtectedRoute anyPermissions={["PRODUCT_VIEW"]}>
                <ProductsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/inventory"
            element={
              <ProtectedRoute
                anyPermissions={["PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE"]}
              >
                <InventoryPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/customers"
            element={
              <ProtectedRoute anyPermissions={["ORDER_VIEW_ALL"]}>
                <CustomersPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/reviews"
            element={
              <ProtectedRoute anyPermissions={["ORDER_VIEW_ALL"]}>
                <ReviewsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/vouchers"
            element={
              <ProtectedRoute anyPermissions={["MARKETING_MANAGE"]}>
                <VouchersPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="/staffs"
            element={
              <ProtectedRoute anyPermissions={["USER_MANAGE"]}>
                <StaffsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/roles-permissions"
            element={
              <ProtectedRoute anyPermissions={["USER_MANAGE"]}>
                <RolesPermissionsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute anyPermissions={["USER_MANAGE"]}>
                <SettingsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin-data-sync"
            element={
              <ProtectedRoute anyRoles={["SUPER_ADMIN"]}>
                <AdminDataSyncPage />
              </ProtectedRoute>
            }
          />

          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/change-password" element={<ChangePasswordPage />} />
        </Route>
      </Route>

      <Route
        path="/"
        element={<Navigate to={isLoggedIn() ? '/dashboard' : '/login'} replace />}
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
