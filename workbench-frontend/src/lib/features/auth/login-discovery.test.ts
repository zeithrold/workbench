import { afterEach, describe, expect, it, vi } from 'vitest'
import { discoverLogin } from './login-discovery.js'

afterEach(() => vi.unstubAllGlobals())

describe('discoverLogin', () => {
  it('encodes the identifier and returns the instance flow', async () => {
    let requestedUrl = ''
    const fetch = vi.fn(async (input: RequestInfo | URL) => {
      requestedUrl = input.toString()
      return new Response(JSON.stringify({
        identifierRecognized: true,
        flow: 'INSTANCE_ONLY',
        instancePasswordMethod: { id: 'lmg_1' },
        tenantMethods: [],
      }), { status: 200 })
    })
    vi.stubGlobal('fetch', fetch)

    const result = await discoverLogin('admin+owner@example.test')
    expect(result.flow).toBe('INSTANCE_ONLY')
    expect(requestedUrl).toContain('identifier=admin%2Bowner%40example.test')
  })
})
