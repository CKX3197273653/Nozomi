<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getReportPage,
  getReportById,
  uploadReport,
  deleteReport,
  analyzeRootCause,
  getDefectsByReportId
} from '@/api/qc'

// 搜索条件
const queryParams = reactive({
  reportNo: '',
  productBatch: '',
  productionLine: '',
  productName: '',
  status: '',
  pageNum: 1,
  pageSize: 10
})

// 表格数据
const tableData = ref([])
const total = ref(0)
const loading = ref(false)

// 上传对话框
const uploadVisible = ref(false)
const uploadForm = reactive({
  reportNo: '',
  productName: '',
  productBatch: '',
  productionLine: '',
  inspector: '',
  prompt: ''
})
const uploadFile = ref(null)
const uploading = ref(false)

// 详情对话框
const detailVisible = ref(false)
const detailData = ref({})
const detailDefects = ref([])

// 根因分析对话框
const rootCauseVisible = ref(false)
const rootCauseLoading = ref(false)
const rootCauseContent = ref('')

//状态
const statusOptions = [
  { value: '', label: '全部' },
  { value: 'PENDING',    label: '待处理' },
  { value: 'PROCESSING', label: '分析中' },
  { value: 'COMPLETED',  label: '已完成' },
  { value: 'FAILED',     label: '分析失败' }
]
const statusMap = {
  PENDING: '待处理',
  PROCESSING: '分析中',
  COMPLETED: '已完成',
  FAILED: '分析失败'
}
const statusTagType = {
  PENDING: 'warning',
  PROCESSING: 'info',
  COMPLETED: 'success',
  FAILED: 'danger'
}

const severityTextMap = {
  LOW: '轻微',
  MEDIUM: '一般',
  HIGH: '严重',
  CRITICAL: '致命'
}
const severityTagMap = {
  LOW: 'info',
  MEDIUM: 'warning',
  HIGH: 'danger',
  CRITICAL: 'danger'
}

//查询报告列表
const loadData = async () => {
  loading.value = true
  try {
    const res = await getReportPage(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.log('加载报告列表失败', error)
  } finally {
    loading.value = false
  }
}

// 搜索与重置
const handleSearch = () => {
  queryParams.pageNum = 1
  loadData()
}
const handleReset = () => {
  queryParams.reportNo = ''
  queryParams.productBatch = ''
  queryParams.productionLine = ''
  queryParams.productName = ''
  queryParams.status = ''
  queryParams.pageNum = 1
  loadData()
}

// 分页变化
const handleSizeChange = (size) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  loadData()
}
const handleCurrentChange = (page) => {
  queryParams.pageNum = page
  loadData()
}

//上传报告
const handleUploadClick = () => {
  uploadForm.reportNo = ''
  uploadForm.productName = ''
  uploadForm.productBatch = ''
  uploadForm.productionLine = ''
  uploadForm.inspector = ''
  uploadForm.prompt = ''
  uploadFile.value = null
  uploadVisible.value = true
}
const handleFileChange = (file) => {
  uploadFile.value = file.raw
}
const handleUploadSubmit = async () => {
  if (!uploadForm.reportNo)      { ElMessage.warning('请输入报告编号'); return }
  if (!uploadForm.productName)   { ElMessage.warning('请输入产品名称'); return }
  if (!uploadForm.productBatch)  { ElMessage.warning('请输入产品批次'); return }
  if (!uploadForm.productionLine){ ElMessage.warning('请输入生产线'); return }
  if (!uploadForm.inspector)     { ElMessage.warning('请输入检验员'); return }
  if (!uploadFile.value)         { ElMessage.warning('请选择报告文件'); return }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.value)
    formData.append('reportNo', uploadForm.reportNo)
    formData.append('productName', uploadForm.productName)
    formData.append('productBatch', uploadForm.productBatch)
    formData.append('productionLine', uploadForm.productionLine)
    formData.append('inspector', uploadForm.inspector)
    if (uploadForm.prompt) {
      formData.append('prompt', uploadForm.prompt)
    }
    await uploadReport(formData)
    ElMessage.success('上传成功，AI 分析中...')
    uploadVisible.value = false
    loadData()
  } catch (error) {
    console.error('上传失败:', error)
  } finally {
    uploading.value = false
  }
}

