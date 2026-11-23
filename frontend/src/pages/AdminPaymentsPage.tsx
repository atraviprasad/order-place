import { Box, FormControl, InputLabel, Link, MenuItem, Pagination, Paper, Select, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { useAdminPayments } from '../api/admin';
import type { PaymentStatus } from '../api/payments';
import { DateRange } from '../components/DateRange';
import { ErrorAlert } from '../components/ErrorAlert';
import { PAYMENT_STATUSES, PaymentStatusChip, paymentStatusLabel } from '../components/PaymentStatusChip';
import { formatDateTime, formatMoney } from '../utils/format';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function AdminPaymentsPage() {
  const [params] = useSearchParams();
  const initial = params.get('status');
  const [status, setStatus] = useState<PaymentStatus | ''>(PAYMENT_STATUSES.includes(initial as PaymentStatus) ? (initial as PaymentStatus) : '');
  const [orderId, setOrderId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [status, orderId, from, to]);

  const validOrderId = UUID.test(orderId.trim()) ? orderId.trim() : undefined; // a half-typed id is not sent
  const payments = useAdminPayments({ status: status || undefined, orderId: validOrderId, from: from || undefined, to: to || undefined, page, size: 20, sort: 'initiatedAt,desc' });

  return (
    <>
      <Typography variant="h5" gutterBottom>Payments</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }} useFlexGap flexWrap="wrap">
        <FormControl size="small" sx={{ minWidth: 190 }}>
          <InputLabel id="pstatus-label">Status</InputLabel>
          <Select labelId="pstatus-label" label="Status" value={status} onChange={(e) => setStatus(e.target.value as PaymentStatus | '')}>
            <MenuItem value="">All</MenuItem>
            {PAYMENT_STATUSES.map((s) => <MenuItem key={s} value={s}>{paymentStatusLabel(s)}</MenuItem>)}
          </Select>
        </FormControl>
        <TextField size="small" label="Order id (full UUID)" value={orderId} onChange={(e) => setOrderId(e.target.value)} sx={{ minWidth: 330 }}
          error={orderId.trim() !== '' && !validOrderId} helperText={orderId.trim() !== '' && !validOrderId ? 'Not a complete id yet, filter not applied' : undefined} />
        <DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} />
      </Stack>

      {payments.error && <ErrorAlert error={payments.error} onRetry={() => void payments.refetch()} />}
      {payments.isLoading && <Skeleton variant="rounded" height={260} />}
      {payments.data && payments.data.content.length === 0 && <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No payments match.</Typography>}
      {payments.data && payments.data.content.length > 0 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{payments.data.totalElements} payments</Typography>
          <Paper variant="outlined" sx={{ overflowX: 'auto', opacity: payments.isPlaceholderData ? 0.6 : 1 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Order</TableCell>
                  <TableCell align="right">Amount</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Gateway order</TableCell>
                  <TableCell>Started</TableCell>
                  <TableCell>Completed</TableCell>
                  <TableCell>Reason</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {payments.data.content.map((p) => (
                  <TableRow key={p.paymentId} hover>
                    <TableCell><Link component={RouterLink} to={`/admin/orders/${p.orderId}`}>{p.orderNumber}</Link></TableCell>
                    <TableCell align="right">{formatMoney(p.amount, p.currency)}</TableCell>
                    <TableCell><PaymentStatusChip status={p.status} /></TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{p.gatewayOrderId}</TableCell>
                    <TableCell>{formatDateTime(p.initiatedAt)}</TableCell>
                    <TableCell>{p.completedAt ? formatDateTime(p.completedAt) : '-'}</TableCell>
                    <TableCell sx={{ color: 'error.main' }}>{p.failureReason ?? ''}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
      {payments.data && payments.data.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination count={payments.data.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} color="primary" />
        </Box>
      )}
    </>
  );
}
