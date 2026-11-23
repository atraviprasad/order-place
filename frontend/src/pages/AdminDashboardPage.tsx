import { Alert, Box, Paper, Skeleton, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useAdminOrderSummary, useTotal } from '../api/admin';
import type { OrderStatus } from '../api/orderTypes';
import { ErrorAlert } from '../components/ErrorAlert';
import { statusLabel } from '../components/StatusChip';

const STATUSES: OrderStatus[] = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'AWAITING_PAYMENT', 'PAID', 'READY_FOR_DELIVERY', 'REJECTED', 'CANCELLED'];

function Count({ value, loading, failed }: { value: number | undefined; loading: boolean; failed: boolean }) {
  if (loading) return <Skeleton width={40} />;
  return <>{failed || value === undefined ? '-' : value}</>;
}

function Tile({ to, label, children, alert }: { to: string; label: string; children: ReactNode; alert?: boolean }) {
  return (
    <Paper variant="outlined" component={RouterLink} to={to}
      sx={{ p: 2, textDecoration: 'none', color: 'inherit', borderColor: alert ? 'error.main' : undefined, '&:hover': { bgcolor: 'action.hover' } }}>
      <Typography variant="h4" color={alert ? 'error' : 'text.primary'}>{children}</Typography>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
    </Paper>
  );
}

const GRID = { display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 1fr))' } as const;

export function AdminDashboardPage() {
  const orders = useAdminOrderSummary();
  const failedPayments = useTotal('/admin/payments', { status: 'FAILED' });
  const confirming = useTotal('/admin/payments', { status: 'PENDING_CONFIRMATION' });
  const paidPayments = useTotal('/admin/payments', { status: 'SUCCESS' });
  const failedMails = useTotal('/admin/notifications', { status: 'FAILED' });
  const pendingMails = useTotal('/admin/notifications', { status: 'PENDING' });

  const attention = (failedPayments.data ?? 0) + (confirming.data ?? 0) + (failedMails.data ?? 0) > 0;

  return (
    <>
      <Typography variant="h5" gutterBottom>Administration</Typography>

      {attention && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Some items need a look: {failedPayments.data ? `${failedPayments.data} failed payment(s) ` : ''}
          {confirming.data ? `${confirming.data} payment(s) waiting for confirmation ` : ''}
          {failedMails.data ? `${failedMails.data} email(s) not delivered` : ''}
        </Alert>
      )}

      <Typography variant="overline">Orders</Typography>
      {orders.error && <ErrorAlert error={orders.error} onRetry={() => void orders.refetch()} />}
      <Box sx={{ ...GRID, mb: 3 }}>
        <Tile to="/admin/orders" label="All orders"><Count value={orders.data?.total} loading={orders.isLoading} failed={!!orders.error} /></Tile>
        {STATUSES.map((s) => (
          <Tile key={s} to={`/admin/orders?status=${s}`} label={statusLabel(s)}>
            <Count value={orders.data?.counts[s] ?? 0} loading={orders.isLoading} failed={!!orders.error} />
          </Tile>
        ))}
      </Box>

      <Typography variant="overline">Payments</Typography>
      <Box sx={{ ...GRID, mb: 3 }}>
        <Tile to="/admin/payments?status=SUCCESS" label="Paid"><Count value={paidPayments.data} loading={paidPayments.isLoading} failed={!!paidPayments.error} /></Tile>
        <Tile to="/admin/payments?status=PENDING_CONFIRMATION" label="Waiting for confirmation" alert={(confirming.data ?? 0) > 0}>
          <Count value={confirming.data} loading={confirming.isLoading} failed={!!confirming.error} />
        </Tile>
        <Tile to="/admin/payments?status=FAILED" label="Failed" alert={(failedPayments.data ?? 0) > 0}>
          <Count value={failedPayments.data} loading={failedPayments.isLoading} failed={!!failedPayments.error} />
        </Tile>
      </Box>

      <Typography variant="overline">Emails</Typography>
      <Box sx={GRID}>
        <Tile to="/admin/notifications?status=FAILED" label="Not delivered (gave up)" alert={(failedMails.data ?? 0) > 0}>
          <Count value={failedMails.data} loading={failedMails.isLoading} failed={!!failedMails.error} />
        </Tile>
        <Tile to="/admin/notifications?status=PENDING" label="Waiting to be sent">
          <Count value={pendingMails.data} loading={pendingMails.isLoading} failed={!!pendingMails.error} />
        </Tile>
      </Box>
    </>
  );
}
