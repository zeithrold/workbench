<script lang='ts'>
  import type { Editor } from '@tiptap/core'
  import { Button } from '$lib/components/ui/button'
  import { Separator } from '$lib/components/ui/separator'
  import {
    Bold,
    Braces,
    CodeXml,
    Heading1,
    Heading2,
    Heading3,
    Italic,
    List,
    ListOrdered,
    Pilcrow,
    Quote,
    Redo2,
    Strikethrough,
    Undo2,
  } from '@lucide/svelte'
  import LinkEditor from './link-editor.svelte'

  const { editor, revision }: { editor: Editor, revision: number } = $props()

  const buttons = [
    { label: '正文', icon: Pilcrow, active: () => editor.isActive('paragraph'), run: () => editor.chain().focus().setParagraph().run() },
    { label: '一级标题', icon: Heading1, active: () => editor.isActive('heading', { level: 1 }), run: () => editor.chain().focus().toggleHeading({ level: 1 }).run() },
    { label: '二级标题', icon: Heading2, active: () => editor.isActive('heading', { level: 2 }), run: () => editor.chain().focus().toggleHeading({ level: 2 }).run() },
    { label: '三级标题', icon: Heading3, active: () => editor.isActive('heading', { level: 3 }), run: () => editor.chain().focus().toggleHeading({ level: 3 }).run() },
    { label: '粗体', icon: Bold, active: () => editor.isActive('bold'), run: () => editor.chain().focus().toggleBold().run() },
    { label: '斜体', icon: Italic, active: () => editor.isActive('italic'), run: () => editor.chain().focus().toggleItalic().run() },
    { label: '删除线', icon: Strikethrough, active: () => editor.isActive('strike'), run: () => editor.chain().focus().toggleStrike().run() },
    { label: '行内代码', icon: Braces, active: () => editor.isActive('code'), run: () => editor.chain().focus().toggleCode().run() },
    { label: '无序列表', icon: List, active: () => editor.isActive('bulletList'), run: () => editor.chain().focus().toggleBulletList().run() },
    { label: '有序列表', icon: ListOrdered, active: () => editor.isActive('orderedList'), run: () => editor.chain().focus().toggleOrderedList().run() },
    { label: '引用', icon: Quote, active: () => editor.isActive('blockquote'), run: () => editor.chain().focus().toggleBlockquote().run() },
    { label: '代码块', icon: CodeXml, active: () => editor.isActive('codeBlock'), run: () => editor.chain().focus().toggleCodeBlock().run() },
  ] as const

  function isActive(item: typeof buttons[number]) {
    void revision
    return item.active()
  }

  function canUndo() {
    void revision
    return editor.can().chain().focus().undo().run()
  }

  function canRedo() {
    void revision
    return editor.can().chain().focus().redo().run()
  }
</script>

<div class='flex flex-wrap items-center gap-x-2 gap-y-1 border-b border-border/60 bg-muted/15 px-2 py-1.5' role='toolbar' aria-label='文本格式' data-revision={revision}>
  {#each [[0, 4], [4, 8], [8, 12]] as range (range[0])}
    <div class='flex items-center gap-0.5'>
      {#each buttons.slice(range[0], range[1]) as item (item.label)}
        <Button
          variant='ghost'
          size='icon-sm'
          class={isActive(item) ? 'bg-accent text-accent-foreground shadow-xs' : 'text-muted-foreground hover:text-foreground'}
          aria-label={item.label}
          aria-pressed={isActive(item)}
          title={item.label}
          onmousedown={event => event.preventDefault()}
          onclick={item.run}
        >
          <item.icon />
        </Button>
      {/each}
    </div>
  {/each}
  <div class='flex items-center gap-0.5'>
    <LinkEditor {editor} {revision} />
  </div>
  <Separator orientation='vertical' class='hidden h-5 sm:block' />
  <div class='flex items-center gap-0.5'>
    <Button
      variant='ghost'
      size='icon-sm'
      class='text-muted-foreground hover:text-foreground'
      aria-label='撤销'
      title='撤销'
      disabled={!canUndo()}
      onmousedown={event => event.preventDefault()}
      onclick={() => editor.chain().focus().undo().run()}
    ><Undo2 /></Button>
    <Button
      variant='ghost'
      size='icon-sm'
      class='text-muted-foreground hover:text-foreground'
      aria-label='重做'
      title='重做'
      disabled={!canRedo()}
      onmousedown={event => event.preventDefault()}
      onclick={() => editor.chain().focus().redo().run()}
    ><Redo2 /></Button>
  </div>
</div>
