import process from 'node:process'
import { defineConfig } from 'orval'

export default defineConfig({
  workbench: {
    input: process.env.OPENAPI_INPUT ?? 'http://localhost:8080/api/openapi',
    output: {
      mode: 'split',
      target: 'src/lib/api/generated/workbench.ts',
      schemas: 'src/lib/api/generated/model',
      client: 'fetch',
      clean: true,
    },
  },
})
