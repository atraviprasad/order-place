import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField } from '@mui/material';
import { useEffect, useState } from 'react';

interface Props {
  open: boolean;
  title: string;
  text: string;
  confirmLabel: string;
  confirmColor?: 'error' | 'primary' | 'success'; // default: error (destructive actions)
  reasonLabel?: string; // when set, a free-text field is shown and passed to onConfirm
  reasonRequired?: boolean; // the confirm button stays disabled until the field has text
  busy: boolean;
  error: string | null;
  onConfirm: (reason: string) => void;
  onClose: () => void;
}

export function ActionDialog({ open, title, text, confirmLabel, confirmColor = 'error', reasonLabel, reasonRequired = false, busy, error, onConfirm, onClose }: Props) {
  const [reason, setReason] = useState('');
  useEffect(() => {
    if (open) setReason('');
  }, [open]);

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: reasonLabel ? 2 : 0 }}>{text}</DialogContentText>
        {reasonLabel && (
          <TextField label={reasonLabel} value={reason} onChange={(e) => setReason(e.target.value)} fullWidth multiline minRows={2}
            required={reasonRequired} slotProps={{ htmlInput: { maxLength: 1000 } }} />
        )}
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>Back</Button>
        <Button onClick={() => onConfirm(reason.trim())} color={confirmColor} variant="contained"
          disabled={busy || (reasonRequired && reason.trim() === '')}>{confirmLabel}</Button>
      </DialogActions>
    </Dialog>
  );
}
