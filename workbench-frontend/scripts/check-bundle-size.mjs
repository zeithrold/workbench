import { readdir, readFile, stat } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

export const DEFAULT_MAX_CHUNK_BYTES = 500 * 1024
const MONACO_WORKER_PATTERN = /^workers\/(?:css|editor|html|json|ts)\.worker-[^.]+\.js$/

export function monacoBundleFiles(chunks, manifest) {
  const files = Object.values(manifest)
    .filter(entry => entry.name === 'json-editor-implementation'
      || entry.name?.startsWith('editor.api')
      || entry.name?.startsWith('monaco.')
      || entry.src?.includes('/monaco-editor/'))
    .map(entry => entry.file.replace(/^_app\/immutable\//, ''))

  return new Set([
    ...files,
    ...chunks.filter(chunk => MONACO_WORKER_PATTERN.test(chunk.file)).map(chunk => chunk.file),
  ])
}

export function oversizedChunks(chunks, maxBytes = DEFAULT_MAX_CHUNK_BYTES, ignoredFiles = new Set()) {
  return chunks.filter(chunk => !ignoredFiles.has(chunk.file) && chunk.bytes > maxBytes)
}

export function summarizeChunks(chunks, limit = 10) {
  return {
    largest: chunks[0] ?? null,
    totalBytes: chunks.reduce((total, chunk) => total + chunk.bytes, 0),
    top: chunks.slice(0, limit),
  }
}

async function javascriptChunks(directory) {
  async function visit(currentDirectory) {
    const entries = await readdir(currentDirectory, { withFileTypes: true })
    const nested = await Promise.all(entries.map(async (entry) => {
      const absolutePath = path.join(currentDirectory, entry.name)
      if (entry.isDirectory())
        return visit(absolutePath)
      if (!entry.isFile() || !entry.name.endsWith('.js'))
        return []
      return [{
        file: path.relative(directory, absolutePath),
        bytes: (await stat(absolutePath)).size,
      }]
    }))
    return nested.flat()
  }

  const chunks = await visit(directory)
  return chunks.sort((left, right) => right.bytes - left.bytes)
}

export async function checkBundleSize({
  directory = '.svelte-kit/output/client/_app/immutable',
  manifestFile = '.svelte-kit/output/client/.vite/manifest.json',
  maxBytes = DEFAULT_MAX_CHUNK_BYTES,
} = {}) {
  const chunks = await javascriptChunks(directory)
  const manifest = JSON.parse(await readFile(manifestFile, 'utf8'))
  const ignoredFiles = monacoBundleFiles(chunks, manifest)
  const oversized = oversizedChunks(chunks, maxBytes, ignoredFiles)
  if (oversized.length === 0)
    return chunks.filter(chunk => !ignoredFiles.has(chunk.file))

  const details = oversized
    .map(chunk => `  - ${chunk.file}: ${(chunk.bytes / 1024).toFixed(2)} KiB`)
    .join('\n')
  throw new Error(`Client bundle chunk limit exceeded (${(maxBytes / 1024).toFixed(0)} KiB):\n${details}`)
}

const isCli = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
if (isCli) {
  const directory = process.argv[2]
  checkBundleSize(directory ? { directory } : undefined)
    .then((chunks) => {
      const { largest, totalBytes, top } = summarizeChunks(chunks)
      const summary = largest
        ? `${largest.file} (${(largest.bytes / 1024).toFixed(2)} KiB)`
        : 'no JavaScript chunks'
      console.log(`Bundle size check passed: largest client chunk is ${summary}.`)
      console.log(`Total JavaScript chunk size: ${(totalBytes / 1024).toFixed(2)} KiB.`)
      if (top.length > 0) {
        console.log('Largest JavaScript chunks:')
        for (const chunk of top)
          console.log(`  - ${chunk.file}: ${(chunk.bytes / 1024).toFixed(2)} KiB`)
      }
    })
    .catch((error) => {
      console.error(error instanceof Error ? error.message : error)
      process.exitCode = 1
    })
}
