import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/Edit';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import UploadIcon from '@mui/icons-material/Upload';
import { Alert, Box, Button, Chip, IconButton, Pagination, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, Tooltip, Typography } from '@mui/material';
import { useRef, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useDeleteImage, usePriceHistory, useSetPrimaryImage, useSetProductActive, useUploadImage } from '../api/adminCatalog';
import { useProduct } from '../api/catalog';
import { errorMessage } from '../api/client';
import { ActionDialog } from '../components/ActionDialog';
import { ErrorAlert } from '../components/ErrorAlert';
import { ProductFormDialog } from '../components/ProductFormDialog';
import { formatDateTime, formatMoney } from '../utils/format';

const MAX_BYTES = 5 * 1024 * 1024;

export function AdminProductDetailPage() {
  const { id } = useParams();
  const product = useProduct(id ?? null);
  const [editing, setEditing] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [historyPage, setHistoryPage] = useState(0);
  const [imageError, setImageError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const setActive = useSetProductActive();
  const upload = useUploadImage(id ?? '');
  const makePrimary = useSetPrimaryImage(id ?? '');
  const removeImage = useDeleteImage(id ?? '');
  const history = usePriceHistory(id, historyPage);

  if (product.isLoading) return <Skeleton variant="rounded" height={320} />;
  if (product.error || !product.data) return <ErrorAlert error={product.error ?? new Error('Product not found')} onRetry={() => void product.refetch()} />;
  const p = product.data;

  function onFile(file: File | undefined) {
    setImageError(null);
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return setImageError('Choose a JPEG, PNG or WebP image');
    if (file.size > MAX_BYTES) return setImageError('The image is larger than 5 MB');
    upload.mutate(file, { onError: (e) => setImageError(errorMessage(e)) });
  }

  return (
    <>
      <Button component={RouterLink} to="/admin/products" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>Products</Button>
      <Stack direction="row" alignItems="center" justifyContent="space-between" flexWrap="wrap" sx={{ mb: 2 }} spacing={1}>
        <Box>
          <Typography variant="h5">{p.name}</Typography>
          <Typography variant="body2" color="text.secondary">{p.sku}</Typography>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip label={p.active ? 'Active' : 'Inactive'} color={p.active ? 'success' : 'default'} />
          <Button startIcon={<EditIcon />} variant="outlined" onClick={() => setEditing(true)}>Edit</Button>
          <Button color={p.active ? 'warning' : 'success'} variant="outlined" onClick={() => setToggling(true)}>{p.active ? 'Deactivate' : 'Activate'}</Button>
        </Stack>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr' } }}>
          <Field label="Category" value={p.categoryName} />
          <Field label="Unit price" value={`${formatMoney(p.unitPrice, p.currency)} / ${p.unitOfMeasure}`} />
          <Field label="Minimum order quantity" value={String(p.minOrderQty)} />
          <Field label="Availability" value={p.availability === 'AVAILABLE' ? 'Available' : 'Out of stock'} />
          <Field label="Created" value={formatDateTime(p.createdAt)} />
          <Field label="Last updated" value={formatDateTime(p.updatedAt)} />
        </Box>
        {p.description && <Typography sx={{ mt: 2, whiteSpace: 'pre-wrap' }}>{p.description}</Typography>}
        {p.attributes && Object.keys(p.attributes).length > 0 && (
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 2 }}>
            {Object.entries(p.attributes).map(([k, v]) => <Chip key={k} size="small" variant="outlined" label={`${k}: ${typeof v === 'object' ? JSON.stringify(v) : String(v)}`} />)}
          </Stack>
        )}
      </Paper>

      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Typography variant="h6">Images</Typography>
        <>
          <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" hidden
            onChange={(e) => { onFile(e.target.files?.[0]); e.target.value = ''; }} />
          <Button startIcon={<UploadIcon />} variant="outlined" disabled={upload.isPending} onClick={() => fileInput.current?.click()}>{upload.isPending ? 'Uploading...' : 'Upload image'}</Button>
        </>
      </Stack>
      {imageError && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setImageError(null)}>{imageError}</Alert>}
      {p.images.length === 0 && <Typography color="text.secondary" sx={{ mb: 3 }}>No images yet. JPEG, PNG or WebP, up to 5 MB.</Typography>}
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' }, mb: 3 }}>
        {p.images.map((img) => (
          <Paper key={img.id} variant="outlined" sx={{ p: 1, borderColor: img.primary ? 'primary.main' : undefined }}>
            <Box component="img" src={img.url} alt={p.name} sx={{ width: '100%', aspectRatio: '1 / 1', objectFit: 'cover', borderRadius: 1, display: 'block' }} />
            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 0.5 }}>
              {img.primary ? <Chip size="small" color="primary" label="Primary" /> : <span />}
              <Stack direction="row">
                {!img.primary && (
                  <Tooltip title="Make primary">
                    <span><IconButton size="small" aria-label="Make primary" disabled={makePrimary.isPending}
                      onClick={() => { setImageError(null); makePrimary.mutate(img.id, { onError: (e) => setImageError(errorMessage(e)) }); }}>
                      <StarBorderIcon fontSize="small" />
                    </IconButton></span>
                  </Tooltip>
                )}
                <Tooltip title="Delete image"><IconButton size="small" aria-label="Delete image" onClick={() => setDeleteId(img.id)}><DeleteOutlineIcon fontSize="small" /></IconButton></Tooltip>
              </Stack>
            </Stack>
          </Paper>
        ))}
      </Box>

      <Typography variant="h6" sx={{ mb: 1 }}>Price history</Typography>
      {history.error && <ErrorAlert error={history.error} onRetry={() => void history.refetch()} />}
      {history.data && history.data.content.length === 0 && <Typography color="text.secondary">No price changes recorded.</Typography>}
      {history.data && history.data.content.length > 0 && (
        <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow><TableCell>When</TableCell><TableCell align="right">Old price</TableCell><TableCell align="right">New price</TableCell><TableCell>Changed by</TableCell></TableRow>
            </TableHead>
            <TableBody>
              {history.data.content.map((h, i) => (
                <TableRow key={`${h.changedAt}-${i}`}>
                  <TableCell>{formatDateTime(h.changedAt)}</TableCell>
                  <TableCell align="right">{h.oldPrice === null ? '-' : formatMoney(h.oldPrice, p.currency)}</TableCell>
                  <TableCell align="right">{formatMoney(h.newPrice, p.currency)}</TableCell>
                  <TableCell>{h.changedBy ?? '-'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}
      {history.data && history.data.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
          <Pagination count={history.data.totalPages} page={historyPage + 1} onChange={(_, v) => setHistoryPage(v - 1)} color="primary" />
        </Box>
      )}

      <ProductFormDialog open={editing} product={p} onClose={() => setEditing(false)} onSaved={() => setEditing(false)} />

      <ActionDialog open={toggling} title={p.active ? 'Deactivate product?' : 'Activate product?'}
        text={p.active ? 'Customers will no longer see this product in the catalog. Existing orders are not affected.' : 'Customers will see this product in the catalog again.'}
        confirmLabel={p.active ? 'Deactivate' : 'Activate'} busy={setActive.isPending} error={setActive.error ? errorMessage(setActive.error) : null}
        onConfirm={() => setActive.mutate({ id: p.id, active: !p.active }, { onSuccess: () => setToggling(false) })}
        onClose={() => { setToggling(false); setActive.reset(); }} />

      <ActionDialog open={deleteId !== null} title="Delete image?" text="The image is removed from this product and cannot be restored."
        confirmLabel="Delete" busy={removeImage.isPending} error={removeImage.error ? errorMessage(removeImage.error) : null}
        onConfirm={() => deleteId && removeImage.mutate(deleteId, { onSuccess: () => setDeleteId(null) })}
        onClose={() => { setDeleteId(null); removeImage.reset(); }} />
    </>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography>{value}</Typography>
    </Box>
  );
}
