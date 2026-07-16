import type { PermissionPolicyDocument, PermissionPolicyRuleDocument } from './permission-document.js'
import { parsePermissionDocument } from './permission-document.js'

export interface TenantPermissionActionOption {
  code: string
  name: string
  description?: string | null
  resourcePattern: string
}

export interface TenantPermissionDocumentResult {
  document?: PermissionPolicyDocument
  errors: string[]
}

export function emptyTenantPermissionRule(action: TenantPermissionActionOption): PermissionPolicyRuleDocument {
  return {
    action: action.code,
    resourcePattern: action.resourcePattern,
    effect: 'ALLOW',
    condition: null,
  }
}

export function validateTenantPermissionDocument(
  document: PermissionPolicyDocument,
  actions: TenantPermissionActionOption[],
): string[] {
  const errors: string[] = []
  if (!document.code.trim())
    errors.push('Policy code is required.')
  if (!document.name.trim())
    errors.push('Policy name is required.')
  if (document.rules.length === 0)
    errors.push('Add at least one tenant permission rule.')
  const byCode = new Map(actions.map(action => [action.code, action]))
  document.rules.forEach((rule, index) => {
    const action = byCode.get(rule.action)
    if (!action) {
      errors.push(`Rule ${index + 1} uses an unavailable tenant capability.`)
      return
    }
    if (rule.resourcePattern !== action.resourcePattern)
      errors.push(`Rule ${index + 1} has an invalid resource scope.`)
    if (rule.condition != null)
      errors.push(`Rule ${index + 1} cannot contain a condition.`)
  })
  return errors
}

export function parseTenantPermissionDocument(
  source: string,
  actions: TenantPermissionActionOption[],
): TenantPermissionDocumentResult {
  const parsed = parsePermissionDocument(source)
  if (!parsed.document)
    return parsed
  const errors = validateTenantPermissionDocument(parsed.document, actions)
  return errors.length === 0 ? { document: parsed.document, errors: [] } : { errors }
}

export function tenantPermissionPolicyJsonSchema(actions: TenantPermissionActionOption[]) {
  return {
    $id: 'https://workbench.local/schemas/tenant-permission-policy-document-v1.json',
    type: 'object',
    additionalProperties: false,
    required: ['schemaVersion', 'code', 'name', 'rules'],
    properties: {
      schemaVersion: { const: 1 },
      code: { type: 'string', minLength: 1 },
      name: { type: 'string', minLength: 1 },
      description: { type: ['string', 'null'] },
      rules: {
        type: 'array',
        minItems: 1,
        items: {
          type: 'object',
          additionalProperties: false,
          required: ['action', 'resourcePattern', 'effect'],
          properties: {
            id: { type: 'string', readOnly: true },
            action: { enum: actions.map(action => action.code) },
            resourcePattern: { enum: [...new Set(actions.map(action => action.resourcePattern))] },
            effect: { enum: ['ALLOW', 'DENY'] },
            condition: { type: 'null' },
          },
        },
      },
    },
  }
}
