<script setup>
import {ref,computed,onMounted,onBeforeUnmount,nextTick} from 'vue'
import * as echarts from 'echarts'
import {
  getTrendStats,
  getDefectTypeStats,
  getProductionLineStats,
  getReportPage
} from '@/api/qc'

const cardData = ref([
  { title: '质检报告总数',value: 0, unit: '份',   icon: 'Document',     color: '#4a9eff' },
  { title: '合格率',     value: 0, unit: '%',    icon: 'CircleCheck',  color: '#67c23a' },
  { title: '缺陷总数',   value: 0, unit: '个',    icon: 'Warning',      color: '#e6a23c' },
  { title: '待处理报告',  value: 0, unit: '份',    icon: 'Clock',       color: '#f56c6c' }
])

const trendChartRef = ref(null)
const defectTypeChartRef = ref(null)
const productionLineChartRef = ref(null)

//图标实例
let trendChart = null
let defectTypeChart = null
let productionLineChart = null
//最近报告列表
const recentReports = ref([])
const scrollRowHeight = 42
const showRows = 5
const scrollY = ref(0)
let scrollTimer = null

const doubledReports = computed(() => {
  const list = recentReports.value || []
  return list.length > showRows ? [...list, ...list] : list
})

const innerScrollStyle = computed(() => ({
  transform: `translateY(-${scrollY.value}px)`
}))

const startScrollCarousel = () => {
  stopScrollCarousel()
  if ((recentReports.value?.length || 0) <= showRows) return
  scrollTimer = setInterval(() => {
    scrollY.value += scrollRowHeight
    const firstHalf = (recentReports.value?.length || 0) * scrollRowHeight
    if (scrollY.value >= firstHalf) {
      setTimeout(() => {
        const el = document.querySelector('.report-scroll-inner')
        if (el) el.style.transition = 'none'
        scrollY.value = 0
        nextTick(() => {
          const el2 = document.querySelector('.report-scroll-inner')
          if (el2) el2.style.transition = 'transform 0.6s ease'
        })
      }, 600)
    }
  }, 2000)
}
const stopScrollCarousel = () => {
  if (scrollTimer) {
    clearInterval(scrollTimer)
    scrollTimer = null
  }
}
//报告轮播
const pageSize = 5
const recentPageIndex = ref(0)
let recentCarouselTimer = null

const recentReportsPage = computed(() => {
  const list = recentReports.value || []
  if (list.length <= pageSize) return list
  const start = recentPageIndex.value * pageSize
  return list.slice(start, start + pageSize)
})

