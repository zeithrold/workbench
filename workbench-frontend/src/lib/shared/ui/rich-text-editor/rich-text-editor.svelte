<script module lang='ts'>
  let editorModule: ReturnType<typeof importEditor> | undefined
  const importEditor = () => import('./rich-text-editor-implementation.svelte')
  const loadEditor = () => editorModule ??= importEditor()
</script>

<script lang='ts'>
  import type { RichTextEditorProps } from './types.js'
  import { Skeleton } from '$lib/components/ui/skeleton'
  import { m } from '$lib/paraglide/messages.js'
  import AsyncComponent from '../async/async-component.svelte'

  const props: RichTextEditorProps = $props()
</script>

{#snippet editorSkeleton()}
  <div
    class='overflow-hidden rounded-xl border border-border/70 bg-background'
    role='status'
    aria-label={m.loading_rich_text_editor()}
    data-slot='rich-text-editor-skeleton'
  >
    <div class='flex h-11 items-center gap-2 border-b border-border/60 px-3'>
      {#each [0, 1, 2, 3, 4, 5] as item (item)}
        <Skeleton class='size-7 shrink-0' label={m.loading_editor_control()} />
      {/each}
    </div>
    <div class='grid min-h-56 content-start gap-3 p-7'>
      <Skeleton class='h-4 w-4/5' label={m.loading_editor_content()} />
      <Skeleton class='h-4 w-full' label={m.loading_editor_content()} />
      <Skeleton class='h-4 w-3/5' label={m.loading_editor_content()} />
    </div>
  </div>
{/snippet}

<AsyncComponent loader={loadEditor} componentProps={props} loading={editorSkeleton} loadingLabel={m.loading_rich_text_editor()} />
