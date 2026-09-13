<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import {
  searchDefects,
  getDefectTypeStats,
  getDefectSeverityStats,
  confirmDefect
} from '@/api/qc'

// 搜索条件
const searchForm = reactive({
  productBatch: '',
  productionLine: ''
})
const loading = ref(false)

// 图表 DOM 与实例
const typeChartRef = ref(null)
const severityChartRef = ref(null)
let typeChart = null
let severityChart = null

// 缺陷列表
const defectList = ref([])

//  严重程度映射
const severityMap = {
  LOW:      'info',
  MEDIUM:   'warning',
  HIGH:     'danger',
  CRITICAL: 'danger'
}
const severityTextMap = {
  LOW: '轻微', MEDIUM: '一般', HIGH: '严重', CRITICAL: '致命'
}

//  缺陷类型饼图
const initTypeChart = (data) => {
  if (!typeChartRef.value) return
  typeChart = echarts.init(typeChartRef.value)
  typeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}个 ({d}%)' },
        legend: [
          {
            orient: 'vertical',
            left: '5%',
            top: 'middle',
            textStyle: { color: '#a0a0a0', fontSize: 11 },
            itemWidth: 12,
            itemHeight: 12,
            itemGap: 6,
            data: data.slice(0, Math.ceil(data.length / 2)).map(item => item.defect_type)
          },
          {
            orient: 'vertical',
            right: '5%',
            top: 'middle',
            textStyle: { color: '#a0a0a0', fontSize: 11 },
            itemWidth: 12,
            itemHeight: 12,
            itemGap: 6,
            data: data.slice(Math.ceil(data.length / 2)).map(item => item.defect_type)
          }
        ],
    series: [{
      name: '缺陷类型',
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: true,
      label: { show: true, formatter: '{b}\n{d}%', color: '#e0e0e0' },
      data: data.map(item => ({
        value: item.defectCount || item.value || 0,
        name:  item.defect_type || item.name || '未知'
      })),
      color: ['#4a9eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9c27b0']
    }]
  })
}

//  严重程度饼图
const initSeverityChart = (data) => {
  if (!severityChartRef.value) return
  severityChart = echarts.init(severityChartRef.value)
  severityChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}个 ({d}%)' },
    legend: { bottom: '5%', left: 'center', textStyle: { color: '#a0a0a0' } },
    series: [{
      name: '严重程度',
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: true,
      label: { show: true, formatter: '{b}\n{d}%', color: '#e0e0e0' },
      data: data.map(item => ({
        value: item.count    || item.value || 0,
        name:  item.severity || item.name  || '未知'
      })),
      color: ['#f56c6c', '#e6a23c', '#909399']
    }]
  })
}

// 窗口自适应
const handleResize = () => {
  typeChart && typeChart.resize()
  severityChart && severityChart.resize()
}

// 统计饼图
const loadStats = async () => {
  try {
    const [typeRes, severityRes] = await Promise.all([
      getDefectTypeStats(),
      getDefectSeverityStats()
    ])
    initTypeChart(typeRes.data || [])
    initSeverityChart(severityRes.data || [])
  } catch (error) {
    console.error('加载缺陷统计失败:', error)
  }
}

// 缺陷列表
const loadDefectList = async () => {
  const batch = (searchForm.productBatch || '').trim()
  const line = (searchForm.productionLine || '').trim()
  if (!batch && !line) {
    defectList.value = []
    return
  }
  loading.value = true
  try {
    const res = await searchDefects({ productBatch: batch, productionLine: line })
    defectList.value = Array.isArray(res.data) ? res.data : []
  } catch (error) {
    console.error('加载缺陷列表失败:', error)
    defectList.value = []
  } finally {
    loading.value = false
  }
}

// 查询
const handleSearch = () => {
  loadDefectList()
}

const handleReset = () => {
  searchForm.productBatch = ''
  searchForm.productionLine = ''
  defectList.value = []
}

