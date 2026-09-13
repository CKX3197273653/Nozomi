<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import {
  getTrendStats,
  getProductionLineStats,
  getProductBatchStats
} from '@/api/qc'

//时间
const daysOptions = [
  { value: 7,  label: '近 7 天' },
  { value: 30, label: '近 30 天' },
  { value: 90, label: '近 90 天' }
]
const selectedDays = ref(7)
const loading = ref(false)

//图表DOM
const trendChartRef = ref(null)
const lineChartRef = ref(null)

// 图表实例
let trendChart = null
let lineChart = null

//批次排名
const batchList = ref([])

// 趋势折线图
const initTrendChart = (data) => {
  if (!trendChartRef.value) return
  trendChart = echarts.init(trendChartRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['质检数', '缺陷数', '合格率'], textStyle: { color: '#a0a0a0' },top:'5px' },
    grid: { left: '3%', right: '4%', bottom: '1%', containLabel: true },
    xAxis: {
      type: 'category',
      data: data.map(item => item.date || ''),
      axisLine: { lineStyle: { color: '#a0a0a0' } },
      axisLabel: { color: '#a0a0a0' }
    },
    yAxis: [
      {
        type: 'value',
        name: '数量',
        axisLine: { lineStyle: { color: '#a0a0a0' } },
        axisLabel: { color: '#a0a0a0' },
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } }
      },
      {
        type: 'value',
        name: '合格率(%)',
        min: 0,
        max: 100,
        axisLine: { lineStyle: { color: '#a0a0a0' } },
        axisLabel: { color: '#a0a0a0', formatter: '{value}%' },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '质检数',
        type: 'line',
        smooth: true,
        data: data.map(item => item.totalCount || 0),
        itemStyle: { color: '#4a9eff' },
        areaStyle: { color: 'rgba(74, 158, 255, 0.2)' }
      },
      {
        name: '缺陷数',
        type: 'line',
        smooth: true,
        data: data.map(item => item.defectCount || 0),
        itemStyle: { color: '#e6a23c' },
        areaStyle: { color: 'rgba(230, 162, 60, 0.2)' }
      },
      {
        name: '合格率',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: data.map(item => {
          const total = item.totalCount || 0
          const defect = item.defectCount || 0
          return total > 0 ? (((total - defect) / total) * 100).toFixed(1) : 0
        }),
        itemStyle: { color: '#67c23a' }
      }
    ]
  })
}

//产线统计柱状图
const initLineChart = (data) => {
  if (!lineChartRef.value) return
  lineChart = echarts.init(lineChartRef.value)
  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: data.map(item => item.production_line || ''),
      axisLine: { lineStyle: { color: '#a0a0a0' } },
      axisLabel: { color: '#a0a0a0' }
    },
    yAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: '#a0a0a0' } },
      axisLabel: { color: '#a0a0a0' },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } }
    },
    series: [{
      name: '质检数',
      type: 'bar',
      data: data.map(item => item.totalCount || item.value || 0),
      itemStyle: { color: '#4a9eff', borderRadius: [4, 4, 0, 0] }
    }]
  })
}

//窗口变化重绘
const handleResize = () => {
  trendChart && trendChart.resize()
  lineChart && lineChart.resize()
}

//加载数据
const loadData = async () => {
  loading.value = true
  try {
    const [trendRes, lineRes, batchRes] = await Promise.all([
      getTrendStats(selectedDays.value),
      getProductionLineStats(),
      getProductBatchStats(10)
    ])
    // 趋势折线图
    const trendData = trendRes.data || []
    initTrendChart(trendData)

    // 产线柱状图
    initLineChart(lineRes.data || [])

    // 批次排名表格
    batchList.value = Array.isArray(batchRes.data) ? batchRes.data : []
  } catch (error) {
    console.error('加载趋势数据失败:', error)
  } finally {
    loading.value = false
  }
}

//时间范围切换
const handleDaysChange = () => {
  loadData()
}

//生命周期
onMounted(async () => {
  await nextTick()
  await loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart && trendChart.dispose()
  lineChart && lineChart.dispose()
})
</script>

<template>
  <div class="trend-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>趋势分析</h2>
      <p class="page-desc">按时间、产线、批次多维度分析质检数据趋势</p>
    </div>

    <!-- 时间筛选栏 -->
    <div class="filter-bar">
      <span class="filter-label">时间范围:</span>
      <el-radio-group v-model="selectedDays" @change="handleDaysChange">
        <el-radio-button
            v-for="item in daysOptions"
            :key="item.value"
            :value="item.value"
        >
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 趋势折线图(主图表) -->
    <div class="chart-card main-chart" v-loading="loading">
      <div class="chart-title">
        {{ daysOptions.find(d => d.value === selectedDays)?.label }}质检趋势
      </div>
      <div ref="trendChartRef" class="chart-box main-chart-box"></div>
    </div>

    <!-- 产线统计 + 批次排名 -->
    <div class="content-row">
      <div class="chart-card" v-loading="loading">
        <div class="chart-title">产线质检统计</div>
        <div ref="lineChartRef" class="chart-box"></div>
      </div>

      <div class="table-container" v-loading="loading">
        <div class="table-title">
          <span>产品批次排名 TOP 10</span>
        </div>
        <el-table
            :data="batchList"
            style="width: 100%"
            class="dark-table"
            stripe
        >
          <el-table-column type="index" label="排名" width="70" align="center" />
          <el-table-column prop="product_batch" label="批次号" min-width="140" />
          <el-table-column prop="production_line" label="产线" width="120" />
          <el-table-column prop="totalCount" label="质检数" width="100" align="center" />
          <el-table-column prop="count" label="缺陷数" width="100" align="center" />
          <el-table-column prop="passRate" label="合格率" width="110" align="center">
            <template #default="{ row }">
              <span v-if="row.passRate != null">{{ row.passRate }}%</span>
              <span v-else class="empty-value">-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.trend-page {
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

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.filter-label {
  font-size: 14px;
  color: #e0e0e0;
}

/* 主图表 */
.main-chart {
  margin-bottom: 24px;
}
.main-chart-box {
  height: 350px;
}

/* 内容行:产线 + 批次 */
.content-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* 图表卡片 */
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

/* 表格容器 */
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
}
.empty-value {
  color: #666;
}

/* 响应式:小屏幕单列 */
@media (max-width: 1200px) {
  .content-row {
    grid-template-columns: 1fr;
  }
}
</style>