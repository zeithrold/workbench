import { describe, expect, it } from 'vitest'
import { problemFromResponse } from './problem.js'

describe('problemFromResponse', () => {
  it('preserves problem details and status', async () => {
    const error = await problemFromResponse(new Response(JSON.stringify({
      title: 'Setup Token Invalid',
      detail: 'The setup token is invalid.',
      code: 'instance.setup_token_invalid',
    }), { status: 403, headers: { 'Content-Type': 'application/problem+json' } }))

    expect(error.status).toBe(403)
    expect(error.message).toBe('The setup token is invalid.')
    expect(error.problem.code).toBe('instance.setup_token_invalid')
  })

  it('falls back for a non-JSON failure', async () => {
    const error = await problemFromResponse(new Response('unavailable', { status: 503 }))
    expect(error.message).toBe('Request failed (503)')
  })
})
