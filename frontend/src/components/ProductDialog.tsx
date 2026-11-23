import AddShoppingCartIcon from '@mui/icons-material/AddShoppingCart';
import { Box, Button, Chip, CircularProgress, Dialog, DialogContent, DialogTitle, Stack, Table, TableBody, TableCell, TableRow, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { useProduct } from '../api/catalog';
import type { ProductDetail } from '../api/types';
import { formatMoney } from '../utils/format';
import { ErrorAlert } from './ErrorAlert';

interface Props {
  productId: string | null;
  onClose: () => void;
  /** Only passed for customers: shows a quantity field and an "Add to cart" button. */
  onAdd?: (p: ProductDetail, qty: number) => void;
}

export function ProductDialog({ productId, onClose, onAdd }: Props) {
  const { data: p, isLoading, error, refetch } = useProduct(productId);
  const [imageId, setImageId] = useState<string | null>(null);
  const [qty, setQty] = useState(1);

  useEffect(() => setImageId(null), [productId]);
  useEffect(() => setQty(p?.minOrderQty ?? 1), [p?.id, p?.minOrderQty]);

  const images = p?.images ?? [];
  const shown = images.find((i) => i.id === imageId) ?? images.find((i) => i.primary) ?? images[0];

  return (
    <Dialog open={productId !== null} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{p?.name ?? 'Product'}</DialogTitle>
      <DialogContent dividers>
        {isLoading && <Box sx={{ display: 'grid', placeItems: 'center', py: 6 }}><CircularProgress /></Box>}
        {error && <ErrorAlert error={error} onRetry={() => void refetch()} />}
        {p && (
          <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
            <Box>
              {shown ? (
                <Box component="img" src={shown.url} alt={p.name} sx={{ width: '100%', borderRadius: 1, maxHeight: 320, objectFit: 'contain', bgcolor: 'action.hover' }} />
              ) : (
                <Box sx={{ height: 220, display: 'grid', placeItems: 'center', bgcolor: 'action.hover', borderRadius: 1, color: 'text.disabled' }}>No image</Box>
              )}
              {images.length > 1 && (
                <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                  {images.map((i) => (
                    <Box
                      key={i.id}
                      component="img"
                      src={i.url}
                      alt=""
                      onClick={() => setImageId(i.id)}
                      sx={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 1, cursor: 'pointer', border: 2, borderColor: i.id === shown?.id ? 'primary.main' : 'transparent' }}
                    />
                  ))}
                </Stack>
              )}
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">{p.sku} · {p.categoryName}</Typography>
              <Typography variant="h5" sx={{ my: 1 }}>
                {formatMoney(p.unitPrice, p.currency)}
                <Typography component="span" color="text.secondary"> / {p.unitOfMeasure}</Typography>
              </Typography>
              <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
                <Chip size="small" color={p.availability === 'AVAILABLE' ? 'success' : 'default'} label={p.availability === 'AVAILABLE' ? 'Available' : 'Out of stock'} />
                <Chip size="small" variant="outlined" label={`Minimum order ${p.minOrderQty}`} />
              </Stack>
              {onAdd && (
                <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
                  <TextField
                    type="number" size="small" label="Quantity" value={qty} sx={{ width: 120 }}
                    onChange={(e) => setQty(Math.max(1, Math.floor(Number(e.target.value)) || 1))}
                    error={qty < p.minOrderQty} helperText={qty < p.minOrderQty ? `Min ${p.minOrderQty}` : undefined}
                    slotProps={{ htmlInput: { min: 1 } }}
                  />
                  <Button variant="contained" startIcon={<AddShoppingCartIcon />} disabled={p.availability !== 'AVAILABLE' || !p.active || qty < p.minOrderQty}
                    onClick={() => onAdd(p, qty)}>
                    Add to cart
                  </Button>
                </Stack>
              )}
              {p.description && <Typography variant="body2" sx={{ mb: 2, whiteSpace: 'pre-line' }}>{p.description}</Typography>}
              {p.attributes && Object.keys(p.attributes).length > 0 && (
                <Table size="small">
                  <TableBody>
                    {Object.entries(p.attributes).map(([k, v]) => (
                      <TableRow key={k}>
                        <TableCell sx={{ fontWeight: 500, textTransform: 'capitalize' }}>{k.replace(/_/g, ' ')}</TableCell>
                        <TableCell>{typeof v === 'object' ? JSON.stringify(v) : String(v)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Box>
          </Box>
        )}
      </DialogContent>
    </Dialog>
  );
}
