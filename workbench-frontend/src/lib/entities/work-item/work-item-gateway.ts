import type { WorkItemResponse } from '$lib/api/generated/model/workItemResponse.js'
import type { WorkItemSearchRequest } from '$lib/api/generated/model/workItemSearchRequest.js'
import type {
  WorkItemDisplayFieldDefinition,
  WorkItemFieldCapability,
  WorkItemFieldOption,
  WorkItemPatch,
  WorkItemSearchInput,
  WorkItemSearchPage,
  WorkItemTransitionOption,
} from './model.js'
import { getSearchUrl } from '$lib/api/generated/workbench.js'
import { apiFetch } from '$lib/api/http.js'
import { problemFromResponse } from '$lib/api/problem.js'

export const WORK_ITEM_NEXT_CURSOR_HEADER = 'X-Workbench-Next-Cursor'

export interface WorkItemSearchOptions {
  cursor?: string
  signal?: AbortSignal
}

function required<T>(value: T | null | undefined, field: string): T {
  if (value === null || value === undefined)
    throw new TypeError(`Work item response is missing ${field}`)
  return value
}

export function mapWorkItem(response: WorkItemResponse): WorkItemSearchPage['items'][number] {
  const extended = response as WorkItemResponse & {
    issueTypeConfigId?: string
    properties?: Record<string, {
      property?: { id?: string, code?: string, name?: string, dataType?: string, array?: boolean }
      value?: unknown
      displayValue?: unknown
    }>
    fieldCapabilities?: Record<string, Partial<WorkItemFieldCapability>>
  }
  const issueType = required(response.issueType, 'issueType')
  const status = required(response.status, 'status')
  const reporter = required(response.reporter, 'reporter')

  return {
    id: required(response.id, 'id'),
    key: required(response.key, 'key'),
    title: required(response.title, 'title'),
    projectId: required(response.projectId, 'projectId'),
    issueTypeConfigId: required(extended.issueTypeConfigId, 'issueTypeConfigId'),
    issueType: {
      id: required(issueType.id, 'issueType.id'),
      code: required(issueType.code, 'issueType.code'),
      name: required(issueType.name, 'issueType.name'),
      icon: issueType.icon ?? null,
      color: issueType.color ?? null,
    },
    status: {
      id: required(status.id, 'status.id'),
      code: required(status.code, 'status.code'),
      name: required(status.name, 'status.name'),
      group: required(status.group, 'status.group'),
      terminal: required(status.terminal, 'status.terminal'),
      color: status.color ?? null,
    },
    priority: response.priority
      ? {
          id: required(response.priority.id, 'priority.id'),
          code: required(response.priority.code, 'priority.code'),
          name: required(response.priority.name, 'priority.name'),
          color: response.priority.color ?? null,
          icon: response.priority.icon ?? null,
        }
      : null,
    reporter: {
      id: required(reporter.id, 'reporter.id'),
      displayName: required(reporter.displayName, 'reporter.displayName'),
    },
    assignee: response.assignee
      ? {
          id: required(response.assignee.id, 'assignee.id'),
          displayName: required(response.assignee.displayName, 'assignee.displayName'),
        }
      : null,
    sprint: response.sprint
      ? {
          id: required(response.sprint.id, 'sprint.id'),
          name: required(response.sprint.name, 'sprint.name'),
          status: required(response.sprint.status, 'sprint.status'),
        }
      : null,
    properties: Object.fromEntries(Object.entries(extended.properties ?? {}).map(([code, presentation]) => {
      const property = required(presentation.property, `properties.${code}.property`)
      return [code, {
        property: {
          id: required(property.id, `properties.${code}.property.id`),
          code: required(property.code, `properties.${code}.property.code`),
          name: required(property.name, `properties.${code}.property.name`),
          dataType: required(property.dataType, `properties.${code}.property.dataType`),
          array: Boolean(property.array),
        },
        value: presentation.value,
        displayValue: presentation.displayValue,
      }]
    })),
    fieldCapabilities: Object.fromEntries(Object.entries(extended.fieldCapabilities ?? {}).map(([key, capability]) => [key, {
      state: required(capability.state, `fieldCapabilities.${key}.state`),
      reason: capability.reason ?? null,
    }])),
    createdAt: required(response.createdAt, 'createdAt'),
    updatedAt: required(response.updatedAt, 'updatedAt'),
  }
}

async function responseJson<T>(response: Response): Promise<T> {
  if (!response.ok)
    throw await problemFromResponse(response)
  return response.json() as Promise<T>
}

export async function listWorkItemDisplayFields(projectId: string): Promise<WorkItemDisplayFieldDefinition[]> {
  const response = await apiFetch(`/api/projects/${encodeURIComponent(projectId)}/work-items/display-fields`)
  return responseJson<WorkItemDisplayFieldDefinition[]>(response)
}

export async function listWorkItemFieldOptions(
  projectId: string,
  workItemId: string,
  fieldKey: string,
  query?: string,
): Promise<WorkItemFieldOption[]> {
  const params = new URLSearchParams()
  if (query)
    params.set('query', query)
  const suffix = params.size ? `?${params}` : ''
  const response = await apiFetch(`/api/projects/${encodeURIComponent(projectId)}/work-items/${encodeURIComponent(workItemId)}/fields/${encodeURIComponent(fieldKey)}/options${suffix}`)
  return responseJson<WorkItemFieldOption[]>(response)
}

export async function listWorkItemTransitions(projectId: string, workItemId: string): Promise<WorkItemTransitionOption[]> {
  const response = await apiFetch(`/api/projects/${encodeURIComponent(projectId)}/work-items/${encodeURIComponent(workItemId)}/transitions`)
  return responseJson<WorkItemTransitionOption[]>(response)
}

export async function patchWorkItem(
  projectId: string,
  workItemId: string,
  patch: WorkItemPatch,
): Promise<WorkItemSearchPage['items'][number]> {
  const response = await apiFetch(`/api/projects/${encodeURIComponent(projectId)}/work-items/${encodeURIComponent(workItemId)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch),
  })
  return mapWorkItem(await responseJson<WorkItemResponse>(response))
}

export async function transitionWorkItem(
  projectId: string,
  workItemId: string,
  transitionId: string,
): Promise<WorkItemSearchPage['items'][number]> {
  const response = await apiFetch(`/api/projects/${encodeURIComponent(projectId)}/work-items/${encodeURIComponent(workItemId)}/transitions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ transitionId }),
  })
  return mapWorkItem(await responseJson<WorkItemResponse>(response))
}

export async function searchWorkItems(
  input: WorkItemSearchInput,
  options: WorkItemSearchOptions = {},
): Promise<WorkItemSearchPage> {
  const request: WorkItemSearchRequest = {
    query: input.query as WorkItemSearchRequest['query'],
    ...(input.scope === undefined
      ? {}
      : { scope: input.scope as WorkItemSearchRequest['scope'] }),
    ...(input.limit === undefined ? {} : { limit: input.limit }),
    ...(options.cursor === undefined ? {} : { cursor: options.cursor }),
  }
  const response = await apiFetch(getSearchUrl(input.projectId), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    signal: options.signal,
  })

  if (!response.ok)
    throw await problemFromResponse(response)

  const items = await response.json() as WorkItemResponse[]
  const nextCursor = response.headers.get(WORK_ITEM_NEXT_CURSOR_HEADER) || undefined
  return { items: items.map(mapWorkItem), nextCursor }
}
