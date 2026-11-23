import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, InputLabel, MenuItem, Paper, Select, Skeleton, Stack, TextField, Typography } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { flattenCategories, indentLabel, subtreeIds, type FlatCategory } from '../admin/categoryUtils';
import { useSaveCategory, useSetCategoryActive } from '../api/adminCatalog';
import { useCategories } from '../api/catalog';
import { errorMessage } from '../api/client';
import { ActionDialog } from '../components/ActionDialog';
import { ErrorAlert } from '../components/ErrorAlert';

type Editing = { mode: 'create'; parentId: string | null } | { mode: 'edit'; category: FlatCategory } | null;

export function AdminCategoriesPage() {
  const tree = useCategories();
  const flat = useMemo(() => flattenCategories(tree.data ?? []), [tree.data]);
  const [editing, setEditing] = useState<Editing>(null);
  const [toggling, setToggling] = useState<FlatCategory | null>(null);
  const setActive = useSetCategoryActive();

  return (
    <>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Typography variant="h5">Categories</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing({ mode: 'create', parentId: null })}>New top-level category</Button>
      </Stack>

      {tree.error && <ErrorAlert error={tree.error} onRetry={() => void tree.refetch()} />}
      {tree.isLoading && <Skeleton variant="rounded" height={200} />}
      {tree.data && flat.length === 0 && <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>No categories yet.</Typography>}
      {flat.length > 0 && (
        <Paper variant="outlined">
          {flat.map((c, i) => (
            <Stack key={c.id} direction="row" alignItems="center" spacing={1} sx={{ py: 1, pr: 1, pl: 2 + c.depth * 3, borderTop: i === 0 ? 'none' : '1px solid', borderColor: 'divider', opacity: c.active ? 1 : 0.6 }}>
              <Typography sx={{ flexGrow: 1 }}>{c.name}</Typography>
              {!c.active && <Chip size="small" label="Inactive" />}
              <Button size="small" startIcon={<AddIcon />} onClick={() => setEditing({ mode: 'create', parentId: c.id })} disabled={!c.active}>Sub-category</Button>
              <Button size="small" startIcon={<EditIcon />} onClick={() => setEditing({ mode: 'edit', category: c })}>Edit</Button>
              <Button size="small" color={c.active ? 'warning' : 'success'} onClick={() => setToggling(c)}>{c.active ? 'Deactivate' : 'Activate'}</Button>
            </Stack>
          ))}
        </Paper>
      )}

      <CategoryDialog editing={editing} flat={flat} onClose={() => setEditing(null)} />

      <ActionDialog open={toggling !== null} title={toggling?.active ? 'Deactivate category?' : 'Activate category?'}
        text={toggling?.active ? `"${toggling.name}" will be hidden from customers. A category that still has active products or sub-categories cannot be deactivated.` : `"${toggling?.name ?? ''}" will be visible to customers again.`}
        confirmLabel={toggling?.active ? 'Deactivate' : 'Activate'} busy={setActive.isPending} error={setActive.error ? errorMessage(setActive.error) : null}
        onConfirm={() => toggling && setActive.mutate({ id: toggling.id, active: !toggling.active }, { onSuccess: () => setToggling(null) })}
        onClose={() => { setToggling(null); setActive.reset(); }} />
    </>
  );
}

function CategoryDialog({ editing, flat, onClose }: { editing: Editing; flat: FlatCategory[]; onClose: () => void }) {
  const save = useSaveCategory();
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');
  const [sortOrder, setSortOrder] = useState('0');
  const [error, setError] = useState<string | null>(null);
  const open = editing !== null;
  const current = editing?.mode === 'edit' ? editing.category : null;

  useEffect(() => {
    if (!editing) return;
    setName(editing.mode === 'edit' ? editing.category.name : '');
    setParentId(editing.mode === 'edit' ? editing.category.parentId ?? '' : editing.parentId ?? '');
    setSortOrder(editing.mode === 'edit' ? String(editing.category.sortOrder) : '0');
    setError(null);
    save.reset();
  }, [editing]); // eslint-disable-line react-hooks/exhaustive-deps

  // a category cannot be moved under itself or one of its own descendants
  const blocked = current ? subtreeIds(flat, current.id) : new Set<string>();
  const parents = flat.filter((c) => !blocked.has(c.id) && (c.active || c.id === current?.parentId));

  function submit() {
    const trimmed = name.trim();
    const order = Number(sortOrder);
    if (!trimmed) return setError('Name is required');
    if (trimmed.length > 100) return setError('Name is at most 100 characters');
    if (!Number.isInteger(order) || order < 0) return setError('Sort order is a whole number, 0 or more');
    setError(null);
    save.mutate({ id: current?.id, body: { name: trimmed, parentId: parentId || null, sortOrder: order } }, { onSuccess: onClose });
  }

  return (
    <Dialog open={open} onClose={save.isPending ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{current ? 'Edit category' : 'New category'}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'grid', gap: 2, mt: 1 }}>
          <TextField label="Name" size="small" required autoFocus value={name} onChange={(e) => setName(e.target.value)} />
          <FormControl size="small">
            <InputLabel id="parent-label">Parent</InputLabel>
            <Select labelId="parent-label" label="Parent" value={parentId} onChange={(e) => setParentId(e.target.value)}>
              <MenuItem value="">None (top level)</MenuItem>
              {parents.map((c) => <MenuItem key={c.id} value={c.id}>{indentLabel(c)}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField label="Sort order" size="small" type="number" value={sortOrder} onChange={(e) => setSortOrder(e.target.value)} helperText="Smaller numbers are shown first" slotProps={{ htmlInput: { min: 0 } }} />
        </Box>
        {(error || save.error) && <Alert severity="error" sx={{ mt: 2 }}>{error ?? errorMessage(save.error)}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={save.isPending}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={save.isPending}>{save.isPending ? 'Saving...' : 'Save'}</Button>
      </DialogActions>
    </Dialog>
  );
}
