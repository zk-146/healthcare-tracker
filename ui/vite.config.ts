import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    // The CSP forbids 'unsafe-inline'. These two settings stop Vite emitting
    // the inline module-preload polyfill and inline critical CSS.
    cssCodeSplit: false,
    modulePreload: { polyfill: false },
    assetsInlineLimit: 0,
  },
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    coverage: {
      // lcov is what the Sonar scanner reads; text keeps the summary visible in
      // the terminal. Only reached via `npm run test:coverage`, so plain
      // `npm test` and the CI frontend job are unaffected.
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/setupTests.ts', 'src/main.tsx'],
    },
  },
});
