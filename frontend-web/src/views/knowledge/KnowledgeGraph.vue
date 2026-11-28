<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { GraphChart } from 'echarts/charts'
import type { GraphSeriesOption } from 'echarts/charts'
import echarts from '@/utils/echarts'
import { graphLabels, loadGraph } from '@/api/rag'
import { escapeGraphText, filterGraph, normalizeGraph } from '@/utils/knowledge'
import type { KnowledgeGraph, KnowledgeNode, KnowledgeEdge } from '@/utils/knowledge'

defineOptions({ name: 'KnowledgeGraph' })
echarts.use([GraphChart])
const route = useRoute()
const graph = ref<KnowledgeGraph>({ nodes: [], edges: [], truncated: false })
const labels = ref<string[]>([])
const label = ref('*')
const entitySearch = ref('')
const depth = ref(2)
const query = ref('')
const type = ref('')
const focus = ref('')
const loading = ref(false)
const error = ref('')
const selected = ref<KnowledgeNode | null>(null)
const selectedEdge = ref<KnowledgeEdge | null>(null)
const canvas = ref<HTMLDivElement>()
const exportVisible = ref(false)
const exportUrl = ref('')
const colors = ['#4c8cdb', '#39aa9d', '#a185ce', '#dda65a', '#6fa9be', '#d088a6', '#7ea779']
const entityTerm = computed(() => entitySearch.value.trim())
const matchingEntities = computed(() => {
  const candidates = label.value === '*' || labels.value.includes(label.value) ? labels.value : [label.value, ...labels.value]
  const term = entityTerm.value.toLowerCase()
  return candidates.filter(name => name.toLowerCase().includes(term))
})
const showCustomEntity = computed(() => !!entityTerm.value && entityTerm.value !== '*' && !matchingEntities.value.includes(entityTerm.value))
const types = computed(() => [...new Set(graph.value.nodes.map(node => node.type || '未分类'))])
const visibleGraph = computed(() => filterGraph(graph.value, query.value, type.value, focus.value))
const neighbors = computed(() => selected.value ? graph.value.edges.filter(edge => edge.source === selected.value!.id || edge.target === selected.value!.id) : [])
let chart: ReturnType<typeof echarts.init> | undefined
let observer: ResizeObserver | undefined
let requestId = 0
function nodeName(id: string) { return graph.value.nodes.find(node => node.id === id)?.label || id }
function color(node: KnowledgeNode) { return colors[Math.max(0, types.value.indexOf(node.type || '未分类')) % colors.length] }
function selectEntity(entity: string) {
  if (entity === label.value) return
  label.value = entity
  query.value = ''
  type.value = ''
  void reload()
}
async function reload() {
  const version = ++requestId
  loading.value = true; error.value = ''; selected.value = null; selectedEdge.value = null
  try {
    const [result, allLabels] = await Promise.all([loadGraph(label.value, depth.value), graphLabels()])
    if (version !== requestId) return
    graph.value = normalizeGraph(result)
    labels.value = [...new Set(allLabels || [])].filter(name => name !== '*')
    focus.value = ''
  } catch (e) {
    if (version !== requestId) return
    graph.value = { nodes: [], edges: [], truncated: false }
    error.value = e instanceof Error ? ('userTip' in e && e.userTip ? String(e.userTip) : e.message) : '图谱暂不可用，请稍后刷新'
  } finally { if (version === requestId) loading.value = false }
}
function draw() {
  if (!chart) return
  const view = visibleGraph.value
  if (!view.nodes.length) {
    chart.clear()
    return
  }
  const series: GraphSeriesOption = {
    type: 'graph', layout: 'force', roam: true, draggable: true,
    zoom: Math.min(1, Math.max(0.3, Math.sqrt(18 / Math.max(1, view.nodes.length)))),
    force: { repulsion: 240, edgeLength: [70, 140], gravity: 0.15 },
    edgeSymbol: ['none', 'arrow'], edgeSymbolSize: [0, 6],
    label: { show: view.nodes.length <= 35, position: 'bottom', color: '#63788f', fontSize: 12, formatter: params => { const name = params.name || ''; return name.length > 16 ? name.slice(0,16) + '…' : name } },
    emphasis: { focus: 'adjacency', label: { show: true } },
    lineStyle: { color: '#9db3cc', opacity: 0.6, curveness: 0.08 },
    edgeLabel: { show: false },
    data: view.nodes.map(node => ({ id: node.id, name: node.label, symbolSize: node.id === focus.value ? 40 : 25, itemStyle: { color: color(node), borderColor: '#fff', borderWidth: 3 }, value: node.type })),
    links: view.edges.map((edge, index) => ({ source: edge.source, target: edge.target, name: edge.description, value: edge.weight || 0, id: String(index) })),
  }
  chart.setOption({
    tooltip: {
      confine: true,
      formatter: (event: unknown) => {
        const item = event as { dataType: string; name: string; data: { source: string; target: string; name: string } }
        if (item.dataType !== 'edge') return escapeGraphText(item.name)

        const endpoints = [item.data.source, item.data.target].map(id => escapeGraphText(nodeName(id))).join(' → ')
        const description = escapeGraphText(item.data.name).replace(/\r?\n/g, '<br>')
        return description ? endpoints + '<br>' + description : endpoints
      },
    },
    animationDuration: 300,
    series: [series],
  }, true)
}
function reset() { query.value = ''; type.value = ''; focus.value = ''; selected.value = null; selectedEdge.value = null; nextTick(draw) }
function zoom(factor: number) {
  const option = chart?.getOption().series as { zoom?: number }[] | undefined
  if (chart && option?.[0]) chart.setOption({ series: [{ zoom: Math.max(0.15, Math.min(5, (option[0].zoom || 1) * factor)) }] })
}
function exportGraph() {
  if (!chart) return
  exportUrl.value = chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#f8fbff' })
  exportVisible.value = true
}
function resizeChart() {
  if (canvas.value?.clientWidth && canvas.value.clientHeight) chart?.resize()
}
watch(visibleGraph, () => { selected.value = null; selectedEdge.value = null; nextTick(draw) })
watch(() => route.path, path => { if (path === '/knowledge/graph') nextTick(resizeChart) })
onMounted(() => {
  if (canvas.value) {
    chart = echarts.init(canvas.value)
    observer = new ResizeObserver(resizeChart)
    observer.observe(canvas.value)
    chart.on('click', event => {
      const data = event.data as { id: string }
      if (event.dataType === 'node') { selected.value = graph.value.nodes.find(node => node.id === data.id) || null; selectedEdge.value = null }
      else { selectedEdge.value = visibleGraph.value.edges[Number(data.id)] || null; selected.value = null }
    })
  }
  reload()
})
onUnmounted(() => { requestId++; observer?.disconnect(); chart?.dispose() })
</script>

