import { describe, expect, it, vi } from 'vitest'
import { createSessionGateway } from './create-session-gateway.js'
import { DemoSessionGateway } from './session-gateway.js'

vi.mock('$env/static/public', () => ({
  PUBLIC_SESSION_GATEWAY: 'demo',
}))

describe('createSessionGateway', () => {
  it('returns the demo gateway by default', () => {
    expect(createSessionGateway()).toBeInstanceOf(DemoSessionGateway)
  })
})
