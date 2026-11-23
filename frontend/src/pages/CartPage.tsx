import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import {
  Alert, Box, Button, FormControl, FormHelperText, IconButton, InputLabel, MenuItem, Paper, Select, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { useRef, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useDealers, useSaveDraft, useSubmitOrder } from '../api/orders';
import type { Order, SaveOrderBody } from '../api/orderTypes';
import { MAX_QTY, useCart } from '../cart/CartContext';
import { ErrorAlert } from '../components/ErrorAlert';
import { formatMoney } from '../utils/format';

export function CartPage() {
  const cart = useCart();
  const navigate = useNavigate();
  const dealers = useDealers();
  const save = useSaveDraft();
  const submit = useSubmitOrder();
  const [error, setError] = useState<string | null>(null);
  const [showMissing, setShowMissing] = useState(false);
  // one key per checkout attempt: pressing "Place order" twice, or retrying after a network error, never creates two orders
  const submitKey = useRef(crypto.randomUUID());

  const busy = save.isPending || submit.isPending;
  const currency = cart.items[0]?.currency ?? 'INR';
  const estimate = cart.items.reduce((sum, i) => sum + Math.round(i.unitPrice * 100) * i.quantity, 0) / 100;
  const belowMin = cart.items.filter((i) => i.quantity < i.minOrderQty);

  if (cart.items.length === 0) {
    return (
      <Paper variant="outlined" sx={{ p: 4 }}>
        <Typography variant="h5" gutterBottom>Your cart is empty</Typography>
        <Button component={RouterLink} to="/catalog" variant="contained">Browse the catalog</Button>
      </Paper>
    );
  }

  async function persistDraft(): Promise<Order> {
    const body: SaveOrderBody = {
      dealerId: cart.dealerId,
      deliveryAddress: cart.address.trim() || undefined,
      customerNotes: cart.notes.trim() || undefined,
      lines: cart.items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
    };
    const order = await save.mutateAsync({ id: cart.draft?.id, body: cart.draft ? { ...body, version: cart.draft.version } : body });
    cart.setDraft({ id: order.id, version: order.version });
    return order;
  }

  async function saveDraft() {
    setError(null);
    try {
      const order = await persistDraft();
      cart.clear(); // the draft now lives in "My orders"; open it from there to continue
      navigate(`/orders/${order.id}`);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function placeOrder() {
    setError(null);
    if (!cart.dealerId) {
      setShowMissing(true);
      return;
    }
    if (belowMin.length > 0) {
      setError(`Some items are below the minimum order quantity: ${belowMin.map((i) => `${i.name} (min ${i.minOrderQty})`).join(', ')}`);
      return;
    }
    try {
      const draft = await persistDraft();
      const submitted = await submit.mutateAsync({ id: draft.id, version: draft.version, key: submitKey.current });
      cart.clear();
      navigate(`/orders/${submitted.id}`);
    } catch (e) {
      // the draft (if it was saved) stays in the cart state, so a retry updates it instead of creating another one
      setError(errorMessage(e));
    }
  }

  return (
    <>
      <Typography variant="h5" gutterBottom>Your cart</Typography>
      {cart.draft && <Alert severity="info" sx={{ mb: 2 }}>You are editing a saved draft. Saving or placing the order updates that draft.</Alert>}
      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, alignItems: 'start' }}>
        <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Product</TableCell>
                <TableCell align="right">Unit price</TableCell>
                <TableCell align="center" sx={{ width: 120 }}>Quantity</TableCell>
                <TableCell align="right">Total</TableCell>
                <TableCell padding="checkbox" />
              </TableRow>
            </TableHead>
            <TableBody>
              {cart.items.map((i) => (
                <TableRow key={i.productId}>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>{i.name}</Typography>
                    <Typography variant="caption" color="text.secondary">{i.sku}</Typography>
                  </TableCell>
                  <TableCell align="right">{formatMoney(i.unitPrice, i.currency)}</TableCell>
                  <TableCell align="center">
                    <TextField
                      type="number"
                      size="small"
                      value={i.quantity}
                      onChange={(e) => cart.setQty(i.productId, Number(e.target.value))}
                      error={i.quantity < i.minOrderQty}
                      helperText={i.quantity < i.minOrderQty ? `Min ${i.minOrderQty}` : undefined}
                      slotProps={{ htmlInput: { min: 1, max: MAX_QTY, 'aria-label': `Quantity of ${i.name}` } }}
                      sx={{ width: 100 }}
                    />
                  </TableCell>
                  <TableCell align="right">{formatMoney((Math.round(i.unitPrice * 100) * i.quantity) / 100, i.currency)}</TableCell>
                  <TableCell padding="checkbox">
                    <IconButton aria-label={`Remove ${i.name}`} onClick={() => cart.remove(i.productId)}><DeleteOutlineIcon /></IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>

        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <FormControl fullWidth size="small" required error={showMissing && !cart.dealerId}>
              <InputLabel id="dealer-label">Dealer</InputLabel>
              <Select labelId="dealer-label" label="Dealer" value={cart.dealerId ?? ''} onChange={(e) => cart.setDealer(e.target.value || null)}>
                {(dealers.data ?? []).map((d) => <MenuItem key={d.id} value={d.id}>{d.name}</MenuItem>)}
              </Select>
              <FormHelperText>{showMissing && !cart.dealerId ? 'Choose the dealer who will review your order' : 'The dealer approves, rejects or edits your order'}</FormHelperText>
            </FormControl>
            {dealers.error && <ErrorAlert error={dealers.error} onRetry={() => void dealers.refetch()} />}

            <TextField label="Delivery address" size="small" multiline minRows={2} value={cart.address} onChange={(e) => cart.setAddress(e.target.value)}
              helperText="Leave empty to use your organisation's address" slotProps={{ htmlInput: { maxLength: 1000 } }} />
            <TextField label="Notes for the dealer" size="small" multiline minRows={2} value={cart.notes} onChange={(e) => cart.setNotes(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 2000 } }} />

            <Box>
              <Typography variant="body2" color="text.secondary">Estimated total</Typography>
              <Typography variant="h5">{formatMoney(estimate, currency)}</Typography>
              <Typography variant="caption" color="text.secondary">Final prices and tax are confirmed by the system when you save the order.</Typography>
            </Box>

            {error && <Alert severity="error">{error}</Alert>}

            <Stack direction="row" spacing={1}>
              <Button variant="contained" onClick={() => void placeOrder()} disabled={busy} fullWidth>
                {submit.isPending ? 'Placing order...' : 'Place order'}
              </Button>
              <Button variant="outlined" onClick={() => void saveDraft()} disabled={busy} fullWidth>Save draft</Button>
            </Stack>
            <Button color="inherit" size="small" onClick={() => { cart.clear(); setError(null); }} disabled={busy}>Empty cart</Button>
          </Stack>
        </Paper>
      </Box>
    </>
  );
}
