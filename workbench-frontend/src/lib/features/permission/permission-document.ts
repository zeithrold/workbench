export type PermissionEffect = 'ALLOW' | 'DENY'

export type PermissionConditionField
  = | string
    | {
      kind: 'property'
      apiId?: string
      code?: string
    }

export type PermissionConditionValue
  = | null
    | boolean
    | number
    | string
    | PermissionConditionValue[]
    | {
      var: 'user.currentUser' | 'issue.reporter' | 'issue.assignee'
    }

export function clonePermissionConditionValue(value: PermissionConditionValue): PermissionConditionValue {
  if (Array.isArray(value))
    return value.map(clonePermissionConditionValue)
  if (typeof value === 'object' && value !== null)
    return { var: value.var }
  return value
}

export type PermissionCondition
  = | { uiId?: string, op: 'and' | 'or', args: PermissionCondition[] }
    | { uiId?: string, op: 'not', arg: PermissionCondition }
    | {
      uiId?: string
      field: PermissionConditionField
      op: PermissionConditionOperator
      value?: PermissionConditionValue
    }

export type PermissionConditionOperator
  = | 'eq'
    | 'neq'
    | 'in'
    | 'not_in'
    | 'is_empty'
    | 'is_not_empty'
    | 'gt'
    | 'gte'
    | 'lt'
    | 'lte'
    | 'contains'
    | 'not_contains'
    | 'has_any'
    | 'has_all'
    | 'has_none'

export interface PermissionPolicyRuleDocument {
  id?: string
  action: string
  resourcePattern: string
  effect: PermissionEffect
  condition?: PermissionCondition | null
}

export interface PermissionPolicyDocument {
  schemaVersion: 1
  code: string
  name: string
  description?: string | null
  rules: PermissionPolicyRuleDocument[]
}

export interface PermissionDocumentResult {
  document?: PermissionPolicyDocument
  errors: string[]
}

export const permissionSystemFields = [
  'issue.reporter',
  'issue.assignee',
  'issue.status',
  'issue.statusGroup',
  'issue.issueType',
  'issue.issueTypeConfig',
  'issue.project',
  'children.notDone',
] as const

export const permissionConditionOperators: PermissionConditionOperator[] = [
  'eq',
  'neq',
  'in',
  'not_in',
  'is_empty',
  'is_not_empty',
  'gt',
  'gte',
  'lt',
  'lte',
  'contains',
  'not_contains',
  'has_any',
  'has_all',
  'has_none',
]

const noValueOperators = new Set<PermissionConditionOperator>([
  'is_empty',
  'is_not_empty',
])
const actionPattern = /^[a-z]+(?:\.[a-z]+)+$/
const resourcePattern = /^(?:\*|[a-z][a-z0-9-]*(?::(?:\*|[\w.-]+))?)$/

export function emptyPermissionPolicyDocument(): PermissionPolicyDocument {
  return { schemaVersion: 1, code: '', name: '', description: null, rules: [] }
}

export function emptyPermissionRule(): PermissionPolicyRuleDocument {
  return {
    action: 'issue.view',
    resourcePattern: 'issue:*',
    effect: 'ALLOW',
    condition: null,
  }
}

export function emptyPermissionPredicate(): PermissionCondition {
  return {
    field: 'issue.reporter',
    op: 'eq',
    value: { var: 'user.currentUser' },
  }
}

export function serializePermissionDocument(
  document: PermissionPolicyDocument,
): string {
  return `${JSON.stringify(stripPermissionUiIds(document), null, 2)}\n`
}

let nextPermissionNodeId = 0

function createPermissionNodeId(): string {
  nextPermissionNodeId += 1
  return `permission-node-${nextPermissionNodeId}`
}

export function withPermissionUiIds(condition: PermissionCondition): PermissionCondition {
  if ('field' in condition)
    return { ...condition, uiId: condition.uiId ?? createPermissionNodeId() }
  if (condition.op === 'not')
    return { ...condition, uiId: condition.uiId ?? createPermissionNodeId(), arg: withPermissionUiIds(condition.arg) }
  return { ...condition, uiId: condition.uiId ?? createPermissionNodeId(), args: condition.args.map(withPermissionUiIds) }
}

export function clonePermissionCondition(condition: PermissionCondition): PermissionCondition {
  const clone = JSON.parse(JSON.stringify(condition)) as PermissionCondition
  function refresh(node: PermissionCondition): PermissionCondition {
    if ('field' in node)
      return { ...node, uiId: createPermissionNodeId() }
    if (node.op === 'not')
      return { ...node, uiId: createPermissionNodeId(), arg: refresh(node.arg) }
    return { ...node, uiId: createPermissionNodeId(), args: node.args.map(refresh) }
  }
  return refresh(clone)
}

export function stripPermissionUiIds(document: PermissionPolicyDocument): PermissionPolicyDocument {
  function stripCondition(condition: PermissionCondition): PermissionCondition {
    if ('field' in condition) {
      const { uiId: _, ...predicate } = condition
      return predicate
    }
    if (condition.op === 'not') {
      const { uiId: _, ...logical } = condition
      return { ...logical, arg: stripCondition(condition.arg) }
    }
    const { uiId: _, ...logical } = condition
    return { ...logical, args: condition.args.map(stripCondition) }
  }
  return {
    ...document,
    rules: document.rules.map(rule => ({
      ...rule,
      condition: rule.condition ? stripCondition(rule.condition) : rule.condition,
    })),
  }
}

