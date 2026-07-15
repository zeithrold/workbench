import type { LoginResponse, UserSummaryResponse } from '$lib/entities/session/api-model.js'

export interface InstanceSetupStatus {
  initialized: boolean
  setupTokenRequired: boolean
}

export interface InstanceSetupInput {
  displayName: string
  email: string
  password: string
  setupToken?: string
}

export interface InstanceBootstrapResponse {
  user: UserSummaryResponse
  session: LoginResponse
}
