import type { ManagementNavigation } from './model.js'
import { apiFetch } from '$lib/api/http.js'
import { problemFromResponse } from '$lib/api/problem.js'

export interface ManagementNavigationGateway {
  current: () => Promise<ManagementNavigation>
}

export class ApiManagementNavigationGateway implements ManagementNavigationGateway {
  async current(): Promise<ManagementNavigation> {
    const response = await apiFetch('/api/session/navigation')
    if (!response.ok)
      throw await problemFromResponse(response)
    return await response.json() as ManagementNavigation
  }
}
