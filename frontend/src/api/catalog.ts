import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from './client';
import type { CategoryNode, Paged, ProductDetail, ProductQuery, ProductSummary } from './types';

export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: ({ signal }) => api<CategoryNode[]>('/categories', { signal }),
    staleTime: 5 * 60_000, // the tree changes rarely
  });
}

export function useProducts(query: ProductQuery) {
  return useQuery({
    queryKey: ['products', query],
    queryFn: ({ signal }) => api<Paged<ProductSummary>>('/products', { query: { ...query }, signal }),
    placeholderData: keepPreviousData, // keep showing the old page while the next one loads
  });
}

export function useProduct(id: string | null) {
  return useQuery({
    queryKey: ['product', id],
    queryFn: ({ signal }) => api<ProductDetail>(`/products/${id}`, { signal }),
    enabled: id !== null,
  });
}