export function parsePermissionDocument(
  source: string,
): PermissionDocumentResult {
  let value: unknown
  try {
    value = JSON.parse(source)
  }
  catch {
    return { errors: ['Document must be valid JSON.'] }
  }
  const errors: string[] = []
  if (!isRecord(value))
    return { errors: ['Document must be a JSON object.'] }
  rejectUnknown(
    value,
    ['schemaVersion', 'code', 'name', 'description', 'rules'],
    '$',
    errors,
  )
  if (value.schemaVersion !== 1)
    errors.push('$.schemaVersion must be 1.')
  requireString(value.code, '$.code', errors)
  requireString(value.name, '$.name', errors)
  if (
    value.description !== undefined
    && value.description !== null
    && typeof value.description !== 'string'
  ) {
    errors.push('$.description must be a string or null.')
  }
  if (!Array.isArray(value.rules)) {
    errors.push('$.rules must be an array.')
  }
  else {
    value.rules.forEach((rule, index) =>
      validateRule(rule, `$.rules[${index}]`, errors),
    )
  }
  return errors.length > 0
    ? { errors }
    : { document: value as unknown as PermissionPolicyDocument, errors }
}

function validateRule(value: unknown, path: string, errors: string[]) {
  if (!isRecord(value)) {
    errors.push(`${path} must be an object.`)
    return
  }
  rejectUnknown(
    value,
    ['id', 'action', 'resourcePattern', 'effect', 'condition'],
    path,
    errors,
  )
  if (value.id !== undefined && typeof value.id !== 'string')
    errors.push(`${path}.id must be a string.`)
  if (
    !requireString(value.action, `${path}.action`, errors)
    || !actionPattern.test(value.action as string)
  ) {
    errors.push(`${path}.action must be a dot-separated lower-case action.`)
  }
  if (
    !requireString(value.resourcePattern, `${path}.resourcePattern`, errors)
    || !resourcePattern.test(value.resourcePattern as string)
  ) {
    errors.push(`${path}.resourcePattern is invalid.`)
  }
  if (value.effect !== 'ALLOW' && value.effect !== 'DENY')
    errors.push(`${path}.effect must be ALLOW or DENY.`)
  if (value.condition !== undefined && value.condition !== null)
    validateCondition(value.condition, `${path}.condition`, errors)
}

function validateCondition(value: unknown, path: string, errors: string[]) {
  if (!isRecord(value)) {
    errors.push(`${path} must be an object.`)
    return
  }
  if (value.op === 'and' || value.op === 'or') {
    rejectUnknown(value, ['op', 'args'], path, errors)
    if (!Array.isArray(value.args) || value.args.length === 0) {
      errors.push(`${path}.args must contain at least one condition.`)
    }
    else {
      value.args.forEach((item, index) =>
        validateCondition(item, `${path}.args[${index}]`, errors),
      )
    }
    return
  }
  if (value.op === 'not') {
    rejectUnknown(value, ['op', 'arg'], path, errors)
    validateCondition(value.arg, `${path}.arg`, errors)
    return
  }
  rejectUnknown(value, ['field', 'op', 'value'], path, errors)
  validateField(value.field, `${path}.field`, errors)
  if (
    !permissionConditionOperators.includes(
      value.op as PermissionConditionOperator,
    )
  ) {
    errors.push(`${path}.op is not supported by permission evaluation.`)
    return
  }
  if (
    !noValueOperators.has(value.op as PermissionConditionOperator)
    && value.value === undefined
  ) {
    errors.push(`${path}.value is required for ${String(value.op)}.`)
  }
  if (value.value !== undefined)
    validateValue(value.value, `${path}.value`, errors)
}

function validateField(value: unknown, path: string, errors: string[]) {
  if (typeof value === 'string') {
    if (!(permissionSystemFields as readonly string[]).includes(value))
      errors.push(`${path} is not a supported system field.`)
    return
  }
  if (!isRecord(value) || value.kind !== 'property') {
    errors.push(`${path} must be a system field or property reference.`)
    return
  }
  rejectUnknown(value, ['kind', 'apiId', 'code'], path, errors)
  if (
    (typeof value.apiId !== 'string' || value.apiId.length === 0)
    && (typeof value.code !== 'string' || value.code.length === 0)
  ) {
    errors.push(`${path} requires apiId or code.`)
  }
}

function validateValue(value: unknown, path: string, errors: string[]) {
  if (value === null || ['string', 'number', 'boolean'].includes(typeof value))
    return
  if (Array.isArray(value)) {
    value.forEach((item, index) =>
      validateValue(item, `${path}[${index}]`, errors),
    )
    return
  }
  if (
    !isRecord(value)
    || !['user.currentUser', 'issue.reporter', 'issue.assignee'].includes(
      String(value.var),
    )
    || Object.keys(value).length !== 1
  ) {
    errors.push(`${path} must be a literal, array, or supported variable.`)
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function requireString(
  value: unknown,
  path: string,
  errors: string[],
): value is string {
  if (typeof value === 'string' && value.trim().length > 0)
    return true
  errors.push(`${path} must be a non-empty string.`)
  return false
}

function rejectUnknown(
  value: Record<string, unknown>,
  allowed: string[],
  path: string,
  errors: string[],
) {
  Object.keys(value)
    .filter(key => !allowed.includes(key))
    .forEach(key => errors.push(`${path}.${key} is not allowed.`))
}

export const permissionPolicyJsonSchema = {
  $id: 'https://workbench.local/schemas/permission-policy-document-v1.json',
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
          action: { type: 'string', pattern: '^[a-z]+(?:\\.[a-z]+)+$' },
          resourcePattern: { type: 'string' },
          effect: { enum: ['ALLOW', 'DENY'] },
          condition: { type: ['object', 'null'] },
        },
      },
    },
  },
} as const
