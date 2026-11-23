import { Alert, Box, Button, Chip, FormControl, InputLabel, Link, MenuItem, Pagination, Paper, Select, Skeleton, Snackbar, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Tooltip, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { useAdminNotifications, useRetryNotification, type NotificationStatus } from '../api/admin';
import { errorMessage } from '../api/client';
import { DateRange } from '../components/DateRange';
import { ErrorAlert } from '../components/ErrorAlert';
import { formatDateTime } from '../utils/format';
import { useDebounce } from '../utils/useDebounce';

const STATUSES: NotificationStatus[] = ['PENDING', 'SENT', 'FAILED'];
const EVENT_TYPES = ['OrderSubmitted', 'OrderApproved', 'OrderEdited', 'OrderRejected', 'OrderCancelled', 'OrderReadyForDelivery', 'PaymentFailed'];
const COLOR: Record<NotificationStatus, 'default' | 'success' | 'error'> = { PENDING: 'default', SENT: 'success', FAILED: 'error' };

export function AdminNotificationsPage() {
  const [params] = useSearchParams();
  const initial = params.get('status');
  const [status, setStatus] = useState<NotificationStatus | ''>(STATUSES.includes(initial as NotificationStatus) ? (initial as NotificationStatus) : '');
  const [eventType, setEventType] = useState('');
  const [recipient, setRecipient] = useState('');
  const recipientQ = useDebounce(recipient.trim());
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState<{ severity: 'success' | 'error'; text: string } | null>(null);
  useEffect(() => setPage(0), [status, eventType, recipientQ, from, to]);

  const list = useAdminNotifications({ status: status || undefined, eventType: eventType || undefined, recipient: recipientQ || undefined, from: from || undefined, to: to || undefined, page, size: 20, sort: 'createdAt,desc' });
  const retry = useRetryNotification();

  async function doRetry(id: string) {
    try {
      const n = await retry.mutateAsync(id);
      setToast(n.status === 'SENT' ? { severity: 'success', text: 'Email sent.' } : { severity: 'error', text: `Still not sent: ${n.lastError ?? n.status}` });
    } catch (e) {
      setToast({ severity: 'error', text: errorMessage(e) });
    }
  }

  return (
    <>
      <Typography variant="h5" gutterBottom>Email log</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }} useFlexGap flexWrap="wrap">
        <FormControl size="small" sx={{ minWidth: 150 }}>
          <InputLabel id="nstatus-label">Status</InputLabel>
          <Select labelId="nstatus-label" label="Status" value={status} onChange={(e) => setStatus(e.target.value as NotificationStatus | '')}>
            <MenuItem value="">All</MenuItem>
            {STATUSES.map((s) => <MenuItem key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</MenuItem>)}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 230 }}>
          <InputLabel id="event-label">Event</InputLabel>
          <Select labelId="event-label" label="Event" value={eventType} onChange={(e) => setEventType(e.target.value)}>
            <MenuItem value="">All</MenuItem>
            {EVENT_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
          </Select>
        </FormControl>
        <TextField size="small" label="Recipient email" value={recipient} onChange={(e) => setRecipient(e.target.value)} sx={{ minWidth: 220 }} />
        <DateRange from={from} to={to} onFrom={setFrom} onTo={setTo} />
      </Stack>

      {list.error && <ErrorAlert error={list.error} onRetry={() => void list.refetch()} />}
      {list.isLoading && <Skeleton variant="rounded" height={260} />}
      {list.data && list.data.content.length === 0 && <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No emails match.</Typography>}
      {list.data && list.data.content.length > 0 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{list.data.totalElements} emails</Typography>
          <Paper variant="outlined" sx={{ overflowX: 'auto', opacity: list.isPlaceholderData ? 0.6 : 1 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Created</TableCell>
                  <TableCell>Event</TableCell>
                  <TableCell>Order</TableCell>
                  <TableCell>Recipient</TableCell>
                  <TableCell>Subject</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Tries</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {list.data.content.map((n) => (
                  <TableRow key={n.id}>
                    <TableCell>{formatDateTime(n.createdAt)}</TableCell>
                    <TableCell>{n.eventType}</TableCell>
                    <TableCell>{n.orderId ? <Link component={RouterLink} to={`/admin/orders/${n.orderId}`}>{n.orderNumber ?? 'open'}</Link> : '-'}</TableCell>
                    <TableCell>
                      {n.recipient}
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{n.recipientRole.toLowerCase()}</Typography>
                    </TableCell>
                    <TableCell>{n.subject}</TableCell>
                    <TableCell>
                      <Tooltip title={n.lastError ?? (n.sentAt ? `Sent ${formatDateTime(n.sentAt)}` : '')} disableHoverListener={!n.lastError && !n.sentAt}>
                        <Chip size="small" label={n.status.charAt(0) + n.status.slice(1).toLowerCase()} color={COLOR[n.status]} variant={n.status === 'PENDING' ? 'outlined' : 'filled'} />
                      </Tooltip>
                    </TableCell>
                    <TableCell align="right">{n.attempts}</TableCell>
                    <TableCell align="right">
                      {n.status === 'FAILED' && <Button size="small" disabled={retry.isPending} onClick={() => void doRetry(n.id)}>Retry</Button>}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
      {list.data && list.data.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination count={list.data.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} color="primary" />
        </Box>
      )}
      {toast && (
        <Snackbar open autoHideDuration={4000} onClose={() => setToast(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
          <Alert severity={toast.severity} variant="filled" onClose={() => setToast(null)}>{toast.text}</Alert>
        </Snackbar>
      )}
    </>
  );
}
