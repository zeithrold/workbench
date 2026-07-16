import type { SelectorOption } from '$lib/shared/ui'
import type { PermissionCondition, PermissionConditionField, PermissionConditionOperator, PermissionConditionValue } from './permission-document.js'
import { m } from '$lib/paraglide/messages.js'
import { clonePermissionConditionValue } from './permission-document.js'

export type PermissionFieldType = 'text' | 'number' | 'boolean' | 'date' | 'user' | 'reference' | 'single-select' | 'multi-select'
export type PermissionValueEditorKind = 'none' | 'text' | 'number' | 'boolean' | 'date' | 'single' | 'multi'

export interface PermissionValueOption extends SelectorOption {
  value: PermissionConditionValue
}

export interface PermissionFieldOption extends SelectorOption {
  field: PermissionConditionField
  type: PermissionFieldType
  operators: PermissionConditionOperator[]
  values?: PermissionValueOption[]
  defaultValue?: PermissionConditionValue
}

export interface PermissionResourceOption extends SelectorOption {
  badge?: string
  resourceKey?: string
}

export function permissionFieldTypeLabel(type: PermissionFieldType): string {
  return ({
    'text': m.permission_field_type_text(),
    'number': m.permission_field_type_number(),
    'boolean': m.permission_field_type_boolean(),
    'date': m.permission_field_type_date(),
    'user': m.permission_field_type_user(),
    'reference': m.permission_field_type_reference(),
    'single-select': m.permission_field_type_single_select(),
    'multi-select': m.permission_field_type_multi_select(),
  } satisfies Record<PermissionFieldType, string>)[type]
}

const emptyOperators = new Set<PermissionConditionOperator>(['is_empty', 'is_not_empty'])
const multiOperators = new Set<PermissionConditionOperator>(['in', 'not_in', 'has_any', 'has_all', 'has_none'])

export function permissionFieldId(field: PermissionConditionField): string {
  if (typeof field === 'string')
    return field
  return `property.${field.apiId ?? field.code ?? ''}`
}

export function permissionOperatorLabel(operator: PermissionConditionOperator): string {
  return ({
    eq: m.permission_operator_eq(),
    neq: m.permission_operator_neq(),
    in: m.permission_operator_in(),
    not_in: m.permission_operator_not_in(),
    is_empty: m.permission_operator_is_empty(),
    is_not_empty: m.permission_operator_is_not_empty(),
    gt: m.permission_operator_gt(),
    gte: m.permission_operator_gte(),
    lt: m.permission_operator_lt(),
    lte: m.permission_operator_lte(),
    contains: m.permission_operator_contains(),
    not_contains: m.permission_operator_not_contains(),
    has_any: m.permission_operator_has_any(),
    has_all: m.permission_operator_has_all(),
    has_none: m.permission_operator_has_none(),
  })[operator]
}

export function permissionValueEditorKind(field: PermissionFieldOption, operator: PermissionConditionOperator): PermissionValueEditorKind {
  if (emptyOperators.has(operator))
    return 'none'
  if (multiOperators.has(operator) || field.type === 'multi-select')
    return 'multi'
  if (field.type === 'boolean')
    return 'boolean'
  if (field.type === 'date')
    return 'date'
  if (field.type === 'number')
    return 'number'
  if (field.type === 'text')
    return 'text'
  return 'single'
}

export function defaultPermissionValue(field: PermissionFieldOption, operator: PermissionConditionOperator): PermissionConditionValue | undefined {
  const kind = permissionValueEditorKind(field, operator)
  if (kind === 'none')
    return undefined
  if (kind === 'multi')
    return []
  if (field.defaultValue !== undefined)
    return clonePermissionConditionValue(field.defaultValue)
  if (field.values?.[0])
    return clonePermissionConditionValue(field.values[0].value)
  if (kind === 'boolean')
    return true
  if (kind === 'number')
    return 0
  return ''
}

export function permissionValuesEqual(
  left: PermissionConditionValue | undefined,
  right: PermissionConditionValue | undefined,
): boolean {
  if (left === right)
    return true
  if (Array.isArray(left) || Array.isArray(right))
    return Array.isArray(left) && Array.isArray(right) && left.length === right.length && left.every((value, index) => permissionValuesEqual(value, right[index]))
  if (isPermissionVariable(left) || isPermissionVariable(right))
    return isPermissionVariable(left) && isPermissionVariable(right) && left.var === right.var
  return false
}

