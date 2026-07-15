import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Customer Display System — standalone customer-facing app.
// Builds into its own /cds/ subdirectory so it never clobbers the admin static output.
export default defineConfig({
  plugins: [react()],
  base: '/cds/',
  server: {
    port: 5273,
    proxy: {
      // Live CDS state from the POS server (public, no-auth) during dev.
      '/public': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../../server/src/main/resources/static/cds',
    emptyOutDir: true,
  },
})
