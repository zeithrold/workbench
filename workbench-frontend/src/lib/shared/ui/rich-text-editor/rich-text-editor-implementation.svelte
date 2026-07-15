<script lang='ts'>
  import type { RichTextDocument, RichTextEditorProps } from './types.js'
  import { m } from '$lib/paraglide/messages.js'
  import { cn } from '$lib/utils.js'
  import { Editor } from '@tiptap/core'
  import { BubbleMenu } from '@tiptap/extension-bubble-menu'
  import Placeholder from '@tiptap/extension-placeholder'
  import StarterKit from '@tiptap/starter-kit'
  import { onMount } from 'svelte'
  import { WorkbenchCodeBlock } from './code-block.js'
  import { resolveCommandGroups } from './editor-commands.js'
  import { DEFAULT_RICH_TEXT_EDITOR_PRESET, EDITOR_PRESETS } from './editor-presets.js'
  import RichTextBubbleMenu from './rich-text-bubble-menu.svelte'
  import RichTextToolbar from './rich-text-toolbar.svelte'
  import { SlashCommand } from './slash-command.js'
  import 'highlight.js/styles/github.css'

  const {
    value,
    onChange,
    preset = DEFAULT_RICH_TEXT_EDITOR_PRESET,
    editable = true,
    placeholder = m.start_writing(),
    ariaLabel = m.rich_text_editor(),
    contentWidth = 'full',
    onSubmit,
    submitting = false,
    class: className,
  }: RichTextEditorProps = $props()

  let element: HTMLDivElement
  let bubbleMenuElement = $state.raw<HTMLDivElement>()
  let editor = $state.raw<Editor | undefined>()
  let revision = $state(0)
  const presetConfig = $derived(EDITOR_PRESETS[preset])
  const commandGroups = $derived(resolveCommandGroups(presetConfig.commandGroups))

  function currentDocument(currentEditor: Editor): RichTextDocument {
    return {
      format: 'tiptap',
      schemaVersion: 1,
      content: currentEditor.getJSON(),
    }
  }

  onMount(() => {
    editor = new Editor({
      element,
      content: value.content,
      editable,
      enableContentCheck: true,
      extensions: [
        StarterKit.configure({
          heading: presetConfig.headings ? { levels: [1, 2, 3] as [1, 2, 3] } : false,
          link: { openOnClick: false, defaultProtocol: 'https' },
          codeBlock: false,
          horizontalRule: presetConfig.horizontalRule ? {} : false,
        }),
        ...(presetConfig.codeBlock ? [WorkbenchCodeBlock] : []),
        ...(preset === 'document'
          ? [BubbleMenu.configure({
            element: bubbleMenuElement,
            shouldShow: ({ editor: currentEditor, state }) => !state.selection.empty && !currentEditor.isActive('codeBlock'),
          })]
          : []),
        ...(presetConfig.slashCommands ? [SlashCommand] : []),
        Placeholder.configure({ placeholder }),
      ],
      editorProps: {
        attributes: {
          'role': 'textbox',
          'aria-label': ariaLabel,
          'aria-multiline': 'true',
          'class': 'tiptap',
        },
        handleKeyDown: (_view, event) => {
          if (preset !== 'comment' || event.key !== 'Enter' || (!event.metaKey && !event.ctrlKey))
            return false
          if (!editable || submitting || editor?.isEmpty || !editor || !onSubmit)
            return true
          event.preventDefault()
          onSubmit(currentDocument(editor))
          return true
        },
      },
      onTransaction: () => revision += 1,
      onUpdate: ({ editor: currentEditor }) => onChange(currentDocument(currentEditor)),
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

<!-- Kept internal so the public facade can load the editor on demand. -->
<div
  class={cn(
    'overflow-hidden rounded-xl border border-border/70 bg-background transition-[border-color,box-shadow] duration-150',
    editable && 'focus-within:border-ring/60 focus-within:ring-3 focus-within:ring-ring/15 focus-within:shadow-sm',
    className,
  )}
  data-slot='rich-text-editor'
  data-editable={editable}
  data-content-width={contentWidth}
  data-preset={preset}
>
  {#if editor && editable}
    <RichTextToolbar {editor} {revision} {commandGroups} compact={presetConfig.compact} disabled={submitting} />
    {#if preset === 'comment'}
      <div class='border-b border-border/60 px-3 py-1 text-right text-[11px] text-muted-foreground'>{m.press_submit_shortcut()}</div>
    {/if}
  {/if}
  {#if preset === 'document'}
    <RichTextBubbleMenu bind:ref={bubbleMenuElement} {editor} {revision} />
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
  :global(.tiptap .hljs-keyword),
  :global(.tiptap .hljs-variable) { color: var(--foreground); }
  :global(.code-block-shell) { position: relative; margin: 1rem 0; }
  :global(.code-block-shell pre) { margin: 0; padding-right: 8rem; }
  :global(.code-block-header) { position: absolute; z-index: 1; top: 0.45rem; right: 0.55rem; }
  :global(.code-block-language-select) { height: 1.75rem; border: 0; border-radius: calc(var(--radius) - 2px); background: color-mix(in oklab, var(--background) 85%, transparent); padding: 0 1.5rem 0 0.5rem; color: var(--muted-foreground); font-size: 0.75rem; box-shadow: var(--shadow-xs); backdrop-filter: blur(4px); }
  :global(.code-block-language-select:hover),
  :global(.code-block-language-select:focus-visible) { color: var(--foreground); outline: none; }
  :global(.tiptap a) { color: color-mix(in oklab, var(--foreground) 82%, var(--ring)); cursor: text; text-decoration: underline; text-decoration-color: color-mix(in oklab, currentColor 45%, transparent); text-underline-offset: 3px; }
  :global(.tiptap p.is-editor-empty:first-child::before) { color: color-mix(in oklab, var(--muted-foreground) 78%, transparent); content: attr(data-placeholder); float: left; height: 0; pointer-events: none; }
  :global(.read-only .tiptap) { min-height: auto; padding-top: 1.5rem; padding-bottom: 1.5rem; }
  [data-preset='comment'] :global(.tiptap) { min-height: 4.5rem; max-height: 16rem; overflow-y: auto; padding: 0.75rem 1rem 1rem; line-height: 1.6; }
  [data-preset='comment'] :global(.tiptap p) { margin: 0.375rem 0; }
  [data-preset='comment'] :global(.tiptap blockquote) { margin: 0.625rem 0; }
  [data-preset='comment'] :global(.tiptap ul),
  [data-preset='comment'] :global(.tiptap ol) { margin: 0.5rem 0; }
  [data-preset='comment'] :global(.read-only .tiptap) { padding: 0.75rem 1rem; }

  @media (max-width: 640px) {
    :global(.tiptap) { padding: 1.25rem 1.125rem 1.75rem; }
  }
</style>
