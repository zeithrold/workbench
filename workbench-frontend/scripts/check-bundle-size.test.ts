import { describe, expect, it } from 'vitest'
import { DEFAULT_MAX_CHUNK_BYTES, oversizedChunks } from './check-bundle-size.mjs'

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
})
