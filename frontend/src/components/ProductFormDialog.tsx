import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormHelperText, IconButton, InputLabel, MenuItem, Select, Stack, TextField, Typography } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { indentLabel, flattenCategories } from '../admin/categoryUtils';
import { useSaveProduct } from '../api/adminCatalog';
import { useCategories } from '../api/catalog';
import { ApiError, errorMessage } from '../api/client';
import type { Availability, ProductDetail } from '../api/types';

const SKU = /^[A-Za-z0-9][A-Za-z0-9._-]{1,39}$/;
const UOM = /^[A-Z][A-Z_]{1,19}$/;
const MAX_ATTRIBUTES = 30;

interface AttrRow {
  key: string;
  value: string;
  original?: unknown; // keeps numbers and booleans as they were when the text is unchanged in meaning
}

interface Props {
  open: boolean;
  product: ProductDetail | null; // null = create
  onClose: () => void;
  onSaved: (p: ProductDetail) => void;
}

/** A value typed in the box keeps the type it had before: 120 stays a number, true stays a boolean. */
function coerce(row: AttrRow): unknown {
  const text = row.value.trim();
  if (typeof row.original === 'number' && text !== '' && !Number.isNaN(Number(text))) return Number(text);
  if (typeof row.original === 'boolean' && (text === 'true' || text === 'false')) return text === 'true';
  return row.value;
}

