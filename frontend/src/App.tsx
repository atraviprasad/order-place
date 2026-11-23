import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthProvider';
import { RequireRole } from './auth/RequireRole';
import { HOME_PATH } from './auth/roles';
import { CUSTOMER_KIND, DEALER_KIND } from './admin/orgKinds';
import { AppLayout } from './layout/AppLayout';
import { CartPage } from './pages/CartPage';
import { CatalogPage } from './pages/CatalogPage';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { AdminUserDetailPage } from './pages/AdminUserDetailPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { AdminProductsPage } from './pages/AdminProductsPage';
import { AdminProductDetailPage } from './pages/AdminProductDetailPage';
import { AdminCategoriesPage } from './pages/AdminCategoriesPage';
import { AdminOrgDetailPage } from './pages/AdminOrgDetailPage';
import { AdminOrgsPage } from './pages/AdminOrgsPage';
import { AdminNotificationsPage } from './pages/AdminNotificationsPage';
import { AdminOrderDetailPage } from './pages/AdminOrderDetailPage';
import { AdminOrdersPage } from './pages/AdminOrdersPage';
import { AdminPaymentsPage } from './pages/AdminPaymentsPage';
import { DealerOrderDetailPage } from './pages/DealerOrderDetailPage';
import { DealerOrdersPage } from './pages/DealerOrdersPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { OrderRoute } from './pages/OrderRoute';
import { OrdersPage } from './pages/OrdersPage';

export default function App() {
  const { user } = useAuth();
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to={HOME_PATH[user.role]} replace />} />

        <Route path="catalog" element={<CatalogPage />} />

        <Route path="orders/:id" element={<OrderRoute />} />

        <Route element={<RequireRole roles={['CUSTOMER']} />}>
          <Route path="cart" element={<CartPage />} />
          <Route path="orders" element={<OrdersPage />} />
        </Route>

        <Route element={<RequireRole roles={['DEALER']} />}>
          <Route path="dealer/orders" element={<DealerOrdersPage />} />
          <Route path="dealer/orders/:id" element={<DealerOrderDetailPage />} />
        </Route>

        <Route element={<RequireRole roles={['ADMIN']} />}>
          <Route path="admin" element={<AdminDashboardPage />} />
          <Route path="admin/orders" element={<AdminOrdersPage />} />
          <Route path="admin/orders/:id" element={<AdminOrderDetailPage />} />
          <Route path="admin/payments" element={<AdminPaymentsPage />} />
          <Route path="admin/notifications" element={<AdminNotificationsPage />} />
          <Route path="admin/customers" element={<AdminOrgsPage kind={CUSTOMER_KIND} />} />
          <Route path="admin/customers/:id" element={<AdminOrgDetailPage kind={CUSTOMER_KIND} />} />
          <Route path="admin/dealers" element={<AdminOrgsPage kind={DEALER_KIND} />} />
          <Route path="admin/dealers/:id" element={<AdminOrgDetailPage kind={DEALER_KIND} />} />
          <Route path="admin/users" element={<AdminUsersPage />} />
          <Route path="admin/users/:id" element={<AdminUserDetailPage />} />
          <Route path="admin/products" element={<AdminProductsPage />} />
          <Route path="admin/products/:id" element={<AdminProductDetailPage />} />
          <Route path="admin/categories" element={<AdminCategoriesPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
