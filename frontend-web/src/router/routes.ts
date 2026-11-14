import type { RouteRecordRaw } from 'vue-router'
import { BASE_DATA_VIEW_ROLES, ROLES } from '@/constants/production'
import { WAGE_RECORD_VIEW_ROLES, WAGE_VIEW_ROLES } from '@/constants/wage'
import {
  ERP_CRAFT_ROLES,
  ERP_TASK_ROLES,
  INTEGRATION_GROUP_ROLES,
  INTEGRATION_MANAGE_ROLES,
} from '@/constants/integration'
import { DEVICE_VIEW_ROLES } from '@/constants/device'
import { ANDON_VIEW_ROLES } from '@/constants/andon'
import { SCENE_EXECUTION_ROLES, SCENE_TASK_MANAGE_ROLES } from '@/constants/scene'
import { QUALITY_VIEW_ROLES } from '@/constants/quality'
import {
  MAINTENANCE_PLAN_VIEW_ROLES,
  MAINTENANCE_RECORD_READ_WRITE_ROLES,
} from '@/constants/equipment'
import { REPORT_VIEW_ROLES } from '@/constants/report'
import { BARCODE_CONFIG_ROLES } from '@/constants/barcode'

/**
 * 主框架菜单路由（树形，供侧边菜单/面包屑使用）。
 * 注册到 router 时经 flattenMenuRoutes 拍平，保证单层 <router-view> 渲染。
 * meta.roles 与后端 Controller 的 @RequiresRoles 对齐（查询级）。
 */
