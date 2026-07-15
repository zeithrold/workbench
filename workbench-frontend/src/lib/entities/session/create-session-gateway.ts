import type { SessionGateway } from './session-gateway.js'
import { PUBLIC_SESSION_GATEWAY } from '$env/static/public'
import { ApiSessionGateway } from './api-session-gateway.js'
import { DemoSessionGateway } from './session-gateway.js'

export function createSessionGateway(): SessionGateway {
  return PUBLIC_SESSION_GATEWAY === 'demo'
    ? new DemoSessionGateway()
    : new ApiSessionGateway()
}
