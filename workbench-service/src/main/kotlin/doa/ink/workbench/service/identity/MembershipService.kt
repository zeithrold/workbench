package doa.ink.workbench.service.identity

import doa.ink.workbench.core.common.summary.TenantSummary
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class MembershipService(
  private val tenantMembers: TenantMemberRepository,
  private val tenants: TenantRepository,
) {
  suspend fun listActiveMemberships(userId: UUID): List<TenantMembershipView> {
    val memberships =
      tenantMembers.listByUser(userId).filter { it.status == TenantMemberStatus.ACTIVE }
    val tenantById = tenants.findByIds(memberships.map { it.tenantId }).associateBy { it.id }
    return memberships.mapNotNull { membership ->
      tenantById[membership.tenantId]?.let { tenant ->
        TenantMembershipView(
          id = membership.apiId.value,
          tenant = TenantSummary.from(tenant),
        )
      }
    }
  }
}