export const menuRoutes: RouteRecordRaw[] = [
  {
    path: '/knowledge', redirect: '/knowledge/documents', meta: { title: '知识中心', icon: 'Share' },
    children: [
      { path: '/knowledge/documents', name: 'KnowledgeDocuments', component: () => import('@/views/knowledge/KnowledgeDocuments.vue'), meta: { title: '文件管理', icon: 'FolderOpened' } },
      { path: '/knowledge/graph', name: 'KnowledgeGraph', component: () => import('@/views/knowledge/KnowledgeGraph.vue'), meta: { title: '知识图谱', icon: 'Share' } },
    ],
  },
  {
    path: '/dashboard',
    name: 'DashboardView',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { title: '工作台', icon: 'Odometer', affix: true },
  },
  {
    path: '/production',
    redirect: '/production/work-orders',
    meta: { title: '生产管理', icon: 'Operation' },
    children: [
      {
        path: '/production/work-orders',
        name: 'WorkOrderList',
        component: () => import('@/views/production/WorkOrderList.vue'),
        meta: { title: '生产工单' },
      },
      {
        path: '/production/work-orders/:id',
        name: 'WorkOrderDetail',
        component: () => import('@/views/production/WorkOrderDetail.vue'),
        meta: { title: '工单详情', hidden: true, activeMenu: '/production/work-orders' },
      },
      {
        path: '/production/dispatch-orders',
        name: 'DispatchList',
        component: () => import('@/views/production/DispatchList.vue'),
        meta: { title: '派工管理' },
      },
      {
        path: '/production/shortage-board',
        name: 'ShortageBoard',
        component: () => import('@/views/production/ShortageBoard.vue'),
        meta: { title: '欠料看板' },
      },
    ],
  },
  {
    path: '/craft',
    redirect: '/craft/processes',
    // 工艺查询后端不限角色（生产模块要引用），写操作页面内按 CRAFT_WRITE_ROLES 控制
    meta: { title: '工艺管理', icon: 'SetUp' },
    children: [
      {
        path: '/craft/processes',
        name: 'CraftProcessList',
        component: () => import('@/views/craft/CraftProcessList.vue'),
        meta: { title: '工序管理' },
      },
      {
        path: '/craft/routes',
        name: 'CraftRouteList',
        component: () => import('@/views/craft/CraftRouteList.vue'),
        meta: { title: '工艺路线' },
      },
    ],
  },
  {
    path: '/wage',
    redirect: '/wage/rules',
    meta: { title: '计件工资', icon: 'Money', roles: WAGE_VIEW_ROLES },
    children: [
      {
        path: '/wage/rules',
        name: 'PieceRateRuleList',
        component: () => import('@/views/wage/PieceRateRuleList.vue'),
        meta: { title: '计件规则', roles: WAGE_VIEW_ROLES },
      },
      {
        path: '/wage/work-records',
        name: 'WageWorkRecordList',
        component: () => import('@/views/wage/WageWorkRecordList.vue'),
        meta: { title: '计件作业记录', roles: WAGE_RECORD_VIEW_ROLES },
      },
      {
        path: '/wage/settlements',
        name: 'WageSettlementList',
        component: () => import('@/views/wage/WageSettlementList.vue'),
        meta: { title: '工资结算', roles: WAGE_VIEW_ROLES },
      },
    ],
  },
  {
    path: '/basedata',
    redirect: '/basedata/products',
    meta: { title: '基础资料', icon: 'Files', roles: BASE_DATA_VIEW_ROLES },
    children: [
      {
        path: '/basedata/products',
        name: 'ProductList',
        component: () => import('@/views/basedata/ProductList.vue'),
        meta: { title: '产品主档', roles: BASE_DATA_VIEW_ROLES },
      },
      {
        path: '/basedata/materials',
        name: 'MaterialList',
        component: () => import('@/views/basedata/MaterialList.vue'),
        meta: { title: '物料主档', roles: BASE_DATA_VIEW_ROLES },
      },
      {
        path: '/basedata/boms',
        name: 'BomList',
        component: () => import('@/views/basedata/BomList.vue'),
        meta: { title: 'BOM 管理', roles: BASE_DATA_VIEW_ROLES },
      },
      {
        path: '/basedata/workshops',
        name: 'WorkshopList',
        component: () => import('@/views/basedata/WorkshopList.vue'),
        meta: { title: '车间管理', roles: BASE_DATA_VIEW_ROLES },
      },
      {
        path: '/basedata/lines',
        name: 'LineList',
        component: () => import('@/views/basedata/LineList.vue'),
        meta: { title: '产线管理', roles: BASE_DATA_VIEW_ROLES },
      },
    ],
  },
  {
    path: '/rag/chat',
    name: 'RagChatView',
    component: () => import('@/views/rag/RagChatView.vue'),
    meta: { title: '知识库问答', icon: 'ChatDotRound' },
  },
  {
    path: '/system',
    redirect: '/system/users',
    meta: { title: '系统管理', icon: 'Setting', roles: [ROLES.ADMIN] },
    children: [
      {
        path: '/system/users',
        name: 'UserList',
        component: () => import('@/views/system/UserList.vue'),
        meta: { title: '用户管理', roles: [ROLES.ADMIN] },
      },
      {
        path: '/system/roles',
        name: 'RoleList',
        component: () => import('@/views/system/RoleList.vue'),
        meta: { title: '角色管理', roles: [ROLES.ADMIN] },
      },
    ],
  },
  {
    path: '/integration',
    redirect: '/integration/write-logs',
    // 组级角色取并集：CRAFT_ENGINEER 仅可见 ERP 工艺待确认
    meta: { title: '接口管理', icon: 'Connection', roles: INTEGRATION_GROUP_ROLES },
    children: [
      {
        path: '/integration/erp-tasks',
        name: 'ErpTaskSyncList',
        component: () => import('@/views/integration/ErpTaskSyncList.vue'),
        meta: { title: 'ERP 任务同步', roles: ERP_TASK_ROLES },
      },
      {
        path: '/integration/erp-crafts',
        name: 'ErpCraftPendingList',
        component: () => import('@/views/integration/ErpCraftPendingList.vue'),
        meta: { title: 'ERP 工艺待确认', roles: ERP_CRAFT_ROLES },
      },
      {
        path: '/integration/write-logs',
        name: 'WriteLogList',
        component: () => import('@/views/integration/WriteLogList.vue'),
        meta: { title: '外部写入日志', roles: INTEGRATION_MANAGE_ROLES },
      },
      {
        path: '/integration/device-exceptions',
        name: 'DeviceCountExceptionList',
        component: () => import('@/views/integration/DeviceCountExceptionList.vue'),
        meta: { title: '设备计数异常池', roles: INTEGRATION_MANAGE_ROLES },
      },
      {
        path: '/integration/completion-orders',
        name: 'CompletionOrderList',
        component: () => import('@/views/integration/CompletionOrderList.vue'),
        meta: { title: '完工单读取', roles: INTEGRATION_MANAGE_ROLES },
      },
    ],
  },
  {
    path: '/device',
    redirect: '/device/access',
    meta: { title: '设备接入', icon: 'Monitor', roles: DEVICE_VIEW_ROLES },
    children: [
      {
        path: '/device/access',
        name: 'DeviceManagement',
        component: () => import('@/views/device/DeviceManagement.vue'),
        meta: { title: '设备数据接入', roles: DEVICE_VIEW_ROLES },
      },
    ],
  },
  {
    path: '/andon',
    redirect: '/andon/management',
    meta: { title: '安灯管理', icon: 'WarnTriangleFilled', roles: ANDON_VIEW_ROLES },
    children: [
      {
        path: '/andon/management',
        name: 'AndonManagement',
        component: () => import('@/views/andon/AndonManagement.vue'),
        meta: { title: '安灯异常管理', roles: ANDON_VIEW_ROLES },
      },
    ],
  },
  {
    path: '/scene',
    redirect: '/scene/tasks',
    meta: { title: '现场执行', icon: 'Guide', roles: SCENE_EXECUTION_ROLES },
    children: [
      {
        path: '/scene/tasks',
        name: 'SceneTaskList',
        component: () => import('@/views/scene/SceneTaskList.vue'),
        meta: { title: '生产任务', roles: SCENE_TASK_MANAGE_ROLES },
      },
      {
        path: '/scene/dispatches',
        name: 'SceneDispatchList',
        component: () => import('@/views/scene/SceneDispatchList.vue'),
        meta: { title: '派工管理' },
      },
      {
        path: '/scene/reports',
        name: 'SceneWorkReportList',
        component: () => import('@/views/scene/SceneWorkReportList.vue'),
        meta: { title: '报工记录' },
      },
      {
        path: '/scene/repairs',
        name: 'SceneRepairList',
        component: () => import('@/views/scene/SceneRepairList.vue'),
        meta: { title: '返修工单' },
      },
      {
        path: '/scene/product-statuses',
        name: 'SceneProductStatusList',
        component: () => import('@/views/scene/SceneProductStatusList.vue'),
        meta: { title: '产品批次状态' },
      },
      {
        path: '/scene/execution',
        name: 'SceneExecution',
        component: () => import('@/views/scene/SceneExecution.vue'),
        meta: { title: '现场执行操作台', roles: SCENE_EXECUTION_ROLES },
      },
    ],
  },
  {
    path: '/quality',
    redirect: '/quality/inspection-records',
    meta: { title: '质量管理', icon: 'Checked', roles: QUALITY_VIEW_ROLES },
    children: [
      {
        path: '/quality/inspection-categories',
        name: 'QualityInspectionCategoryList',
        component: () => import('@/views/quality/QualityInspectionCategoryList.vue'),
        meta: { title: '检验分类', roles: QUALITY_VIEW_ROLES },
      },
      {
        path: '/quality/inspection-items',
        name: 'QualityInspectionItemList',
        component: () => import('@/views/quality/QualityInspectionItemList.vue'),
        meta: { title: '检验项目', roles: QUALITY_VIEW_ROLES },
      },
      {
        path: '/quality/inspection-plans',
        name: 'QualityInspectionPlanList',
        component: () => import('@/views/quality/QualityInspectionPlanList.vue'),
        meta: { title: '检验方案', roles: QUALITY_VIEW_ROLES },
      },
      {
        path: '/quality/inspection-records',
        name: 'QualityInspectionRecordList',
        component: () => import('@/views/quality/QualityInspectionRecordList.vue'),
        meta: { title: '检验单', roles: QUALITY_VIEW_ROLES },
      },
    ],
  },
  {
    path: '/equipment',
    redirect: '/equipment/ledgers',
    meta: { title: '设备管理', icon: 'Cpu' },
    children: [
      {
        path: '/equipment/ledgers',
        name: 'EquipmentLedgerList',
        component: () => import('@/views/equipment/EquipmentLedgerList.vue'),
        meta: { title: '设备台账' },
      },
      {
        path: '/equipment/repair-orders',
        name: 'EquipmentRepairOrderList',
        component: () => import('@/views/equipment/EquipmentRepairOrderList.vue'),
        meta: { title: '报修任务' },
      },
      {
        path: '/equipment/maintenance-plans',
        name: 'EquipmentMaintenancePlanList',
        component: () => import('@/views/equipment/EquipmentMaintenancePlanList.vue'),
        meta: { title: '保养计划', roles: MAINTENANCE_PLAN_VIEW_ROLES },
      },
      {
        path: '/equipment/maintenance-records',
        name: 'EquipmentMaintenanceRecordList',
        component: () => import('@/views/equipment/EquipmentMaintenanceRecordList.vue'),
        meta: { title: '保养记录', roles: MAINTENANCE_RECORD_READ_WRITE_ROLES },
      },
      {
        path: '/equipment/categories',
        name: 'EquipmentCategoryList',
        component: () => import('@/views/equipment/EquipmentCategoryList.vue'),
        meta: { title: '设备类别' },
      },
      {
        path: '/equipment/fault-principles',
        name: 'EquipmentFaultPrincipleList',
        component: () => import('@/views/equipment/EquipmentFaultPrincipleList.vue'),
        meta: { title: '故障原理' },
      },
      {
        path: '/equipment/manufacturers',
        name: 'EquipmentManufacturerList',
        component: () => import('@/views/equipment/EquipmentManufacturerList.vue'),
        meta: { title: '制造商' },
      },
    ],
  },
  {
    path: '/barcode',
    redirect: '/barcode/types',
    meta: { title: '条码管理', icon: 'Ticket', roles: BARCODE_CONFIG_ROLES },
    children: [
      {
        path: '/barcode/types',
        name: 'BarcodeTypeList',
        component: () => import('@/views/barcode/BarcodeTypeList.vue'),
        meta: { title: '条码类型' },
      },
      {
        path: '/barcode/rules',
        name: 'BarcodeRuleList',
        component: () => import('@/views/barcode/BarcodeRuleList.vue'),
        meta: { title: '条码规则' },
      },
      {
        path: '/barcode/templates',
        name: 'BarcodeTemplateList',
        component: () => import('@/views/barcode/BarcodeTemplateList.vue'),
        meta: { title: '条码模板' },
      },
      {
        path: '/barcode/instances',
        name: 'BarcodeInstanceList',
        component: () => import('@/views/barcode/BarcodeInstanceList.vue'),
        meta: { title: '条码实例' },
      },
      {
        path: '/barcode/application-rules',
        name: 'BarcodeApplicationRuleList',
        component: () => import('@/views/barcode/BarcodeApplicationRuleList.vue'),
        meta: { title: '应用规则' },
      },
    ],
  },
  {
    path: '/report',
    redirect: '/report/production-outputs',
    meta: { title: '报表分析', icon: 'TrendCharts', roles: REPORT_VIEW_ROLES },
    children: [
      {
        path: '/report/production-outputs',
        name: 'ProductionOutputReport',
        component: () => import('@/views/report/ProductionOutputReport.vue'),
        meta: { title: '产量报表' },
      },
      {
        path: '/report/workshop-periods',
        name: 'WorkshopPeriodReport',
        component: () => import('@/views/report/WorkshopPeriodReport.vue'),
        meta: { title: '车间时段报表' },
      },
      {
        path: '/report/realtime-production',
        name: 'RealtimeProductionView',
        component: () => import('@/views/report/RealtimeProductionView.vue'),
        meta: { title: '实时生产' },
      },
      {
        path: '/report/defects',
        name: 'DefectReportView',
        component: () => import('@/views/report/DefectReportView.vue'),
        meta: { title: '不良报表' },
      },
      {
        path: '/report/traces',
        name: 'ProductTraceView',
        component: () => import('@/views/report/ProductTraceView.vue'),
        meta: { title: '产品追溯' },
      },
    ],
  },
  {
    path: '/kanban',
    redirect: '/kanban/central',
    meta: { title: '电子看板', icon: 'DataBoard', roles: REPORT_VIEW_ROLES },
    children: [
      {
        path: '/kanban/line',
        name: 'LineKanban',
        component: () => import('@/views/kanban/KanbanBoardView.vue'),
        props: { mode: 'line' },
        meta: { title: '产线看板' },
      },
      {
        path: '/kanban/workshop',
        name: 'WorkshopKanban',
        component: () => import('@/views/kanban/KanbanBoardView.vue'),
        props: { mode: 'workshop' },
        meta: { title: '车间看板' },
      },
      {
        path: '/kanban/central',
        name: 'CentralKanban',
        component: () => import('@/views/kanban/KanbanBoardView.vue'),
        props: { mode: 'central' },
        meta: { title: '中控看板' },
      },
    ],
  },
  {
    path: '/demo',
    redirect: '/demo/table',
    meta: { title: '组件示例', icon: 'Grid' },
    children: [
      {
        path: '/demo/table',
        name: 'TableDemo',
        component: () => import('@/views/demo/TableDemo.vue'),
        meta: { title: '筛选列表页' },
      },
      {
        path: '/demo/form',
        name: 'FormDemo',
        component: () => import('@/views/demo/FormDemo.vue'),
        meta: { title: '主从表单与详情' },
      },
      {
        path: '/demo/business',
        name: 'BusinessDemo',
        component: () => import('@/views/demo/BusinessDemo.vue'),
        meta: { title: '业务通用组件' },
      },
      {
        path: '/demo/charts',
        name: 'ChartsDemo',
        component: () => import('@/views/demo/ChartsDemo.vue'),
        meta: { title: '图表组件' },
      },
    ],
  },
]

/** 拍平菜单树：所有带 component/redirect 的节点注册为布局的直接子路由 */
export function flattenMenuRoutes(routes: RouteRecordRaw[]): RouteRecordRaw[] {
  const result: RouteRecordRaw[] = []
  for (const route of routes) {
    const { children, ...rest } = route
    if (rest.component || rest.redirect) {
      result.push(rest as RouteRecordRaw)
    }
    if (children?.length) {
      result.push(...flattenMenuRoutes(children))
    }
  }
  return result
}

/** 在菜单树中查找 path 的祖先链（面包屑用） */
export function findMenuChain(
  routes: RouteRecordRaw[],
  path: string,
  chain: RouteRecordRaw[] = [],
): RouteRecordRaw[] | null {
  for (const route of routes) {
    const current = [...chain, route]
    if (route.path === path) return current
    if (route.children?.length) {
      const found = findMenuChain(route.children, path, current)
      if (found) return found
    }
  }
  return null
}
