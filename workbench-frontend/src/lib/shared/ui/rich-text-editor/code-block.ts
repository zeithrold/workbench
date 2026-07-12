import type { NodeViewRenderer } from '@tiptap/core'
import CodeBlockLowlight from '@tiptap/extension-code-block-lowlight'
import { CODE_LANGUAGES, lowlight } from './code-languages.js'

export const WorkbenchCodeBlock = CodeBlockLowlight.extend({
  addNodeView(): NodeViewRenderer {
    return ({ editor, getPos, node }) => {
      const dom = document.createElement('div')
      dom.className = 'code-block-shell'
      const header = document.createElement('div')
      header.className = 'code-block-header'
      header.contentEditable = 'false'
      const languageSelect = document.createElement('select')
      languageSelect.className = 'code-block-language-select'
      languageSelect.setAttribute('aria-label', 'Code language')
      for (const [value, label] of CODE_LANGUAGES) {
        languageSelect.add(new Option(label, value))
      }
      languageSelect.value = node.attrs.language ?? 'plaintext'
      languageSelect.addEventListener('change', () => {
        const pos = getPos()
        if (typeof pos !== 'number')
          return
        editor.view.dispatch(
          editor.state.tr.setNodeMarkup(pos, undefined, { ...node.attrs, language: languageSelect.value }),
        )
      })
      header.append(languageSelect)
      const pre = document.createElement('pre')
      const contentDOM = document.createElement('code')
      contentDOM.className = `hljs language-${node.attrs.language ?? 'plaintext'}`
      pre.append(contentDOM)
      dom.append(header, pre)

      return {
        dom,
        contentDOM,
        update(updatedNode) {
          if (updatedNode.type !== node.type)
            return false
          node = updatedNode
          const language = updatedNode.attrs.language ?? 'plaintext'
          languageSelect.value = language
          contentDOM.className = `hljs language-${language}`
          return true
        },
      }
    }
  },
}).configure({
  lowlight,
  defaultLanguage: 'plaintext',
  enableTabIndentation: true,
  tabSize: 2,
})
