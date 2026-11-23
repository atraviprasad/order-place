import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { StatusSummary } from './dealerOrders';
import { api } from './client';
import type { HistoryEntry, Order, OrderQuery, OrderSummary } from './orderTypes';
import type { Payment, PaymentStatus } from './payments';
import type { Paged } from './types';

// ---- orders ----
export interface AdminOrderQuery extends OrderQuery {
  customerId?: string;
  dealerId?: string;
  from?: string; // YYYY-MM-DD
  to?: string;
}

export interface AdminOrderDetail {
  order: Order;
  history: HistoryEntry[];
}

export function useAdminOrderSummary() {
  return useQuery({
    queryKey: ['admin-order-summary'],
    queryFn: ({ signal }) => api<StatusSummary>('/admin/orders/summary', { signal }),
  });
}

export function useAdminOrders(query: AdminOrderQuery) {
  return useQuery({
    queryKey: ['admin-orders', query],
    queryFn: ({ signal }) => api<Paged<OrderSummary>>('/admin/orders', { query: { ...query }, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useAdminOrder(id: string | undefined) {
  return useQuery({
    queryKey: ['admin-order', id],
    queryFn: ({ signal }) => api<AdminOrderDetail>(`/admin/orders/${id}`, { signal }),
    enabled: !!id,
  });
}

// ---- payments ----
export interface AdminPaymentQuery {
  status?: PaymentStatus;
  orderId?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
  sort: string;
}

export function useAdminPayments(query: AdminPaymentQuery) {
  return useQuery({
    queryKey: ['admin-payments', query],
    queryFn: ({ signal }) => api<Paged<Payment>>('/admin/payments', { query: { ...query }, signal }),
    placeholderData: keepPreviousData,
  });
}

// ---- notifications ----
export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED';

export interface AppNotification {
  id: string;
  eventType: string;
  orderId: string | null;
  orderNumber: string | null;
  recipientRole: 'CUSTOMER' | 'DEALER' | 'ADMIN';
  recipient: string;
  channel: string;
  subject: string;
  status: NotificationStatus;
  attempts: number;
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
}

export interface AdminNotificationQuery {
  eventType?: string;
  status?: NotificationStatus;
  recipient?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
  sort: string;
}

export function useAdminNotifications(query: AdminNotificationQuery) {
  return useQuery({
    queryKey: ['admin-notifications', query],
    queryFn: ({ signal }) => api<Paged<AppNotification>>('/admin/notifications', { query: { ...query }, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useRetryNotification() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<AppNotification>(`/admin/notifications/${id}/retry`, { method: 'POST' }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin-notifications'] });
      void qc.invalidateQueries({ queryKey: ['admin-total'] });
    },
  });
}

// ---- dashboard counts: ask for one row and read the total ----
export function useTotal(path: string, filter: Record<string, string>) {
  return useQuery({
    queryKey: ['admin-total', path, filter],
    queryFn: async ({ signal }) => (await api<Paged<unknown>>(path, { query: { ...filter, page: 0, size: 1 }, signal })).totalElements,
    refetchInterval: 60_000,
  });
}
