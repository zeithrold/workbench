<script lang='ts'>
  import type { RichTextDocument } from './types.js'
  import { untrack } from 'svelte'
  import RichTextEditor from './rich-text-editor.svelte'

  const {
    initialValue,
    editable = true,
    contentWidth = 'full',
    placeholder,
    showJson = false,
    onChange = () => {},
  }: {
    initialValue: RichTextDocument
    editable?: boolean
    contentWidth?: 'full' | 'reading'
    placeholder?: string
    showJson?: boolean
    onChange?: (value: RichTextDocument) => void
  } = $props()

  let value = $state(untrack(() => initialValue))

  function update(nextValue: RichTextDocument) {
    value = nextValue
    onChange(nextValue)
  }
</script>

<div class='w-[min(52rem,calc(100vw-2rem))] space-y-4'>
  <RichTextEditor {value} onChange={update} {editable} {placeholder} {contentWidth} />
  {#if showJson}
    <details open class='max-h-64 overflow-auto rounded-lg border bg-muted p-4'>
      <summary class='sr-only'>Document JSON</summary>
      <pre data-testid='document-json' class='text-xs'>{JSON.stringify(value, null, 2)}</pre>
    </details>
  {/if}
</div>
