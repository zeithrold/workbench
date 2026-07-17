import { ApiProblemError } from '$lib/api/problem.js'
import { fireEvent, render, screen, waitFor } from '@testing-library/svelte'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProjectCreateForm from './project-create-form.svelte'

const { create } = vi.hoisted(() => ({ create: vi.fn() }))

vi.mock('$lib/entities/project/project-gateway.js', () => ({
  projectGateway: { create },
}))

describe('projectCreateForm', () => {
  beforeEach(() => vi.clearAllMocks())

  it('normalizes the identifier and returns the created project', async () => {
    const project = { id: 'prj_1', identifier: 'CORE', name: 'Core', status: 'active' }
    create.mockResolvedValue(project)
    const onCreated = vi.fn()
    render(ProjectCreateForm, { onCreated })

    await fireEvent.input(screen.getByLabelText('Project name'), { target: { value: 'Core' } })
    await fireEvent.input(screen.getByLabelText('Identifier'), { target: { value: 'core' } })
    await fireEvent.click(screen.getByRole('button', { name: 'Create project' }))

    await waitFor(() => expect(create).toHaveBeenCalledWith({
      name: 'Core',
      identifier: 'CORE',
      description: undefined,
    }))
    expect(onCreated).toHaveBeenCalledWith(project)
  })

  it('keeps entered values and shows the server denial when creation is forbidden', async () => {
    create.mockRejectedValue(new ApiProblemError(403, {
      title: 'Permission denied',
      detail: 'You cannot create projects in this tenant.',
    }))
    render(ProjectCreateForm)

    const name = screen.getByLabelText('Project name')
    const identifier = screen.getByLabelText('Identifier')
    await fireEvent.input(name, { target: { value: 'Core' } })
    await fireEvent.input(identifier, { target: { value: 'core' } })
    await fireEvent.click(screen.getByRole('button', { name: 'Create project' }))

    await screen.findByText('You cannot create projects in this tenant.')
    expect((name as HTMLInputElement).value).toBe('Core')
    expect((identifier as HTMLInputElement).value).toBe('core')
  })
})
