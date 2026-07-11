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
  const commentChange = fn()
  const commentSubmit = fn()
  const emptyCommentSubmit = fn()
  const submittingCommentSubmit = fn()
  const readOnlyCommentSubmit = fn()

  const selectAllShortcut = navigator.userAgent.includes('Mac OS')
    ? '{Meta>}a{/Meta}'
    : '{Control>}a{/Control}'

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
    await userEvent.keyboard(selectAllShortcut)
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
    await userEvent.keyboard(selectAllShortcut)
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

  async function commentPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const editor = await canvas.findByRole('textbox', { name: 'Rich text editor' })
    const toolbar = canvas.getByRole('toolbar', { name: 'Text formatting' })
    await expect(within(toolbar).getByRole('button', { name: 'Bold' })).toBeVisible()
    await expect(within(toolbar).getByRole('button', { name: 'Bulleted list' })).toBeVisible()
    await expect(within(toolbar).getByRole('button', { name: 'Quote' })).toBeVisible()
    await expect(within(toolbar).queryByRole('button', { name: 'Heading 1' })).not.toBeInTheDocument()
    await expect(within(toolbar).queryByRole('button', { name: 'Code block' })).not.toBeInTheDocument()

    await userEvent.click(editor)
    await userEvent.type(editor, '/heading')
    await expect(within(document.body).queryByRole('listbox', { name: 'Editor commands' })).not.toBeInTheDocument()
    await userEvent.keyboard(selectAllShortcut)
    await userEvent.type(editor, 'First line{Enter}Second line')
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('Second line')
    await userEvent.keyboard(selectAllShortcut)
    await userEvent.click(within(toolbar).getByRole('button', { name: 'Bold' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('bold')
    await userEvent.click(within(toolbar).getByRole('button', { name: 'Edit link' }))
    const body = within(document.body)
    await userEvent.type(await body.findByRole('textbox', { name: 'Link URL' }), 'https://workbench.example/comment')
    await userEvent.click(body.getByRole('button', { name: 'Apply' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('https://workbench.example/comment')
    await userEvent.click(within(toolbar).getByRole('button', { name: 'Bulleted list' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('bulletList')
    await userEvent.click(within(toolbar).getByRole('button', { name: 'Bulleted list' }))
    await userEvent.click(within(toolbar).getByRole('button', { name: 'Quote' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('blockquote')
    await userEvent.keyboard('{Meta>}{Enter}{/Meta}')
    await expect(commentSubmit).toHaveBeenCalledTimes(1)
    await expect(commentChange).toHaveBeenCalled()
  }

  async function emptyCommentPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const editor = await canvas.findByRole('textbox')
    await userEvent.click(editor)
    await userEvent.keyboard('{Meta>}{Enter}{/Meta}')
    await expect(emptyCommentSubmit).not.toHaveBeenCalled()
  }

  async function submittingCommentPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const editor = await canvas.findByRole('textbox')
    await userEvent.click(editor)
    await userEvent.keyboard('{Meta>}{Enter}{/Meta}')
    await expect(submittingCommentSubmit).not.toHaveBeenCalled()
    await expect(canvas.getByRole('button', { name: 'Bold' })).toBeDisabled()
  }

  async function pastedCommentPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const editor = await canvas.findByRole('textbox')
    await userEvent.click(editor)
    const clipboard = new DataTransfer()
    clipboard.setData('text/plain', 'Heading\nconst value = 1')
    clipboard.setData('text/html', '<h1>Heading</h1><pre><code>const value = 1</code></pre>')
    await userEvent.paste(clipboard)
    const json = canvas.getByTestId('document-json')
    await expect(json).toHaveTextContent('Heading')
    await expect(json).toHaveTextContent('const value = 1')
    await expect(json).not.toHaveTextContent('heading')
    await expect(json).not.toHaveTextContent('codeBlock')
  }

  async function readOnlyCommentPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(canvas.queryByRole('toolbar', { name: 'Text formatting' })).not.toBeInTheDocument()
    await expect(canvas.queryByText('Press Ctrl/⌘+Enter to submit')).not.toBeInTheDocument()
    const editor = canvas.getByRole('textbox')
    await expect(editor).toHaveAttribute('contenteditable', 'false')
    editor.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', metaKey: true, bubbles: true }))
    await expect(readOnlyCommentSubmit).not.toHaveBeenCalled()
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
<Story
  name='Comment'
  args={{ initialValue: EMPTY_RICH_TEXT_DOCUMENT, preset: 'comment', placeholder: 'Write a comment…', showJson: true, onChange: commentChange, onSubmit: commentSubmit }}
  play={commentPlay}
/>
<Story
  name='Empty Comment'
  args={{ initialValue: EMPTY_RICH_TEXT_DOCUMENT, preset: 'comment', onSubmit: emptyCommentSubmit }}
  play={emptyCommentPlay}
/>
<Story
  name='Submitting Comment'
  args={{ initialValue: { ...EMPTY_RICH_TEXT_DOCUMENT, content: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'Sending' }] }] } }, preset: 'comment', submitting: true, onSubmit: submittingCommentSubmit }}
  play={submittingCommentPlay}
/>
<Story
  name='Comment Paste Normalization'
  args={{ initialValue: EMPTY_RICH_TEXT_DOCUMENT, preset: 'comment', showJson: true }}
  play={pastedCommentPlay}
/>
<Story
  name='Read Only Comment'
  args={{ initialValue: { ...EMPTY_RICH_TEXT_DOCUMENT, content: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'A compact read-only comment.' }] }] } }, preset: 'comment', editable: false, onSubmit: readOnlyCommentSubmit }}
  play={readOnlyCommentPlay}
/>
<Story
  name='Dark Comment'
  args={{ initialValue: EMPTY_RICH_TEXT_DOCUMENT, preset: 'comment', dark: true, placeholder: 'Write a comment…' }}
/>
<Story
  name='Narrow Comment'
  args={{ initialValue: EMPTY_RICH_TEXT_DOCUMENT, preset: 'comment' }}
  parameters={{ viewport: { defaultViewport: 'mobile1' } }}
/>