<template>
  <div class="page-container knowledge-graph">
    <el-card shadow="never" class="graph-card">
      <div class="graph-workspace">
        <aside class="graph-entities" aria-label="起始实体">
          <h2>起始实体</h2>
          <el-input v-model="entitySearch" :prefix-icon="Search" clearable placeholder="搜索实体" aria-label="搜索起始实体" />
          <button type="button" class="entity-option" :aria-pressed="label === '*'" @click="selectEntity('*')">全部实体</button>
          <ul v-if="matchingEntities.length || showCustomEntity" class="entity-list" aria-label="实体列表">
            <li v-for="name in matchingEntities" :key="name">
              <button type="button" class="entity-option" :aria-pressed="label === name" :title="name" @click="selectEntity(name)">{{ name }}</button>
            </li>
            <li v-if="showCustomEntity">
              <button type="button" class="entity-option" :title="entityTerm" @click="selectEntity(entityTerm)">按名称加载「{{ entityTerm }}」</button>
            </li>
          </ul>
          <p v-else class="entity-empty" role="status">{{ loading ? '正在加载实体…' : error ? '实体列表暂不可用' : '暂无实体' }}</p>
        </aside>
        <div class="graph-content">
          <div class="graph-controls">
            <div class="graph-depth">
              <span>展开深度</span>
              <el-select v-model="depth" aria-label="展开深度">
                <el-option v-for="value in [1, 2, 3]" :key="value" :value="value" :label="value + ' 层关系'" />
              </el-select>
            </div>
            <div class="graph-actions">
              <el-button type="primary" :loading="loading" @click="reload">加载图谱</el-button>
              <router-link class="el-button graph-documents-link" to="/knowledge/documents">管理知识资料</router-link>
            </div>
            <span class="graph-count">{{ visibleGraph.nodes.length }} 个节点 · {{ visibleGraph.edges.length }} 条关系</span>
          </div>
          <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
          <el-alert v-if="graph.truncated" title="子图达到服务端节点上限，仅展示部分关系。请选择具体实体或减小展开深度。" type="warning" show-icon :closable="false" />
          <div class="graph-filters">
            <el-input v-model="query" clearable placeholder="在当前图谱中搜索节点" aria-label="搜索图谱节点" />
            <el-select v-model="type" clearable placeholder="全部实体类型" aria-label="筛选实体类型">
              <el-option v-for="item in types" :key="item" :label="item" :value="item" />
            </el-select>
            <el-button v-if="focus" @click="focus = ''">退出节点聚焦</el-button>
          </div>
          <div v-loading="loading" class="graph-canvas-wrap">
            <div ref="canvas" class="graph-canvas" role="img" :aria-label="`知识图谱：${visibleGraph.nodes.length}个节点，${visibleGraph.edges.length}条关系`" />
            <el-empty v-if="!loading && !visibleGraph.nodes.length" class="graph-empty" :description="error ? '图谱暂时无法读取' : query || type || label !== '*' ? '没有匹配的节点' : '暂无实体关系，请上传资料并等待处理完成'" />
            <div class="graph-view-tools">
              <el-button size="small" aria-label="放大图谱" @click="zoom(1.25)">＋</el-button>
              <el-button size="small" aria-label="缩小图谱" @click="zoom(0.8)">－</el-button>
              <el-button size="small" @click="reset">重置</el-button>
              <el-button size="small" :disabled="!visibleGraph.nodes.length || loading" @click="exportGraph">导出图片</el-button>
            </div>
            <p class="graph-hint">拖动节点 · 滚轮缩放 · 点击节点或连线查看详情</p>
          </div>
          <div v-if="types.length" class="graph-legend">
            <span v-for="(item, index) in types" :key="item"><i :style="{ background: colors[index % colors.length] }" />{{ item }}</span>
          </div>
        </div>
      </div>
    </el-card>
    <el-drawer :model-value="!!selected || !!selectedEdge" title="图谱详情" size="min(420px, 94vw)" @close="selected=null;selectedEdge=null">
      <template v-if="selected"><el-tag>{{ selected.type || '未分类' }}</el-tag><h2>{{ selected.label }}</h2><p class="graph-id">{{ selected.id }}</p><el-button type="primary" plain @click="focus=selected.id">聚焦此节点</el-button><h4>直接关联 · {{ neighbors.length }}</h4><button v-for="(edge,index) in neighbors" :key="index" class="neighbor" @click="selectedEdge=edge;selected=null">{{ nodeName(edge.source) }} → {{ nodeName(edge.target) }}<small>{{ edge.description || '关联关系' }}</small></button></template>
      <template v-else-if="selectedEdge"><el-tag effect="plain">实体关系</el-tag><h3>{{ nodeName(selectedEdge.source) }}</h3><p class="graph-arrow">↓</p><h3>{{ nodeName(selectedEdge.target) }}</h3><h4>关系描述</h4><p class="edge-description">{{ selectedEdge.description || '上游未提供关系描述' }}</p><p class="graph-id">关系权重：{{ selectedEdge.weight ?? '未提供' }}</p><el-alert title="实体关系由模型从资料中提取，使用前请结合原始资料核对。" type="info" :closable="false" /></template>
    </el-drawer>
    <el-dialog v-model="exportVisible" title="导出知识图谱" width="min(880px, 94vw)"><img v-if="exportUrl" :src="exportUrl" class="graph-export" alt="知识图谱 PNG 预览"><template #footer><el-button @click="exportVisible=false">关闭</el-button><a :href="exportUrl" download="知识图谱.png" class="export-download">下载 PNG 图片</a></template></el-dialog>
  </div>
