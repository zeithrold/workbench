package ink.doa.workbench.agile.workitem

fun fieldMutationPolicy(
  submission: FieldSubmissionPolicy = FieldSubmissionPolicy.INHERIT_BINDING,
  bindingAllowsWrite: Boolean = true,
): FieldMutationPolicy = FieldMutationPolicy(submission, bindingAllowsWrite)

fun permissiveFieldMutationPolicy(): FieldMutationPolicy =
  fieldMutationPolicy(
    submission = FieldSubmissionPolicy.INHERIT_BINDING,
    bindingAllowsWrite = true,
  )

fun transitionOverridePolicy(bindingAllowsWrite: Boolean = false): FieldMutationPolicy =
  fieldMutationPolicy(
    submission = FieldSubmissionPolicy.TRANSITION_OVERRIDE,
    bindingAllowsWrite = bindingAllowsWrite,
  )

fun readOnlyFieldMutationPolicy(bindingAllowsWrite: Boolean = true): FieldMutationPolicy =
  fieldMutationPolicy(
    submission = FieldSubmissionPolicy.READ_ONLY,
    bindingAllowsWrite = bindingAllowsWrite,
  )
