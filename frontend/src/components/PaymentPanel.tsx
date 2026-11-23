import { Alert, Box, Button, Chip, CircularProgress, Divider, Paper, Stack, Typography } from '@mui/material';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { errorMessage } from '../api/client';
import type { Order } from '../api/orderTypes';
import { useCheckoutCallback, usePayments, useStartPayment, type Payment, type PaymentStatus } from '../api/payments';
import { useAuth } from '../auth/AuthProvider';
import { isFakeGatewayOrder, loadRazorpay, type RazorpaySuccess } from '../payment/razorpay';
import { formatDateTime, formatMoney } from '../utils/format';
import { ErrorAlert } from './ErrorAlert';

const POLL_MS = 3000;
const SLOW_AFTER_MS = 60_000;

const STATUS_LOOK: Record<PaymentStatus, { label: string; color: 'default' | 'info' | 'success' | 'error' }> = {
  INITIATED: { label: 'Started', color: 'default' },
  PENDING_CONFIRMATION: { label: 'Confirming', color: 'info' },
  SUCCESS: { label: 'Paid', color: 'success' },
  FAILED: { label: 'Failed', color: 'error' },
  EXPIRED: { label: 'Expired', color: 'default' },
};

/**
 * Payment for an approved order. The browser never decides that an order is paid: after Checkout it only reports the
 * result, then waits (by re-reading the order) until the gateway's confirmation has reached our servers.
 */
