import { Alert, Box, Button, CircularProgress, Stack, Typography } from '@mui/material';
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { initKeycloak, keycloak } from './keycloak';
import { pickRole, type Role } from './roles';

export interface CurrentUser {
  id: string;
  username: string;
  name: string;
  email: string | null;
  role: Role;
  orgId: string | null;
  orgType: string | null;
}

interface AuthContextValue {
  user: CurrentUser;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

interface Claims {
  sub?: string;
  preferred_username?: string;
  name?: string;
  email?: string;
  org_id?: string;
  org_type?: string;
  realm_access?: { roles?: string[] };
}

function readUser(): CurrentUser | { error: string } {
  const c = (keycloak.tokenParsed ?? {}) as Claims;
  const role = pickRole(c.realm_access?.roles);
  if (!role) return { error: 'Your account has no Order Place role (ADMIN, DEALER or CUSTOMER). Ask an administrator.' };
  return {
    id: c.sub ?? '',
    username: c.preferred_username ?? '',
    name: c.name ?? c.preferred_username ?? 'User',
    email: c.email ?? null,
    role,
    orgId: c.org_id ?? null,
    orgType: c.org_type ?? null,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; user: CurrentUser }>({ status: 'loading' });

  useEffect(() => {
    initKeycloak()
      .then((authenticated) => {
        if (!authenticated) return keycloak.login();
        const result = readUser();
        setState('error' in result ? { status: 'error', message: result.error } : { status: 'ready', user: result });
      })
      .catch(() => setState({ status: 'error', message: 'Could not reach the login server. Is Keycloak running on port 8180?' }));
  }, []);

  const value = useMemo<AuthContextValue | null>(
    () => (state.status === 'ready' ? { user: state.user, logout: () => keycloak.logout({ redirectUri: window.location.origin }) } : null),
    [state],
  );

  if (state.status === 'loading') {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <CircularProgress aria-label="Signing in" />
      </Box>
    );
  }
  if (state.status === 'error') {
    return (
      <Stack spacing={2} sx={{ maxWidth: 480, mx: 'auto', mt: 10, px: 2 }}>
        <Typography variant="h5">Order Place</Typography>
        <Alert severity="error">{state.message}</Alert>
        <Stack direction="row" spacing={1}>
          <Button variant="contained" onClick={() => window.location.reload()}>Try again</Button>
          <Button onClick={() => keycloak.logout({ redirectUri: window.location.origin })}>Sign out</Button>
        </Stack>
      </Stack>
    );
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
