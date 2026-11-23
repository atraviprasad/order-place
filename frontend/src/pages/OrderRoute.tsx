import { Navigate, useParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { HOME_PATH } from '../auth/roles';
import { OrderDetailPage } from './OrderDetailPage';

/**
 * /orders/:id is the link used in e-mails. A customer sees the order, a dealer and an admin are sent to their own
 * version of the same order, anyone else goes home.
 */
export function OrderRoute() {
  const { user } = useAuth();
  const { id } = useParams();
  if (user.role === 'CUSTOMER') return <OrderDetailPage />;
  if (user.role === 'DEALER') return <Navigate to={`/dealer/orders/${id}`} replace />;
  if (user.role === 'ADMIN') return <Navigate to={`/admin/orders/${id}`} replace />;
  return <Navigate to={HOME_PATH[user.role]} replace />;
}