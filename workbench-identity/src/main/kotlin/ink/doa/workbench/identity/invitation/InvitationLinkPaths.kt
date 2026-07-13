package ink.doa.workbench.identity.invitation

object InvitationLinkPaths {
  /** invitationLink path segment; token is the invitation secret. Type is distinguished in DB. */
  const val INVITATION = "/invitations/{token}"
}
