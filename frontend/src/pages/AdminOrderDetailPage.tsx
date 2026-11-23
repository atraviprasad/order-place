import { Alert, Box, Button, CircularProgress, Divider, Paper, Stack, Typography } from '@mui/material';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useAdminOrder } from '../api/admin';
import { usePayments } from '../api/payments';
import { ErrorAlert } from '../components/ErrorAlert';
import { OrderLinesTable } from '../components/OrderLinesTable';
import { PaymentStatusChip } from '../components/PaymentStatusChip';
import { StatusChip, statusLabel } from '../components/StatusChip';
import { formatDateTime, formatMoney } from '../utils/format';

export function AdminOrderDetailPage() {
  const { id } = useParams();
  const detail = useAdminOrder(id);
  const payments = usePayments(id ?? '');

  if (detail.isLoading) return <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}><CircularProgress /></Box>;
  if (detail.error) return <ErrorAlert error={detail.error} onRetry={() => void detail.refetch()} />;
  if (!detail.data) return null;
  const { order: o, history } = detail.data;

  return (
    <>
      <Button component={RouterLink} to="/admin/orders" size="small" sx={{ mb: 1 }}>Back to all orders</Button>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }} useFlexGap flexWrap="wrap">
        <Typography variant="h5">{o.orderNumber ? `Order ${o.orderNumber}` : 'Draft order'}</Typography>
        <StatusChip status={o.status} size="medium" />
        <Button size="small" onClick={() => void detail.refetch()}>Refresh</Button>
      </Stack>
      {o.dealerAction && (
        <Alert severity={o.dealerAction.action === 'REJECT' ? 'error' : 'info'} sx={{ mb: 2 }}>
          Dealer decision: {o.dealerAction.action.toLowerCase().replace(/_/g, ' ')} on {formatDateTime(o.dealerAction.actedAt)}
          {o.dealerAction.remarks ? `. Remarks: ${o.dealerAction.remarks}` : ''}
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, alignItems: 'start' }}>
        <OrderLinesTable order={o} />

        <Stack spacing={2}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="overline">Customer</Typography>
            <Typography>{o.customerName}</Typography>
            <Typography variant="overline" sx={{ mt: 1, display: 'block' }}>Dealer</Typography>
            <Typography>{o.dealer?.name ?? '-'}</Typography>
            <Typography variant="overline" sx={{ mt: 1, display: 'block' }}>Delivery address</Typography>
            <Typography sx={{ whiteSpace: 'pre-line' }}>{o.deliveryAddress ?? '-'}</Typography>
            {o.customerNotes && (
              <>
                <Typography variant="overline" sx={{ mt: 1, display: 'block' }}>Customer notes</Typography>
                <Typography sx={{ whiteSpace: 'pre-line' }}>{o.customerNotes}</Typography>
              </>
            )}
            <Typography variant="overline" sx={{ mt: 1, display: 'block' }}>Created</Typography>
            <Typography>{formatDateTime(o.createdAt)}</Typography>
            {o.submittedAt && (
              <>
                <Typography variant="overline" sx={{ mt: 1, display: 'block' }}>Submitted</Typography>
                <Typography>{formatDateTime(o.submittedAt)}</Typography>
              </>
            )}
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="overline">Payments</Typography>
            {payments.error && <ErrorAlert error={payments.error} onRetry={() => void payments.refetch()} />}
            {payments.data && payments.data.length === 0 && <Typography variant="body2" color="text.secondary">No payment attempts.</Typography>}
            {payments.data?.map((p) => (
              <Box key={p.paymentId} sx={{ mt: 1 }}>
                <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                  <PaymentStatusChip status={p.status} />
                  <Typography variant="body2">{formatMoney(p.amount, p.currency)}</Typography>
                </Stack>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                  {formatDateTime(p.initiatedAt)}{p.completedAt ? ` to ${formatDateTime(p.completedAt)}` : ''}
                </Typography>
                <Typography variant="caption" sx={{ display: 'block', fontFamily: 'monospace', wordBreak: 'break-all' }}>{p.gatewayOrderId}</Typography>
                {p.failureReason && <Typography variant="caption" color="error">{p.failureReason}</Typography>}
              </Box>
            ))}
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="overline">History</Typography>
            {history.map((h, idx) => (
              <Box key={idx} sx={{ mt: 1 }}>
                {idx > 0 && <Divider sx={{ mb: 1 }} />}
                <Typography variant="body2" sx={{ fontWeight: 500 }}>{h.fromStatus ? `${statusLabel(h.fromStatus)} to ` : ''}{statusLabel(h.toStatus)}</Typography>
                <Typography variant="caption" color="text.secondary">{formatDateTime(h.changedAt)} · {h.changedByRole.toLowerCase()}</Typography>
                {h.reason && <Typography variant="body2">{h.reason}</Typography>}
              </Box>
            ))}
          </Paper>
        </Stack>
      </Box>
    </>
  );
}
