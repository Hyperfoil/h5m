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
        // carbon-wc gets a fixed filename (like h5m-roq.css) so layouts/base.html can
        // modulepreload it: otherwise the docs shell waits a full round trip while the
        // browser discovers the chunk only after parsing h5m-roq.js.
        chunkFileNames: (chunkInfo) => (chunkInfo.name === 'carbon-wc' ? 'carbon-wc.js' : 'assets/[name]-[hash].js'),
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
          // Plex subsets keep their own name so layouts/base.html can preload the four the docs render with. Safe to drop the hash:
          // the filename already pins family, weight and subset, so the contents only change with the @ibm/plex version.
          if (assetInfo.names.some((name) => name.endsWith('.woff2'))) {
            return 'assets/fonts/[name][extname]';
          }
          return 'assets/[name]-[hash][extname]';
        },
        manualChunks: (id: string) => {
          if (id.includes('node_modules')) {
            if (id.includes('@carbon/web-components')) {
              // code-snippet is lazily imported by h5m-roq.js (docs site) and must stay
              // out of carbon-wc, or the dynamic import pulls the whole family back in.
              // Its shared base (lit, CDSElement, mixins) still lands in carbon-wc, which
              // ui-shell needs up front anyway.
              if (id.includes('/code-snippet')) {
                return;
              }
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
