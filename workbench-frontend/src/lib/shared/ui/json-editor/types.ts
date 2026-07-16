export interface JsonEditorProps {
  value: string
  schema: Record<string, unknown>
  modelId: string
  readOnly?: boolean
  ariaLabel?: string
  onChange: (value: string) => void
  onValidityChange?: (valid: boolean) => void
  class?: string
}
