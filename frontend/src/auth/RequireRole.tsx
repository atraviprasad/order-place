import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthProvider';
import { HOME_PATH, type Role } from './roles';

/** Route guard. The menu hides links by role too, but the real protection is always on the server (@PreAuthorize). */
export function RequireRole({ roles }: { roles: Role[] }) {
  const { user } = useAuth();
  if (!roles.includes(user.role)) return <Navigate to={HOME_PATH[user.role]} replace />;
  return <Outlet />;
}
