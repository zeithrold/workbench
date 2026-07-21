import { delay, http, HttpResponse } from 'msw'

export const workItemFixtures = [
  {
    id: 'wi-1',
    key: 'WB-1',
    title: 'Build a reusable work item list',
    projectId: 'project-story',
    issueType: { id: 'type-task', code: 'task', name: 'Task', icon: null, color: '#64748b' },
    issueTypeConfigId: 'config-task',
    status: { id: 'status-open', code: 'open', name: 'Open', group: 'unstarted', color: '#3b82f6', terminal: false },
    priority: { id: 'priority-high', code: 'high', name: 'High', color: '#ef4444' },
    reporter: { id: 'user-alex', displayName: 'Alex' },
    assignee: { id: 'user-sam', displayName: 'Sam' },
    sprint: { id: 'sprint-1', name: 'Sprint 24', status: 'ACTIVE' },
    properties: {
      effort: {
        property: { id: 'prop-effort', code: 'effort', name: 'Effort', dataType: 'number', array: false },
        value: 5,
        displayValue: 5,
      },
    },
    fieldCapabilities: {
      'status': { state: 'EDITABLE', reason: null },
      'assignee': { state: 'EDITABLE', reason: null },
      'priority': { state: 'EDITABLE', reason: null },
      'sprint': { state: 'EDITABLE', reason: null },
      'property.effort': { state: 'EDITABLE', reason: null },
    },
    createdAt: '2026-07-16T09:00:00Z',
    updatedAt: '2026-07-17T07:30:00Z',
  },
  {
    id: 'wi-2',
    key: 'WB-2',
    title: 'Document cursor pagination behavior',
    projectId: 'project-story',
    issueType: { id: 'type-story', code: 'story', name: 'Story', icon: null, color: null },
    issueTypeConfigId: 'config-story',
    status: { id: 'status-progress', code: 'progress', name: 'In progress', group: 'started', color: null, terminal: false },
    priority: null,
    reporter: { id: 'user-alex', displayName: 'Alex' },
    assignee: null,
    sprint: null,
    properties: {},
    fieldCapabilities: {
      'status': { state: 'READ_ONLY', reason: 'no_available_transition' },
      'assignee': { state: 'READ_ONLY', reason: 'permission_denied' },
      'priority': { state: 'READ_ONLY', reason: 'permission_denied' },
      'sprint': { state: 'UNAVAILABLE', reason: 'field_not_applicable' },
      'property.effort': { state: 'UNAVAILABLE', reason: 'field_not_applicable' },
    },
    createdAt: '2026-07-16T10:00:00Z',
    updatedAt: '2026-07-17T08:00:00Z',
  },
]

export const defaultWorkItemHandler = http.post('/api/projects/:projectId/work-items/search', () =>
  HttpResponse.json(workItemFixtures))

export const emptyWorkItemHandler = http.post('/api/projects/:projectId/work-items/search', () =>
  HttpResponse.json([]))

export const forbiddenWorkItemHandler = http.post('/api/projects/:projectId/work-items/search', () =>
  HttpResponse.json({ title: 'Forbidden', detail: 'You do not have access to this project.' }, { status: 403 }))

export const serverErrorWorkItemHandler = http.post('/api/projects/:projectId/work-items/search', () =>
  HttpResponse.json({ title: 'Server error', detail: 'The search service is unavailable.' }, { status: 500 }))

export const slowWorkItemHandler = http.post('/api/projects/:projectId/work-items/search', async () => {
  await delay(1_500)
  return HttpResponse.json(workItemFixtures)
})

export const nullableWorkItemHandler = http.post('/api/projects/:projectId/work-items/search', () =>
  HttpResponse.json([workItemFixtures[1]]))

export const longTitleWorkItemHandler = http.post('/api/projects/:projectId/work-items/search', () =>
  HttpResponse.json([{
    ...workItemFixtures[0],
    title: 'A deliberately long work item title that verifies truncation, a useful title tooltip, and horizontal scrolling without changing the row model',
  }]))

export const pagedWorkItemHandler = http.post('/api/projects/:projectId/work-items/search', async ({ request }) => {
  const body = await request.json() as { cursor?: string }
  return body.cursor
    ? HttpResponse.json([{ ...workItemFixtures[1], id: 'wi-3', key: 'WB-3', title: 'Second cursor page' }])
    : HttpResponse.json([workItemFixtures[0]], { headers: { 'X-Workbench-Next-Cursor': 'page-2' } })
})

export const displayFieldsHandler = http.get('/api/projects/:projectId/work-items/display-fields', () =>
  HttpResponse.json([
    { key: 'key', name: 'Key', dataType: 'text', array: false, propertyId: null, validation: {} },
    { key: 'title', name: 'Title', dataType: 'text', array: false, propertyId: null, validation: {} },
    { key: 'property.effort', name: 'Effort', dataType: 'number', array: false, propertyId: 'prop-effort', validation: { minimum: 0 } },
  ]))

export const fieldOptionsHandler = http.get('/api/projects/:projectId/work-items/:workItemId/fields/:fieldKey/options', ({ params }) => {
  const field = String(params.fieldKey)
  if (field === 'assignee')
    return HttpResponse.json([{ id: 'user-jordan', label: 'Jordan', description: 'jordan@example.test', color: null, icon: null, status: null }])
  if (field === 'priority')
    return HttpResponse.json([{ id: 'priority-medium', label: 'Medium', description: 'medium', color: '#f59e0b', icon: null, status: null }])
  return HttpResponse.json([{ id: 'sprint-2', label: 'Sprint 25', description: null, color: null, icon: null, status: 'planned' }])
})

export const transitionsHandler = http.get('/api/projects/:projectId/work-items/:workItemId/transitions', () =>
  HttpResponse.json([{ id: 'transition-start', name: 'Start progress', enabled: true, reason: null, targetStatus: { id: 'status-progress', code: 'progress', name: 'In progress', group: 'in_progress', color: '#8b5cf6', terminal: false } }]))

export const patchHandler = http.patch('/api/projects/:projectId/work-items/:workItemId', async ({ request }) => {
  const patch = await request.json() as Record<string, unknown>
  const current = workItemFixtures[0]
  return HttpResponse.json({
    ...current,
    assignee: patch.assigneeId ? { id: patch.assigneeId, displayName: 'Jordan' } : current.assignee,
    priority: patch.priorityId ? { id: patch.priorityId, code: 'medium', name: 'Medium', color: '#f59e0b', icon: null } : current.priority,
    sprint: patch.clearSprint ? null : current.sprint,
  })
})

export const transitionHandler = http.post('/api/projects/:projectId/work-items/:workItemId/transitions', () =>
  HttpResponse.json({ ...workItemFixtures[0], status: { id: 'status-progress', code: 'progress', name: 'In progress', group: 'in_progress', color: '#8b5cf6', terminal: false } }))

export const patchErrorHandler = http.patch('/api/projects/:projectId/work-items/:workItemId', () =>
  HttpResponse.json({ type: 'about:blank', title: 'Field update rejected', status: 403, detail: 'Assignment blocked by field policy.', code: 'work_item.field.write_denied' }, { status: 403 }))

export const interactiveWorkItemHandlers = [displayFieldsHandler, fieldOptionsHandler, transitionsHandler, patchHandler, transitionHandler]
