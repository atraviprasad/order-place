import { Paper, Typography } from '@mui/material';

export function ComingSoonPage({ title, note }: { title: string; note: string }) {
  return (
    <Paper variant="outlined" sx={{ p: 4 }}>
      <Typography variant="h5" gutterBottom>{title}</Typography>
      <Typography color="text.secondary">{note}</Typography>
    </Paper>
  );
}
