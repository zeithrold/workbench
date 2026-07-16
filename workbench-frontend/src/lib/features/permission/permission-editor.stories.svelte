<script module lang='ts'>
  import type { PermissionPolicyDocument } from './permission-document.js'
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import { expect, fn, userEvent, waitFor, within } from 'storybook/test'
  import { emptyPermissionPolicyDocument } from './permission-document.js'
  import PermissionEditorStoryHost from './permission-editor-story-host.svelte'

  const actions = [
    { code: 'issue.view', description: 'View work items' },
    { code: 'issue.update', description: 'Update work items' },
    { code: 'issue.delete', description: 'Delete work items' },
    { code: 'project.manage', description: 'Manage projects' },
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
                { field: { kind: 'property', code: 'support-escalated' }, op: 'eq', value: true },
              ],
            },
          ],
        },
      },
      {
        id: 'deny-delete',
        action: 'issue.delete',
        resourcePattern: 'issue:*',
        effect: 'DENY',
        condition: null,
      },
    ],
  }

  const emptyChange = fn()
  const configuredChange = fn()

  const { Story } = defineMeta({
    title: 'Features/Permission/Permission editor',
    component: PermissionEditorStoryHost,
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
    const documentJson = canvas.getByTestId('permission-document-json')

    await expect(canvas.getAllByText(/^Rule \d+$/)).toHaveLength(2)
    await userEvent.selectOptions(canvas.getAllByRole('combobox', { name: 'Condition kind' })[0], 'or')
    await expect(documentJson).toHaveTextContent('"op": "or"')

    await userEvent.click(canvas.getAllByRole('button', { name: 'Duplicate' })[0])
    await expect(canvas.getAllByText(/^Rule \d+$/)).toHaveLength(3)
    await userEvent.click(canvas.getAllByRole('button', { name: 'Delete' })[0])
    await expect(canvas.getAllByText(/^Rule \d+$/)).toHaveLength(2)
    await expect(configuredChange).toHaveBeenCalled()
  }

  async function readOnlyPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)

    await expect(canvas.getByText('Read only')).toBeVisible()
    await expect(canvas.getByLabelText('Code')).toBeDisabled()
    await expect(canvas.queryByRole('button', { name: 'Add rule' })).not.toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Duplicate' })).not.toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  }

  async function jsonPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)

    await userEvent.click(canvas.getByRole('button', { name: 'JSON' }))
    await waitFor(
      () => expect(canvasElement.querySelector('[data-slot="json-editor"]')).toBeVisible(),
      { timeout: 10_000 },
    )
    await expect(canvas.getByTestId('permission-document-json')).toHaveTextContent('project-contributor')
  }
</script>

<Story
  name='Empty policy'
  args={{
    initialValue: emptyPermissionPolicyDocument(),
    actions,
    codeEditable: true,
    modelId: 'permission-story-empty',
    onChange: emptyChange,
  }}
  play={emptyPlay}
/>
<Story
  name='Configured policy'
  args={{
    initialValue: configuredPolicy,
    actions,
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
    modelId: 'permission-story-json',
  }}
  play={jsonPlay}
/>
