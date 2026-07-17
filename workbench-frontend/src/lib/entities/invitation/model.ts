export interface InvitationPreview {
  type: 'TENANT_ADMIN' | 'TENANT_MEMBER'
  tenant: { id: string, name: string, slug: string }
  email: string
  displayName?: string | null
}

export interface InvitationAcceptance {
  type: 'TENANT_ADMIN' | 'TENANT_MEMBER'
  tenant: { id: string, name: string, slug: string }
  user: { id: string, displayName: string, primaryEmail?: string | null }
}
