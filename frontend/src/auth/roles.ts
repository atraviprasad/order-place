export type Role = 'ADMIN' | 'DEALER' | 'CUSTOMER';

// Same priority as the backend (TenantContext): the first role found wins.
const PRIORITY: Role[] = ['ADMIN', 'DEALER', 'CUSTOMER'];

export function pickRole(realmRoles: string[] | undefined): Role | null {
  return PRIORITY.find((r) => realmRoles?.includes(r)) ?? null;
}

export const HOME_PATH: Record<Role, string> = {
  CUSTOMER: '/catalog',
  DEALER: '/dealer/orders',
  ADMIN: '/admin',
};
