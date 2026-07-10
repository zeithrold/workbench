import { copyFileSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const envFile = join(root, '.env')
const exampleFile = join(root, '.env.example')

if (!existsSync(envFile)) {
  copyFileSync(exampleFile, envFile)
}
