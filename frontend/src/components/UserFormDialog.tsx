import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, InputLabel, MenuItem, Select, Stack, TextField } from '@mui/material';
import { useEffect, useState } from 'react';
import { ApiError, errorMessage } from '../api/client';
import { useOrg, type Org, type OrgPath } from '../api/orgs';
import { useCreateUser, useUpdateUser, type AppUser, type UserCreated, type UserRole } from '../api/users';
import { OrgPicker } from './OrgPicker';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PATH: Record<UserRole, OrgPath> = { CUSTOMER: '/customers', DEALER: '/dealers' };

interface Props {
  open: boolean;
  user: AppUser | null; // null = create
  preset?: { role: UserRole; org: Org }; // create from a customer page: role and organisation are fixed
  onClose: () => void;
  onSaved: (result: { user: AppUser; setPasswordEmailSent?: boolean }) => void;
}

export function UserFormDialog({ open, user, preset, onClose, onSaved }: Props) {
  const create = useCreateUser();
  const update = useUpdateUser();
  const [v, setV] = useState({ email: '', firstName: '', lastName: '', phone: '' });
  const [role, setRole] = useState<UserRole>('CUSTOMER');
  const [orgChoice, setOrgChoice] = useState<Org | null | undefined>(undefined); // undefined = not touched
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const editing = user !== null;
  const currentOrg = useOrg(PATH[user?.role ?? role], user ? user.orgId : undefined);
  const org: Org | null = orgChoice !== undefined ? orgChoice : preset ? preset.org : currentOrg.data ?? null;
  const busy = create.isPending || update.isPending;

  useEffect(() => {
    if (!open) return;
    setV({ email: user?.email ?? '', firstName: user?.firstName ?? '', lastName: user?.lastName ?? '', phone: user?.phone ?? '' });
    setRole(user?.role ?? preset?.role ?? 'CUSTOMER');
    setOrgChoice(undefined);
    setErrors({});
    setFormError(null);
  }, [open, user?.id, preset?.org.id]); // eslint-disable-line react-hooks/exhaustive-deps -- a background refresh must not wipe what the admin is typing

  const set = (k: keyof typeof v, value: string) => setV((s) => ({ ...s, [k]: value }));

  function validate(): Record<string, string> {
    const e: Record<string, string> = {};
    if (!editing) {
      if (!v.email.trim()) e.email = 'Required';
      else if (!EMAIL.test(v.email.trim())) e.email = 'Enter a valid email address';
      else if (v.email.trim().length > 254) e.email = 'At most 254 characters';
    }
    if (!v.firstName.trim()) e.firstName = 'Required';
    else if (v.firstName.trim().length > 100) e.firstName = 'At most 100 characters';
    if (!v.lastName.trim()) e.lastName = 'Required';
    else if (v.lastName.trim().length > 100) e.lastName = 'At most 100 characters';
    if (v.phone.trim().length > 30) e.phone = 'At most 30 characters';
    if (!org) e.orgId = `Choose the ${role === 'CUSTOMER' ? 'customer' : 'dealer'}`;
    return e;
  }

  async function submit() {
    setFormError(null);
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    try {
      if (user) {
        const body: Record<string, unknown> = { firstName: v.firstName.trim(), lastName: v.lastName.trim(), phone: v.phone.trim() || undefined, version: user.version };
        if (orgChoice) body.orgId = orgChoice.id; // sent only when the admin picked another organisation
        onSaved({ user: await update.mutateAsync({ id: user.id, body }) });
      } else {
        const r: UserCreated = await create.mutateAsync({
          email: v.email.trim(), firstName: v.firstName.trim(), lastName: v.lastName.trim(), phone: v.phone.trim() || undefined, role, orgId: org!.id,
        });
        onSaved(r);
      }
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length > 0) setErrors(Object.fromEntries(err.fieldErrors.map((f) => [f.field, f.message])));
      setFormError(errorMessage(err));
    }
  }

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{editing ? 'Edit user' : 'New user'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Email (login name)" size="small" required value={v.email} onChange={(e) => set('email', e.target.value)} disabled={editing}
            error={!!errors.email} helperText={errors.email ?? (editing ? 'The email cannot be changed' : 'The user receives an email to set a password')} />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="First name" size="small" required fullWidth value={v.firstName} onChange={(e) => set('firstName', e.target.value)} error={!!errors.firstName} helperText={errors.firstName} />
            <TextField label="Last name" size="small" required fullWidth value={v.lastName} onChange={(e) => set('lastName', e.target.value)} error={!!errors.lastName} helperText={errors.lastName} />
          </Stack>
          <TextField label="Phone" size="small" value={v.phone} onChange={(e) => set('phone', e.target.value)} error={!!errors.phone} helperText={errors.phone} />
          <FormControl size="small" disabled={editing || !!preset}>
            <InputLabel id="role-label">Role</InputLabel>
            <Select labelId="role-label" label="Role" value={role} onChange={(e) => { setRole(e.target.value as UserRole); setOrgChoice(null); }}>
              <MenuItem value="CUSTOMER">Customer user</MenuItem>
              <MenuItem value="DEALER">Dealer user</MenuItem>
            </Select>
          </FormControl>
          <OrgPicker path={PATH[role]} label={role === 'CUSTOMER' ? 'Customer' : 'Dealer'} value={org} onChange={setOrgChoice} error={errors.orgId} disabled={!!preset} />
          {editing && <Alert severity="info">The role cannot be changed. You can move the user to another {role === 'CUSTOMER' ? 'customer' : 'dealer'}.</Alert>}
          {formError && <Alert severity="error">{formError}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant="contained" onClick={() => void submit()} disabled={busy}>{busy ? 'Saving...' : 'Save'}</Button>
      </DialogActions>
    </Dialog>
  );
}
