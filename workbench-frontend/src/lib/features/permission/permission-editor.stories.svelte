<script module lang='ts'>
  import type { PermissionPolicyDocument } from './permission-document.js'
  import type { PermissionFieldOption, PermissionResourceOption } from './permission-editor-model.js'
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import { expect, fn, spyOn, userEvent, waitFor, within } from 'storybook/test'
  import AgilePermissionEditorStoryHost from './agile-permission-editor-story-host.svelte'
  import { emptyPermissionPolicyDocument } from './permission-document.js'

  const actions = [
    { code: 'issue.view', name: 'View work items', description: 'Read work-item details' },
    { code: 'issue.update', name: 'Update work items', description: 'Edit work-item fields' },
    { code: 'issue.delete', name: 'Delete work items', description: 'Remove work items' },
    { code: 'project.manage', name: 'Manage projects', description: 'Change project settings' },
  ]

  const userValues = [
    { id: 'current-user', label: 'Current user', description: 'The signed-in user', value: { var: 'user.currentUser' } as const },
    { id: 'issue-reporter', label: 'Work item reporter', value: { var: 'issue.reporter' } as const },
    { id: 'issue-assignee', label: 'Work item assignee', value: { var: 'issue.assignee' } as const },
    { id: 'ada', label: 'Ada Lovelace', description: 'ada@example.com', avatar: 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 80 80%22%3E%3Crect width=%2280%22 height=%2280%22 fill=%22%237c3aed%22/%3E%3Ctext x=%2240%22 y=%2251%22 text-anchor=%22middle%22 font-size=%2230%22 fill=%22white%22%3EAL%3C/text%3E%3C/svg%3E', value: 'user-ada' },
  ]
  const fields: PermissionFieldOption[] = [
    { id: 'issue.reporter', label: 'Reporter', description: 'System field', group: 'System fields', field: 'issue.reporter', type: 'user', operators: ['eq', 'neq', 'in', 'not_in', 'is_empty', 'is_not_empty'], values: userValues, defaultValue: { var: 'user.currentUser' } },
    { id: 'issue.assignee', label: 'Assignee', description: 'System field', group: 'System fields', field: 'issue.assignee', type: 'user', operators: ['eq', 'neq', 'in', 'not_in', 'is_empty', 'is_not_empty'], values: userValues, defaultValue: { var: 'user.currentUser' } },
    { id: 'issue.statusGroup', label: 'Status category', description: 'System field', group: 'System fields', field: 'issue.statusGroup', type: 'single-select', operators: ['eq', 'neq', 'in', 'not_in'], values: [{ id: 'started', label: 'Started', color: '#f59e0b', value: 'started' }, { id: 'completed', label: 'Completed', color: '#22c55e', value: 'completed' }] },
    { id: 'property.support-escalated', label: 'Support escalated', description: 'Custom property', group: 'Custom properties', field: { kind: 'property', code: 'support-escalated' }, type: 'boolean', operators: ['eq', 'neq'], defaultValue: true },
    { id: 'property.due-date', label: 'Due date', description: 'Custom property', group: 'Custom properties', field: { kind: 'property', code: 'due-date' }, type: 'date', operators: ['eq', 'neq', 'gt', 'gte', 'lt', 'lte', 'is_empty', 'is_not_empty'] },
  ]
  const resources: PermissionResourceOption[] = [
    { id: 'issue:*', label: 'All work items', description: 'Every work item in the tenant', group: 'Work items' },
    { id: 'issue:wi-checkout', label: 'Checkout fails for invited users', description: 'A concrete work item', group: 'Work items', badge: 'Bug', resourceKey: 'CORE-128' },
    { id: 'issue:wi-import', label: 'Import existing project data', description: 'A concrete work item', group: 'Work items', badge: 'Story', resourceKey: 'CORE-91' },
  ]

  const configuredPolicy: PermissionPolicyDocument = {
    schemaVersion: 1,
    code: 'project-contributor',
    name: 'Project contributor',
    description: 'Lets project contributors work on assigned issues while protecting deletion.',
    rules: [
      {
        id: 'allow-assigned-update',
        action: 'issue.update',
        resourcePattern: 'issue:*',
        effect: 'ALLOW',
        condition: {
          op: 'and',
          args: [
            { field: 'issue.assignee', op: 'eq', value: { var: 'user.currentUser' } },
            {
              op: 'or',
              args: [
                { field: 'issue.statusGroup', op: 'neq', value: 'completed' },
                {
                  op: 'and',
                  args: [
                    { field: { kind: 'property', code: 'support-escalated' }, op: 'eq', value: true },
                    { field: { kind: 'property', code: 'due-date' }, op: 'gte', value: '2026-07-16' },
                  ],
                },
              ],
            },
          ],
        },
      },
      {
        id: 'deny-delete',
        action: 'issue.delete',
        resourcePattern: 'issue:wi-checkout',
        effect: 'DENY',
        condition: null,
      },
    ],
  }

  const deeplyNestedPolicy: PermissionPolicyDocument = {
    ...configuredPolicy,
    code: 'deeply-nested-agile-policy',
    name: 'Deeply nested Agile policy',
    rules: [{
      action: 'issue.update',
      resourcePattern: 'issue:*',
      effect: 'ALLOW',
      condition: {
        op: 'and',
        args: [
          { field: 'issue.assignee', op: 'eq', value: { var: 'user.currentUser' } },
          {
            op: 'or',
            args: [
              { field: 'issue.statusGroup', op: 'neq', value: 'completed' },
              {
                op: 'and',
                args: [
                  { field: { kind: 'property', code: 'support-escalated' }, op: 'eq', value: true },
                  { op: 'not', arg: { field: { kind: 'property', code: 'due-date' }, op: 'lt', value: '2026-07-16' } },
                ],
              },
            ],
          },
        ],
      },
    }],
  }

  const emptyChange = fn()
  const configuredChange = fn()

  const { Story } = defineMeta({
    title: 'Features/Permission/Agile permission editor',
    component: AgilePermissionEditorStoryHost,
    parameters: { layout: 'fullscreen', a11y: { test: 'todo' } },
  })

  async function emptyPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const documentJson = canvas.getByTestId('permission-document-json')

    await expect(canvas.getByText('This policy has no rules.')).toBeVisible()
    await userEvent.type(canvas.getByLabelText('Code'), 'custom-reviewer')
    await userEvent.click(canvas.getByRole('button', { name: 'Add rule' }))

    await expect(canvas.getByText('Rule 1')).toBeVisible()
    await expect(documentJson).toHaveTextContent('custom-reviewer')
    await expect(documentJson).toHaveTextContent('issue.view')
    await expect(emptyChange).toHaveBeenCalled()
  }

  async function configuredPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const consoleWarn = spyOn(console, 'warn')
    const consoleError = spyOn(console, 'error')
    const firstRuleCard = canvas.getByText('Rule 1').closest('[data-slot="card"]') as HTMLElement
    const firstRule = within(firstRuleCard)

    await expect(canvas.getAllByText(/^Rule \d+$/)).toHaveLength(2)
    await expect(canvasElement.querySelector('select')).not.toBeInTheDocument()
    await expect(canvasElement.querySelector('datalist')).not.toBeInTheDocument()
    await expect(canvas.getAllByText('and').length).toBeGreaterThan(0)
    await expect(firstRule.getAllByRole('button', { name: 'all conditions' }).length).toBeGreaterThan(0)
    await expect(firstRule.getAllByRole('button', { name: 'any condition' }).length).toBeGreaterThan(0)
    await expect(canvas.getAllByText('All work items').length).toBeGreaterThan(0)
    await expect(canvas.getByText('All').closest('[data-slot="badge"]')).toHaveClass('bg-foreground', 'text-background')
    await expect(canvas.getByText('Checkout fails for invited users')).toBeVisible()
    await expect(canvas.getByText('CORE-128')).toBeVisible()
    await expect(canvas.getByText('Bug')).toBeVisible()
    await expect(firstRule.getAllByRole('button', { name: 'Add group' })).toHaveLength(2)

    const actionField = firstRule.getByText('Action').closest('[data-slot="field"]') as HTMLElement
    await expect(actionField.querySelector('[data-slot="permission-action-content"]')).toBeVisible()
    await expect(within(actionField).queryByText('UW')).not.toBeInTheDocument()
    await userEvent.click(within(actionField).getByRole('button', { name: 'Update work items' }))
    await expect(actionField.querySelectorAll('[data-slot="permission-action-content"].min-w-0').length).toBeGreaterThan(0)
    await userEvent.click(firstRule.getByText('Rule 1'))
    await waitFor(() => expect(actionField.querySelectorAll('[data-slot="permission-action-content"]')).toHaveLength(1))

    const assigneeRule = firstRule.getByText('Assignee').closest('[data-node-id]') as HTMLElement
    await userEvent.click(within(assigneeRule).getByRole('button', { name: /Current user/ }))
    await userEvent.click(within(assigneeRule).getByRole('option', { name: /Ada Lovelace/ }))
    await expect(canvas.getByTestId('permission-document-json')).toHaveTextContent('user-ada')

    await userEvent.click(within(assigneeRule).getByRole('button', { name: /Ada Lovelace/ }))
    await userEvent.click(within(assigneeRule).getByRole('option', { name: /Current user/ }))
    await expect(canvas.getByTestId('permission-document-json')).toHaveTextContent('user.currentUser')

    await userEvent.click(within(assigneeRule).getByRole('button', { name: /Current user/ }))
    await userEvent.click(within(assigneeRule).getByRole('option', { name: /Work item reporter/ }))
    await expect(canvas.getByTestId('permission-document-json')).toHaveTextContent('issue.reporter')

    await userEvent.click(within(assigneeRule).getByRole('button', { name: /Work item reporter/ }))
    await userEvent.click(within(assigneeRule).getByRole('option', { name: /Work item assignee/ }))
    await expect(canvas.getByTestId('permission-document-json')).toHaveTextContent('issue.assignee')
    await expect(within(assigneeRule).getByRole('button', { name: 'is' })).toBeVisible()
    await userEvent.click(within(assigneeRule).getByRole('button', { name: /Assignee/ }))
    await expect(within(assigneeRule).getByText('Boolean')).toBeInTheDocument()
    await expect(within(assigneeRule).getByText('Date')).toBeInTheDocument()
    await userEvent.click(firstRule.getByText('Rule 1'))
    await waitFor(() => expect(canvas.getAllByText('Support escalated')).toHaveLength(1))

    const statusRule = canvas.getByText('Status category').closest('[data-node-id]') as HTMLElement
    const completedTrigger = within(statusRule).getByRole('button', { name: /Completed/ })
    await expect(within(statusRule).getByRole('button', { name: 'is not' })).toBeVisible()
    await expect(completedTrigger.querySelector('[style*="background-color: rgb(34, 197, 94)"]')).toBeInTheDocument()

    const booleanRule = canvas.getByText('Support escalated').closest('[data-node-id]') as HTMLElement
    const booleanToggle = within(booleanRule).getByRole('group', { name: 'Value' })
    await expect(booleanToggle).toHaveAttribute('data-variant', 'default')
    await expect(booleanToggle).toHaveAttribute('data-spacing', '1')
    await expect(booleanToggle).not.toHaveClass('shadow-xs')

    const effectToggle = firstRule.getByRole('group', { name: 'Effect' })
    const allowEffect = within(effectToggle).getByRole('radio', { name: 'Allow' })
    const denyEffect = within(effectToggle).getByRole('radio', { name: 'Deny' })
    await expect(effectToggle).toHaveAttribute('data-variant', 'default')
    await expect(effectToggle).not.toHaveClass('shadow-xs')
    await expect(allowEffect).toHaveAttribute('data-state', 'on')
    await expect(allowEffect).toHaveAttribute('aria-checked', 'true')
    await userEvent.click(denyEffect)
    await expect(allowEffect).toHaveAttribute('data-state', 'off')
    await expect(denyEffect).toHaveAttribute('data-state', 'on')
    await expect(denyEffect).toHaveAttribute('aria-checked', 'true')

    await userEvent.click(firstRule.getByRole('button', { name: 'Remove condition' }))
    await expect(firstRule.getByRole('button', { name: 'Add condition' })).toBeVisible()
    await expect(firstRule.getByRole('button', { name: 'Add group' })).toBeVisible()
    await userEvent.click(firstRule.getByRole('button', { name: 'Add group' }))
    await expect(firstRule.getByRole('button', { name: 'Remove condition' })).toBeVisible()
    await expect(firstRule.getByRole('button', { name: 'Collapse group' })).toBeVisible()

    await userEvent.click(canvas.getAllByRole('button', { name: 'Duplicate' })[0])
    await expect(canvas.getAllByText(/^Rule \d+$/)).toHaveLength(3)
    await userEvent.click(canvas.getAllByRole('button', { name: 'Delete' })[0])
    await expect(canvas.getAllByText(/^Rule \d+$/)).toHaveLength(2)
    await expect(configuredChange).toHaveBeenCalled()
    await expect(consoleWarn.mock.calls.flat().join(' ')).not.toContain('derived_inert')
    await expect(consoleError.mock.calls.flat().join(' ')).not.toContain('DataCloneError')
    consoleWarn.mockRestore()
    consoleError.mockRestore()
  }

  async function readOnlyPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)

    await expect(canvas.getByText('Read only')).toBeVisible()
    await expect(canvas.getByLabelText('Code')).toBeDisabled()
    await expect(canvas.queryByRole('button', { name: 'Add rule' })).not.toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Duplicate' })).not.toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    await expect(canvas.getAllByRole('button', { name: 'all conditions' })[0]).toBeDisabled()
    await expect(canvas.getAllByRole('button', { name: 'is' })[0]).toBeDisabled()
    await expect(canvas.getByRole('button', { name: /Current user/ })).toBeDisabled()
  }

  async function jsonPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const page = within(canvasElement.ownerDocument.body)

    await userEvent.click(canvas.getByRole('button', { name: 'Advanced JSON' }))
    await waitFor(
      () => expect(page.getByRole('dialog').querySelector('[data-slot="json-editor"]')).toBeVisible(),
      { timeout: 10_000 },
    )
    await expect(canvas.getByTestId('permission-document-json')).toHaveTextContent('project-contributor')
  }

  async function deeplyNestedPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const page = within(canvasElement.ownerDocument.body)
    await waitFor(() => expect(canvasElement.querySelectorAll('[data-slot="permission-condition-group"]')).toHaveLength(1))
    await expect(canvasElement.querySelector('[data-depth="1"]')).toBeVisible()
    await expect(canvasElement.querySelectorAll('[data-slot="permission-logic-rail"]').length).toBeGreaterThan(0)
    await expect(canvas.getByTestId('permission-document-json')).not.toHaveTextContent('uiId')
    canvas.getByRole('button', { name: 'Edit nested condition group' }).focus()
    await userEvent.keyboard('{Enter}')
    const firstDrawer = await waitFor(() => {
      const drawer = page.getAllByRole('dialog').at(-1) as HTMLElement
      expect(drawer.querySelector('[data-depth="2"]')).toBeVisible()
      return drawer
    })
    within(firstDrawer).getByRole('button', { name: 'Edit nested condition group' }).focus()
    await userEvent.keyboard('{Enter}')
    const nestedDrawer = await waitFor(() => {
      const drawer = page.getAllByRole('dialog').at(-1) as HTMLElement
      expect(drawer.querySelector('[data-depth="3"]')).toBeVisible()
      return drawer
    })
    await expect(nestedDrawer.querySelector('[data-slot="permission-not-condition"]')).toBeVisible()
    const editor = canvasElement.querySelector('[data-slot="permission-policy-editor-core"]') as HTMLElement
    await expect(editor.scrollWidth).toBeLessThanOrEqual(editor.clientWidth)
  }
</script>

<Story
  name='Empty'
  args={{
    initialValue: emptyPermissionPolicyDocument(),
    actions,
    fields,
    resources,
    codeEditable: true,
    modelId: 'permission-story-empty',
    onChange: emptyChange,
  }}
  play={emptyPlay}
/>
<Story
  name='Configured'
  args={{
    initialValue: configuredPolicy,
    actions,
    fields,
    resources,
    modelId: 'permission-story-configured',
    onChange: configuredChange,
  }}
  play={configuredPlay}
/>
<Story
  name='Read only'
  args={{
    initialValue: configuredPolicy,
    actions,
    fields,
    resources,
    editable: false,
    modelId: 'permission-story-read-only',
  }}
  play={readOnlyPlay}
/>
<Story
  name='JSON mode'
  args={{
    initialValue: configuredPolicy,
    actions,
    fields,
    resources,
    modelId: 'permission-story-json',
  }}
  play={jsonPlay}
/>
<Story
  name='Deep nested conditions'
  args={{
    initialValue: deeplyNestedPolicy,
    actions,
    fields,
    resources,
    modelId: 'permission-story-deeply-nested',
  }}
  parameters={{ viewport: { defaultViewport: 'mobile1' } }}
  play={deeplyNestedPlay}
/>
