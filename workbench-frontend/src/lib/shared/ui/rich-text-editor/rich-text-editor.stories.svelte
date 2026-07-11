<script module lang='ts'>
  import type { RichTextDocument } from './types.js'
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import { expect, fn, userEvent, within } from 'storybook/test'
  import RichTextEditorStoryHost from './rich-text-editor-story-host.svelte'
  import { EMPTY_RICH_TEXT_DOCUMENT } from './types.js'

  const formattedDocument: RichTextDocument = {
    format: 'tiptap',
    schemaVersion: 1,
    content: { type: 'doc', content: [
      { type: 'heading', attrs: { level: 1 }, content: [{ type: 'text', text: 'Workbench Editor' }] },
      { type: 'paragraph', content: [{ type: 'text', text: 'Rich text is stored as structured JSON.', marks: [{ type: 'bold' }] }] },
      { type: 'bulletList', content: [
        { type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Core formatting toolbar' }] }] },
        { type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Light and dark theme support' }] }] },
      ] },
      { type: 'blockquote', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Validate the component contract in Storybook first.' }] }] },
      { type: 'paragraph', content: [{ type: 'text', text: 'Visit the project homepage', marks: [{ type: 'link', attrs: { href: 'https://example.com', target: '_blank', rel: 'noopener noreferrer nofollow', class: null } }] }] },
      { type: 'codeBlock', attrs: { language: 'typescript' }, content: [{ type: 'text', text: 'const document = editor.getJSON()' }] },
    ] },
  }

  const controlledChange = fn()
  const emptyChange = fn()
  const slashChange = fn()

  const { Story } = defineMeta({
    title: 'Shared/RichTextEditor',
    component: RichTextEditorStoryHost,
    parameters: { layout: 'centered' },
  })

  async function controlledPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const editor = await canvas.findByRole('textbox', { name: 'Rich text editor' })
    await userEvent.click(editor)
    await userEvent.type(editor, 'Controlled content')
    await userEvent.keyboard('{Meta>}a{/Meta}')
    await userEvent.click(canvas.getByRole('button', { name: 'Bold' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('bold')
    await userEvent.click(canvas.getByRole('button', { name: 'Heading 1' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('heading')
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('Controlled content')
    await expect(controlledChange).toHaveBeenCalled()
  }

  async function emptyPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const editor = await canvas.findByRole('textbox')
    await userEvent.click(editor)
    await userEvent.click(canvas.getByRole('button', { name: 'Bulleted list' }))
    await userEvent.type(editor, 'List item')
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('bulletList')
    await userEvent.click(canvas.getByRole('button', { name: 'Undo' }))
    await userEvent.click(canvas.getByRole('button', { name: 'Redo' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('List item')
    await userEvent.keyboard('{Meta>}a{/Meta}')
    await userEvent.click(canvas.getByRole('button', { name: 'Edit link' }))
    const body = within(document.body)
    const input = await body.findByRole('textbox', { name: 'Link URL' })
    await userEvent.type(input, 'https://workbench.example')
    await userEvent.click(body.getByRole('button', { name: 'Apply' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('https://workbench.example')
    await userEvent.click(canvas.getByRole('button', { name: 'Edit link' }))
    await userEvent.click(await body.findByRole('button', { name: 'Remove' }))
    await expect(canvas.getByTestId('document-json')).not.toHaveTextContent('https://workbench.example')
    await expect(emptyChange).toHaveBeenCalled()
  }

  async function readingPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(canvas.getByRole('textbox')).toBeVisible()
    await expect(canvasElement.querySelector('[data-slot="rich-text-editor"]')).toHaveAttribute('data-content-width', 'reading')
    await expect(canvas.getByRole('toolbar', { name: 'Text formatting' })).toBeVisible()
    const language = canvas.getByRole('combobox', { name: 'Code language' })
    await expect(language).toHaveValue('typescript')
    await userEvent.selectOptions(language, 'kotlin')
    await expect(language).toHaveValue('kotlin')
  }

  async function slashPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const editor = await canvas.findByRole('textbox', { name: 'Rich text editor' })
    await userEvent.click(editor)
    await userEvent.type(editor, '/heading 1')
    const menu = await within(document.body).findByRole('listbox', { name: 'Editor commands' })
    await userEvent.click(within(menu).getByRole('option', { name: /Heading 1/ }))
    await userEvent.keyboard('Created with Slash')
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('heading')
    await expect(canvas.getByTestId('document-json')).not.toHaveTextContent('/heading 1')
    await expect(slashChange).toHaveBeenCalled()
  }

  async function readOnlyPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(canvas.queryByRole('toolbar', { name: 'Text formatting' })).not.toBeInTheDocument()
    await expect(canvasElement.querySelector('[data-slot="rich-text-editor"]')).toHaveAttribute('data-editable', 'false')
    await expect(canvas.getByRole('textbox')).toHaveAttribute('contenteditable', 'false')
  }
</script>

<Story
  name='Controlled'
  args={{ initialValue: EMPTY_RICH_TEXT_DOCUMENT, showJson: true, onChange: controlledChange }}
  play={controlledPlay}
/>
<Story
  name='Empty'
  args={{ initialValue: EMPTY_RICH_TEXT_DOCUMENT, placeholder: 'Describe your idea…', showJson: true, onChange: emptyChange }}
  play={emptyPlay}
/>
<Story
  name='Formatted Content'
  args={{ initialValue: formattedDocument, contentWidth: 'reading' }}
  play={readingPlay}
/>
<Story
  name='Slash Commands'
  args={{ initialValue: EMPTY_RICH_TEXT_DOCUMENT, showJson: true, onChange: slashChange }}
  play={slashPlay}
/>
<Story name='Read Only' args={{ initialValue: formattedDocument, editable: false }} play={readOnlyPlay} />
