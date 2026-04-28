import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { getPermissions, getRoles, isLoggedIn } from '../auth/auth.js';

function hasAny(items, required) {
  if (!Array.isArray(required) || required.length === 0) return true;
  if (!Array.isArray(items) || items.length === 0) return false;
  return required.some((r) => items.includes(r));
}

export default function ProtectedRoute({
  children,
  anyRoles,
  anyPermissions,
  redirectTo = '/dashboard',
}) {
  const location = useLocation();

  if (!isLoggedIn()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  const roles = getRoles();
  const permissions = getPermissions();
  const roleOk = hasAny(roles, anyRoles);
  const permissionOk = hasAny(permissions, anyPermissions);
  if (!roleOk || !permissionOk) {
    return <Navigate to={redirectTo} replace />;
  }

  return children ? children : <Outlet />;
}
