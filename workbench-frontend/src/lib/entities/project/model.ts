export interface Project {
  id: string
  identifier: string
  name: string
  description?: string | null
  status: string
  lead?: { id: string, displayName: string, primaryEmail?: string | null } | null
}

export interface CreateProjectInput {
  identifier: string
  name: string
  description?: string
}

export interface ProjectCapabilities {
  tenant: { id: string, name: string, slug: string }
  actions: string[]
}
