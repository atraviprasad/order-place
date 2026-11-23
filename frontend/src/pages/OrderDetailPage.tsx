import { Alert, Box, Button, CircularProgress, Divider, Paper, Stack, Step, StepLabel, Stepper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useCancelOrder, useDeleteDraft, useOrder, useOrderHistory, useSubmitOrder } from '../api/orders';
import type { OrderStatus } from '../api/orderTypes';
import { useCart } from '../cart/CartContext';
import { ActionDialog } from '../components/ActionDialog';
import { ErrorAlert } from '../components/ErrorAlert';
import { PaymentPanel } from '../components/PaymentPanel';
import { StatusChip, statusLabel } from '../components/StatusChip';
import { formatDateTime, formatMoney } from '../utils/format';

const HAPPY_PATH: OrderStatus[] = ['SUBMITTED', 'UNDER_REVIEW', 'AWAITING_PAYMENT', 'PAID', 'READY_FOR_DELIVERY'];
const CANCELLABLE: OrderStatus[] = ['SUBMITTED', 'UNDER_REVIEW', 'AWAITING_PAYMENT'];
// the payment panel shows for approved orders and keeps showing the receipt once paid
const PAYABLE: OrderStatus[] = ['AWAITING_PAYMENT', 'PAID', 'READY_FOR_DELIVERY'];
export function OrderDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const cart = useCart();
  const order = useOrder(id);
  const history = useOrderHistory(id, order.data?.status);
  const cancel = useCancelOrder();
  const remove = useDeleteDraft();
  const submit = useSubmitOrder();
  const [dialog, setDialog] = useState<'cancel' | 'delete' | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [submitKey] = useState(() => crypto.randomUUID());

  if (order.isLoading) return <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}><CircularProgress /></Box>;
  if (order.error) return <ErrorAlert error={order.error} onRetry={() => void order.refetch()} />;
  const o = order.data;
  if (!o) return null;

  const showApproved = o.lines.some((l) => l.approvedQty !== null);
  const step = HAPPY_PATH.indexOf(o.status);

  function closeDialog() {
    setDialog(null);
    setActionError(null);
  }

  async function doCancel(reason: string) {
    try {
      await cancel.mutateAsync({ id: o!.id, version: o!.version, reason });
      closeDialog();
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  async function doDelete() {
    try {
      await remove.mutateAsync(o!.id);
      navigate('/orders', { replace: true });
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  async function doSubmit() {
    setActionError(null);
    try {
      await submit.mutateAsync({ id: o!.id, version: o!.version, key: submitKey });
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  function editDraft() {
    cart.loadFromDraft(o!);
    navigate('/cart');
  }

  return (
    <>
      <Button component={RouterLink} to="/orders" size="small" sx={{ mb: 1 }}>Back to my orders</Button>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }} useFlexGap flexWrap="wrap">
        <Typography variant="h5">{o.orderNumber ? `Order ${o.orderNumber}` : 'Draft order'}</Typography>
        <StatusChip status={o.status} size="medium" />
      </Stack>

      {step >= 0 && (
        <Paper variant="outlined" sx={{ p: 2, mb: 2, overflowX: 'auto' }}>
          <Stepper activeStep={step} alternativeLabel>
            {HAPPY_PATH.map((s) => <Step key={s}><StepLabel>{statusLabel(s)}</StepLabel></Step>)}
          </Stepper>
        </Paper>
      )}
      {o.status === 'REJECTED' && <Alert severity="error" sx={{ mb: 2 }}>The dealer rejected this order.{o.dealerAction?.remarks ? ` Reason: ${o.dealerAction.remarks}` : ''}</Alert>}
      {o.status === 'CANCELLED' && <Alert severity="info" sx={{ mb: 2 }}>This order was cancelled.</Alert>}
      {o.status === 'AWAITING_PAYMENT' && (
        <Alert severity="success" sx={{ mb: 2 }}>
        The dealer approved this order{o.dealerAction?.remarks ? `: "${o.dealerAction.remarks}"` : ''}. Pay below to confirm it.  
      </Alert>
      )}
      {o.dealerAction && o.status !== 'REJECTED' && o.status !== 'AWAITING_PAYMENT' && o.dealerAction.remarks && (
        <Alert severity="info" sx={{ mb: 2 }}>Dealer remarks: {o.dealerAction.remarks}</Alert>
      )}
      {actionError && dialog === null && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, alignItems: 'start' }}>
        <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Product</TableCell>
                <TableCell align="right">Unit price</TableCell>
                <TableCell align="right">Ordered</TableCell>
                {showApproved && <TableCell align="right">Approved</TableCell>}
                <TableCell align="right">Total</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {o.lines.slice().sort((a, b) => a.lineNo - b.lineNo).map((l) => (
                <TableRow key={l.id}>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>{l.productName}</Typography>
                    <Typography variant="caption" color="text.secondary">{l.sku}</Typography>
                  </TableCell>
                  <TableCell align="right">{formatMoney(l.unitPrice, o.currency)}</TableCell>
                  <TableCell align="right">{l.submittedQty}</TableCell>
                  {showApproved && (
                    <TableCell align="right" sx={{ color: l.approvedQty !== null && l.approvedQty !== l.submittedQty ? 'warning.main' : undefined }}>
                      {l.approvedQty ?? '-'}
                    </TableCell>
                  )}
                  <TableCell align="right">{formatMoney(l.lineTotal, o.currency)}</TableCell>
                </TableRow>
              ))}
              <TableRow>
                <TableCell colSpan={showApproved ? 4 : 3} align="right">Subtotal</TableCell>
                <TableCell align="right">{formatMoney(o.subtotal, o.currency)}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell colSpan={showApproved ? 4 : 3} align="right">Tax</TableCell>
                <TableCell align="right">{formatMoney(o.taxAmount, o.currency)}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell colSpan={showApproved ? 4 : 3} align="right" sx={{ fontWeight: 700 }}>Total</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>{formatMoney(o.totalAmount, o.currency)}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </Paper>

        <Stack spacing={2}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="overline">Dealer</Typography>
            <Typography>{o.dealer?.name ?? 'Not chosen yet'}</Typography>
            <Typography variant="overline" sx={{ mt: 1, display: 'block' }}>Delivery address</Typography>
            <Typography sx={{ whiteSpace: 'pre-line' }}>{o.deliveryAddress ?? '-'}</Typography>
            {o.customerNotes && (
              <>
                <Typography variant="overline" sx={{ mt: 1, display: 'block' }}>Your notes</Typography>
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
      {PAYABLE.includes(o.status) && <PaymentPanel order={o} />}
          {(o.status === 'DRAFT' || CANCELLABLE.includes(o.status)) && (
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Stack spacing={1}>
                {o.status === 'DRAFT' && (
                  <>
                    <Button variant="contained" onClick={() => void doSubmit()} disabled={submit.isPending || !o.dealer || o.lines.length === 0}>
                      {submit.isPending ? 'Submitting...' : 'Submit order'}
                    </Button>
                    {!o.dealer && <Typography variant="caption" color="text.secondary">Choose a dealer (Edit in cart) before submitting.</Typography>}
                    <Button variant="outlined" onClick={editDraft}>Edit in cart</Button>
                    <Button color="error" onClick={() => setDialog('delete')}>Delete draft</Button>
                  </>
                )}
                {CANCELLABLE.includes(o.status) && <Button color="error" variant="outlined" onClick={() => setDialog('cancel')}>Cancel order</Button>}
              </Stack>
            </Paper>
          )}

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="overline">History</Typography>
            {history.error && <ErrorAlert error={history.error} onRetry={() => void history.refetch()} />}
            {history.data?.map((h, idx) => (
              <Box key={idx} sx={{ mt: 1 }}>
                {idx > 0 && <Divider sx={{ mb: 1 }} />}
                <Typography variant="body2" sx={{ fontWeight: 500 }}>{statusLabel(h.toStatus)}</Typography>
                <Typography variant="caption" color="text.secondary">{formatDateTime(h.changedAt)} · {h.changedByRole.toLowerCase()}</Typography>
                {h.reason && <Typography variant="body2">{h.reason}</Typography>}
              </Box>
            ))}
          </Paper>
        </Stack>
      </Box>

      <ActionDialog open={dialog === 'cancel'} title="Cancel this order?" text="The dealer will be told. This cannot be undone." confirmLabel="Cancel order"
        reasonLabel="Reason (optional)" busy={cancel.isPending} error={actionError} onConfirm={(r) => void doCancel(r)} onClose={closeDialog} />
      <ActionDialog open={dialog === 'delete'} title="Delete this draft?" text="The draft will be removed permanently." confirmLabel="Delete draft"
        busy={remove.isPending} error={actionError} onConfirm={() => void doDelete()} onClose={closeDialog} />
    </>
  );
}
