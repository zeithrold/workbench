<script module lang='ts'>
  let editorModule: ReturnType<typeof importEditor> | undefined
  const importEditor = () => import('./json-editor-implementation.svelte')
  const loadEditor = () => editorModule ??= importEditor()
</script>

<script lang='ts'>
  import type { JsonEditorProps } from './types.js'
  import { Skeleton } from '$lib/components/ui/skeleton'
  import { m } from '$lib/paraglide/messages.js'
  import AsyncComponent from '../async/async-component.svelte'

  const props: JsonEditorProps = $props()
</script>

{#snippet loading()}
  <div class='grid min-h-96 gap-3 rounded-xl border p-5' role='status' aria-label={m.json_editor_loading()}>
    <Skeleton class='h-4 w-3/4' label={m.json_editor_loading_content()} />
    <Skeleton class='h-4 w-full' label={m.json_editor_loading_content()} />
    <Skeleton class='h-4 w-2/3' label={m.json_editor_loading_content()} />
  </div>
{/snippet}

<AsyncComponent loader={loadEditor} componentProps={props} {loading} loadingLabel={m.json_editor_loading()} />
