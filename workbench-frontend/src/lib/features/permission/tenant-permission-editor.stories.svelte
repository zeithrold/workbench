<script module lang='ts'>
  import type { PermissionPolicyDocument } from './permission-document.js'
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import { expect, fn, spyOn, userEvent, waitFor, within } from 'storybook/test'
  import { emptyPermissionPolicyDocument } from './permission-document.js'
  import TenantPermissionEditorStoryHost from './tenant-permission-editor-story-host.svelte'

  const actions = [
    { code: 'tenant.read', name: 'View tenant settings', description: 'View tenant metadata', resourcePattern: 'tenant:*' },
    { code: 'permission.policy.manage', name: 'Manage permission policies', description: 'Create and update tenant policies', resourcePattern: 'permission:*' },
  ]
  const configuredPolicy: PermissionPolicyDocument = {
    schemaVersion: 1,
    code: 'tenant-operator',
    name: 'Tenant operator',
    description: 'Allows routine tenant administration.',
    rules: [
      { id: 'allow-read', action: 'tenant.read', resourcePattern: 'tenant:*', effect: 'ALLOW', condition: null },
      { id: 'deny-policy', action: 'permission.policy.manage', resourcePattern: 'permission:*', effect: 'DENY', condition: null },
    ],
  }
  const invalidAgilePolicy: PermissionPolicyDocument = {
    schemaVersion: 1,
    code: 'invalid-agile',
    name: 'Invalid Agile import',
    rules: [{ action: 'issue.view', resourcePattern: 'issue:*', effect: 'ALLOW', condition: { field: 'issue.assignee', op: 'eq', value: { var: 'user.currentUser' } } }],
  }
  const emptyChange = fn()
  const configuredChange = fn()
  const { Story } = defineMeta({
    title: 'Features/Permission/Tenant permission editor',
    component: TenantPermissionEditorStoryHost,
    parameters: { layout: 'fullscreen' },
  })

  async function emptyPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(canvas.getByText('No tenant permissions yet')).toBeVisible()
    await userEvent.type(canvas.getByLabelText('Code'), 'tenant-reader')
    await userEvent.type(canvas.getByLabelText('Name'), 'Tenant reader')
    await userEvent.click(canvas.getByRole('button', { name: 'Add permission' }))
    await expect(canvas.getAllByText('View tenant settings')[0]).toBeVisible()
    await expect(canvas.getByTestId('permission-document-json')).toHaveTextContent('tenant:*')
    await expect(emptyChange).toHaveBeenCalled()
  }

  async function configuredPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const consoleWarn = spyOn(console, 'warn')
    await expect(canvas.queryByRole('button', { name: 'Add condition' })).not.toBeInTheDocument()
    await expect(canvas.getByText('Entire tenant')).toBeVisible()
    await expect(canvas.getByText('Tenant access control')).toBeVisible()
    await userEvent.click(canvas.getAllByRole('button', { name: 'Duplicate permission' })[0])
    await expect(canvas.getAllByRole('button', { name: 'Delete permission' })).toHaveLength(3)
    await userEvent.click(canvas.getAllByRole('button', { name: 'Delete permission' })[0])
    await expect(configuredChange).toHaveBeenCalled()
    await new Promise(resolve => globalThis.setTimeout(resolve, 30))
    const warnings = consoleWarn.mock.calls.flat().join(' ')
    consoleWarn.mockRestore()
    await expect(warnings).not.toContain('derived_inert')
  }

  async function readOnlyPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(canvas.getByText('Read only')).toBeVisible()
    await expect(canvas.getByLabelText('Code')).toBeDisabled()
    await expect(canvas.queryByRole('button', { name: 'Add permission' })).not.toBeInTheDocument()
    await expect(canvas.queryByRole('button', { name: 'Delete permission' })).not.toBeInTheDocument()
  }

  async function jsonPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const page = within(canvasElement.ownerDocument.body)
    await userEvent.click(canvas.getByRole('button', { name: 'Advanced JSON' }))
    await waitFor(() => expect(page.getByRole('dialog').querySelector('[data-slot="json-editor"]')).toBeVisible(), { timeout: 10_000 })
    await expect(page.getByText('Advanced tenant policy JSON')).toBeVisible()
  }

  async function invalidPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(canvas.getByRole('alert')).toHaveTextContent('uses an unavailable tenant capability')
  }
</script>

<Story name='Empty' args={{ initialValue: emptyPermissionPolicyDocument(), actions, codeEditable: true, modelId: 'tenant-permission-story-empty', onChange: emptyChange }} play={emptyPlay} />
<Story name='Configured' args={{ initialValue: configuredPolicy, actions, modelId: 'tenant-permission-story-configured', onChange: configuredChange }} play={configuredPlay} />
<Story name='Read only' args={{ initialValue: configuredPolicy, actions, editable: false, modelId: 'tenant-permission-story-read-only' }} play={readOnlyPlay} />
<Story name='JSON mode' args={{ initialValue: configuredPolicy, actions, modelId: 'tenant-permission-story-json' }} play={jsonPlay} />
<Story name='Rejected Agile JSON' args={{ initialValue: invalidAgilePolicy, actions, modelId: 'tenant-permission-story-invalid' }} play={invalidPlay} />
