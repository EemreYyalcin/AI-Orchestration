import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Yerel React derleme/geliştirme ayarıdır. Sabit localhost:5173, Google callback adresiyle eşleşir.
 * Proxy yolları tarayıcıya aynı origin sunar; API, OAuth ve çıkış istekleri localhost:8080 backend’e gider.
 * Container dağıtımında bu geliştirme proxy’sinin görevini nginx.conf üstlenir.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    host: 'localhost',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080' },
      '/oauth2': { target: 'http://localhost:8080' },
      '/login/oauth2': { target: 'http://localhost:8080' },
      '/logout': { target: 'http://localhost:8080' },
    },
  },
});
