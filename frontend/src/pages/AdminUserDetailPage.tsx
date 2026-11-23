import { Alert, Box, Button, Chip, CircularProgress, Paper, Snackbar, Stack, Typography } from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, useLocation, useParams } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useOrg } from '../api/orgs';
import { useResetPassword, useSetUserActive, useUser } from '../api/users';
import { ActionDialog } from '../components/ActionDialog';
import { ErrorAlert } from '../components/ErrorAlert';
import { UserFormDialog } from '../components/UserFormDialog';
import { formatDateTime } from '../utils/format';
import { ROLE_LABEL } from './AdminUsersPage';

function Row({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="overline" sx={{ display: 'block', lineHeight: 1.5 }}>{label}</Typography>
      <Typography>{value || '-'}</Typography>
    </Box>
  );
}

export function AdminUserDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const user = useUser(id);
  const orgPath = user.data?.role === 'DEALER' ? '/dealers' : '/customers';
  const org = useOrg(orgPath, user.data?.orgId);
  const setActive = useSetUserActive();
  const reset = useResetPassword();
  const [editing, setEditing] = useState(false);
  const [dialog, setDialog] = useState<'toggle' | 'reset' | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  // set by the create screen: false means the account exists but Keycloak could not send the password email
  const createdState = (location.state as { emailSent?: boolean } | null) ?? null;
  const [hideCreatedNote, setHideCreatedNote] = useState(false);

  if (user.isLoading) return <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}><CircularProgress /></Box>;
  if (user.error) return <ErrorAlert error={user.error} onRetry={() => void user.refetch()} />;
  const u = user.data;
  if (!u) return null;

  const orgRoute = u.role === 'CUSTOMER' ? 'customers' : 'dealers';
  const orgName = u.orgName ?? org.data?.name ?? '-';

  function closeDialog() {
    setDialog(null);
    setActionError(null);
  }

  async function toggle() {
    setActionError(null);
    try {
      await setActive.mutateAsync({ id: u!.id, active: !u!.active });
      setDialog(null);
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  async function sendReset() {
    setActionError(null);
    try {
      await reset.mutateAsync(u!.id);
      setDialog(null);
      setToast(`Password email sent to ${u!.email}`);
    } catch (e) {
      setActionError(errorMessage(e));
    }
  }

  return (
    <>
      <Button component={RouterLink} to="/admin/users" size="small" sx={{ mb: 1 }}>Back to users</Button>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }} useFlexGap flexWrap="wrap">
        <Typography variant="h5">{u.firstName} {u.lastName}</Typography>
        <Chip label={u.active ? 'Active' : 'Inactive'} color={u.active ? 'success' : 'default'} variant={u.active ? 'filled' : 'outlined'} />
      </Stack>

      {createdState?.emailSent === true && !hideCreatedNote && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setHideCreatedNote(true)}>User created. An email to set a password was sent to {u.email}.</Alert>}
      {createdState?.emailSent === false && !hideCreatedNote && (
        <Alert severity="warning" sx={{ mb: 2 }} onClose={() => setHideCreatedNote(true)}>
          The account was created, but the "set your password" email could not be sent. Use "Send password email again".
        </Alert>
      )}
      {!u.active && <Alert severity="warning" sx={{ mb: 2 }}>This user is inactive and cannot sign in.</Alert>}
      {actionError && dialog === null && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, alignItems: 'start' }}>
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Box sx={{ display: 'grid', columnGap: 4, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
            <Row label="Email (login name)" value={u.email} />
            <Row label="Phone" value={u.phone ?? ''} />
            <Row label="Role" value={ROLE_LABEL[u.role]} />
            <Box sx={{ mb: 1.5 }}>
              <Typography variant="overline" sx={{ display: 'block', lineHeight: 1.5 }}>{u.role === 'CUSTOMER' ? 'Customer' : 'Dealer'}</Typography>
              <Typography component={RouterLink} to={`/admin/${orgRoute}/${u.orgId}`} sx={{ color: 'primary.main' }}>{orgName}</Typography>
            </Box>
            <Row label="Created" value={formatDateTime(u.createdAt)} />
            <Row label="Last changed" value={formatDateTime(u.updatedAt)} />
          </Box>
        </Paper>

        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={1}>
            <Button variant="contained" onClick={() => setEditing(true)}>Edit</Button>
            <Button variant="outlined" disabled={!u.active} onClick={() => { setActionError(null); setDialog('reset'); }}>Send password email again</Button>
            <Button variant="outlined" color={u.active ? 'error' : 'success'} onClick={() => { setActionError(null); setDialog('toggle'); }}>{u.active ? 'Deactivate' : 'Activate'}</Button>
          </Stack>
        </Paper>
      </Box>

      <UserFormDialog open={editing} user={u} onClose={() => setEditing(false)} onSaved={() => setEditing(false)} />
      <ActionDialog open={dialog === 'toggle'} title={`${u.active ? 'Deactivate' : 'Activate'} this user?`}
        text={u.active ? `${u.firstName} ${u.lastName} will no longer be able to sign in. Their orders stay as they are.` : `${u.firstName} ${u.lastName} will be able to sign in again.`}
        confirmLabel={u.active ? 'Deactivate' : 'Activate'} confirmColor={u.active ? 'error' : 'success'} busy={setActive.isPending} error={actionError}
        onConfirm={() => void toggle()} onClose={closeDialog} />
      <ActionDialog open={dialog === 'reset'} title="Send the password email again?" text={`${u.email} will receive an email with a link to set a new password.`}
        confirmLabel="Send email" confirmColor="primary" busy={reset.isPending} error={actionError} onConfirm={() => void sendReset()} onClose={closeDialog} />
      {toast && (
        <Snackbar open autoHideDuration={4000} onClose={() => setToast(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
          <Alert severity="success" variant="filled" onClose={() => setToast(null)}>{toast}</Alert>
        </Snackbar>
      )}
    </>
  );
}
