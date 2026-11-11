const test = require('node:test')
const assert = require('node:assert/strict')
const ts = require('typescript')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')
const source = fs.readFileSync(path.join(__dirname, '../src/utils/knowledge.ts'), 'utf8')
const exportsObject = {}
vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText, { exports: exportsObject })
const { normalizeGraph, filterGraph, validateKnowledgeFile, escapeGraphText } = exportsObject
const graph = { nodes: [{ id: 'a', label: '球头', type: '物料' }, { id: 'b', label: '胶合', type: '工序' }, { id: 'c', label: '包装', type: '工序' }], edges: [{ source: 'a', target: 'b', description: '加工' }, { source: 'b', target: 'c', description: '下一工序' }], truncated: true }

test('normalization removes duplicate nodes and dangling links while retaining truncation', () => {
  const result = normalizeGraph({ ...graph, nodes: [...graph.nodes, graph.nodes[0]], edges: [...graph.edges, { source: 'a', target: 'missing' }] })
  assert.equal(result.nodes.length, 3)
  assert.equal(result.edges.length, 2)
  assert.equal(result.truncated, true)
})
test('search preserves immediate neighbors but excludes unrelated nodes', () => {
  const result = filterGraph(graph, '球头')
  assert.equal(result.nodes.length, 2)
  assert.equal(result.edges.length, 1)
  assert(!result.nodes.some(node => node.id === 'c'))
})
test('focus retains just one neighborhood and supports subsequent type filtering', () => {
  const result = filterGraph(graph, '', '', 'a')
  assert.equal(result.nodes.length, 2)
  assert.equal(result.edges.length, 1)
  assert.equal(filterGraph(graph, '不存在').nodes.length, 0)
})
test('unclassified nodes can be selected consistently', () => {
  const result = normalizeGraph({ nodes: [{ id: 'a', label: '设备', type: '' }], edges: [], truncated: false })
  assert.equal(filterGraph(result, '', '未分类').nodes.length, 1)
})
test('supported text files enforce size and extension boundaries', () => {
  assert.equal(validateKnowledgeFile('工艺.MD', 20 * 1024 * 1024), '')
  assert(validateKnowledgeFile('empty.txt', 0))
  assert(validateKnowledgeFile('large.csv', 20 * 1024 * 1024 + 1))
  assert(validateKnowledgeFile('binary.exe', 10))
})
test('untrusted graph names are escaped before inclusion in tooltips', () => {
  assert.equal(escapeGraphText('<script>"run"</script>'), '&lt;script&gt;&quot;run&quot;&lt;/script&gt;')
})
