import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { FINAL_STATUSES, type DealerSummary, type HistoryEntry, type Order, type OrderQuery, type OrderSummary, type SaveOrderBody } from './orderTypes';
import type { Paged } from './types';

export function useDealers() {
  return useQuery({
    queryKey: ['dealers', 'active'],
    queryFn: ({ signal }) => api<DealerSummary[]>('/dealers/active', { signal }),
    staleTime: 5 * 60_000,
  });
}

export function useOrders(query: OrderQuery) {
  return useQuery({
    queryKey: ['orders', query],
    queryFn: ({ signal }) => api<Paged<OrderSummary>>('/orders', { query: { ...query }, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useOrder(id: string | undefined) {
  return useQuery({
    queryKey: ['order', id],
    queryFn: ({ signal }) => api<Order>(`/orders/${id}`, { signal }),
    enabled: !!id,
    // an order that can still change is re-read every 15 seconds so dealer decisions show up without a reload
    refetchInterval: (q) => (q.state.data && !FINAL_STATUSES.includes(q.state.data.status) ? 15_000 : false),
  });
}

export function useOrderHistory(id: string | undefined, status: string | undefined) {
  return useQuery({
    queryKey: ['order', id, 'history', status], // status in the key: a new status means new history rows
    queryFn: ({ signal }) => api<HistoryEntry[]>(`/orders/${id}/history`, { signal }),
    enabled: !!id,
  });
}

/** Creates a draft, or replaces the draft when an id is given. The body carries the version for update. */
export function useSaveDraft() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: SaveOrderBody }) =>
      id ? api<Order>(`/orders/${id}`, { method: 'PUT', body }) : api<Order>('/orders', { method: 'POST', body }),
    onSuccess: (order) => {
      void qc.invalidateQueries({ queryKey: ['orders'] });
      qc.setQueryData(['order', order.id], order);
    },
  });
}

export function useSubmitOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, version, key }: { id: string; version: number; key: string }) =>
      api<Order>(`/orders/${id}/submit`, { method: 'POST', body: { version }, headers: { 'Idempotency-Key': key } }),
    onSuccess: (order) => {
      void qc.invalidateQueries({ queryKey: ['orders'] });
      void qc.invalidateQueries({ queryKey: ['order', order.id] });
    },
  });
}

export function useCancelOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, version, reason }: { id: string; version: number; reason: string }) =>
      api<Order>(`/orders/${id}/cancel`, { method: 'POST', body: { version, reason: reason || undefined } }),
    onSuccess: (order) => {
      void qc.invalidateQueries({ queryKey: ['orders'] });
      void qc.invalidateQueries({ queryKey: ['order', order.id] });
    },
  });
}

export function useDeleteDraft() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/orders/${id}`, { method: 'DELETE' }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['orders'] }),
  });
}
