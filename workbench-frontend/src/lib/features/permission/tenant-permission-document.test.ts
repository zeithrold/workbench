import type { PermissionPolicyDocument } from './permission-document.js'
import type { TenantPermissionActionOption } from './tenant-permission-document.js'
import { describe, expect, it } from 'vitest'
import { parseTenantPermissionDocument, validateTenantPermissionDocument } from './tenant-permission-document.js'

const actions: TenantPermissionActionOption[] = [
  {
    code: 'tenant.read',
    name: 'View tenant settings',
    description: 'View tenant metadata.',
    resourcePattern: 'tenant:*',
  },
  {
    code: 'permission.policy.manage',
    name: 'Manage tenant policies',
    description: 'Manage tenant policies.',
    resourcePattern: 'permission:*',
  },
]

function document(overrides: Partial<PermissionPolicyDocument> = {}): PermissionPolicyDocument {
  return {
    schemaVersion: 1,
    code: 'tenant-reader',
    name: 'Tenant reader',
    description: null,
    rules: [
      {
        action: 'tenant.read',
        resourcePattern: 'tenant:*',
        effect: 'ALLOW',
        condition: null,
      },
    ],
    ...overrides,
  }
}

describe('tenant permission policy document', () => {
  it('accepts allowlisted tenant capabilities', () => {
    expect(validateTenantPermissionDocument(document(), actions)).toEqual([])
  })

  it('rejects Agile actions, resource overrides, and conditions', () => {
    const errors = validateTenantPermissionDocument(
      document({
        rules: [
          {
            action: 'issue.view',
            resourcePattern: 'issue:*',
            effect: 'ALLOW',
            condition: { field: 'issue.statusGroup', op: 'eq', value: 'todo' },
          },
        ],
      }),
      actions,
    )

    expect(errors).toContain('Rule 1 uses an unavailable tenant capability.')
  })

  it('validates advanced JSON through the same tenant boundary', () => {
    const source = JSON.stringify(document({
      rules: [
        {
          action: 'tenant.read',
          resourcePattern: 'permission:*',
          effect: 'ALLOW',
          condition: null,
        },
      ],
    }))

    expect(parseTenantPermissionDocument(source, actions).errors).toContain(
      'Rule 1 has an invalid resource scope.',
    )
  })
})
