import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  palette: {
    primary: { main: '#1a56db' },
    background: { default: '#f5f7fa' },
  },
  shape: { borderRadius: 8 },
  typography: { fontFamily: 'Roboto, system-ui, -apple-system, "Segoe UI", sans-serif' },
  components: {
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { textTransform: 'none' } } },
  },
});
