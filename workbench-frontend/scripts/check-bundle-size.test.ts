import { describe, expect, it } from 'vitest'
import { DEFAULT_MAX_CHUNK_BYTES, oversizedChunks, summarizeChunks } from './check-bundle-size.mjs'

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
