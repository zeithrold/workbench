<script lang='ts'>
  import type { RichTextDocument, RichTextEditorPreset } from './types.js'
  import { untrack } from 'svelte'
  import RichTextEditor from './rich-text-editor.svelte'

  const {
    initialValue,
    editable = true,
    preset = 'document',
    contentWidth = 'full',
    placeholder,
    showJson = false,
    submitting = false,
    dark = false,
    onChange = () => {},
    onSubmit,
  }: {
    initialValue: RichTextDocument
    editable?: boolean
    preset?: RichTextEditorPreset
    contentWidth?: 'full' | 'reading'
    placeholder?: string
    showJson?: boolean
    submitting?: boolean
    dark?: boolean
    onChange?: (value: RichTextDocument) => void
    onSubmit?: (value: RichTextDocument) => void
  } = $props()

  let value = $state(untrack(() => initialValue))

  function update(nextValue: RichTextDocument) {
    value = nextValue
    onChange(nextValue)
  }
</script>

<div class:dark class='w-[min(52rem,calc(100vw-2rem))] space-y-4 rounded-xl bg-background text-foreground'>
  <RichTextEditor {value} onChange={update} {editable} {preset} {placeholder} {contentWidth} {onSubmit} {submitting} />
  {#if showJson}
    <details open class='max-h-64 overflow-auto rounded-lg border bg-muted p-4'>
      <summary class='sr-only'>Document JSON</summary>
      <pre data-testid='document-json' class='text-xs'>{JSON.stringify(value, null, 2)}</pre>
    </details>
  {/if}
</div>
