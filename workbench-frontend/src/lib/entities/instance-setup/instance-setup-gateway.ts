import type { Session } from '$lib/entities/session/model.js'
import type { InstanceBootstrapResponse, InstanceSetupInput, InstanceSetupStatus } from './model.js'
import { apiFetch } from '$lib/api/http.js'
import { problemFromResponse } from '$lib/api/problem.js'
import { sessionFromLogin } from '$lib/entities/session/api-model.js'

export interface InstanceSetupGateway {
  status: () => Promise<InstanceSetupStatus>
  setup: (input: InstanceSetupInput) => Promise<Session>
}

export class ApiInstanceSetupGateway implements InstanceSetupGateway {
  async status(): Promise<InstanceSetupStatus> {
    const response = await apiFetch('/api/instance/setup-status')
    if (!response.ok)
      throw await problemFromResponse(response)
    return await response.json() as InstanceSetupStatus
  }

  async setup(input: InstanceSetupInput): Promise<Session> {
    const response = await apiFetch('/api/instance/setup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
    if (!response.ok)
      throw await problemFromResponse(response)
    const body = await response.json() as InstanceBootstrapResponse
    return sessionFromLogin(body.session)
  }
}
