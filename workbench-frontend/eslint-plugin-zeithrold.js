/** @typedef {any} AstNode */

const visibleNames = new Set([
  'alt',
  'aria-description',
  'aria-label',
  'description',
  'emptytext',
  'errortext',
  'label',
  'loadingtext',
  'message',
  'placeholder',
  'title',
])

export const productionSvelteFiles = ['src/**/*.svelte']

export const ignoredSvelteFiles = [
  'src/**/*.stories.svelte',
  'src/**/*-story-host.svelte',
  'src/**/*.test.svelte',
  'src/**/*.spec.svelte',
]

/** @param {unknown} name */
function normalizedName(name) {
  return typeof name === 'string' ? name.toLowerCase() : null
}

/** @param {AstNode} node */
function propertyName(node) {
  if (!node || node.computed)
    return null

  if (node.key.type === 'Identifier')
    return normalizedName(node.key.name)

  if (node.key.type === 'Literal')
    return normalizedName(node.key.value)

  return null
}

/** @param {AstNode} node */
function callName(node) {
  const callee = node.callee
  if (callee.type === 'Identifier')
    return callee.name

  if (
    callee.type === 'MemberExpression'
    && !callee.computed
    && callee.object.type === 'Identifier'
    && callee.object.name === 'window'
    && callee.property.type === 'Identifier'
  ) {
    return callee.property.name
  }

  return null
}

/** @param {unknown} value */
function isTranslatableText(value) {
  if (typeof value !== 'string' || !/\p{L}/u.test(value))
    return false

  const text = value.trim()
  return !/^(?:[a-z][a-z\d+.-]*:)?\/\//iu.test(text)
    && !(text.includes('@') && !/\s/u.test(text))
}

/** @param {AstNode} node */
function isInsideNonRenderedBlock(node) {
  let current = node.parent
  while (current) {
    if (current.type === 'SvelteScriptElement' || current.type === 'SvelteStyleElement')
      return true
    current = current.parent
  }
  return false
}

/**
 * @param {AstNode} node
 * @param {AstNode} root
 * @param {boolean} [allowConcatenation]
 */
function expressionContributes(node, root, allowConcatenation = false) {
  let current = node

  while (current !== root) {
    const parent = current.parent
    if (!parent)
      return false

    if (parent.type === 'ConditionalExpression') {
      if (parent.test === current)
        return false
    }
    else if (parent.type === 'LogicalExpression') {
      if (parent.left === current)
        return false
    }
    else if (parent.type === 'TemplateLiteral') {
      // Both quasis and interpolated expressions contribute to the rendered string.
    }
    else if (allowConcatenation && parent.type === 'BinaryExpression' && parent.operator === '+') {
      // String concatenation contributes to a browser prompt.
    }
    else if (
      parent.type === 'TSAsExpression'
      || parent.type === 'TSNonNullExpression'
      || parent.type === 'TSSatisfiesExpression'
      || parent.type === 'TypeCastExpression'
      || parent.type === 'ChainExpression'
    ) {
      // Transparent expression wrappers.
    }
    else {
      return false
    }

    current = parent
  }

  return true
}

/** @param {AstNode} node */
function visibleAttributeExpression(node) {
  let current = node

  while (current.parent) {
    const parent = current.parent
    if (parent.type === 'SvelteMustacheTag') {
      const attribute = parent.parent
      if (attribute?.type !== 'SvelteAttribute')
        return expressionContributes(node, parent.expression)

      const name = normalizedName(attribute.key.name)
      return name !== null
        && visibleNames.has(name)
        && expressionContributes(node, parent.expression)
    }
    current = parent
  }

  return false
}

/** @param {AstNode} node */
function visibleObjectProperty(node) {
  let current = node

  while (current.parent) {
    const parent = current.parent
    if (parent.type === 'Property') {
      const name = propertyName(parent)
      return name !== null
        && visibleNames.has(name)
        && expressionContributes(node, parent.value)
    }
    if (parent.type === 'Program' || parent.type === 'SvelteScriptElement')
      return false
    current = parent
  }

  return false
}

/** @param {AstNode} node */
function visibleBrowserPrompt(node) {
  let current = node

  while (current.parent) {
    const parent = current.parent
    if (parent.type === 'CallExpression') {
      const name = callName(parent)
      if ((name === 'alert' || name === 'confirm') && parent.arguments[0])
        return expressionContributes(node, parent.arguments[0], true)
    }
    if (parent.type === 'Program' || parent.type === 'SvelteScriptElement')
      return false
    current = parent
  }

  return false
}

/** @param {AstNode} node */
function literalValue(node) {
  if (node.type === 'TemplateElement')
    return node.value.cooked ?? node.value.raw
  return node.value
}

export const noUntranslatedLiteral = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'warn about user-visible Svelte literals that bypass Paraglide',
    },
    schema: [],
    messages: {
      untranslated: 'User-visible literal "{{text}}" is not covered by i18n. Use a Paraglide m.*() message or add a reasoned local ESLint suppression.',
    },
  },
  /** @param {import('eslint').Rule.RuleContext} context */
  create(context) {
    /** @type {Set<string>} */
    const reportedRanges = new Set()

    /**
     * @param {AstNode} node
     * @param {unknown} value
     */
    function report(node, value) {
      if (!isTranslatableText(value))
        return

      const rangeKey = node.range?.join(':') ?? null
      if (rangeKey && reportedRanges.has(rangeKey))
        return
      if (rangeKey)
        reportedRanges.add(rangeKey)

      const text = /** @type {string} */ (value).trim().replace(/\s+/g, ' ')
      context.report({
        node,
        messageId: 'untranslated',
        data: { text: text.length > 48 ? `${text.slice(0, 47)}…` : text },
      })
    }

    /** @param {AstNode} node */
    function checkExpressionLiteral(node) {
      if (
        visibleBrowserPrompt(node)
        || visibleAttributeExpression(node)
        || visibleObjectProperty(node)
      ) {
        report(node, literalValue(node))
      }
    }

    return {
      /** @param {AstNode} node */
      Literal(node) {
        if (typeof node.value === 'string')
          checkExpressionLiteral(node)
      },
      /** @param {AstNode} node */
      SvelteAttribute(node) {
        const name = normalizedName(node.key.name)
        if (name === null || !visibleNames.has(name))
          return

        for (const value of node.value) {
          if (value.type === 'SvelteLiteral')
            report(value, value.value)
        }
      },
      /** @param {AstNode} node */
      SvelteText(node) {
        if (!isInsideNonRenderedBlock(node))
          report(node, node.value)
      },
      TemplateElement: checkExpressionLiteral,
    }
  },
}

export default {
  rules: {
    'no-untranslated-literal': noUntranslatedLiteral,
  },
}
