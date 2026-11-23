import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from '@mui/material';
import { useEffect, useState } from 'react';
import { ApiError, errorMessage } from '../api/client';
import { useSaveOrg, type Org } from '../api/orgs';
import { CODE_PATTERN, type OrgKind } from '../admin/orgKinds';

interface Props {
  open: boolean;
  kind: OrgKind;
  org: Org | null; // null = create
  onClose: () => void;
  onSaved: (org: Org) => void;
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function OrgFormDialog({ open, kind, org, onClose, onSaved }: Props) {
  const save = useSaveOrg(kind.path);
  const [values, setValues] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    const initial: Record<string, string> = { [kind.codeKey]: '' };
    for (const f of kind.fields) initial[f.key] = org ? String(org[f.key] ?? '') : '';
    if (org) initial[kind.codeKey] = String(org[kind.codeKey] ?? '');
    setValues(initial);
    setErrors({});
    setFormError(null);
  }, [open, org?.id, kind]); // eslint-disable-line react-hooks/exhaustive-deps -- a background refresh must not wipe what the admin is typing

  const set = (key: string, v: string) => setValues((s) => ({ ...s, [key]: v }));

  function validate(): Record<string, string> {
    const e: Record<string, string> = {};
    if (!org && !CODE_PATTERN.test((values[kind.codeKey] ?? '').trim())) e[kind.codeKey] = '2 to 30 characters: letters, digits, - or _';
    for (const f of kind.fields) {
      const v = (values[f.key] ?? '').trim();
      if (f.required && v === '') e[f.key] = 'Required';
      else if (v.length > f.max) e[f.key] = `At most ${f.max} characters`;
      else if (f.email && v !== '' && !EMAIL.test(v)) e[f.key] = 'Enter a valid email address';
    }
    return e;
  }

  async function submit() {
    setFormError(null);
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;

    const body: Record<string, unknown> = {};
    for (const f of kind.fields) {
      const v = (values[f.key] ?? '').trim();
      body[f.key] = v === '' ? undefined : v; // an empty optional field is left out
    }
    if (org) body.version = org.version;
    else body[kind.codeKey] = (values[kind.codeKey] ?? '').trim();

    try {
      onSaved(await save.mutateAsync({ id: org?.id, body }));
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length > 0) {
        setErrors(Object.fromEntries(err.fieldErrors.map((f) => [f.field, f.message])));
      }
      setFormError(errorMessage(err));
    }
  }

  return (
    <Dialog open={open} onClose={save.isPending ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{org ? `Edit ${kind.singular}` : `New ${kind.singular}`}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label={kind.codeLabel} size="small" required value={values[kind.codeKey] ?? ''} onChange={(e) => set(kind.codeKey, e.target.value)}
            disabled={!!org} error={!!errors[kind.codeKey]} helperText={errors[kind.codeKey] ?? (org ? 'The code cannot be changed' : 'Letters, digits, - or _; cannot be changed later')} />
          {kind.fields.map((f) => (
            <TextField key={f.key} label={f.label} size="small" required={f.required} multiline={f.multiline} minRows={f.multiline ? 2 : undefined}
              value={values[f.key] ?? ''} onChange={(e) => set(f.key, e.target.value)} error={!!errors[f.key]} helperText={errors[f.key]}
              slotProps={{ htmlInput: { maxLength: f.max + 50 } }} />
          ))}
          {formError && <Alert severity="error">{formError}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={save.isPending}>Cancel</Button>
        <Button variant="contained" onClick={() => void submit()} disabled={save.isPending}>{save.isPending ? 'Saving...' : 'Save'}</Button>
      </DialogActions>
    </Dialog>
  );
}
