import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import type { EditLine } from '../api/dealerOrders';
import type { Order } from '../api/orderTypes';
import { formatMoney } from '../utils/format';

interface Props {
  open: boolean;
  order: Order;
  busy: boolean;
  error: string | null;
  onConfirm: (lines: EditLine[], remarks: string) => void;
  onClose: () => void;
}

/** The dealer may only lower quantities (0 removes a line). Prices and products cannot be changed. */
export function EditQuantitiesDialog({ open, order, busy, error, onConfirm, onClose }: Props) {
  const [qty, setQty] = useState<Record<string, string>>({});
  const [remarks, setRemarks] = useState('');

  useEffect(() => {
    if (open) {
      setQty(Object.fromEntries(order.lines.map((l) => [l.id, String(l.submittedQty)])));
      setRemarks('');
    }
  }, [open, order]);

  const check = useMemo(() => {
    const lineErrors: Record<string, string> = {};
    const changed: EditLine[] = [];
    let kept = 0;
    let subtotalCents = 0;
    for (const l of order.lines) {
      const raw = qty[l.id] ?? String(l.submittedQty);
      const n = Number(raw);
      if (raw.trim() === '' || !Number.isInteger(n) || n < 0) lineErrors[l.id] = 'Whole number, 0 or more';
      else if (n > l.submittedQty) lineErrors[l.id] = `At most ${l.submittedQty}`;
      else {
        if (n !== l.submittedQty) changed.push({ lineId: l.id, approvedQty: n });
        if (n > 0) kept += 1;
        subtotalCents += Math.round(l.unitPrice * 100) * n;
      }
    }
    let problem: string | null = null;
    if (Object.keys(lineErrors).length === 0) {
      if (changed.length === 0) problem = 'Change at least one quantity, or use Approve instead.';
      else if (kept === 0) problem = 'All lines are removed. Use Reject instead.';
    }
    return { lineErrors, changed, problem, subtotal: subtotalCents / 100 };
  }, [qty, order.lines]);

  const canSend = Object.keys(check.lineErrors).length === 0 && check.problem === null && remarks.trim() !== '';

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Edit quantities and approve</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>You can lower quantities, or set 0 to remove a line. The customer sees your remarks and pays the new total.</DialogContentText>
        <Table size="small" sx={{ mb: 2 }}>
          <TableHead>
            <TableRow>
              <TableCell>Product</TableCell>
              <TableCell align="right">Ordered</TableCell>
              <TableCell align="right" sx={{ width: 130 }}>Approve</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {order.lines.map((l) => (
              <TableRow key={l.id}>
                <TableCell>
                  <Typography variant="body2">{l.productName}</Typography>
                  <Typography variant="caption" color="text.secondary">{formatMoney(l.unitPrice, order.currency)} each</Typography>
                </TableCell>
                <TableCell align="right">{l.submittedQty}</TableCell>
                <TableCell align="right">
                  <TextField type="number" size="small" value={qty[l.id] ?? ''} onChange={(e) => setQty((q) => ({ ...q, [l.id]: e.target.value }))}
                    error={!!check.lineErrors[l.id]} helperText={check.lineErrors[l.id]} slotProps={{ htmlInput: { min: 0, max: l.submittedQty, 'aria-label': `Approved quantity of ${l.productName}` } }}
                    sx={{ width: 110 }} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Subtotal after your changes (before tax): <strong>{formatMoney(check.subtotal, order.currency)}</strong>
        </Typography>
        <TextField label="Remarks for the customer" required fullWidth multiline minRows={2} value={remarks} onChange={(e) => setRemarks(e.target.value)}
          slotProps={{ htmlInput: { maxLength: 1000 } }} />
        {check.problem && <Alert severity="warning" sx={{ mt: 2 }}>{check.problem}</Alert>}
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>Back</Button>
        <Button variant="contained" color="success" disabled={busy || !canSend} onClick={() => onConfirm(check.changed, remarks.trim())}>Approve with changes</Button>
      </DialogActions>
    </Dialog>
  );
}
