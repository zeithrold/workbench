import type { PermissionFieldOption } from './permission-editor-model.js'
import { describe, expect, it } from 'vitest'
import { normalizePermissionOperator, normalizePermissionPredicate, permissionConditionErrorCount, permissionFieldTypeLabel, permissionPredicateErrors, permissionValueEditorKind, permissionValueForOptionId, permissionValueOptionId, permissionValueOptionIds, permissionValuesEqual, permissionValuesForOptionIds } from './permission-editor-model.js'

const booleanField: PermissionFieldOption = {
  id: 'property.flag',
  label: 'Flag',
  field: { kind: 'property', code: 'flag' },
  type: 'boolean',
  operators: ['eq', 'neq'],
  defaultValue: true,
}
const userField: PermissionFieldOption = {
  id: 'issue.assignee',
  label: 'Assignee',
  field: 'issue.assignee',
  type: 'user',
  operators: ['eq', 'in', 'is_empty'],
  values: [
    { id: 'current', label: 'Current user', value: { var: 'user.currentUser' } },
    { id: 'reporter', label: 'Reporter', value: { var: 'issue.reporter' } },
    { id: 'ada', label: 'Ada Lovelace', value: 'usr_ada' },
  ],
  defaultValue: { var: 'user.currentUser' },
}

describe('permission editor model', () => {
  it('provides human-readable field type labels', () => {
    expect(permissionFieldTypeLabel('boolean')).toBe('Boolean')
    expect(permissionFieldTypeLabel('single-select')).toBe('Single select')
  })

  it('maps field and operator metadata to dedicated value editors', () => {
    expect(permissionValueEditorKind(booleanField, 'eq')).toBe('boolean')
    expect(permissionValueEditorKind(userField, 'in')).toBe('multi')
    expect(permissionValueEditorKind(userField, 'is_empty')).toBe('none')
  })

  it('atomically normalizes an incompatible predicate after changing fields', () => {
    const normalized = normalizePermissionPredicate({ field: 'issue.assignee', op: 'in', value: ['user-a'], uiId: 'stable' }, booleanField)
    expect(normalized).toEqual({ field: { kind: 'property', code: 'flag' }, op: 'eq', value: true, uiId: 'stable' })
  })

  it('resets shape-compatible stale values after changing fields', () => {
    const normalized = normalizePermissionPredicate({ field: 'issue.statusGroup', op: 'eq', value: 'completed' }, userField)
    expect(normalized.value).toEqual({ var: 'user.currentUser' })
  })

  it('retains only values supported by the next operator', () => {
    expect(normalizePermissionOperator({ field: 'issue.assignee', op: 'eq', value: { var: 'issue.reporter' } }, userField, 'eq').value).toEqual({ var: 'issue.reporter' })
    expect(normalizePermissionOperator({ field: 'issue.assignee', op: 'eq', value: 'missing-user' }, userField, 'eq').value).toEqual({ var: 'user.currentUser' })
    expect(normalizePermissionOperator({ field: 'issue.assignee', op: 'in', value: ['usr_ada'] }, userField, 'is_empty')).not.toHaveProperty('value')
  })

  it('maps literal and variable values without JSON serialization', () => {
    expect(permissionValuesEqual({ var: 'user.currentUser' }, { var: 'user.currentUser' })).toBe(true)
    expect(permissionValuesEqual({ var: 'user.currentUser' }, { var: 'issue.reporter' })).toBe(false)
    expect(permissionValueOptionId(userField.values!, { var: 'issue.reporter' })).toBe('reporter')
    expect(permissionValueOptionIds(userField.values!, [{ var: 'user.currentUser' }, 'usr_ada'])).toEqual(['current', 'ada'])
    expect(permissionValueForOptionId(userField.values!, 'current')).toEqual({ var: 'user.currentUser' })
    expect(permissionValuesForOptionIds(userField.values!, ['reporter', 'ada'])).toEqual([{ var: 'issue.reporter' }, 'usr_ada'])
  })

  it('copies proxied variable values without relying on structured cloning', () => {
    const variables = [
      ['current', 'user.currentUser'],
      ['reporter', 'issue.reporter'],
      ['assignee', 'issue.assignee'],
    ] as const
    const options: PermissionFieldOption['values'] = variables.map(([id, variable]) => ({
      id,
      label: id,
      value: new Proxy({ var: variable }, {}),
    }))

    for (const [id, variable] of variables) {
      const selected = permissionValueForOptionId(options!, id)
      expect(selected).toEqual({ var: variable })
      expect(selected).not.toBe(options!.find(option => option.id === id)?.value)
    }
  })

  it('returns node-local validation reasons', () => {
    expect(permissionPredicateErrors({ field: 'issue.assignee', op: 'in', value: 'user-a' }, [userField])).toEqual(['Choose one or more values.'])
    expect(permissionPredicateErrors({ field: 'issue.reporter', op: 'eq', value: 'user-a' }, [userField])).toEqual(['This field is not available.'])
  })

  it('summarizes nested node errors for group headers', () => {
    expect(permissionConditionErrorCount({ op: 'and', args: [
      { field: 'issue.assignee', op: 'in', value: 'user-a' },
      { op: 'or', args: [{ field: 'issue.reporter', op: 'eq', value: 'user-a' }] },
    ] }, [userField])).toBe(2)
  })
})