// 查看详情
const handleView = async (row) => {
  try {
    const [reportRes, defectsRes] = await Promise.all([
      getReportById(row.id),
      getDefectsByReportId(row.id)
    ])
    detailData.value = reportRes.data || {}
    detailDefects.value = defectsRes.data || []
    detailVisible.value = true
  } catch (error) {
    console.error('加载详情失败', error)
  }
}

//  根因分析
const handleRootCause = async (row) => {
  rootCauseVisible.value = true
  rootCauseLoading.value = true
  rootCauseContent.value = ''
  try {
    const res = await analyzeRootCause(row.id)
    rootCauseContent.value = typeof res.data === 'string'
        ? res.data
        : JSON.stringify(res.data || {}, null, 2)
  } catch (error) {
    rootCauseContent.value = '分析失败'
    console.error('根因分析失败', error)
  } finally {
    rootCauseLoading.value = false
  }
}

//删除报告
const handleDelete = (row) => {
  ElMessageBox.confirm(
      `确定要删除报告「${row.reportNo || row.id}」吗?`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(async () => {
    try {
      await deleteReport(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      console.error('删除失败', error)
    }
  }).catch(() => {})
}

// 初始化加载
onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="report-page">
    <!--页面标题-->
    <div class="page-header">
      <h2>报告管理</h2>
      <p class="page-desc">管理质检报告</p>
    </div>

    <!--搜索栏-->
    <div class="search-bar">
      <el-input
          v-model="queryParams.reportNo"
          placeholder="报告编号"
          style="width: 160px"
          clearable
          @keyup.enter="handleSearch"
      />
      <el-input
          v-model="queryParams.productBatch"
          placeholder="批次号"
          style="width: 160px"
          clearable
          @keyup.enter="handleSearch"
      />
      <el-input
          v-model="queryParams.productionLine"
          placeholder="生产线"
          style="width: 160px"
          clearable
          @keyup.enter="handleSearch"
      />
      <el-input
          v-model="queryParams.productName"
          placeholder="产品名称"
          style="width: 160px"
          clearable
          @keyup.enter="handleSearch"
      />
      <el-select
          v-model="queryParams.status"
          placeholder="状态"
          style="width: 140px"
          clearable
      >
        <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
        />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <el-button type="success" @click="handleUploadClick">
        <el-icon><Upload /></el-icon> 上传报告
      </el-button>
    </div>

    <!-- 表格 -->
    <div class="table-container">
      <el-table
          v-loading="loading"
          :data="tableData"
          style="width: 100%"
          class="dark-table"
          stripe
      >
        <el-table-column prop="reportNo"       label="报告编号" width="150" />
        <el-table-column prop="productName"    label="产品名称" width="140" />
        <el-table-column prop="productBatch"   label="批次号"   width="130" />
        <el-table-column prop="productionLine" label="生产线"   width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType[row.status] || 'info'" size="small">
              {{ statusMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="severityLevel" label="级别" width="90" align="center" />
        <el-table-column prop="totalDefects"  label="缺陷数" width="90" align="center" />
        <el-table-column prop="createTime"    label="创建时间" min-width="160" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看</el-button>
            <el-button size="small" type="warning" @click="handleRootCause(row)">
              根因分析
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
      />
    </div>

    <!-- 上传报告对话框 -->
    <el-dialog v-model="uploadVisible" title="上传质检报告" width="560px">
      <el-form :model="uploadForm" label-width="90px">
        <el-form-item label="报告编号">
          <el-input v-model="uploadForm.reportNo" placeholder="请输入报告编号" />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="uploadForm.productName" placeholder="请输入产品名称" />
        </el-form-item>
        <el-form-item label="产品批次">
          <el-input v-model="uploadForm.productBatch" placeholder="请输入产品批次" />
        </el-form-item>
        <el-form-item label="生产线">
          <el-input v-model="uploadForm.productionLine" placeholder="请输入生产线" />
        </el-form-item>
        <el-form-item label="检验员">
          <el-input v-model="uploadForm.inspector" placeholder="请输入检验员" />
        </el-form-item>
        <el-form-item label="AI 提示词">
          <el-input
              v-model="uploadForm.prompt"
              type="textarea"
              :rows="2"
              placeholder="可选：自定义 AI 缺陷分析要求"
          />
        </el-form-item>
        <el-form-item label="报告文件">
          <el-upload
              :auto-upload="false"
              :limit="1"
              :on-change="handleFileChange"
              accept=".pdf,.jpg,.jpeg,.png,.gif,.bmp,.doc,.docx,.xls,.xlsx"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 pdf / 图片 / word / excel，单个文件</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUploadSubmit">
          确认上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="报告详情" width="720px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="报告编号">{{ detailData.reportNo }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ detailData.productName }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detailData.productBatch }}</el-descriptions-item>
        <el-descriptions-item label="生产线">{{ detailData.productionLine }}</el-descriptions-item>
        <el-descriptions-item label="检验员">{{ detailData.inspector }}</el-descriptions-item>
        <el-descriptions-item label="文件类型">{{ detailData.fileType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType[detailData.status] || 'info'" size="small">
            {{ statusMap[detailData.status] || detailData.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="级别">{{ detailData.severityLevel }}</el-descriptions-item>
        <el-descriptions-item label="缺陷数">{{ detailData.totalDefects }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-section-title">缺陷列表</div>
      <el-table :data="detailDefects" class="dark-table" size="small" max-height="300">
        <el-table-column prop="defectType"     label="缺陷类型" width="120" />
        <el-table-column prop="defectPosition" label="位置" width="110" />
        <el-table-column label="严重程度" width="100">
          <template #default="{ row }">
            <el-tag :type="severityTagMap[row.severity] || 'info'" size="small">
              {{ severityTextMap[row.severity] || row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="置信度" width="90" align="center">
          <template #default="{ row }">
            {{ row.confidence != null ? row.confidence + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="是否确认" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isConfirmed === 1 ? 'success' : 'info'" size="small">
              {{ row.isConfirmed === 1 ? '已确认' : '未确认' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 根因分析对话框 -->
    <el-dialog v-model="rootCauseVisible" title="AI 根因分析" width="640px">
      <div v-loading="rootCauseLoading" class="root-cause-content">
        <el-input
            v-if="!rootCauseLoading"
            v-model="rootCauseContent"
            type="textarea"
            :rows="16"
            readonly
            resize="none"
        />
        <div v-else class="loading-tip">AI 正在分析中,请稍候...</div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.report-page {
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
  margin-bottom: 20px;
  flex-wrap: wrap;
}

/* 表格容器 */
.table-container {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px;
}

/* 分页 */
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 详情区域标题 */
.detail-section-title {
  margin: 20px 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #4a9eff;
}

/* 根因分析内容 */
.root-cause-content {
  min-height: 200px;
}
.loading-tip {
  text-align: center;
  padding: 60px 0;
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

/* 深色主题分页器 */
:deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-text-color: #e0e0e0;
  --el-pagination-button-color: #e0e0e0;
  --el-pagination-hover-color: #4a9eff;
}

/* 深色主题对话框 */
:deep(.el-dialog) {
  background: #1a1f3a !important;
  border: 1px solid rgba(255, 255, 255, 0.1);
}
:deep(.el-dialog__title) {
  color: #e0e0e0 !important;
}
:deep(.el-dialog__body) {
  color: #e0e0e0 !important;
}
:deep(.el-descriptions__label) {
  color: #a0a0a0 !important;
  background: rgba(255, 255, 255, 0.03) !important;
}
:deep(.el-descriptions__content) {
  color: #e0e0e0 !important;
}
:deep(.el-form-item__label) {
  color: #e0e0e0 !important;
}
:deep(.el-upload__tip) {
  color: #a0a0a0 !important;
}
</style>