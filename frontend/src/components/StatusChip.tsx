import { Chip, type ChipProps } from '@mui/material';
import type { OrderStatus } from '../api/orderTypes';

const LOOK: Record<OrderStatus, { label: string; color: ChipProps['color'] }> = {
  DRAFT: { label: 'Draft', color: 'default' },
  SUBMITTED: { label: 'Submitted', color: 'info' },
  UNDER_REVIEW: { label: 'Under review', color: 'info' },
  AWAITING_PAYMENT: { label: 'Awaiting payment', color: 'warning' },
  PAID: { label: 'Paid', color: 'success' },
  READY_FOR_DELIVERY: { label: 'Ready for delivery', color: 'success' },
  REJECTED: { label: 'Rejected', color: 'error' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

export function statusLabel(s: OrderStatus): string {
  return LOOK[s].label;
}

export function StatusChip({ status, size = 'small' }: { status: OrderStatus; size?: 'small' | 'medium' }) {
  const look = LOOK[status];
  return <Chip size={size} label={look.label} color={look.color} variant={status === 'DRAFT' || status === 'CANCELLED' ? 'outlined' : 'filled'} />;
}
