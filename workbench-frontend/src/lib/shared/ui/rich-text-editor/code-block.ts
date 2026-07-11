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
      const select = document.createElement('select')
      select.className = 'code-block-language'
      select.setAttribute('aria-label', 'Code language')
      for (const [value, label] of CODE_LANGUAGES) {
        const option = document.createElement('option')
        option.value = value
        option.textContent = label
        select.append(option)
      }
      select.value = node.attrs.language ?? 'plaintext'
      header.append(select)
      const pre = document.createElement('pre')
      const contentDOM = document.createElement('code')
      pre.append(contentDOM)
      dom.append(header, pre)

      const changeLanguage = () => {
        const pos = getPos()
        if (typeof pos !== 'number')
          return
        editor.view.dispatch(
          editor.state.tr.setNodeMarkup(pos, undefined, { ...node.attrs, language: select.value }),
        )
      }
      select.addEventListener('change', changeLanguage)

      return {
        dom,
        contentDOM,
        update(updatedNode) {
          if (updatedNode.type !== node.type)
            return false
          node = updatedNode
          select.value = updatedNode.attrs.language ?? 'plaintext'
          contentDOM.className = `language-${select.value}`
          return true
        },
        destroy() {
          select.removeEventListener('change', changeLanguage)
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
