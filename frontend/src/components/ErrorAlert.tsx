import { Alert, Button } from '@mui/material';
import { errorMessage } from '../api/client';

export function ErrorAlert({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <Alert severity="error" action={onRetry ? <Button color="inherit" size="small" onClick={onRetry}>Retry</Button> : undefined}>
      {errorMessage(error)}
    </Alert>
  );
}
