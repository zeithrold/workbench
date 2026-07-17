import type { InvitationAcceptance, InvitationPreview } from './model.js'
import { apiFetch } from '$lib/api/http.js'
import { problemFromResponse } from '$lib/api/problem.js'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok)
    throw await problemFromResponse(response)
  return await response.json() as T
}

function post(body: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

export const invitationGateway = {
  preview: (token: string) =>
    request<InvitationPreview>(`/api/invitations/preview?${new URLSearchParams({ token })}`),
  acceptNew: (token: string, displayName: string, password: string) =>
    request<InvitationAcceptance>(
      '/api/invitations/accept',
      post({ token, displayName, password }),
    ),
  acceptExisting: (token: string) =>
    request<InvitationAcceptance>(
      '/api/invitations/accept-existing',
      post({ token }),
    ),
}
