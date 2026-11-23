import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';

export type PaymentStatus = 'INITIATED' | 'PENDING_CONFIRMATION' | 'SUCCESS' | 'FAILED' | 'EXPIRED';

export interface Payment {
  paymentId: string;
  orderId: string;
  orderNumber: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  gateway: string;
  gatewayOrderId: string;
  gatewayKeyId: string; // the public test key, safe to use in the browser
  failureReason: string | null;
  initiatedAt: string;
  completedAt: string | null;
  version: number;
}

export interface CheckoutResult {
  razorpayPaymentId: string;
  razorpayOrderId: string;
  razorpaySignature: string;
}

/** All payment attempts of an order, newest first. */
export function usePayments(orderId: string) {
  return useQuery({
    queryKey: ['payments', orderId],
    queryFn: ({ signal }) => api<Payment[]>('/payments', { query: { orderId }, signal }),
  });
}

/**
 * Starts a payment, or returns the one already open for the order. A fresh Idempotency-Key per click is correct:
 * the server reuses an open payment, and after a failed one a new key is what allows a new attempt.
 */
export function useStartPayment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, key }: { orderId: string; key: string }) =>
      api<Payment>('/payments', { method: 'POST', body: { orderId }, headers: { 'Idempotency-Key': key } }),
    onSuccess: (_p, v) => void qc.invalidateQueries({ queryKey: ['payments', v.orderId] }),
  });
}

/** Tells the server what Checkout returned. The server only checks the signature; the order is paid when the gateway confirms it. */
export function useCheckoutCallback(orderId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ paymentId, result }: { paymentId: string; result: CheckoutResult }) =>
      api<{ status: PaymentStatus }>(`/payments/${paymentId}/client-callback`, { method: 'POST', body: result }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['payments', orderId] }),
  });
}
