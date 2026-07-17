import type { PermissionPolicyDocument, PermissionPolicyRuleDocument } from './permission-document.js'
import type { PermissionFieldOption, PermissionResourceOption } from './permission-editor-model.js'
import type { PermissionPolicyDocumentResult } from './permission-policy-editor-core.js'
import { parsePermissionDocument } from './permission-document.js'
import { permissionConditionErrorCount } from './permission-editor-model.js'

export interface AgilePermissionActionOption {
  code: string
  name?: string
  description?: string | null
}

export function emptyAgilePermissionRule(
  actions: AgilePermissionActionOption[],
  resources: PermissionResourceOption[],
): PermissionPolicyRuleDocument {
  return {
    action: actions[0]?.code ?? 'issue.view',
    resourcePattern: resources[0]?.id ?? 'issue:*',
    effect: 'ALLOW',
    condition: null,
  }
}

export function validateAgilePermissionDocument(
  document: PermissionPolicyDocument,
  actions: AgilePermissionActionOption[],
  resources: PermissionResourceOption[],
  fields: PermissionFieldOption[],
): string[] {
  const errors: string[] = []
  if (!document.code.trim())
    errors.push('Policy code is required.')
  if (!document.name.trim())
    errors.push('Policy name is required.')
  const actionCodes = new Set(actions.map(action => action.code))
  const resourcePatterns = new Set(resources.map(resource => resource.id))
  document.rules.forEach((rule, index) => {
    if (!actionCodes.has(rule.action))
      errors.push(`Rule ${index + 1} uses an unavailable Agile action.`)
    if (!resourcePatterns.has(rule.resourcePattern))
      errors.push(`Rule ${index + 1} uses an unavailable Agile resource.`)
    const conditionErrors = rule.condition ? permissionConditionErrorCount(rule.condition, fields) : 0
    if (conditionErrors > 0)
      errors.push(`Rule ${index + 1} contains ${conditionErrors} invalid condition${conditionErrors === 1 ? '' : 's'}.`)
  })
  return errors
}

export function parseAgilePermissionDocument(
  source: string,
  actions: AgilePermissionActionOption[],
  resources: PermissionResourceOption[],
  fields: PermissionFieldOption[],
): PermissionPolicyDocumentResult {
  const parsed = parsePermissionDocument(source)
  if (!parsed.document)
    return parsed
  const errors = validateAgilePermissionDocument(parsed.document, actions, resources, fields)
  return errors.length === 0 ? { document: parsed.document, errors: [] } : { errors }
}

export function agilePermissionPolicyJsonSchema(
  actions: AgilePermissionActionOption[],
  resources: PermissionResourceOption[],
): Record<string, unknown> {
  return {
    $id: 'https://workbench.local/schemas/agile-permission-policy-document-v1.json',
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
        items: {
          type: 'object',
          additionalProperties: false,
          required: ['action', 'resourcePattern', 'effect'],
          properties: {
            id: { type: 'string', readOnly: true },
            action: { enum: actions.map(action => action.code) },
            resourcePattern: { enum: resources.map(resource => resource.id) },
            effect: { enum: ['ALLOW', 'DENY'] },
            condition: { type: ['object', 'null'] },
          },
        },
      },
    },
  }
}
