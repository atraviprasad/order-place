import SearchIcon from '@mui/icons-material/Search';
import { Box, Chip, FormControl, InputAdornment, InputLabel, MenuItem, Pagination, Paper, Select, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAdminOrders } from '../api/admin';
import { useDealers } from '../api/orders';
import { useOrg } from '../api/orgs';
import type { OrderStatus } from '../api/orderTypes';
import { DateRange } from '../components/DateRange';
import { ErrorAlert } from '../components/ErrorAlert';
import { StatusChip, statusLabel } from '../components/StatusChip';
import { formatDateTime, formatMoney } from '../utils/format';
import { useDebounce } from '../utils/useDebounce';

const STATUSES: OrderStatus[] = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'AWAITING_PAYMENT', 'PAID', 'READY_FOR_DELIVERY', 'REJECTED', 'CANCELLED'];

export function AdminOrdersPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const initial = params.get('status');
  const [search, setSearch] = useState('');
  const q = useDebounce(search.trim());
  const [status, setStatus] = useState<OrderStatus | ''>(STATUSES.includes(initial as OrderStatus) ? (initial as OrderStatus) : '');
  const [dealerId, setDealerId] = useState(params.get('dealerId') ?? '');
  const [customerId, setCustomerId] = useState(params.get('customerId') ?? '');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [q, status, dealerId, customerId, from, to]);

  const customer = useOrg('/customers', customerId || undefined);

  const dealers = useDealers();
  const orders = useAdminOrders({ q: q || undefined, status: status || undefined, dealerId: dealerId || undefined, customerId: customerId || undefined, from: from || undefined, to: to || undefined, page, size: 20, sort: 'createdAt,desc' });

  return (
    <>
      <Typography variant="h5" gutterBottom>All orders</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }} useFlexGap flexWrap="wrap">
        <TextField size="small" label="Search by order number or customer" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ minWidth: 280, flexGrow: 1 }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel id="status-label">Status</InputLabel>
          <Select labelId="status-label" label="Status" value={status} onChange={(e) => setStatus(e.target.value as OrderStatus | '')}>
            <MenuItem value="">All</MenuItem>
            {STATUSES.map((s) => <MenuItem key={s} value={s}>{statusLabel(s)}</MenuItem>)}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel id="dealer-label">Dealer</InputLabel>
          <Select labelId="dealer-label" label="Dealer" value={dealerId} onChange={(e) => setDealerId(e.target.value)}>
            <MenuItem value="">All</MenuItem>
            {(dealers.data ?? []).map((d) => <MenuItem key={d.id} value={d.id}>{d.name}</MenuItem>)}
          </Select>
        </FormControl>
        <DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} />
        {customerId && <Chip label={`Customer: ${customer.data?.name ?? '...'}`} onDelete={() => setCustomerId('')} color="primary" variant="outlined" sx={{ alignSelf: 'center' }} />}
      </Stack>

      {orders.error && <ErrorAlert error={orders.error} onRetry={() => void orders.refetch()} />}
      {orders.isLoading && <Skeleton variant="rounded" height={260} />}
      {orders.data && orders.data.content.length === 0 && <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No orders match.</Typography>}
      {orders.data && orders.data.content.length > 0 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{orders.data.totalElements} orders</Typography>
          <Paper variant="outlined" sx={{ overflowX: 'auto', opacity: orders.isPlaceholderData ? 0.6 : 1 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Order</TableCell>
                  <TableCell>Customer</TableCell>
                  <TableCell>Dealer</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Total</TableCell>
                  <TableCell>Created</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {orders.data.content.map((o) => (
                  <TableRow key={o.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/admin/orders/${o.id}`)}>
                    <TableCell>{o.orderNumber ?? <em>Draft</em>}</TableCell>
                    <TableCell>{o.customerName}</TableCell>
                    <TableCell>{o.dealerName ?? '-'}</TableCell>
                    <TableCell><StatusChip status={o.status} /></TableCell>
                    <TableCell align="right">{formatMoney(o.totalAmount, o.currency)}</TableCell>
                    <TableCell>{formatDateTime(o.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
      {orders.data && orders.data.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination count={orders.data.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} color="primary" />
        </Box>
      )}
    </>
  );
}