export function permissionValueOptionId(
  options: PermissionValueOption[],
  value: PermissionConditionValue | undefined,
): string | null {
  return options.find(option => permissionValuesEqual(option.value, value))?.id ?? null
}

export function permissionValueOptionIds(
  options: PermissionValueOption[],
  value: PermissionConditionValue | undefined,
): string[] {
  if (!Array.isArray(value))
    return []
  return value.map(item => permissionValueOptionId(options, item)).filter((id): id is string => id !== null)
}

export function permissionValueForOptionId(
  options: PermissionValueOption[],
  id: string | null,
): PermissionConditionValue | undefined {
  const value = options.find(option => option.id === id)?.value
  return value === undefined ? undefined : clonePermissionConditionValue(value)
}

export function permissionValuesForOptionIds(
  options: PermissionValueOption[],
  ids: string[],
): PermissionConditionValue[] {
  return ids
    .map(id => permissionValueForOptionId(options, id))
    .filter((value): value is PermissionConditionValue => value !== undefined)
}

export function normalizePermissionPredicate(
  current: { field: PermissionConditionField, op: PermissionConditionOperator, value?: PermissionConditionValue, uiId?: string },
  field: PermissionFieldOption,
): typeof current {
  const operator = field.operators.includes(current.op) ? current.op : field.operators[0] ?? 'eq'
  const kind = permissionValueEditorKind(field, operator)
  if (kind === 'none')
    return { uiId: current.uiId, field: field.field, op: operator }
  return { uiId: current.uiId, field: field.field, op: operator, value: defaultPermissionValue(field, operator) }
}

export function normalizePermissionOperator(
  current: { field: PermissionConditionField, op: PermissionConditionOperator, value?: PermissionConditionValue, uiId?: string },
  field: PermissionFieldOption,
  operator: PermissionConditionOperator,
): typeof current {
  const kind = permissionValueEditorKind(field, operator)
  if (kind === 'none')
    return { uiId: current.uiId, field: current.field, op: operator }
  return {
    ...current,
    op: operator,
    value: isCompatiblePermissionValue(current.value, field, kind)
      ? clonePermissionConditionValue(current.value)
      : defaultPermissionValue(field, operator),
  }
}

export function permissionPredicateErrors(
  predicate: { field: PermissionConditionField, op: PermissionConditionOperator, value?: PermissionConditionValue },
  fields: PermissionFieldOption[],
): string[] {
  const field = fields.find(item => permissionFieldId(item.field) === permissionFieldId(predicate.field))
  if (!field)
    return [m.permission_error_field_unavailable()]
  if (!field.operators.includes(predicate.op))
    return [m.permission_error_operator_unsupported()]
  const kind = permissionValueEditorKind(field, predicate.op)
  if (kind !== 'none' && predicate.value === undefined)
    return [m.permission_error_choose_value()]
  if (kind === 'multi' && !Array.isArray(predicate.value))
    return [m.permission_error_choose_values()]
  return []
}

export function permissionConditionErrorCount(condition: PermissionCondition, fields: PermissionFieldOption[]): number {
  if ('field' in condition)
    return permissionPredicateErrors(condition, fields).length
  if (condition.op === 'not')
    return permissionConditionErrorCount(condition.arg, fields)
  return condition.args.reduce((count, child) => count + permissionConditionErrorCount(child, fields), 0)
}

function isPermissionVariable(value: PermissionConditionValue | undefined): value is Extract<PermissionConditionValue, { var: string }> {
  return typeof value === 'object' && value !== null && !Array.isArray(value) && 'var' in value
}

function isCompatiblePermissionValue(
  value: PermissionConditionValue | undefined,
  field: PermissionFieldOption,
  kind: PermissionValueEditorKind,
): value is PermissionConditionValue {
  if (value === undefined)
    return false
  if (kind === 'multi')
    return Array.isArray(value) && (!field.values || value.every(item => permissionValueOptionId(field.values ?? [], item) !== null))
  if (Array.isArray(value))
    return false
  if (kind === 'boolean')
    return typeof value === 'boolean'
  if (kind === 'number')
    return typeof value === 'number'
  if (kind === 'date' || kind === 'text')
    return typeof value === 'string'
  return !field.values || permissionValueOptionId(field.values, value) !== null
}
