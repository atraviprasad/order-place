import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { Paged } from './types';

/** Customers and dealers are alike: both are organisations with a code, a contact and an active flag. */
export type OrgPath = '/customers' | '/dealers';

export interface Org {
  id: string;
  name: string;
  contactPerson: string;
  email: string;
  phone: string;
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
  // the rest differs by kind (customerCode, billingAddress, dealerCode, taxRegistrationNo, ...)
  [key: string]: string | number | boolean | null | undefined;
}

export interface OrgQuery {
  q?: string;
  active?: boolean;
  page: number;
  size: number;
  sort: string;
}

export interface OrgUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  role: string;
  active: boolean;
}

export function useOrgs(path: OrgPath, query: OrgQuery) {
  return useQuery({
    queryKey: ['orgs', path, query],
    queryFn: ({ signal }) => api<Paged<Org>>(path, { query: { ...query }, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useOrg(path: OrgPath, id: string | undefined) {
  return useQuery({
    queryKey: ['org', path, id],
    queryFn: ({ signal }) => api<Org>(`${path}/${id}`, { signal }),
    enabled: !!id,
  });
}

export function useCustomerUsers(id: string | undefined) {
  return useQuery({
    queryKey: ['org-users', id],
    queryFn: ({ signal }) => api<OrgUser[]>(`/customers/${id}/users`, { signal }),
    enabled: !!id,
  });
}

function useRefresh(path: OrgPath) {
  const qc = useQueryClient();
  return (org: Org) => {
    qc.setQueryData(['org', path, org.id], org);
    void qc.invalidateQueries({ queryKey: ['orgs', path] });
    if (path === '/dealers') void qc.invalidateQueries({ queryKey: ['dealers', 'active'] }); // the customer's dealer dropdown
  };
}

/** Create (no id) or update (id) an organisation. */
export function useSaveOrg(path: OrgPath) {
  const refresh = useRefresh(path);
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: Record<string, unknown> }) =>
      id ? api<Org>(`${path}/${id}`, { method: 'PUT', body }) : api<Org>(path, { method: 'POST', body }),
    onSuccess: refresh,
  });
}

export function useSetOrgActive(path: OrgPath) {
  const refresh = useRefresh(path);
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => api<Org>(`${path}/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
    onSuccess: refresh,
  });
}
