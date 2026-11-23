import SearchIcon from '@mui/icons-material/Search';
import { Badge, Box, InputAdornment, Pagination, Paper, Skeleton, Stack, Tab, Table, TableBody, TableCell, TableHead, TableRow, Tabs, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDealerOrders, useDealerSummary } from '../api/dealerOrders';
import type { OrderStatus } from '../api/orderTypes';
import { ErrorAlert } from '../components/ErrorAlert';
import { StatusChip, statusLabel } from '../components/StatusChip';
import { formatDateTime, formatMoney } from '../utils/format';
import { useDebounce } from '../utils/useDebounce';

type TabKey = OrderStatus | 'ALL';

// DRAFT never reaches the dealer. Tabs follow the order's life, the first two are the dealer's work queue.
const TABS: TabKey[] = ['SUBMITTED', 'UNDER_REVIEW', 'AWAITING_PAYMENT', 'PAID', 'READY_FOR_DELIVERY', 'REJECTED', 'CANCELLED', 'ALL'];

export function DealerOrdersPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<TabKey>('SUBMITTED');
  const [search, setSearch] = useState('');
  const q = useDebounce(search.trim());
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [q, tab]);

  const summary = useDealerSummary();
  // the work queue shows the oldest waiting order first, other tabs show the newest first
  const queue = tab === 'SUBMITTED' || tab === 'UNDER_REVIEW';
  const orders = useDealerOrders({ q: q || undefined, status: tab === 'ALL' ? undefined : tab, page, size: 15, sort: queue ? 'submittedAt,asc' : 'submittedAt,desc' });

  return (
    <>
      <Typography variant="h5" gutterBottom>Orders to review</Typography>
      <Paper variant="outlined" sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={(_, v: TabKey) => setTab(v)} variant="scrollable" scrollButtons="auto">
          {TABS.map((t) => {
            const count = t === 'ALL' ? summary.data?.total : summary.data?.counts[t];
            return (
              <Tab key={t} value={t} label={
                <Badge badgeContent={count ?? 0} color={t === 'SUBMITTED' ? 'error' : 'default'} max={999} showZero={false} sx={{ '& .MuiBadge-badge': { right: -14, top: 2 } }}>
                  <span>{t === 'ALL' ? 'All' : statusLabel(t)}</span>
                </Badge>
              } sx={{ pr: 3 }} />
            );
          })}
        </Tabs>
      </Paper>
      {summary.error && <ErrorAlert error={summary.error} onRetry={() => void summary.refetch()} />}

      <Stack direction="row" sx={{ mb: 2 }}>
        <TextField size="small" label="Search by order number or customer" value={search} onChange={(e) => setSearch(e.target.value)} sx={{ minWidth: { sm: 360 }, flexGrow: { xs: 1, sm: 0 } }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
      </Stack>

      {orders.error && <ErrorAlert error={orders.error} onRetry={() => void orders.refetch()} />}
      {orders.isLoading && <Skeleton variant="rounded" height={240} />}
      {orders.data && orders.data.content.length === 0 && (
        <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>
          {tab === 'SUBMITTED' ? 'No orders are waiting for you.' : 'No orders here.'}
        </Typography>
      )}
      {orders.data && orders.data.content.length > 0 && (
        <Paper variant="outlined" sx={{ overflowX: 'auto', opacity: orders.isPlaceholderData ? 0.6 : 1 }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Order</TableCell>
                <TableCell>Customer</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Total</TableCell>
                <TableCell>Submitted</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {orders.data.content.map((o) => (
                <TableRow key={o.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/dealer/orders/${o.id}`)}>
                  <TableCell>{o.orderNumber}</TableCell>
                  <TableCell>{o.customerName}</TableCell>
                  <TableCell><StatusChip status={o.status} /></TableCell>
                  <TableCell align="right">{formatMoney(o.totalAmount, o.currency)}</TableCell>
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
