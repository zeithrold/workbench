import { describe, expect, it } from 'vitest'
import {
  emptyPermissionPolicyDocument,
  parsePermissionDocument,
  serializePermissionDocument,
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
})
