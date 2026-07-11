<script module lang='ts'>
  import type { RichTextDocument } from './types.js'
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import { expect, fn, userEvent, within } from 'storybook/test'
  import RichTextEditorStoryHost from './rich-text-editor-story-host.svelte'
  import { EMPTY_RICH_TEXT_DOCUMENT } from './types.js'

  const formattedDocument: RichTextDocument = {
    type: 'doc',
    content: [
      { type: 'heading', attrs: { level: 1 }, content: [{ type: 'text', text: 'Workbench 编辑器' }] },
      { type: 'paragraph', content: [{ type: 'text', text: '使用结构化 JSON 保存富文本内容。', marks: [{ type: 'bold' }] }] },
      { type: 'bulletList', content: [
        { type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: '基础格式工具栏' }] }] },
        { type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: '亮暗主题支持' }] }] },
      ] },
      { type: 'blockquote', content: [{ type: 'paragraph', content: [{ type: 'text', text: '先在 Storybook 中验证组件契约。' }] }] },
      { type: 'paragraph', content: [{ type: 'text', text: '访问项目主页', marks: [{ type: 'link', attrs: { href: 'https://example.com', target: '_blank', rel: 'noopener noreferrer nofollow', class: null } }] }] },
      { type: 'codeBlock', attrs: { language: null }, content: [{ type: 'text', text: 'const document = editor.getJSON()' }] },
    ],
  }

  const controlledChange = fn()
  const emptyChange = fn()

  const { Story } = defineMeta({
    title: 'Shared/RichTextEditor',
    component: RichTextEditorStoryHost,
    parameters: { layout: 'centered' },
  })

  async function controlledPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const editor = await canvas.findByRole('textbox', { name: '富文本编辑器' })
    await userEvent.click(editor)
    await userEvent.type(editor, '可控内容')
    await userEvent.keyboard('{Meta>}a{/Meta}')
    await userEvent.click(canvas.getByRole('button', { name: '粗体' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('bold')
    await userEvent.click(canvas.getByRole('button', { name: '一级标题' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('heading')
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('可控内容')
    await expect(controlledChange).toHaveBeenCalled()
  }

  async function emptyPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const editor = await canvas.findByRole('textbox')
    await userEvent.click(editor)
    await userEvent.click(canvas.getByRole('button', { name: '无序列表' }))
    await userEvent.type(editor, '列表项')
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('bulletList')
    await userEvent.click(canvas.getByRole('button', { name: '撤销' }))
    await userEvent.click(canvas.getByRole('button', { name: '重做' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('列表项')
    await userEvent.keyboard('{Meta>}a{/Meta}')
    await userEvent.click(canvas.getByRole('button', { name: '编辑链接' }))
    const body = within(document.body)
    const input = await body.findByRole('textbox', { name: '链接地址' })
    await userEvent.type(input, 'https://workbench.example')
    await userEvent.click(body.getByRole('button', { name: '应用' }))
    await expect(canvas.getByTestId('document-json')).toHaveTextContent('https://workbench.example')
    await userEvent.click(canvas.getByRole('button', { name: '编辑链接' }))
    await userEvent.click(await body.findByRole('button', { name: '移除' }))
    await expect(canvas.getByTestId('document-json')).not.toHaveTextContent('https://workbench.example')
    await expect(emptyChange).toHaveBeenCalled()
  }

  async function readingPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(canvas.getByRole('textbox')).toBeVisible()
    await expect(canvasElement.querySelector('[data-slot="rich-text-editor"]')).toHaveAttribute('data-content-width', 'reading')
    await expect(canvas.getByRole('toolbar', { name: '文本格式' })).toBeVisible()
  }

  async function readOnlyPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    await expect(canvas.queryByRole('toolbar', { name: '文本格式' })).not.toBeInTheDocument()
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
  args={{ initialValue: EMPTY_RICH_TEXT_DOCUMENT, placeholder: '描述你的想法…', showJson: true, onChange: emptyChange }}
  play={emptyPlay}
/>
<Story
  name='Formatted Content'
  args={{ initialValue: formattedDocument, contentWidth: 'reading' }}
  play={readingPlay}
/>
<Story name='Read Only' args={{ initialValue: formattedDocument, editable: false }} play={readOnlyPlay} />
