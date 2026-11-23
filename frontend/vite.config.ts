import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// In development the browser talks only to http://localhost:5173. Vite forwards /api to the API gateway,
// so no CORS setup is needed on any service.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true, // Keycloak only allows redirects to 5173 and 3000
    proxy: {
      '/api': { target: process.env.VITE_GATEWAY_URL ?? 'http://localhost:8080', changeOrigin: true },
    },
  },
});