const startRecentCarousel = () => {
  stopRecentCarousel()
  if ((recentReports.value?.length || 0) <= pageSize) return
  recentCarouselTimer = setInterval(() => {
    const totalPages = Math.ceil((recentReports.value?.length || 0) / pageSize)
    recentPageIndex.value = (recentPageIndex.value + 1) % Math.max(totalPages, 1)
  }, 2500)
}
const stopRecentCarousel = () => {
  if (recentCarouselTimer) {
    clearInterval(recentCarouselTimer)
    recentCarouselTimer = null
  }
}
//趋势折现图
const initTrendChart = (data) => {
  if (!trendChartRef.value) return
  trendChart = echarts.init(trendChartRef.value)
  trendChart.setOption({
    tooltip:{ trigger: 'axis'},
    legend:{ data:['质检数','缺陷数'], textStyle: { color:'#a0a0a0'},top:'5px'},
    grid: { left:'3%', right: '4%',bottom:'-1%',containLabel:true},
    xAxis:{
      type:'category',
      data: data.map( item => item.date || ''),
      axisLine: { lineStyle: { color: '#a0a0a0' } },
      axisLabel: { color: '#a0a0a0' }
    },
    yAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: '#a0a0a0' } },
      axisLabel: { color: '#a0a0a0' },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } }
    },
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
      }
    ]
  })
}
//缺陷类饼图
const initDefectTypeChart = (data) => {
  if (!defectTypeChartRef.value) return
  defectTypeChart = echarts.init(defectTypeChartRef.value)
  defectTypeChart.setOption({
    tooltip: { trigger: 'item' },
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
      radius: ['35%', '60%'], center: ['50%', '45%'],
      avoidLabelOverlap: true,
      label: { show: true,color:'#e0e0e0',fontSize:12 },
      labelLine: { show: true },
      data: data.map(item => ({ value: item.defectCount || 0, name: item.defect_type || '未知' })),
      color: ['#4a9eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9c27b0']
    }]
  })
}
//产线统计柱状图
const initProductionLineChart = (data) => {
  if (!productionLineChartRef.value) return
  productionLineChart = echarts.init(productionLineChartRef.value)
  productionLineChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%',  containLabel: true },
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
      data: data.map(item => item.totalCount || 0),
      itemStyle: { color: '#4a9eff', borderRadius: [4, 4, 0, 0] }
    }]
  })
}
//窗口大小变化重绘
const handleResize = () => {
  trendChart && trendChart.resize()
  defectTypeChart && defectTypeChart.resize()
  productionLineChart && productionLineChart.resize()
}
//加载所有数据
const loadData = async () => {
  try {
    // 并行请求所有数据
    const [trendRes, defectTypeRes, productionLineRes, reportRes] = await Promise.all([
      getTrendStats(7),
      getDefectTypeStats(),
      getProductionLineStats(),
      getReportPage({ pageNum: 1, pageSize: 15 })
    ])

    // 趋势数据
    const trendData = trendRes.data || []
    initTrendChart(trendData)
    const records = reportRes.data?.records || []
    // 更新卡片数据
    if (trendData.length > 0) {
      const totalReports = trendData.reduce((sum, item) => sum + (item.totalCount || 0), 0)
      const totalDefects = trendData.reduce((sum, item) => sum + (item.defectCount || 0), 0)

      const qualifiedCount = records.filter(r =>
          r.status === 'COMPLETED' && (!r.totalDefects || r.totalDefects === 0)).length
      const passRate = totalReports > 0
          ? ((qualifiedCount / totalReports) * 100).toFixed(1)
          : 0
      cardData.value[0].value = totalReports
      cardData.value[2].value = totalDefects
      cardData.value[1].value = passRate
    }
    // 缺陷类型饼图
    initDefectTypeChart(defectTypeRes.data || [])
    // 产线统计柱状图
    initProductionLineChart(productionLineRes.data || [])
    // 最近报告列表
    recentReports.value = records
    recentPageIndex.value = 0
    recentReports.value = records
    scrollY.value = 0
    nextTick(startScrollCarousel)
    const pendingCount = records.filter(r => r.status === 'PENDING').length
    cardData.value[3].value = pendingCount
  } catch (error) {
    console.error('加载仪表盘数据失败:', error)
  }
}
//生命周期
onMounted(async () => {
  await nextTick() //等待DOM渲染完成
  await loadData() //加载数据并初始化图表
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(()=>{
  stopScrollCarousel()
  window.removeEventListener('resize',handleResize)
  trendChart && trendChart.dispose()
  defectTypeChart && defectTypeChart.dispose()
  productionLineChart && productionLineChart.dispose()
})
</script>

<template>
  <div class="dashboard">
    <!--页面标题-->
    <div class="page-header">
      <h2>质检仪表盘</h2>
      <p class="page-desc"> 实时监控质检数据,掌握生产质量动态</p>
    </div>
    <!--数据卡片区域-->
    <div class="card-row">
      <div v-for="(card, idx) in cardData" :key="card.title" class="data-card" :style="{ '--card-color': card.color }">
        <span class="card-index">{{ String(idx + 1).padStart(2, '0') }}</span>
        <div class="card-icon-wrap">
          <el-icon class="card-icon"><component :is="card.icon"/></el-icon>
        </div>
        <div class="card-info">
          <div class="card-value">
            {{ card.value }}<span class="card-unit">{{ card.unit }}</span>
          </div>
          <div class="card-label">{{ card.title }}</div>
        </div>
      </div>
    </div>
  <!--趋势+缺陷-->
    <div class="chart-row">
      <div class="chart-card tech-card">
        <div class="card-title">近7天质检趋势</div>
        <div ref="trendChartRef" class="chart-box"></div>
      </div>
      <div class="chart-card tech-card">
        <div class="card-title">缺陷类型分布</div>
        <div ref="defectTypeChartRef" class="chart-box"></div>
      </div>
    </div>
  <!--产线统计+最近报告-->
    <div class="chart-row">
      <div class="chart-card tech-card">
        <div class="card-title">产线质检统计</div>
        <div ref="productionLineChartRef" class="chart-box"></div>
      </div>
      <div class="chart-card tech-card">
      <div class="card-title">最近质检报告</div>
      <!-- 固定表头 -->
      <div class="report-header-row">
        <div class="report-cell" style="width:140px">报告编号</div>
        <div class="report-cell" style="width:100px">产线</div>
        <div class="report-cell" style="width:100px">状态</div>
        <div class="report-cell flex-1">创建时间</div>
      </div>
      <!-- 数据滚动区 -->
      <div class="report-scroll-wrap" :style="{ height: scrollRowHeight * showRows + 'px' }">
        <div class="report-scroll-inner" :style="innerScrollStyle">
          <div
              v-for="(row, idx) in doubledReports"
              :key="idx"
              class="report-row"
              :style="{ height: scrollRowHeight + 'px', lineHeight: scrollRowHeight + 'px' }"
          >
            <div class="report-cell" style="width:140px">{{ row.reportNo || '-' }}</div>
            <div class="report-cell" style="width:100px">{{ row.productionLine || '-' }}</div>
            <div class="report-cell" style="width:100px">
              <el-tag
                  :type="row.status === 'COMPLETED' ? 'success' : (row.status === 'PENDING' ? 'warning' : 'danger')"
                  size="small"
              >
                {{ row.status === 'COMPLETED' ? '合格' : (row.status === 'PENDING' ? '待处理' : '不合格') }}
              </el-tag>
            </div>
            <div class="report-cell flex-1">{{ row.createTime || '-' }}</div>
          </div>
        </div>
      </div>

    </div>
  </div>
  </div>
</template>

<style scoped>
.dashboard {
  color: #e0e7ff;
}
/* 页面标题 */
.page-header {
  margin-bottom: 24px;
}
.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(90deg, #60a5fa 0%, #a78bfa 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin: 0 0 8px 0;
  letter-spacing: 0.5px;
  filter: drop-shadow(0 0 12px rgba(96, 165, 250, 0.35));
}
.page-desc {
  font-size: 13px;
  color: #94a3b8;
  margin: 0;
}

/* ========== 4 个数据卡片（科技感渐变蓝） ========== */
.card-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  margin-bottom: 24px;
}
.data-card {
  --card-color: #60a5fa;
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 22px;
  min-height: 96px;
  background: linear-gradient(145deg, rgba(15, 30, 70, 0.88) 0%, rgba(10, 22, 54, 0.92) 100%);
  border: 1px solid rgba(74, 158, 255, 0.24);
  border-radius: 14px;
  box-shadow:
      0 0 0 1px rgba(74, 158, 255, 0.08) inset,
      0 4px 22px rgba(0, 0, 0, 0.45),
      0 0 36px rgba(59, 130, 246, 0.1);
  overflow: hidden;
  transition: all 0.35s ease;
}
.data-card::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  background: linear-gradient(180deg, var(--card-color, #60a5fa) 0%, rgba(59, 130, 246, 0) 100%);
  box-shadow: 0 0 12px var(--card-color, #60a5fa);
}
.data-card::after {
  content: '';
  position: absolute;
  right: -40px; top: -40px;
  width: 140px; height: 140px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(96, 165, 250, 0.22) 0%, transparent 70%);
  pointer-events: none;
}
.data-card:hover {
  border-color: rgba(125, 180, 255, 0.5);
  box-shadow:
      0 0 0 1px rgba(125, 180, 255, 0.22) inset,
      0 6px 32px rgba(0, 0, 0, 0.55),
      0 0 56px rgba(59, 130, 246, 0.22);
  transform: translateY(-2px);
}
.card-index {
  position: absolute;
  right: 14px; top: 10px;
  font-size: 12px;
  color: rgba(147, 197, 253, 0.45);
  font-weight: 600;
  letter-spacing: 0.5px;
  z-index: 2;
}
.card-icon-wrap {
  width: 52px; height: 52px;
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  border-radius: 14px;
  background: linear-gradient(145deg, color-mix(in srgb, var(--card-color, #60a5fa) 22%, transparent), rgba(15, 30, 70, 0.8));
  border: 1px solid color-mix(in srgb, var(--card-color, #60a5fa) 40%, transparent);
  box-shadow:
      0 0 0 1px color-mix(in srgb, var(--card-color, #60a5fa) 15%, transparent) inset,
      0 3px 14px color-mix(in srgb, var(--card-color, #60a5fa) 25%, transparent);
}
.card-icon {
  font-size: 24px;
  color: var(--card-color, #60a5fa);
  filter: drop-shadow(0 0 6px color-mix(in srgb, var(--card-color, #60a5fa) 50%, transparent));
}
.card-info { flex: 1; position: relative; z-index: 2; }
.card-value {
  font-size: 30px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.1;
  letter-spacing: 0.5px;
  text-shadow: 0 0 14px rgba(96, 165, 250, 0.4);
}
.card-unit {
  font-size: 14px;
  font-weight: 400;
  color: #93c5fd;
  margin-left: 5px;
}
.card-label {
  font-size: 13px;
  color: #94a3b8;
  margin-top: 5px;
  letter-spacing: 0.3px;
}

/* ========== 图表区域 ========== */
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  margin-bottom: 24px;
}
.chart-card {
  padding: 18px !important;
}
.chart-box {
  width: 100%;
  height: 320px;
}

/* ========== 最近报告：表头固定 + 数据平滑滚动 ========== */
.report-header-row {
  display: flex; align-items: center;
  padding: 0 12px; height: 40px;
  background: linear-gradient(180deg, rgba(74, 158, 255, 0.22) 0%, rgba(74, 158, 255, 0.14) 100%);
  border-radius: 10px 10px 0 0;
  font-size: 13px; font-weight: 600; color: #c7d2fe;
  letter-spacing: 0.3px;
}
.report-cell {
  flex-shrink: 0; padding: 0 8px;
  color: #e0e7ff; font-size: 13px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.report-cell.flex-1 { flex: 1; flex-shrink: 1; }
.report-scroll-wrap {
  overflow: hidden;
  background: rgba(74, 158, 255, 0.02);
  border-radius: 0 0 10px 10px;
  border: 1px solid rgba(74, 158, 255, 0.08);
  border-top: none;
}
.report-scroll-inner {
  transition: transform 0.6s ease;
  will-change: transform;
}
.report-row {
  display: flex; align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid rgba(74, 158, 255, 0.08);
}
.report-row:nth-child(even) {
  background: rgba(74, 158, 255, 0.03);
}
.report-row:last-child {
  border-bottom: none;
}

/* 响应式：小屏幕单列显示 */
@media (max-width: 1200px) {
  .card-row {
    grid-template-columns: repeat(2, 1fr);
  }
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>
