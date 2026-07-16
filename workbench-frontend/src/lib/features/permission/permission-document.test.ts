import { describe, expect, it } from 'vitest'
import {
  clonePermissionCondition,
  emptyPermissionPolicyDocument,
  parsePermissionDocument,
  serializePermissionDocument,
  withPermissionUiIds,
} from './permission-document.js'

describe('permission policy document', () => {
  it('round trips a recursive condition', () => {
    const document = {
      ...emptyPermissionPolicyDocument(),
      code: 'project-editor',
      name: 'Project editor',
      rules: [
        {
          action: 'issue.update',
          resourcePattern: 'issue:*',
          effect: 'ALLOW' as const,
          condition: {
            op: 'and' as const,
            args: [
              {
                field: 'issue.project',
                op: 'eq' as const,
                value: 'prj_example',
              },
              {
                field: 'issue.assignee',
                op: 'eq' as const,
                value: { var: 'user.currentUser' as const },
              },
            ],
          },
        },
      ],
    }
    expect(
      parsePermissionDocument(serializePermissionDocument(document)),
    ).toEqual({ document, errors: [] })
  })

  it('rejects unknown fields and unsupported operators', () => {
    const result = parsePermissionDocument(
      JSON.stringify({
        schemaVersion: 1,
        code: 'bad',
        name: 'Bad',
        extra: true,
        rules: [
          {
            action: 'issue.view',
            resourcePattern: 'issue:*',
            effect: 'ALLOW',
            condition: { field: 'issue.reporter', op: 'matches', value: 'x' },
          },
        ],
      }),
    )
    expect(result.errors).toContain('$.extra is not allowed.')
    expect(result.errors).toContain(
      '$.rules[0].condition.op is not supported by permission evaluation.',
    )
  })

  it('keeps invalid JSON separate from a document', () => {
    expect(parsePermissionDocument('{')).toEqual({
      errors: ['Document must be valid JSON.'],
    })
  })

  it('keeps UI node IDs out of serialized backend documents', () => {
    const condition = withPermissionUiIds({ field: 'issue.reporter', op: 'eq', value: { var: 'user.currentUser' } })
    const document = { ...emptyPermissionPolicyDocument(), code: 'ui-ids', name: 'UI IDs', rules: [{ action: 'issue.view', resourcePattern: 'issue:*', effect: 'ALLOW' as const, condition }] }

    expect(condition.uiId).toBeTruthy()
    expect(serializePermissionDocument(document)).not.toContain('uiId')
  })

  it('allocates fresh IDs when cloning a condition tree', () => {
    const condition = withPermissionUiIds({ op: 'and', args: [{ field: 'issue.reporter', op: 'eq', value: { var: 'user.currentUser' } }] })
    const clone = clonePermissionCondition(condition)

    expect(clone.uiId).not.toBe(condition.uiId)
    expect('args' in clone && 'args' in condition && clone.args[0]?.uiId).not.toBe('args' in condition ? condition.args[0]?.uiId : undefined)
  })
})
