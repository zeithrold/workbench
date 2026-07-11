<script lang='ts'>
  import type { RichTextEditorProps } from './types.js'
  import { cn } from '$lib/utils.js'
  import { Editor } from '@tiptap/core'
  import Placeholder from '@tiptap/extension-placeholder'
  import StarterKit from '@tiptap/starter-kit'
  import { onMount } from 'svelte'
  import { WorkbenchCodeBlock } from './code-block.js'
  import RichTextToolbar from './rich-text-toolbar.svelte'
  import { SlashCommand } from './slash-command.js'

  const {
    value,
    onChange,
    editable = true,
    placeholder = 'Start writing…',
    ariaLabel = 'Rich text editor',
    contentWidth = 'full',
    class: className,
  }: RichTextEditorProps = $props()

  let element: HTMLDivElement
  let editor = $state.raw<Editor | undefined>()
  let revision = $state(0)

  onMount(() => {
    editor = new Editor({
      element,
      content: value.content,
      editable,
      enableContentCheck: true,
      extensions: [
        StarterKit.configure({
          heading: { levels: [1, 2, 3] },
          link: { openOnClick: false, defaultProtocol: 'https' },
          codeBlock: false,
        }),
        WorkbenchCodeBlock,
        SlashCommand,
        Placeholder.configure({ placeholder }),
      ],
      editorProps: {
        attributes: {
          'role': 'textbox',
          'aria-label': ariaLabel,
          'aria-multiline': 'true',
          'class': 'tiptap',
        },
      },
      onTransaction: () => revision += 1,
      onUpdate: ({ editor: currentEditor }) => onChange({
        format: 'tiptap',
        schemaVersion: 1,
        content: currentEditor.getJSON(),
      }),
    })

    return () => editor?.destroy()
  })

  $effect(() => {
    if (!editor)
      return
    editor.setEditable(editable)
    editor.view.dom.setAttribute('aria-label', ariaLabel)
  })

  $effect(() => {
    if (!editor)
      return
    if (JSON.stringify(value.content) !== JSON.stringify(editor.getJSON())) {
      editor.commands.setContent(value.content, { emitUpdate: false, errorOnInvalidContent: true })
    }
  })
</script>

<div
  class={cn(
    'overflow-hidden rounded-xl border border-border/70 bg-background transition-[border-color,box-shadow] duration-150',
    editable && 'focus-within:border-ring/60 focus-within:ring-3 focus-within:ring-ring/15 focus-within:shadow-sm',
    className,
  )}
  data-slot='rich-text-editor'
  data-editable={editable}
  data-content-width={contentWidth}
