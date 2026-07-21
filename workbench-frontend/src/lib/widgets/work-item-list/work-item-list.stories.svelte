<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import { expect, fireEvent, userEvent, waitFor, within } from 'storybook/test'
  import { DEFAULT_WORK_ITEM_DISPLAY_FIELDS } from './work-item-list-config.js'
  import WorkItemListStoryHost from './work-item-list-story-host.svelte'
  import {
    defaultWorkItemHandler,
    displayFieldsHandler,
    emptyWorkItemHandler,
    forbiddenWorkItemHandler,
    interactiveWorkItemHandlers,
    longTitleWorkItemHandler,
    nullableWorkItemHandler,
    pagedWorkItemHandler,
    patchErrorHandler,
    serverErrorWorkItemHandler,
    slowWorkItemHandler,
  } from './work-item-list.msw.js'

  const { Story } = defineMeta({
    title: 'Widgets/WorkItemList',
    component: WorkItemListStoryHost,
    parameters: { layout: 'fullscreen' },
  })

  async function defaultPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Build a reusable work item list')).toBeVisible()
    await userEvent.click(canvas.getByRole('checkbox', { name: 'Select WB-1' }))
    await expect(canvas.getByRole('checkbox', { name: 'Select WB-1' })).toBeChecked()
    await userEvent.click(canvas.getByRole('button', { name: 'Columns' }))
    const page = within(canvasElement.ownerDocument.body)
    const dialog = await page.findByRole('dialog')
    await waitFor(() => expect(dialog).toBeVisible())
    const modal = within(dialog)
    await expect(modal.getByText('Available columns')).toBeVisible()
    await expect(modal.getByText('Displayed columns')).toBeVisible()
    const displayed = modal.getByRole('region', { name: 'Displayed columns' })
    const available = modal.getByRole('region', { name: 'Available columns' })
    await fireEvent.keyDown(modal.getByRole('button', { name: 'Drag Title' }), { key: 'ArrowDown' })
    await fireEvent.keyDown(modal.getByRole('button', { name: 'Drag Title' }), { key: 'ArrowLeft' })
    await expect([...displayed.querySelectorAll('[data-column-id]')].map(node => node.getAttribute('data-column-id')).slice(0, 3)).toEqual(['key', 'issueType', 'title'])
    await fireEvent.keyDown(modal.getByRole('button', { name: 'Drag Key' }), { key: 'ArrowLeft' })
    await expect(available.querySelector('[data-column-id="key"]')).not.toBeNull()
    await expect(displayed.querySelector('[data-column-id="title"]')).not.toBeNull()
    await userEvent.click(modal.getByRole('button', { name: 'Cancel' }))
  }

  async function pagedPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Build a reusable work item list')).toBeVisible()
    await userEvent.click(canvas.getByRole('button', { name: 'Load more' }))
    await expect(await canvas.findByText('Second cursor page')).toBeVisible()
  }

  async function errorPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(await canvas.findByText('Build a reusable work item list')).toBeVisible()
    await userEvent.click(canvas.getByRole('button', { name: 'S Sam' }))
    const page = within(canvasElement.ownerDocument.body)
    await userEvent.click(await page.findByRole('button', { name: 'Jordan' }))
    await waitFor(() => expect(canvas.getByText('Sam')).toBeVisible())
    await expect(canvasElement.querySelector('[title="Assignment blocked by field policy."]')).not.toBeNull()
  }
</script>

<Story name='Editable list, column modal, and keyboard management' parameters={{ msw: { handlers: [defaultWorkItemHandler, ...interactiveWorkItemHandlers] } }} play={defaultPlay} />
<Story name='Mixed field permissions' parameters={{ msw: { handlers: [defaultWorkItemHandler, ...interactiveWorkItemHandlers] } }} />
<Story name='Custom property and unavailable cells' args={{ initialDisplayFields: [...DEFAULT_WORK_ITEM_DISPLAY_FIELDS, { field: { kind: 'property', code: 'effort' }, width: 180 }] }} parameters={{ msw: { handlers: [defaultWorkItemHandler, ...interactiveWorkItemHandlers] } }} />
<Story name='Inline update error and rollback' parameters={{ msw: { handlers: [defaultWorkItemHandler, patchErrorHandler, ...interactiveWorkItemHandlers] } }} play={errorPlay} />
<Story name='Loading and slow response' parameters={{ msw: { handlers: [slowWorkItemHandler, displayFieldsHandler] } }} />
<Story name='Empty' parameters={{ msw: { handlers: [emptyWorkItemHandler, displayFieldsHandler] } }} />
<Story name='Forbidden' parameters={{ msw: { handlers: [forbiddenWorkItemHandler, displayFieldsHandler] } }} />
<Story name='Server error' parameters={{ msw: { handlers: [serverErrorWorkItemHandler, displayFieldsHandler] } }} />
<Story name='Nullable fields' parameters={{ msw: { handlers: [nullableWorkItemHandler, displayFieldsHandler] } }} />
<Story name='Long title and horizontal scroll' parameters={{ msw: { handlers: [longTitleWorkItemHandler, displayFieldsHandler] } }} />
<Story name='Two cursor pages' parameters={{ msw: { handlers: [pagedWorkItemHandler, displayFieldsHandler] } }} play={pagedPlay} />
