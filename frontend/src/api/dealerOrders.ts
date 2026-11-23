import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api } from './client';
import { FINAL_STATUSES, type Order, type OrderQuery, type OrderStatus, type OrderSummary } from './orderTypes';
import type { Paged } from './types';

export interface StatusSummary {
  counts: Partial<Record<OrderStatus, number>>;
  total: number;
}

const LIST_REFRESH_MS = 30_000;

export function useDealerSummary() {
  return useQuery({
    queryKey: ['dealer-summary'],
    queryFn: ({ signal }) => api<StatusSummary>('/dealer/orders/summary', { signal }),
    refetchInterval: LIST_REFRESH_MS,
  });
}

export function useDealerOrders(query: OrderQuery) {
  return useQuery({
    queryKey: ['dealer-orders', query],
    queryFn: ({ signal }) => api<Paged<OrderSummary>>('/dealer/orders', { query: { ...query }, signal }),
    placeholderData: keepPreviousData,
    refetchInterval: LIST_REFRESH_MS,
  });
}

export function useDealerOrder(id: string | undefined) {
  return useQuery({
    queryKey: ['dealer-order', id],
    queryFn: ({ signal }) => api<Order>(`/dealer/orders/${id}`, { signal }),
    enabled: !!id,
    refetchInterval: (q) => (q.state.data && !FINAL_STATUSES.includes(q.state.data.status) ? 15_000 : false),
  });
}

function refresh(qc: QueryClient, order: Order) {
  qc.setQueryData(['dealer-order', order.id], order);
  void qc.invalidateQueries({ queryKey: ['dealer-orders'] });
  void qc.invalidateQueries({ queryKey: ['dealer-summary'] });
}

export function useStartReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) =>
      api<Order>(`/dealer/orders/${id}/start-review`, { method: 'POST', body: { version } }),
    onSuccess: (o) => refresh(qc, o),
  });
}

export function useApprove() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, version, remarks }: { id: string; version: number; remarks: string }) =>
      api<Order>(`/dealer/orders/${id}/approve`, { method: 'POST', body: { version, remarks: remarks || undefined } }),
    onSuccess: (o) => refresh(qc, o),
  });
}

export function useReject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, version, reason }: { id: string; version: number; reason: string }) =>
      api<Order>(`/dealer/orders/${id}/reject`, { method: 'POST', body: { version, reason } }),
    onSuccess: (o) => refresh(qc, o),
  });
}

export interface EditLine {
  lineId: string;
  approvedQty: number;
}

export function useEditAndApprove() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, version, remarks, lines }: { id: string; version: number; remarks: string; lines: EditLine[] }) =>
      api<Order>(`/dealer/orders/${id}/edit-and-approve`, { method: 'POST', body: { version, remarks, lines } }),
    onSuccess: (o) => refresh(qc, o),
  });
}
