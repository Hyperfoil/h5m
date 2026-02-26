import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: 'openapi.yaml',
  logs: {
    level: process.env.NODE !== 'production' ? 'info' : 'warn',
  },
  output: {
    case: 'camelCase',
    indexFile: true,
    path: 'src/client',
    postProcess: ['prettier'],
  },
  plugins: [
    '@hey-api/typescript',
    {
      name: '@hey-api/client-axios',
      throwOnError: true,
    },
    {
      name: '@hey-api/sdk',
      operations: {
        containerName: '{{name}}Service',
        strategy: 'byTags',
      },
      transformer: true,
      validator: false,
    },
    {
      bigInt: false,
      dates: true,
      name: '@hey-api/transformers',
    },
    '@tanstack/react-query',
    '@hey-api/schemas',
    'zod',
  ],
});
