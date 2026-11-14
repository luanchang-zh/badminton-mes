<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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
const types = computed(() => [...new Set(graph.value.nodes.map(node => node.type || '未分类'))])
const visibleGraph = computed(() => filterGraph(graph.value, query.value, type.value, focus.value))
const neighbors = computed(() => selected.value ? graph.value.edges.filter(edge => edge.source === selected.value!.id || edge.target === selected.value!.id) : [])
let chart: ReturnType<typeof echarts.init> | undefined
let observer: ResizeObserver | undefined
let requestId = 0
function nodeName(id: string) { return graph.value.nodes.find(node => node.id === id)?.label || id }
function color(node: KnowledgeNode) { return colors[Math.max(0, types.value.indexOf(node.type || '未分类')) % colors.length] }
async function reload() {
  const version = ++requestId
  loading.value = true; error.value = ''; selected.value = null; selectedEdge.value = null
  try {
    const [result, allLabels] = await Promise.all([loadGraph(label.value, depth.value), graphLabels()])
    if (version !== requestId) return
    graph.value = normalizeGraph(result); labels.value = allLabels || []; focus.value = ''
  } catch (e) {
    if (version !== requestId) return
    graph.value = { nodes: [], edges: [], truncated: false }
    error.value = e instanceof Error ? ('userTip' in e && e.userTip ? String(e.userTip) : e.message) : '图谱暂不可用，请稍后刷新'
  } finally { if (version === requestId) loading.value = false }
}
function draw() {
  if (!chart) return
  const view = visibleGraph.value
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
  chart.setOption({ tooltip: { confine: true, formatter: (event: unknown) => { const item = event as { dataType: string; name: string; data: { source: string; target: string } }; return item.dataType === 'edge' ? escapeGraphText(nodeName(item.data.source)) + ' → ' + escapeGraphText(nodeName(item.data.target)) + '<br>' + escapeGraphText(item.name) : escapeGraphText(item.name) } }, animationDuration: 300, series: [series] }, true)
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
watch(visibleGraph, () => { selected.value = null; selectedEdge.value = null; nextTick(draw) })
watch(() => route.path, path => { if (path === '/knowledge/graph') nextTick(() => chart?.resize()) })
onMounted(() => {
  if (canvas.value) {
    chart = echarts.init(canvas.value)
    observer = new ResizeObserver(() => chart?.resize()); observer.observe(canvas.value)
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
    <header class="graph-heading"><div><span class="eyebrow">EXPLORE YOUR KNOWLEDGE</span><h1>知识图谱</h1><p>查看资料中提取的实体与关系，发现生产知识之间的联系。</p></div><router-link to="/knowledge/documents">管理知识资料 →</router-link></header>
    <el-card shadow="never" class="graph-card">
      <div class="graph-controls"><div><label>起始实体</label><el-select v-model="label" filterable allow-create placeholder="选择实体或输入名称" aria-label="选择起始实体"><el-option label="全部实体" value="*" /><el-option v-for="item in labels" :key="item" :label="item" :value="item" /></el-select></div><div><label>展开深度</label><el-select v-model="depth" aria-label="展开深度"><el-option v-for="value in [1,2,3]" :key="value" :value="value" :label="value+' 层关系'" /></el-select></div><el-button type="primary" :loading="loading" @click="reload">加载图谱</el-button><span class="graph-count">{{ visibleGraph.nodes.length }} 个节点 <b>·</b> {{ visibleGraph.edges.length }} 条关系</span></div>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-alert v-if="graph.truncated" title="子图达到服务端节点上限，仅展示部分关系。请选择具体实体或减小展开深度。" type="warning" show-icon :closable="false" />
      <div class="graph-filters"><el-input v-model="query" clearable placeholder="在当前图谱中搜索节点" aria-label="搜索图谱节点" /><el-select v-model="type" clearable placeholder="全部实体类型" aria-label="筛选实体类型"><el-option v-for="item in types" :key="item" :label="item" :value="item" /></el-select><el-button v-if="focus" @click="focus=''">退出节点聚焦</el-button></div>
      <div v-loading="loading" class="graph-canvas-wrap"><div ref="canvas" class="graph-canvas" role="img" :aria-label="'知识图谱：'+visibleGraph.nodes.length+'个节点，'+visibleGraph.edges.length+'条关系'" /><el-empty v-if="!loading && !visibleGraph.nodes.length" class="graph-empty" :description="error ? '图谱暂时无法读取' : query || type ? '没有匹配的节点' : '暂无实体关系，请上传资料并等待处理完成'" /><div class="graph-view-tools"><el-button size="small" aria-label="放大图谱" @click="zoom(1.25)">＋</el-button><el-button size="small" aria-label="缩小图谱" @click="zoom(0.8)">－</el-button><el-button size="small" @click="reset">重置</el-button><el-button size="small" :disabled="!visibleGraph.nodes.length || loading" @click="exportGraph">导出图片</el-button></div><p class="graph-hint">拖动节点 · 滚轮缩放 · 点击节点或连线查看详情</p></div>
      <div class="graph-legend"><span v-for="(item,index) in types" :key="item"><i :style="{background:colors[index % colors.length]}" />{{ item }}</span><small>仅展示当前登录账号知识库中的真实图谱数据</small></div>
    </el-card>
    <el-drawer :model-value="!!selected || !!selectedEdge" title="图谱详情" size="min(420px, 94vw)" @close="selected=null;selectedEdge=null">
      <template v-if="selected"><el-tag>{{ selected.type || '未分类' }}</el-tag><h2>{{ selected.label }}</h2><p class="graph-id">{{ selected.id }}</p><el-button type="primary" plain @click="focus=selected.id">聚焦此节点</el-button><h4>直接关联 · {{ neighbors.length }}</h4><button v-for="(edge,index) in neighbors" :key="index" class="neighbor" @click="selectedEdge=edge;selected=null">{{ nodeName(edge.source) }} → {{ nodeName(edge.target) }}<small>{{ edge.description || '关联关系' }}</small></button></template>
      <template v-else-if="selectedEdge"><el-tag effect="plain">实体关系</el-tag><h3>{{ nodeName(selectedEdge.source) }}</h3><p class="graph-arrow">↓</p><h3>{{ nodeName(selectedEdge.target) }}</h3><h4>关系描述</h4><p class="edge-description">{{ selectedEdge.description || '上游未提供关系描述' }}</p><p class="graph-id">关系权重：{{ selectedEdge.weight ?? '未提供' }}</p><el-alert title="实体关系由模型从资料中提取，使用前请结合原始资料核对。" type="info" :closable="false" /></template>
    </el-drawer>
    <el-dialog v-model="exportVisible" title="导出知识图谱" width="min(880px, 94vw)"><img v-if="exportUrl" :src="exportUrl" class="graph-export" alt="知识图谱 PNG 预览"><template #footer><el-button @click="exportVisible=false">关闭</el-button><a :href="exportUrl" download="知识图谱.png" class="export-download">下载 PNG 图片</a></template></el-dialog>
  </div>
</template>

<style scoped>
.graph-heading{display:flex;align-items:center;justify-content:space-between;padding:10px 2px;gap:16px}.eyebrow{font-size:10px;color:#6d8aa6;letter-spacing:2px;font-weight:600}.graph-heading h1{color:#213c56;font-size:26px;margin:10px 0}.graph-heading p{font-size:12px;color:#8996a7}.graph-heading a{font-size:12px;color:#4089ce;text-decoration:none}.graph-card{border-radius:10px}.graph-controls{display:flex;gap:14px;flex-wrap:wrap;align-items:flex-end}.graph-controls label{display:block;font-size:11px;color:#8294a8;margin-bottom:8px}.graph-controls .el-select{width:220px}.graph-controls>div:nth-child(2) .el-select{width:130px}.graph-count{margin:auto 0 8px auto;font-size:12px;color:#7c8fa4}.graph-count b{padding:0 8px;color:#bdc8d5}.graph-filters{display:flex;gap:10px;flex-wrap:wrap;margin:20px 0 14px}.graph-filters .el-input{width:240px}.graph-filters .el-select{width:160px}.graph-canvas-wrap{position:relative;border:1px solid #e5edf5;background-color:#f8fbff;background-image:radial-gradient(#c7d8eb80 1px,transparent 1px);background-size:22px 22px;border-radius:10px;overflow:hidden}.graph-canvas{height:560px;width:100%}.graph-empty{position:absolute;inset:0;pointer-events:none}.graph-view-tools{position:absolute;right:14px;top:14px;display:flex;gap:5px}.graph-view-tools .el-button{margin:0}.graph-hint{position:absolute;bottom:8px;left:18px;font-size:11px;color:#8b9bb0;pointer-events:none}.graph-legend{display:flex;gap:16px;align-items:center;flex-wrap:wrap;margin:16px 0 0;font-size:11px;color:#8194a9}.graph-legend i{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:7px}.graph-legend small{margin-left:auto;color:#a0adbc}.graph-id{font-size:11px;color:#96a3b3;overflow-wrap:anywhere}.neighbor{display:block;text-align:left;background:#f6f9fd;border:1px solid #e7eef6;width:100%;border-radius:7px;padding:12px;margin:10px 0;color:#526b85;font-size:12px;cursor:pointer;overflow-wrap:anywhere}.neighbor small{display:block;color:#8a9bad;margin-top:8px;line-height:1.8}.edge-description{white-space:pre-wrap;line-height:1.9;color:#526b85;overflow-wrap:anywhere}.graph-arrow{color:#8aaccf;font-size:24px}.graph-export{display:block;width:100%;border:1px solid #edf1f6}.export-download{display:inline-block;padding:9px 15px;background:#409eff;color:white;text-decoration:none;border-radius:5px;margin-left:12px;font-size:13px}.graph-card :deep(.el-alert){margin-top:16px}@media(max-width:700px){.graph-heading{align-items:flex-start;flex-direction:column}.graph-count{width:100%;margin:0}.graph-controls>div:first-child{flex:1}.graph-controls>div:first-child .el-select{width:100%}.graph-canvas{height:480px}.graph-filters .el-input{flex:1;min-width:160px}.graph-legend small{width:100%;margin-left:0}}
</style>
