import { describe, expect, it } from 'vitest'
import { apiVersion, apiVersionHeader } from './client'

describe('aPI client constants', () => {
  it('exposes the Workbench API version header', () => {
    expect(apiVersionHeader).toBe('X-Workbench-API-Version')
    expect(apiVersion).toBe('2026-07-13')
  })
})
