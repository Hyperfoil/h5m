import { devtools } from '@tanstack/devtools-vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';
import devtoolsJson from 'vite-plugin-devtools-json';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  build: {
    cssMinify: 'esbuild',
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      input: {
        app: resolve(__dirname, 'index.html'),
        roq: resolve(__dirname, 'h5m-roq.js'), // lightweight bundle of carbon dependencies for roq
      },
      output: {
        entryFileNames: (chunkInfo) => {
          if (chunkInfo.name === 'roq') {
            return 'h5m-roq.js'; // emits directly to /h5m-roq.js
          }
          return 'assets/[name]-[hash].js';
        },
        assetFileNames: (assetInfo) => {
          // give Roq's compiled CSS a fixed filename at root
          if (assetInfo.names.some((name) => name.includes('roq') && name.endsWith('.css'))) {
            return 'h5m-roq.css';
          }
          return 'assets/[name]-[hash][extname]';
        },
        manualChunks: (id: string) => {
          if (id.includes('node_modules')) {
            if (id.includes('@carbon/web-components')) {
              return 'carbon-wc';
            }
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
  plugins: [devtools({ consolePiping: { enabled: false } }), devtoolsJson(), react()],
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