export function ProductFormDialog({ open, product, onClose, onSaved }: Props) {
  const save = useSaveProduct();
  const categories = useCategories();
  const flat = useMemo(() => flattenCategories(categories.data ?? []), [categories.data]);
  const [v, setV] = useState({ sku: '', name: '', description: '', unitPrice: '', unitOfMeasure: 'EACH', minOrderQty: '1' });
  const [categoryId, setCategoryId] = useState('');
  const [availability, setAvailability] = useState<Availability>('AVAILABLE');
  const [attrs, setAttrs] = useState<AttrRow[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setV({
      sku: product?.sku ?? '', name: product?.name ?? '', description: product?.description ?? '', unitPrice: product ? String(product.unitPrice) : '',
      unitOfMeasure: product?.unitOfMeasure ?? 'EACH', minOrderQty: String(product?.minOrderQty ?? 1),
    });
    setCategoryId(product?.categoryId ?? '');
    setAvailability(product?.availability ?? 'AVAILABLE');
    setAttrs(Object.entries(product?.attributes ?? {}).map(([key, value]) => ({ key, value: typeof value === 'object' ? JSON.stringify(value) : String(value), original: value })));
    setErrors({});
    setFormError(null);
  }, [open, product?.id]); // eslint-disable-line react-hooks/exhaustive-deps -- a background refresh must not wipe what the admin is typing

  const set = (k: keyof typeof v, value: string) => setV((s) => ({ ...s, [k]: value }));
  const setAttr = (i: number, patch: Partial<AttrRow>) => setAttrs((rows) => rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  // offer active categories; the product's current one is kept in the list even if it has since been deactivated
  const options = flat.filter((c) => c.active || c.id === product?.categoryId);

  function validate(): Record<string, string> {
    const e: Record<string, string> = {};
    if (!product && !SKU.test(v.sku.trim())) e.sku = "2-40 characters: letters, digits, '.', '_' or '-'";
    if (!v.name.trim()) e.name = 'Required';
    else if (v.name.trim().length > 200) e.name = 'At most 200 characters';
    if (v.description.length > 5000) e.description = 'At most 5000 characters';
    if (!categoryId) e.categoryId = 'Choose a category';
    const price = v.unitPrice.trim();
    if (!/^\d{1,10}(\.\d{1,2})?$/.test(price)) e.unitPrice = 'A price like 4500 or 4500.50 (at most 2 decimals)';
    if (!UOM.test(v.unitOfMeasure.trim())) e.unitOfMeasure = 'Upper case letters, for example EACH or SET';
    const qty = Number(v.minOrderQty);
    if (!Number.isInteger(qty) || qty < 1) e.minOrderQty = 'A whole number, 1 or more';
    const keys = attrs.map((r) => r.key.trim()).filter((k) => k !== '');
    if (keys.length > MAX_ATTRIBUTES) e.attributes = `At most ${MAX_ATTRIBUTES} attributes`;
    else if (new Set(keys).size !== keys.length) e.attributes = 'Attribute names must be different';
    return e;
  }

  async function submit() {
    setFormError(null);
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    const attributes: Record<string, unknown> = {};
    for (const r of attrs) if (r.key.trim() !== '') attributes[r.key.trim()] = coerce(r);
    const body: Record<string, unknown> = {
      name: v.name.trim(), description: v.description.trim() || undefined, categoryId, unitPrice: Number(v.unitPrice.trim()),
      unitOfMeasure: v.unitOfMeasure.trim(), availability, minOrderQty: Number(v.minOrderQty), attributes,
    };
    if (product) body.version = product.version;
    else {
      body.sku = v.sku.trim();
      body.currency = 'INR';
    }
    try {
      onSaved(await save.mutateAsync({ id: product?.id, body }));
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length > 0) setErrors(Object.fromEntries(err.fieldErrors.map((f) => [f.field, f.message])));
      setFormError(errorMessage(err));
    }
  }

  return (
    <Dialog open={open} onClose={save.isPending ? undefined : onClose} maxWidth="md" fullWidth>
      <DialogTitle>{product ? 'Edit product' : 'New product'}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'grid', gap: 2, mt: 1, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
          <TextField label="SKU" size="small" required value={v.sku} onChange={(e) => set('sku', e.target.value)} disabled={!!product}
            error={!!errors.sku} helperText={errors.sku ?? (product ? 'The SKU cannot be changed' : 'Cannot be changed later')} />
          <TextField label="Name" size="small" required value={v.name} onChange={(e) => set('name', e.target.value)} error={!!errors.name} helperText={errors.name} />
          <FormControl size="small" required error={!!errors.categoryId}>
            <InputLabel id="cat-label">Category</InputLabel>
            <Select labelId="cat-label" label="Category" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
              {options.map((c) => <MenuItem key={c.id} value={c.id}>{indentLabel(c)}</MenuItem>)}
            </Select>
            {errors.categoryId && <FormHelperText>{errors.categoryId}</FormHelperText>}
          </FormControl>
          <TextField label="Unit price (INR)" size="small" required value={v.unitPrice} onChange={(e) => set('unitPrice', e.target.value)}
            error={!!errors.unitPrice} helperText={errors.unitPrice ?? (product ? 'A change is recorded in the price history' : undefined)} slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
          <TextField label="Unit of measure" size="small" required value={v.unitOfMeasure} onChange={(e) => set('unitOfMeasure', e.target.value.toUpperCase())} error={!!errors.unitOfMeasure} helperText={errors.unitOfMeasure ?? 'For example EACH, SET'} />
          <TextField label="Minimum order quantity" size="small" required type="number" value={v.minOrderQty} onChange={(e) => set('minOrderQty', e.target.value)}
            error={!!errors.minOrderQty} helperText={errors.minOrderQty} slotProps={{ htmlInput: { min: 1 } }} />
          <FormControl size="small">
            <InputLabel id="avail-label">Availability</InputLabel>
            <Select labelId="avail-label" label="Availability" value={availability} onChange={(e) => setAvailability(e.target.value as Availability)}>
              <MenuItem value="AVAILABLE">Available</MenuItem>
              <MenuItem value="OUT_OF_STOCK">Out of stock</MenuItem>
            </Select>
          </FormControl>
          <TextField label="Currency" size="small" value="INR" disabled helperText="Only INR is supported" />
          <TextField label="Description" size="small" multiline minRows={3} value={v.description} onChange={(e) => set('description', e.target.value)}
            error={!!errors.description} helperText={errors.description} sx={{ gridColumn: { sm: '1 / -1' } }} />
        </Box>

        <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>Attributes (for example colour, material)</Typography>
        <Stack spacing={1}>
          {attrs.map((r, i) => (
            <Stack key={i} direction="row" spacing={1}>
              <TextField size="small" label="Name" value={r.key} onChange={(e) => setAttr(i, { key: e.target.value })} sx={{ flexBasis: '35%' }} />
              <TextField size="small" label="Value" value={r.value} onChange={(e) => setAttr(i, { value: e.target.value })} sx={{ flexGrow: 1 }} />
              <IconButton aria-label="Remove attribute" onClick={() => setAttrs((rows) => rows.filter((_, idx) => idx !== i))}><DeleteOutlineIcon /></IconButton>
            </Stack>
          ))}
          {errors.attributes && <Alert severity="error">{errors.attributes}</Alert>}
          <Box><Button size="small" startIcon={<AddIcon />} onClick={() => setAttrs((rows) => [...rows, { key: '', value: '' }])} disabled={attrs.length >= MAX_ATTRIBUTES}>Add attribute</Button></Box>
        </Stack>

        {formError && <Alert severity="error" sx={{ mt: 2 }}>{formError}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={save.isPending}>Cancel</Button>
        <Button variant="contained" onClick={() => void submit()} disabled={save.isPending}>{save.isPending ? 'Saving...' : 'Save'}</Button>
      </DialogActions>
    </Dialog>
  );
}
