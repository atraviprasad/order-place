import { Alert, Box, Button, Chip, CircularProgress, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useCustomerUsers, useOrg, useSetOrgActive } from '../api/orgs';
import type { OrgKind } from '../admin/orgKinds';
import { ActionDialog } from '../components/ActionDialog';
import { ErrorAlert } from '../components/ErrorAlert';
import { OrgFormDialog } from '../components/OrgFormDialog';
import { UserFormDialog } from '../components/UserFormDialog';
import { formatDateTime } from '../utils/format';

function Row({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="overline" sx={{ display: 'block', lineHeight: 1.5 }}>{label}</Typography>
      <Typography sx={{ whiteSpace: 'pre-line' }}>{value || '-'}</Typography>
    </Box>
  );
}

export function AdminOrgDetailPage({ kind }: { kind: OrgKind }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const org = useOrg(kind.path, id);
  const users = useCustomerUsers(kind.route === 'customers' ? id : undefined);
  const setActive = useSetOrgActive(kind.path);
  const [editing, setEditing] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [addingUser, setAddingUser] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  if (org.isLoading) return <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}><CircularProgress /></Box>;
  if (org.error) return <ErrorAlert error={org.error} onRetry={() => void org.refetch()} />;
  const o = org.data;
  if (!o) return null;

  async function toggle() {
    setActionError(null);
    try {
      await setActive.mutateAsync({ id: o!.id, active: !o!.active });
      setConfirm(false);
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  return (
    <>
      <Button component={RouterLink} to={`/admin/${kind.route}`} size="small" sx={{ mb: 1 }}>Back to {kind.plural.toLowerCase()}</Button>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }} useFlexGap flexWrap="wrap">
        <Typography variant="h5">{o.name}</Typography>
        <Chip label={o.active ? 'Active' : 'Inactive'} color={o.active ? 'success' : 'default'} variant={o.active ? 'filled' : 'outlined'} />
      </Stack>
      {!o.active && <Alert severity="warning" sx={{ mb: 2 }}>This {kind.singular} is inactive. {kind.route === 'dealers' ? 'It no longer appears in the customers\' dealer list.' : 'Its users cannot be created or activated until it is active again.'}</Alert>}
      {actionError && !confirm && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, alignItems: 'start' }}>
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Box sx={{ display: 'grid', columnGap: 4, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
            <Row label={kind.codeLabel} value={String(o[kind.codeKey] ?? '')} />
            {kind.fields.map((f) => <Row key={f.key} label={f.label} value={String(o[f.key] ?? '')} />)}
            <Row label="Created" value={formatDateTime(o.createdAt)} />
            <Row label="Last changed" value={formatDateTime(o.updatedAt)} />
          </Box>
        </Paper>

        <Stack spacing={2}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={1}>
              <Button variant="contained" onClick={() => setEditing(true)}>Edit</Button>
              <Button variant="outlined" component={RouterLink} to={`/admin/orders?${kind.orderFilter}=${o.id}`}>View orders</Button>
              <Button variant="outlined" color={o.active ? 'error' : 'success'} onClick={() => { setActionError(null); setConfirm(true); }}>
                {o.active ? 'Deactivate' : 'Activate'}
              </Button>
            </Stack>
          </Paper>
        </Stack>
      </Box>

      {kind.route === 'customers' && (
        <Paper variant="outlined" sx={{ mt: 3, overflowX: 'auto' }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ p: 2 }}>
            <Typography variant="overline">Users of this customer</Typography>
            <Button size="small" variant="outlined" disabled={!o.active} onClick={() => setAddingUser(true)}>New user</Button>
          </Stack>
          {users.error && <ErrorAlert error={users.error} onRetry={() => void users.refetch()} />}
          {users.data && users.data.length === 0 && <Typography color="text.secondary" sx={{ px: 2, pb: 2 }}>No users yet.</Typography>}
          {users.data && users.data.length > 0 && (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Phone</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {users.data.map((u) => (
                  <TableRow key={u.id} hover sx={{ cursor: 'pointer', opacity: u.active ? 1 : 0.6 }} onClick={() => navigate(`/admin/users/${u.id}`)}>
                    <TableCell>{u.firstName} {u.lastName}</TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>{u.phone ?? '-'}</TableCell>
                    <TableCell><Chip size="small" label={u.active ? 'Active' : 'Inactive'} color={u.active ? 'success' : 'default'} variant={u.active ? 'filled' : 'outlined'} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Paper>
      )}

      {kind.route === 'customers' && (
        <UserFormDialog open={addingUser} user={null} preset={{ role: 'CUSTOMER', org: o }} onClose={() => setAddingUser(false)}
          onSaved={(r) => { setAddingUser(false); navigate(`/admin/users/${r.user.id}`, { state: { emailSent: r.setPasswordEmailSent } }); }} />
      )}
      <OrgFormDialog open={editing} kind={kind} org={o} onClose={() => setEditing(false)} onSaved={() => setEditing(false)} />
      <ActionDialog open={confirm} title={`${o.active ? 'Deactivate' : 'Activate'} this ${kind.singular}?`}
        text={o.active ? `${o.name} will be hidden from new orders. Existing orders are not changed.` : `${o.name} will be usable again.`}
        confirmLabel={o.active ? 'Deactivate' : 'Activate'} confirmColor={o.active ? 'error' : 'success'} busy={setActive.isPending} error={actionError}
        onConfirm={() => void toggle()} onClose={() => { setConfirm(false); setActionError(null); }} />
    </>
  );
}