export function PaymentPanel({ order }: { order: Order }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const payments = usePayments(order.id);
  const start = useStartPayment();
  const callback = useCheckoutCallback(order.id);

  const [error, setError] = useState<string | null>(null);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [slow, setSlow] = useState(false);
  const [fake, setFake] = useState<Payment | null>(null);

  const awaiting = order.status === 'AWAITING_PAYMENT';
  const list = payments.data ?? [];
  const latest = list[0];
  const pendingOnServer = list.some((p) => p.status === 'PENDING_CONFIRMATION');
  const waiting = awaiting && (confirming || pendingOnServer);
  const paid = list.find((p) => p.status === 'SUCCESS');

  // while waiting for the gateway's confirmation, re-read the order and its payments every few seconds
  useEffect(() => {
    if (!waiting) {
      setSlow(false);
      return;
    }
    const started = Date.now();
    const timer = setInterval(() => {
      if (Date.now() - started > SLOW_AFTER_MS) setSlow(true);
      void qc.invalidateQueries({ queryKey: ['order', order.id] });
      void qc.invalidateQueries({ queryKey: ['payments', order.id] });
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [waiting, order.id, qc]);

  // a failure confirmed by the gateway ends the waiting and tells the customer why
  useEffect(() => {
    if (confirming && latest?.status === 'FAILED') {
      setConfirming(false);
      setFake(null);
      setError(latest.failureReason ?? 'The payment failed.');
    }
  }, [confirming, latest]);

  // once the order has moved on (paid), the helper states are no longer needed
  useEffect(() => {
    if (!awaiting) {
      setConfirming(false);
      setFake(null);
    }
  }, [awaiting]);

  async function onCheckoutSuccess(paymentId: string, r: RazorpaySuccess) {
    try {
      await callback.mutateAsync({
        paymentId,
        result: { razorpayPaymentId: r.razorpay_payment_id, razorpayOrderId: r.razorpay_order_id, razorpaySignature: r.razorpay_signature },
      });
    } catch (e) {
      // the webhook can still confirm the payment, so keep waiting; just show what went wrong
      setError(errorMessage(e));
    }
    setCheckoutOpen(false);
    setConfirming(true);
  }

  async function pay() {
    setError(null);
    setFake(null);
    try {
      const p = await start.mutateAsync({ orderId: order.id, key: crypto.randomUUID() });
      if (isFakeGatewayOrder(p.gatewayOrderId)) {
        setFake(p);
        setConfirming(true);
        return;
      }
      const Razorpay = await loadRazorpay();
      const rzp = new Razorpay({
        key: p.gatewayKeyId,
        order_id: p.gatewayOrderId,
        amount: Math.round(p.amount * 100),
        currency: p.currency,
        name: 'Order Place',
        description: `Order ${p.orderNumber}`,
        prefill: { name: user.name, email: user.email ?? undefined },
        theme: { color: '#1a56db' },
        handler: (r) => void onCheckoutSuccess(p.paymentId, r),
        modal: { ondismiss: () => setCheckoutOpen(false) },
      });
      rzp.on('payment.failed', (r) => setError(r.error.description ?? 'The payment failed. You can try again.'));
      setCheckoutOpen(true);
      rzp.open();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  if (payments.isLoading) return <Paper variant="outlined" sx={{ p: 2 }}><CircularProgress size={20} /></Paper>;

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="overline">Payment</Typography>

      {payments.error && <ErrorAlert error={payments.error} onRetry={() => void payments.refetch()} />}

      {!awaiting && (
        <Alert severity="success" sx={{ mt: 1 }}>
          Payment received{paid?.completedAt ? ` on ${formatDateTime(paid.completedAt)}` : ''}.
          {order.status === 'READY_FOR_DELIVERY' ? ' Your order is ready for delivery.' : ''}
        </Alert>
      )}

      {awaiting && (
        <>
          <Typography variant="h5" sx={{ mt: 0.5 }}>{formatMoney(order.totalAmount, order.currency)}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>Amount to pay, including tax.</Typography>

          {error && <Alert severity="error" sx={{ mb: 1 }} onClose={() => setError(null)}>{error}</Alert>}

          {fake && (
            <Alert severity="warning" sx={{ mb: 1 }}>
              <strong>Development gateway.</strong> There is no payment window. Send a signed webhook for this gateway order
              (see the payment test sheet, section 3) and this page will update by itself.
              <Box component="code" sx={{ display: 'block', mt: 1, wordBreak: 'break-all' }}>{fake.gatewayOrderId}</Box>
            </Alert>
          )}

          {waiting ? (
            <Stack spacing={1} sx={{ py: 1 }}>
              <Stack direction="row" spacing={1} alignItems="center">
                <CircularProgress size={20} />
                <Typography variant="body2">Confirming your payment. Please do not pay again.</Typography>
              </Stack>
              {slow && (
                <Alert severity="info" action={<Button color="inherit" size="small" onClick={() => void pay()}>Open payment again</Button>}>
                  This is taking longer than usual. If you completed the payment, it will show here soon. You will not be charged twice for one order.
                </Alert>
              )}
            </Stack>
          ) : (
            <Button fullWidth variant="contained" size="large" disabled={start.isPending || checkoutOpen} onClick={() => void pay()}>
              {start.isPending ? 'Starting...' : latest?.status === 'FAILED' ? 'Try payment again' : 'Pay now'}
            </Button>
          )}
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            You pay on the payment provider's secure window. Card details never reach Order Place.
          </Typography>
        </>
      )}

      {list.length > 0 && (
        <>
          <Divider sx={{ my: 2 }} />
          <Typography variant="caption" color="text.secondary">Attempts</Typography>
          {list.map((p) => (
            <Stack key={p.paymentId} direction="row" spacing={1} alignItems="center" sx={{ mt: 0.5 }} useFlexGap flexWrap="wrap">
              <Chip size="small" label={STATUS_LOOK[p.status].label} color={STATUS_LOOK[p.status].color} variant={p.status === 'SUCCESS' || p.status === 'FAILED' ? 'filled' : 'outlined'} />
              <Typography variant="caption">{formatDateTime(p.initiatedAt)}</Typography>
              {p.failureReason && <Typography variant="caption" color="error">{p.failureReason}</Typography>}
            </Stack>
          ))}
        </>
      )}
    </Paper>
  );
}
