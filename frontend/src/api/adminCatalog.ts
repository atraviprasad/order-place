import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { Availability, Paged, ProductDetail, ProductSummary } from './types';

// ---------- products ----------
export interface AdminProductQuery {
  q?: string;
  categoryId?: string;
  availability?: Availability;
  includeInactive: boolean; // the API cannot list only inactive products: true = active and inactive together
  page: number;
  size: number;
  sort: string;
}

export interface PriceChange {
  oldPrice: number | null;
  newPrice: number;
  changedBy: string | null;
  changedAt: string;
}

export function useAdminProducts(query: AdminProductQuery) {
  return useQuery({
    queryKey: ['admin-products', query],
    queryFn: ({ signal }) => api<Paged<ProductSummary>>('/products', { query: { ...query }, signal }),
    placeholderData: keepPreviousData,
  });
}

export function usePriceHistory(id: string | undefined, page: number) {
  return useQuery({
    queryKey: ['price-history', id, page],
    queryFn: ({ signal }) => api<Paged<PriceChange>>(`/products/${id}/price-history`, { query: { page, size: 10 }, signal }),
    enabled: !!id,
    placeholderData: keepPreviousData,
  });
}

/** After any change, the lists, the detail, and the catalog customers see must be read again. */
function refreshProduct(qc: QueryClient, p?: ProductDetail, id?: string) {
  if (p) qc.setQueryData(['product', p.id], p);
  else if (id) void qc.invalidateQueries({ queryKey: ['product', id] });
  void qc.invalidateQueries({ queryKey: ['admin-products'] });
  void qc.invalidateQueries({ queryKey: ['products'] });
  void qc.invalidateQueries({ queryKey: ['price-history'] });
}

export function useSaveProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: Record<string, unknown> }) =>
      id ? api<ProductDetail>(`/products/${id}`, { method: 'PUT', body }) : api<ProductDetail>('/products', { method: 'POST', body }),
    onSuccess: (p) => refreshProduct(qc, p),
  });
}

export function useSetProductActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => api<ProductDetail>(`/products/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
    onSuccess: (p) => refreshProduct(qc, p),
  });
}

export function useUploadImage(productId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return api<ProductDetail>(`/products/${productId}/images`, { method: 'POST', body: form });
    },
    onSuccess: (p) => refreshProduct(qc, p),
  });
}

export function useSetPrimaryImage(productId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (imageId: string) => api<ProductDetail>(`/products/${productId}/images/${imageId}/primary`, { method: 'PUT' }),
    onSuccess: (p) => refreshProduct(qc, p),
  });
}

export function useDeleteImage(productId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (imageId: string) => api<void>(`/products/${productId}/images/${imageId}`, { method: 'DELETE' }),
    onSuccess: () => refreshProduct(qc, undefined, productId),
  });
}

// ---------- categories (the tree is read with useCategories) ----------
export interface CategoryBody {
  name: string;
  parentId: string | null;
  sortOrder?: number;
}

function refreshCategories(qc: QueryClient) {
  void qc.invalidateQueries({ queryKey: ['categories'] });
  void qc.invalidateQueries({ queryKey: ['products'] });
  void qc.invalidateQueries({ queryKey: ['admin-products'] });
}

export function useSaveCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: CategoryBody }) =>
      id ? api<unknown>(`/categories/${id}`, { method: 'PUT', body }) : api<unknown>('/categories', { method: 'POST', body }),
    onSuccess: () => refreshCategories(qc),
  });
}

export function useSetCategoryActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => api<unknown>(`/categories/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
    onSuccess: () => refreshCategories(qc),
  });
}
