import type { CreateProjectInput, Project, ProjectCapabilities } from './model.js'
import { apiFetch } from '$lib/api/http.js'
import { problemFromResponse } from '$lib/api/problem.js'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok)
    throw await problemFromResponse(response)
  return await response.json() as T
}

export const projectGateway = {
  capabilities: () => request<ProjectCapabilities>('/api/projects/capabilities'),
  list: () => request<Project[]>('/api/projects'),
  create: (input: CreateProjectInput) =>
    request<Project>('/api/projects', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }),
}