>
  {#if editor && editable}
    <RichTextToolbar {editor} {revision} />
  {/if}
  <div class={cn(contentWidth === 'reading' && 'mx-auto w-full max-w-[45rem]')}>
    <div bind:this={element} class:read-only={!editable}></div>
  </div>
</div>

<style>
  :global(.tiptap) {
    min-height: 14rem;
    padding: 1.75rem 2rem 2.25rem;
    outline: none;
    color: var(--foreground);
    font-size: 0.9375rem;
    line-height: 1.75;
  }
  :global(.tiptap p) { margin: 0.625rem 0; }
  :global(.tiptap h1),
  :global(.tiptap h2),
  :global(.tiptap h3) { color: var(--foreground); font-weight: 650; letter-spacing: -0.025em; }
  :global(.tiptap h1) { margin: 2rem 0 0.75rem; font-size: 1.75rem; line-height: 1.25; }
  :global(.tiptap h2) { margin: 1.75rem 0 0.625rem; font-size: 1.375rem; line-height: 1.35; }
  :global(.tiptap h3) { margin: 1.5rem 0 0.5rem; font-size: 1.125rem; line-height: 1.45; }
  :global(.tiptap > :first-child) { margin-top: 0; }
  :global(.tiptap ul),
  :global(.tiptap ol) { margin: 0.75rem 0; padding-left: 1.625rem; }
  :global(.tiptap ul) { list-style: disc; }
  :global(.tiptap ol) { list-style: decimal; }
  :global(.tiptap li) { padding-left: 0.25rem; }
  :global(.tiptap li + li) { margin-top: 0.25rem; }
  :global(.tiptap li > p) { margin: 0; }
  :global(.tiptap li ul),
  :global(.tiptap li ol) { margin: 0.25rem 0; }
  :global(.tiptap blockquote) {
    margin: 1rem 0;
    border-left: 2px solid color-mix(in oklab, var(--foreground) 28%, transparent);
    border-radius: 0 var(--radius) var(--radius) 0;
    background: color-mix(in oklab, var(--muted) 65%, transparent);
    color: var(--muted-foreground);
    padding: 0.625rem 1rem;
  }
  :global(.tiptap blockquote p) { margin: 0; }
  :global(.tiptap code) {
    border: 1px solid color-mix(in oklab, var(--border) 72%, transparent);
    border-radius: 0.3rem;
    background: color-mix(in oklab, var(--muted) 78%, transparent);
    padding: 0.1rem 0.325rem;
    color: color-mix(in oklab, var(--foreground) 88%, var(--muted-foreground));
    font-size: 0.875em;
  }
  :global(.tiptap pre) {
    overflow-x: auto;
    margin: 1rem 0;
    border: 1px solid color-mix(in oklab, var(--border) 70%, transparent);
    border-radius: calc(var(--radius) * 1.15);
    background: color-mix(in oklab, var(--muted) 70%, var(--background));
    padding: 1rem 1.125rem;
    color: var(--foreground);
    line-height: 1.65;
  }
  :global(.tiptap pre code) { border: 0; background: transparent; padding: 0; color: inherit; }
  :global(.code-block-shell) { position: relative; margin: 1rem 0; }
  :global(.code-block-shell pre) { margin: 0; padding-top: 2.5rem; }
  :global(.code-block-header) { position: absolute; z-index: 1; top: 0.45rem; right: 0.55rem; }
  :global(.code-block-language) { border: 0; border-radius: 0.35rem; background: color-mix(in oklab, var(--background) 82%, transparent); padding: 0.2rem 1.4rem 0.2rem 0.45rem; color: var(--muted-foreground); font-size: 0.7rem; outline: none; }
  :global(.slash-menu) { max-height: min(22rem, 60vh); width: 19rem; overflow-y: auto; border: 1px solid var(--border); border-radius: calc(var(--radius) * 1.15); background: var(--popover); padding: 0.35rem; color: var(--popover-foreground); box-shadow: 0 12px 32px color-mix(in oklab, black 18%, transparent); }
  :global(.slash-menu button) { display: flex; width: 100%; align-items: center; gap: 0.65rem; border-radius: var(--radius); padding: 0.5rem 0.6rem; }
  :global(.slash-menu button.active) { background: var(--accent); color: var(--accent-foreground); }
  :global(.slash-menu-icon) { display: grid; width: 1.75rem; height: 1.75rem; flex: none; place-items: center; border: 1px solid var(--border); border-radius: 0.4rem; background: var(--background); }
  :global(.tiptap a) { color: color-mix(in oklab, var(--foreground) 82%, var(--ring)); cursor: text; text-decoration: underline; text-decoration-color: color-mix(in oklab, currentColor 45%, transparent); text-underline-offset: 3px; }
  :global(.tiptap p.is-editor-empty:first-child::before) { color: color-mix(in oklab, var(--muted-foreground) 78%, transparent); content: attr(data-placeholder); float: left; height: 0; pointer-events: none; }
  :global(.read-only .tiptap) { min-height: auto; padding-top: 1.5rem; padding-bottom: 1.5rem; }

  @media (max-width: 640px) {
    :global(.tiptap) { padding: 1.25rem 1.125rem 1.75rem; }
  }
</style>
