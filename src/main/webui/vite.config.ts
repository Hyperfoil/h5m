import react from '@vitejs/plugin-react-swc';
import { resolve } from 'node:path';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  build: {
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks: (id: string) => {
          if (id.includes('node_modules')) {
            if (id.includes('react-core') || id.includes('react-router') || id.includes('react-dom')) {
              return 'react-core';
            }
          }
        },
      },
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        loadPaths: ['node_modules'],
      },
    },
  },
  plugins: [react()],
  resolve: {
    alias: [
      {
        find: /^~@ibm\/plex/,
        replacement: resolve(__dirname, 'node_modules/@ibm/plex'),
      },
    ],
    tsconfigPaths: true,
  },
  test: {
    clearMocks: true,
    coverage: {
      enabled: true,
      include: ['src/app/**'],
      provider: 'istanbul',
      reporter: ['text-summary', 'html-spa', 'json'],
      skipFull: true,
    },
    css: false,
    environment: 'jsdom',
    globals: true,
    include: ['test/**/*.test.tsx'],
  },
});
