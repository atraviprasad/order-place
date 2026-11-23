import { Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import type { Order } from '../api/orderTypes';
import { formatMoney } from '../utils/format';

/** Lines and totals of an order. The "Approved" column appears once the dealer has decided quantities. */
export function OrderLinesTable({ order: o }: { order: Order }) {
  const showApproved = o.lines.some((l) => l.approvedQty !== null);
  const span = showApproved ? 4 : 3;
  return (
    <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Product</TableCell>
            <TableCell align="right">Unit price</TableCell>
            <TableCell align="right">Ordered</TableCell>
            {showApproved && <TableCell align="right">Approved</TableCell>}
            <TableCell align="right">Total</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {o.lines.slice().sort((a, b) => a.lineNo - b.lineNo).map((l) => (
            <TableRow key={l.id}>
              <TableCell>
                <Typography variant="body2" sx={{ fontWeight: 500 }}>{l.productName}</Typography>
                <Typography variant="caption" color="text.secondary">{l.sku}</Typography>
              </TableCell>
              <TableCell align="right">{formatMoney(l.unitPrice, o.currency)}</TableCell>
              <TableCell align="right">{l.submittedQty}</TableCell>
              {showApproved && (
                <TableCell align="right" sx={{ color: l.approvedQty !== null && l.approvedQty !== l.submittedQty ? 'warning.main' : undefined }}>
                  {l.approvedQty ?? '-'}
                </TableCell>
              )}
              <TableCell align="right">{formatMoney(l.lineTotal, o.currency)}</TableCell>
            </TableRow>
          ))}
          <TableRow>
            <TableCell colSpan={span} align="right">Subtotal</TableCell>
            <TableCell align="right">{formatMoney(o.subtotal, o.currency)}</TableCell>
          </TableRow>
          <TableRow>
            <TableCell colSpan={span} align="right">Tax</TableCell>
            <TableCell align="right">{formatMoney(o.taxAmount, o.currency)}</TableCell>
          </TableRow>
          <TableRow>
            <TableCell colSpan={span} align="right" sx={{ fontWeight: 700 }}>Total</TableCell>
            <TableCell align="right" sx={{ fontWeight: 700 }}>{formatMoney(o.totalAmount, o.currency)}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </Paper>
  );
}
