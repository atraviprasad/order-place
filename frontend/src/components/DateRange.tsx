import { TextField } from '@mui/material';

interface Props {
  from: string;
  to: string;
  onFrom: (v: string) => void;
  onTo: (v: string) => void;
}

/** Two date boxes (YYYY-MM-DD). Both ends are included. */
export function DateRange({ from, to, onFrom, onTo }: Props) {
  return (
    <>
      <TextField size="small" type="date" label="From" value={from} onChange={(e) => onFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ minWidth: 150 }} />
      <TextField size="small" type="date" label="To" value={to} onChange={(e) => onTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ minWidth: 150 }} />
    </>
  );
}
