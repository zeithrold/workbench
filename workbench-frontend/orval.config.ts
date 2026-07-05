import { defineConfig } from 'orval'

export default defineConfig({
  workbench: {
    input: 'http://localhost:8080/api/openapi',
    output: {
      mode: 'split',
      target: 'src/lib/api/generated/workbench.ts',
      schemas: 'src/lib/api/generated/model',
      client: 'fetch',
      clean: true,
    },
  },
})
