import type { PermissionPolicyDocument } from './permission-document.js'
import type { PermissionFieldOption, PermissionResourceOption } from './permission-editor-model.js'
import { describe, expect, it } from 'vitest'
import { emptyAgilePermissionRule, parseAgilePermissionDocument, validateAgilePermissionDocument } from './agile-permission-document.js'

const actions = [{ code: 'issue.view', name: 'View work items' }]
const resources: PermissionResourceOption[] = [{ id: 'issue:*', label: 'All work items' }]
const fields: PermissionFieldOption[] = [{ id: 'issue.assignee', label: 'Assignee', field: 'issue.assignee', type: 'user', operators: ['eq'], defaultValue: { var: 'user.currentUser' } }]

function document(overrides: Partial<PermissionPolicyDocument> = {}): PermissionPolicyDocument {
  return {
    schemaVersion: 1,
    code: 'agile-reader',
    name: 'Agile reader',
    rules: [emptyAgilePermissionRule(actions, resources)],
    ...overrides,
  }
}

describe('agile permission policy document', () => {
  it('creates rules from the Agile adapter options', () => {
    expect(emptyAgilePermissionRule(actions, resources)).toEqual({ action: 'issue.view', resourcePattern: 'issue:*', effect: 'ALLOW', condition: null })
  })

  it('keeps tenant actions and resources outside the Agile boundary', () => {
    const errors = validateAgilePermissionDocument(document({ rules: [{ action: 'tenant.read', resourcePattern: 'tenant:*', effect: 'ALLOW', condition: null }] }), actions, resources, fields)
    expect(errors).toContain('Rule 1 uses an unavailable Agile action.')
    expect(errors).toContain('Rule 1 uses an unavailable Agile resource.')
  })

  it('validates nested conditions against Agile field metadata', () => {
    const errors = validateAgilePermissionDocument(document({ rules: [{ action: 'issue.view', resourcePattern: 'issue:*', effect: 'ALLOW', condition: { op: 'and', args: [{ field: 'issue.reporter', op: 'eq', value: { var: 'user.currentUser' } }] } }] }), actions, resources, fields)
    expect(errors).toContain('Rule 1 contains 1 invalid condition.')
  })

  it('parses advanced JSON through the Agile adapter boundary', () => {
    const result = parseAgilePermissionDocument(JSON.stringify(document()), actions, resources, fields)
    expect(result.errors).toEqual([])
    expect(result.document?.code).toBe('agile-reader')
  })
})
