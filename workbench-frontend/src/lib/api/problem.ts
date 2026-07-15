export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
}

export class ApiProblemError extends Error {
  constructor(
    readonly status: number,
    readonly problem: ProblemDetail,
  ) {
    super(problem.detail || problem.title || `Request failed (${status})`)
    this.name = 'ApiProblemError'
  }
}

export async function problemFromResponse(response: Response): Promise<ApiProblemError> {
  let problem: ProblemDetail = {}
  try {
    problem = await response.json() as ProblemDetail
  }
  catch {
    // Non-JSON failures still retain their HTTP status and a useful fallback message.
  }
  return new ApiProblemError(response.status, problem)
}
