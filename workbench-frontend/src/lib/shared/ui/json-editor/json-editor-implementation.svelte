<script lang='ts'>
  import type { JsonEditorProps } from './types.js'
  import { cn } from '$lib/utils.js'
  import * as monaco from 'monaco-editor'
  import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker.js?worker'
  import JsonWorker from 'monaco-editor/esm/vs/language/json/json.worker.js?worker'
  import { onMount } from 'svelte'

  const {
    value,
    schema,
    modelId,
    readOnly = false,
    ariaLabel = 'JSON editor',
    onChange,
    onValidityChange,
    class: className,
  }: JsonEditorProps = $props()

  let container: HTMLDivElement
  let editor = $state.raw<monaco.editor.IStandaloneCodeEditor>()
  let model = $state.raw<monaco.editor.ITextModel>()

  onMount(() => {
    ;(globalThis as typeof globalThis & { MonacoEnvironment: { getWorker: (moduleId: string, label: string) => Worker } }).MonacoEnvironment = {
      getWorker: (_moduleId: string, label: string) => label === 'json' ? new JsonWorker() : new EditorWorker(),
    }
    const uri = monaco.Uri.parse(`inmemory://workbench/${encodeURIComponent(modelId)}.json`)
    model = monaco.editor.getModel(uri) ?? monaco.editor.createModel(value, 'json', uri)
    monaco.json.jsonDefaults.setDiagnosticsOptions({
      validate: true,
      allowComments: false,
      enableSchemaRequest: false,
      schemas: [{ uri: String(schema.$id ?? `${uri}.schema`), fileMatch: [uri.toString()], schema }],
    })
    editor = monaco.editor.create(container, {
      model,
      readOnly,
      automaticLayout: true,
      ariaLabel,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      fontSize: 13,
      lineHeight: 21,
      tabSize: 2,
      insertSpaces: true,
      formatOnPaste: true,
      wordWrap: 'on',
      padding: { top: 16, bottom: 16 },
      theme: document.documentElement.classList.contains('dark') ? 'vs-dark' : 'vs',
    })
    const contentSubscription = model.onDidChangeContent(() => onChange(model!.getValue()))
    const markerSubscription = monaco.editor.onDidChangeMarkers((uris) => {
      if (!uris.some(changed => changed.toString() === uri.toString()))
        return
      onValidityChange?.(monaco.editor.getModelMarkers({ resource: uri }).every(marker => marker.severity < monaco.MarkerSeverity.Error))
    })
    return () => {
      contentSubscription.dispose()
      markerSubscription.dispose()
      editor?.dispose()
      model?.dispose()
    }
  })

  $effect(() => {
    if (editor)
      editor.updateOptions({ readOnly, ariaLabel })
  })

  $effect(() => {
    if (model && model.getValue() !== value)
      model.setValue(value)
  })
</script>

<div bind:this={container} class={cn('min-h-96 overflow-hidden rounded-xl border bg-background', className)} data-slot='json-editor'></div>
