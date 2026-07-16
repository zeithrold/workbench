import { describe, expect, it } from 'vitest'
import { DEFAULT_MAX_CHUNK_BYTES, monacoBundleFiles, oversizedChunks, summarizeChunks } from './check-bundle-size.mjs'

describe('bundle size budget', () => {
  it('allows chunks at or below the 500 KiB limit', () => {
    expect(oversizedChunks([
      { file: 'below.js', bytes: DEFAULT_MAX_CHUNK_BYTES - 1 },
      { file: 'equal.js', bytes: DEFAULT_MAX_CHUNK_BYTES },
    ])).toEqual([])
  })

  it('reports chunks above the 500 KiB limit', () => {
    expect(oversizedChunks([
      { file: 'below.js', bytes: DEFAULT_MAX_CHUNK_BYTES },
      { file: 'above.js', bytes: DEFAULT_MAX_CHUNK_BYTES + 1 },
    ])).toEqual([{ file: 'above.js', bytes: DEFAULT_MAX_CHUNK_BYTES + 1 }])
  })

  it('ignores lazy Monaco chunks and workers without weakening the default budget', () => {
    const chunks = [
      { file: 'chunks/editor.js', bytes: DEFAULT_MAX_CHUNK_BYTES + 1 },
      { file: 'workers/json.worker-hash.js', bytes: DEFAULT_MAX_CHUNK_BYTES + 1 },
      { file: 'chunks/application.js', bytes: DEFAULT_MAX_CHUNK_BYTES + 1 },
    ]
    const ignoredFiles = monacoBundleFiles(chunks, {
      editor: {
        file: '_app/immutable/chunks/editor.js',
        name: 'editor.api2',
      },
    })

    expect(oversizedChunks(chunks, DEFAULT_MAX_CHUNK_BYTES, ignoredFiles)).toEqual([
      { file: 'chunks/application.js', bytes: DEFAULT_MAX_CHUNK_BYTES + 1 },
    ])
  })

  it('summarizes total size and the largest chunks', () => {
    expect(summarizeChunks([
      { file: 'large.js', bytes: 300 },
      { file: 'small.js', bytes: 100 },
    ], 1)).toEqual({
      largest: { file: 'large.js', bytes: 300 },
      totalBytes: 400,
      top: [{ file: 'large.js', bytes: 300 }],
    })
  })
})
