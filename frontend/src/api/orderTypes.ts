export type OrderStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'AWAITING_PAYMENT'
  | 'PAID'
  | 'READY_FOR_DELIVERY'
  | 'REJECTED'
  | 'CANCELLED';

export interface DealerSummary {
  id: string;
  dealerCode: string;
  name: string;
}

export interface OrderSummary {
  id: string;
  orderNumber: string | null; // a draft has no number yet
  status: OrderStatus;
  customerId: string;
  customerName: string;
  dealerId: string | null;
  dealerName: string | null;
  totalAmount: number;
  currency: string;
  submittedAt: string | null;
  createdAt: string;
  version: number;
}

export interface OrderLine {
  id: string;
  lineNo: number;
  productId: string;
  sku: string;
  productName: string;
  unitPrice: number;
  submittedQty: number;
  approvedQty: number | null; // null until the dealer approves
  lineTotal: number;
}

export interface Order {
  id: string;
  orderNumber: string | null;
  status: OrderStatus;
  customerId: string;
  customerName: string;
  dealer: { id: string; name: string } | null;
  deliveryAddress: string | null;
  customerNotes: string | null;
  lines: OrderLine[];
  subtotal: number;
  taxAmount: number;
  totalAmount: number;
  currency: string;
  submittedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  dealerAction: { action: string; remarks: string | null; actedAt: string } | null;
}

export interface HistoryEntry {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  changedByRole: string;
  changedByUserId: string | null;
  reason: string | null;
  changedAt: string;
}

export interface OrderQuery {
  q?: string;
  status?: OrderStatus;
  page: number;
  size: number;
  sort: string;
}

export interface SaveOrderBody {
  version?: number;
  dealerId: string | null;
  deliveryAddress?: string;
  customerNotes?: string;
  lines: { productId: string; quantity: number }[];
}

export const FINAL_STATUSES: OrderStatus[] = ['READY_FOR_DELIVERY', 'REJECTED', 'CANCELLED'];
