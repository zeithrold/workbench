import { describe, expect, it } from 'vitest'
import { apiVersionHeader } from './client'

describe('aPI client constants', () => {
  it('exposes the Workbench API version header', () => {
    expect(apiVersionHeader).toBe('X-Workbench-API-Version')
  })
})
