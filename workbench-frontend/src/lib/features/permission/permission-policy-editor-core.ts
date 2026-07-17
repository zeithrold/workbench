import type { PermissionPolicyDocument } from './permission-document.js'

export interface PermissionPolicyDocumentResult {
  document?: PermissionPolicyDocument
  errors: string[]
}

export interface PermissionPolicyEditorContext {
  document: PermissionPolicyDocument
  errors: string[]
  commit: (document: PermissionPolicyDocument) => void
}
