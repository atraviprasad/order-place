import { Button, Paper, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <Paper variant="outlined" sx={{ p: 4 }}>
      <Typography variant="h5" gutterBottom>Page not found</Typography>
      <Button component={RouterLink} to="/" variant="contained">Go to the start page</Button>
    </Paper>
  );
}
