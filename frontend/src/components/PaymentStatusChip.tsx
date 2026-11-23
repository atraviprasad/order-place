import { Chip } from '@mui/material';
import type { PaymentStatus } from '../api/payments';

const LOOK: Record<PaymentStatus, { label: string; color: 'default' | 'info' | 'success' | 'error' }> = {
  INITIATED: { label: 'Started', color: 'default' },
  PENDING_CONFIRMATION: { label: 'Confirming', color: 'info' },
  SUCCESS: { label: 'Paid', color: 'success' },
  FAILED: { label: 'Failed', color: 'error' },
  EXPIRED: { label: 'Expired', color: 'default' },
};

export const PAYMENT_STATUSES = Object.keys(LOOK) as PaymentStatus[];

export function paymentStatusLabel(s: PaymentStatus): string {
  return LOOK[s].label;
}

export function PaymentStatusChip({ status }: { status: PaymentStatus }) {
  const l = LOOK[status];
  return <Chip size="small" label={l.label} color={l.color} variant={status === 'INITIATED' || status === 'EXPIRED' ? 'outlined' : 'filled'} />;
}
