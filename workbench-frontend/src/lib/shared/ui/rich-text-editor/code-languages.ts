import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import kotlin from 'highlight.js/lib/languages/kotlin'
import markdown from 'highlight.js/lib/languages/markdown'
import plaintext from 'highlight.js/lib/languages/plaintext'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import { createLowlight } from 'lowlight'

export const CODE_LANGUAGES = [
  ['plaintext', 'Plain Text'],
  ['kotlin', 'Kotlin'],
  ['java', 'Java'],
  ['typescript', 'TypeScript'],
  ['javascript', 'JavaScript'],
  ['json', 'JSON'],
  ['sql', 'SQL'],
  ['bash', 'Bash'],
  ['yaml', 'YAML'],
  ['xml', 'XML / HTML'],
  ['css', 'CSS'],
  ['markdown', 'Markdown'],
] as const

export const lowlight = createLowlight({
  bash,
  css,
  html: xml,
  java,
  javascript,
  json,
  kotlin,
  markdown,
  plaintext,
  sql,
  typescript,
  xml,
  yaml,
})
