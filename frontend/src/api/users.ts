import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { Paged } from './types';

export type UserRole = 'CUSTOMER' | 'DEALER';

export interface AppUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  role: UserRole;
  orgId: string;
  orgName: string | null; // filled for customer users only; dealer names come from the dealer list
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface UserCreated {
  user: AppUser;
  setPasswordEmailSent: boolean;
}

export interface UserQuery {
  q?: string;
  role?: UserRole;
  active?: boolean;
  page: number;
  size: number;
  sort: string;
}

export function useUsers(query: UserQuery) {
  return useQuery({
    queryKey: ['users', query],
    queryFn: ({ signal }) => api<Paged<AppUser>>('/users', { query: { ...query }, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useUser(id: string | undefined) {
  return useQuery({
    queryKey: ['user', id],
    queryFn: ({ signal }) => api<AppUser>(`/users/${id}`, { signal }),
    enabled: !!id,
  });
}

function useRefresh() {
  const qc = useQueryClient();
  return (u: AppUser) => {
    qc.setQueryData(['user', u.id], u);
    void qc.invalidateQueries({ queryKey: ['users'] });
    void qc.invalidateQueries({ queryKey: ['org-users'] }); // the user list on the customer page
  };
}

export function useCreateUser() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => api<UserCreated>('/users', { method: 'POST', body }),
    onSuccess: (r) => refresh(r.user),
  });
}

export function useUpdateUser() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: Record<string, unknown> }) => api<AppUser>(`/users/${id}`, { method: 'PUT', body }),
    onSuccess: refresh,
  });
}

export function useSetUserActive() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => api<AppUser>(`/users/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
    onSuccess: refresh,
  });
}

/** Asks Keycloak to send the "set your password" email again. The answer is 202 with no body. */
export function useResetPassword() {
  return useMutation({
    mutationFn: (id: string) => api<void>(`/users/${id}/reset-password`, { method: 'POST' }),
  });
}