</template>

<style scoped>
.neighbor small{white-space:pre-wrap}
.knowledge-graph, .graph-card, .graph-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
}
.knowledge-graph { min-height: 100%; }
.graph-card { flex: 1; container-type: inline-size; border-radius: 4px; }
.graph-card :deep(.el-card__body) { flex: 1; padding: 0; }
.graph-workspace {
  display: grid;
  flex: 1;
  grid-template-columns: 220px minmax(0, 1fr);
}
.graph-entities {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 12px;
  background: #f8fafc;
  border-right: 1px solid #e4e7ed;
}
.graph-entities h2 {
  margin: 4px 4px 2px;
  color: #687381;
  font-size: 14px;
  font-weight: 500;
}
.entity-list {
  flex: 1;
  min-height: 0;
  contain: size;
  overflow-y: auto;
  margin: 0;
  padding: 0;
  list-style: none;
}
.entity-list li + li { margin-top: 4px; }
.entity-option {
  display: block;
  width: 100%;
  padding: 8px 10px;
  overflow: hidden;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #303133;
  cursor: pointer;
  font: inherit;
  line-height: 20px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.entity-option:hover { background: #eef2f7; }
.entity-option[aria-pressed='true'] { background: #e9f2ff; color: #286bb4; }
.entity-option:focus-visible { outline: 2px solid #409eff; outline-offset: -2px; }
.entity-empty { margin: 8px 4px; color: #687381; font-size: 13px; }
.graph-content { display: flex; flex-direction: column; min-width: 0; padding: 16px; }
.graph-content > :not(.graph-canvas-wrap) { flex-shrink: 0; }
.graph-controls, .graph-depth, .graph-actions, .graph-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.graph-depth { color: #687381; font-size: 13px; }
.graph-depth .el-select { width: 112px; }
.graph-controls .graph-documents-link { margin: 0; text-decoration: none; }
.graph-count { margin-left: auto; font-size: 12px; color: #687381; }
.graph-filters { margin: 12px 0; }
.graph-filters .el-input { width: 240px; }
.graph-filters .el-select { width: 160px; }
.graph-content > .el-alert { margin-top: 12px; }
.graph-canvas-wrap {
  position: relative;
  flex: 1;
  min-height: 280px;
  overflow: hidden;
  border: 1px solid #e5edf5;
  border-radius: 10px;
  background: #f8fbff radial-gradient(#c7d8eb80 1px, transparent 1px) 0 0 / 22px 22px;
}
.graph-canvas { position: absolute; inset: 0; }
.graph-empty{position:absolute;inset:0;pointer-events:none}
.graph-view-tools{position:absolute;right:14px;top:14px;display:flex;gap:5px}
.graph-view-tools .el-button{margin:0}
.graph-hint{position:absolute;bottom:8px;left:18px;font-size:11px;color:#8b9bb0;pointer-events:none}
.graph-legend { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 12px; color: #687381; font-size: 11px; }
.graph-legend i { display: inline-block; width: 8px; height: 8px; margin-right: 6px; border-radius: 50%; }
.graph-id{font-size:11px;color:#96a3b3;overflow-wrap:anywhere}
.neighbor{display:block;text-align:left;background:#f6f9fd;border:1px solid #e7eef6;width:100%;border-radius:7px;padding:12px;margin:10px 0;color:#526b85;font-size:12px;cursor:pointer;overflow-wrap:anywhere}
.neighbor small{display:block;color:#8a9bad;margin-top:8px;line-height:1.8}
.edge-description{white-space:pre-wrap;line-height:1.9;color:#526b85;overflow-wrap:anywhere}
.graph-arrow{color:#8aaccf;font-size:24px}
.graph-export{display:block;width:100%;border:1px solid #edf1f6}
.export-download{display:inline-block;padding:9px 15px;background:#409eff;color:white;text-decoration:none;border-radius:5px;margin-left:12px;font-size:13px}
@container (max-width: 640px) {
  .graph-workspace { grid-template-columns: minmax(0, 1fr); }
  .graph-entities {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    border-right: 0;
    border-bottom: 1px solid #e4e7ed;
  }
  .graph-entities h2, .entity-list, .entity-empty { grid-column: 1 / -1; }
  .entity-list { contain: none; max-height: 152px; }
  .graph-content { padding: 12px; }
  .graph-count { margin-left: 0; }
  .graph-view-tools { left: 8px; right: 8px; justify-content: flex-end; flex-wrap: wrap; }
  .graph-filters .el-input { flex: 1 1 160px; }
}
</style>