// 人工确认
const handleConfirm = (row) => {
  if (row.isConfirmed === 1) {
    ElMessage.warning('该缺陷已确认,无需重复操作')
    return
  }
  ElMessageBox.confirm(
      `确认缺陷「${row.defectType || row.description || '未命名'}」吗?`,
      '确认缺陷',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(async () => {
    try {
      await confirmDefect(row.id)
      ElMessage.success('确认成功')
      loadDefectList()
      loadStats()
    } catch (error) {
      console.error('确认缺陷失败:', error)
    }
  }).catch(() => {})
}

// 当前筛选
const filterText = () => {
  const parts = []
  if (searchForm.productBatch) parts.push(`批次 ${searchForm.productBatch}`)
  if (searchForm.productionLine) parts.push(`产线 ${searchForm.productionLine}`)
  return parts.join(' · ')
}

// 生命周期
onMounted(async () => {
  await nextTick()
  await loadStats()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  typeChart && typeChart.dispose()
  severityChart && severityChart.dispose()
})
</script>

<template>
  <div class="defect-page">
    <div class="page-header">
      <h2>缺陷分析</h2>
      <p class="page-desc">分析缺陷类型与严重程度分布</p>
    </div>

    <!-- 搜索栏：批次号 + 生产线 -->
    <div class="search-bar">
      <el-input
          v-model="searchForm.productBatch"
          placeholder="批次号"
          style="width: 200px"
          clearable
          @keyup.enter="handleSearch"
      />
      <el-input
          v-model="searchForm.productionLine"
          placeholder="生产线"
          style="width: 200px"
          clearable
          @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 图表 -->
    <div class="chart-row">
      <div class="chart-card">
        <div class="chart-title">缺陷类型分布</div>
        <div ref="typeChartRef" class="chart-box"></div>
      </div>
      <div class="chart-card">
        <div class="chart-title">严重程度分布</div>
        <div ref="severityChartRef" class="chart-box"></div>
      </div>
    </div>

    <!-- 缺陷列表 -->
    <div class="table-container">
      <div class="table-title">
        <span>缺陷列表</span>
        <span v-if="filterText()" class="table-subtitle">
          当前筛选: {{ filterText() }}
        </span>
        <span v-else class="table-subtip">
           请输入批次号或生产线查询对应缺陷
        </span>
      </div>
      <el-table
          v-loading="loading"
          :data="defectList"
          style="width: 100%"
          class="dark-table"
          stripe
      >
        <el-table-column prop="id" label="缺陷ID" width="100" />
        <el-table-column prop="defectType" label="缺陷类型" width="160" />
        <el-table-column prop="severity" label="严重程度" width="120">
          <template #default="{ row }">
            <el-tag :type="severityMap[row.severity] || 'info'" size="small">
              {{ severityTextMap[row.severity] || row.severity || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="缺陷描述" show-overflow-tooltip />
        <el-table-column label="是否确认" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isConfirmed === 1 ? 'success' : 'info'" size="small">
              {{ row.isConfirmed === 1 ? '已确认' : '未确认' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
                size="small"
                type="primary"
                :disabled="row.isConfirmed === 1"
                @click="handleConfirm(row)"
            >
              人工确认
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.defect-page {
  color: #e0e0e0;
}

/* 页面标题 */
.page-header {
  margin-bottom: 24px;
}
.page-header h2 {
  font-size: 22px;
  font-weight: 600;
  color: #4a9eff;
  margin: 0 0 8px 0;
}
.page-desc {
  font-size: 13px;
  color: #a0a0a0;
  margin: 0;
}

/* 搜索栏 */
.search-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 24px;
}

/* 图表区域 */
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}
.chart-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px;
}
.chart-title {
  font-size: 16px;
  font-weight: 600;
  color: #e0e0e0;
  margin-bottom: 16px;
}
.chart-box {
  width: 100%;
  height: 300px;
}

/* 表格区域 */
.table-container {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px;
}
.table-title {
  font-size: 16px;
  font-weight: 600;
  color: #e0e0e0;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.table-subtitle {
  font-size: 13px;
  font-weight: 400;
  color: #4a9eff;
}
.table-subtip {
  font-size: 13px;
  font-weight: 400;
  color: #a0a0a0;
}

/* 深色主题表格 */
.dark-table {
  background: transparent !important;
  color: #e0e0e0 !important;
}
:deep(.dark-table .el-table__inner-wrapper) {
  background: transparent;
}
:deep(.dark-table th.el-table__cell) {
  background: rgba(255, 255, 255, 0.05) !important;
  color: #a0a0a0 !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08) !important;
}
:deep(.dark-table td.el-table__cell) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.05) !important;
}
:deep(.dark-table tr:hover > td.el-table__cell) {
  background: rgba(255, 255, 255, 0.05) !important;
}
:deep(.dark-table .el-table__row.el-table__row--striped td.el-table__cell) {
  background: rgba(255, 255, 255, 0.02) !important;
}
:deep(.dark-table .el-table__empty-text) {
  color: #a0a0a0;
}

/* 响应式:小屏幕单列显示 */
@media (max-width: 1200px) {
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>