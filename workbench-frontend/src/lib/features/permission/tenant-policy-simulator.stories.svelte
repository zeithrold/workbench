<script module lang='ts'>
  import type { PermissionPolicyDocument } from './permission-document.js'
  import type { TenantPermissionActionOption } from './tenant-permission-document.js'
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import TenantPolicySimulator from './tenant-policy-simulator.svelte'

  const actions: TenantPermissionActionOption[] = [
    {
      code: 'tenant.read',
      name: 'View tenant settings',
      description: 'View tenant metadata and configuration.',
      resourcePattern: 'tenant:*',
    },
    {
      code: 'permission.policy.manage',
      name: 'Manage permission policies',
      description: 'Create and update tenant permission policies.',
      resourcePattern: 'permission:*',
    },
  ]
  const document: PermissionPolicyDocument = {
    schemaVersion: 1,
    code: 'tenant-operator',
    name: 'Tenant operator',
    description: 'Allows routine tenant administration.',
    rules: [
      {
        id: 'allow-tenant-read',
        action: 'tenant.read',
        resourcePattern: 'tenant:*',
        effect: 'ALLOW',
        condition: null,
      },
    ],
  }
  const { Story } = defineMeta({
    title: 'Features/Permission/Tenant policy simulator',
    component: TenantPolicySimulator,
  })
</script>

<Story name='Configured draft' args={{ document, actions }} />
