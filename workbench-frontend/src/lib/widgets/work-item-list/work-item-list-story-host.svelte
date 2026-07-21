<script lang='ts'>
  import type { WorkItemDisplayField } from '$lib/entities/work-item/index.js'
  import { QueryClient, QueryClientProvider } from '@tanstack/svelte-query'
  import { onDestroy, untrack } from 'svelte'
  import ProjectWorkItemList from './project-work-item-list.svelte'
  import { DEFAULT_WORK_ITEM_DISPLAY_FIELDS } from './work-item-list-config.js'

  interface Props {
    initialDisplayFields?: WorkItemDisplayField[]
  }

  const { initialDisplayFields = DEFAULT_WORK_ITEM_DISPLAY_FIELDS }: Props = $props()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  let displayFields = $state<WorkItemDisplayField[]>(untrack(() => initialDisplayFields))
  let selectedIds = $state<string[]>([])
  onDestroy(() => queryClient.clear())
</script>

<QueryClientProvider client={queryClient}>
  <div class='w-[min(1200px,calc(100vw-2rem))] p-4'>
    <ProjectWorkItemList
      projectId='project-story'
      {displayFields}
      onDisplayFieldsChange={fields => displayFields = fields}
      {selectedIds}
      onSelectionChange={ids => selectedIds = ids}
      onRowOpen={() => undefined}
    />
  </div>
</QueryClientProvider>
