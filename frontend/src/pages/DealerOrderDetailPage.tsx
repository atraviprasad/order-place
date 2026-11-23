import { Alert, Box, Button, CircularProgress, Paper, Stack, Step, StepLabel, Stepper, Typography } from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { ApiError, errorMessage } from '../api/client';
import { useApprove, useDealerOrder, useEditAndApprove, useReject, useStartReview, type EditLine } from '../api/dealerOrders';
import type { OrderStatus } from '../api/orderTypes';
import { ActionDialog } from '../components/ActionDialog';
import { EditQuantitiesDialog } from '../components/EditQuantitiesDialog';
import { ErrorAlert } from '../components/ErrorAlert';
import { OrderLinesTable } from '../components/OrderLinesTable';
import { StatusChip, statusLabel } from '../components/StatusChip';
import { formatDateTime } from '../utils/format';

const HAPPY_PATH: OrderStatus[] = ['SUBMITTED', 'UNDER_REVIEW', 'AWAITING_PAYMENT', 'PAID', 'READY_FOR_DELIVERY'];

type Dialog = 'approve' | 'reject' | 'edit' | null;

export function DealerOrderDetailPage() {
  const { id } = useParams();
  const order = useDealerOrder(id);
  const start = useStartReview();
  const approve = useApprove();
  const reject = useReject();
  const edit = useEditAndApprove();
  const [dialog, setDialog] = useState<Dialog>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  if (order.isLoading) return <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}><CircularProgress /></Box>;
  if (order.error) return <ErrorAlert error={order.error} onRetry={() => void order.refetch()} />;
  const o = order.data;
  if (!o) return null;

  const busy = start.isPending || approve.isPending || reject.isPending || edit.isPending;
  const step = HAPPY_PATH.indexOf(o.status);

  function closeDialog() {
    setDialog(null);
    setActionError(null);
  }

  /** Runs a dealer action. On success the dialog closes; a version conflict reloads the order so the dealer sees what changed. */
  async function run(action: () => Promise<unknown>) {
    setActionError(null);
    try {
      await action();
      setDialog(null);
    } catch (e) {
      if (e instanceof ApiError && (e.code === 'VERSION_CONFLICT' || e.code === 'INVALID_STATE_TRANSITION')) void order.refetch();
      setActionError(errorMessage(e));
    }
  }

  return (
    <>
      <Button component={RouterLink} to="/dealer/orders" size="small" sx={{ mb: 1 }}>Back to orders</Button>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }} useFlexGap flexWrap="wrap">
        <Typography variant="h5">Order {o.orderNumber}</Typography>
        <StatusChip status={o.status} size="medium" />
      </Stack>

      {step >= 0 && (
        <Paper variant="outlined" sx={{ p: 2, mb: 2, overflowX: 'auto' }}>
          <Stepper activeStep={step} alternativeLabel>
            {HAPPY_PATH.map((s) => <Step key={s}><StepLabel>{statusLabel(s)}</StepLabel></Step>)}
          </Stepper>
        </Paper>
      )}
      {o.status === 'REJECTED' && <Alert severity="error" sx={{ mb: 2 }}>You rejected this order.{o.dealerAction?.remarks ? ` Reason: ${o.dealerAction.remarks}` : ''}</Alert>}
      {o.status === 'CANCELLED' && <Alert severity="info" sx={{ mb: 2 }}>The customer cancelled this order.</Alert>}
      {o.status === 'AWAITING_PAYMENT' && <Alert severity="success" sx={{ mb: 2 }}>You approved this order. It is waiting for the customer's payment.</Alert>}
      {(o.status === 'PAID' || o.status === 'READY_FOR_DELIVERY') && <Alert severity="success" sx={{ mb: 2 }}>The customer has paid. {o.status === 'READY_FOR_DELIVERY' ? 'The order is ready for delivery.' : ''}</Alert>}
      {o.dealerAction?.remarks && !['REJECTED'].includes(o.status) && <Alert severity="info" sx={{ mb: 2 }}>Your remarks: {o.dealerAction.remarks}</Alert>}
      {actionError && dialog === null && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, alignItems: 'start' }}>
        <OrderLinesTable order={o} />

        <Stack spacing={2}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="overline">Customer</Typography>
            <Typography>{o.customerName}</Typography>
            <Typography variant="overline" sx={{ mt: 1, display: 'block' }}>Delivery address</Typography>
            <Typography sx={{ whiteSpace: 'pre-line' }}>{o.deliveryAddress ?? '-'}</Typography>
            {o.customerNotes && (
              <>
                <Typography variant="overline" sx={{ mt: 1, display: 'block' }}>Customer notes</Typography>
                <Typography sx={{ whiteSpace: 'pre-line' }}>{o.customerNotes}</Typography>
              </>
            )}
            {o.submittedAt && (
              <>
                <Typography variant="overline" sx={{ mt: 1, display: 'block' }}>Submitted</Typography>
                <Typography>{formatDateTime(o.submittedAt)}</Typography>
              </>
            )}
          </Paper>

          {o.status === 'SUBMITTED' && (
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>Start the review to lock the order for your decision.</Typography>
              <Button fullWidth variant="contained" disabled={busy} onClick={() => void run(() => start.mutateAsync({ id: o.id, version: o.version }))}>
                {start.isPending ? 'Starting...' : 'Start review'}
              </Button>
            </Paper>
          )}

          {o.status === 'UNDER_REVIEW' && (
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Stack spacing={1}>
                <Button variant="contained" color="success" disabled={busy} onClick={() => setDialog('approve')}>Approve</Button>
                <Button variant="outlined" disabled={busy} onClick={() => setDialog('edit')}>Edit quantities and approve</Button>
                <Button variant="outlined" color="error" disabled={busy} onClick={() => setDialog('reject')}>Reject</Button>
              </Stack>
            </Paper>
          )}
        </Stack>
      </Box>

      <ActionDialog open={dialog === 'approve'} title="Approve this order?" text="The customer will be told and can pay the full amount."
        confirmLabel="Approve" confirmColor="success" reasonLabel="Remarks for the customer (optional)" busy={approve.isPending} error={actionError}
        onConfirm={(remarks) => void run(() => approve.mutateAsync({ id: o.id, version: o.version, remarks }))} onClose={closeDialog} />
      <ActionDialog open={dialog === 'reject'} title="Reject this order?" text="The customer will be told. This cannot be undone."
        confirmLabel="Reject order" reasonLabel="Reason for the customer" reasonRequired busy={reject.isPending} error={actionError}
        onConfirm={(reason) => void run(() => reject.mutateAsync({ id: o.id, version: o.version, reason }))} onClose={closeDialog} />
      <EditQuantitiesDialog open={dialog === 'edit'} order={o} busy={edit.isPending} error={actionError}
        onConfirm={(lines: EditLine[], remarks) => void run(() => edit.mutateAsync({ id: o.id, version: o.version, remarks, lines }))} onClose={closeDialog} />
    </>
  );
}
