import SearchIcon from '@mui/icons-material/Search';
import { Box, FormControl, InputAdornment, InputLabel, MenuItem, Pagination, Paper, Select, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useOrders } from '../api/orders';
import type { OrderStatus } from '../api/orderTypes';
import { ErrorAlert } from '../components/ErrorAlert';
import { StatusChip, statusLabel } from '../components/StatusChip';
import { formatDateTime, formatMoney } from '../utils/format';
import { useDebounce } from '../utils/useDebounce';

const STATUSES: OrderStatus[] = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'AWAITING_PAYMENT', 'PAID', 'READY_FOR_DELIVERY', 'REJECTED', 'CANCELLED'];

export function OrdersPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const q = useDebounce(search.trim());
  const [status, setStatus] = useState<OrderStatus | ''>('');
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [q, status]);

  const orders = useOrders({ q: q || undefined, status: status || undefined, page, size: 15, sort: 'createdAt,desc' });

  return (
    <>
      <Typography variant="h5" gutterBottom>My orders</Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField size="small" label="Search" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ flexGrow: 1 }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel id="status-label">Status</InputLabel>
          <Select labelId="status-label" label="Status" value={status} onChange={(e) => setStatus(e.target.value as OrderStatus | '')}>
            <MenuItem value="">All</MenuItem>
            {STATUSES.map((s) => <MenuItem key={s} value={s}>{statusLabel(s)}</MenuItem>)}
          </Select>
        </FormControl>
      </Stack>

      {orders.error && <ErrorAlert error={orders.error} onRetry={() => void orders.refetch()} />}
      {orders.isLoading && <Skeleton variant="rounded" height={240} />}
      {orders.data && orders.data.content.length === 0 && (
        <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No orders found.</Typography>
      )}
      {orders.data && orders.data.content.length > 0 && (
        <Paper variant="outlined" sx={{ overflowX: 'auto', opacity: orders.isPlaceholderData ? 0.6 : 1 }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Order</TableCell>
                <TableCell>Dealer</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Total</TableCell>
                <TableCell>Created</TableCell>
                <TableCell>Submitted</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {orders.data.content.map((o) => (
                <TableRow key={o.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/orders/${o.id}`)}>
                  <TableCell>{o.orderNumber ?? <em>Draft</em>}</TableCell>
                  <TableCell>{o.dealerName ?? '-'}</TableCell>
                  <TableCell><StatusChip status={o.status} /></TableCell>
                  <TableCell align="right">{formatMoney(o.totalAmount, o.currency)}</TableCell>
                  <TableCell>{formatDateTime(o.createdAt)}</TableCell>
                  <TableCell>{o.submittedAt ? formatDateTime(o.submittedAt) : '-'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
      {orders.data && orders.data.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination count={orders.data.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} color="primary" />
        </Box>
      )}
    </>
  );
}
