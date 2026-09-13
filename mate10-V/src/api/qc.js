import request from '@/utils/request'

// ==================== 报告管理 ====================

/** 分页查询报告列表 */
export function getReportPage(params) {
  return request.get('/api/qc/report/page', { params })
}

/** 查询报告详情 */
export function getReportById(id) {
  return request.get(`/api/qc/report/${id}`)
}

/** 上传质检报告文件 */
export function uploadReport(formData) {
  return request.post('/api/qc/report/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 更新报告状态 */
export function updateReportStatus(id, status) {
  return request.put(`/api/qc/report/${id}/status`, null, { params: { status } })
}

/** 删除报告 */
export function deleteReport(id) {
  return request.delete(`/api/qc/report/${id}`)
}

/** 根因分析 */
export function analyzeRootCause(id) {
  return request.get(`/api/qc/report/${id}/root-cause`)
}

// ==================== 趋势统计 ====================

/** 按时间趋势统计 */
export function getTrendStats(days = 7) {
  return request.get('/api/qc/report/stats/trend', { params: { days } })
}

/** 按产线统计 */
export function getProductionLineStats() {
  return request.get('/api/qc/report/stats/production-line')
}

/** 按产品批次统计 */
export function getProductBatchStats(limit = 10) {
  return request.get('/api/qc/report/stats/product-batch', { params: { limit } })
}

// ==================== 缺陷管理 ====================

/** 根据报告ID查缺陷列表 */
export function getDefectsByReportId(reportId) {
  return request.get(`/api/qc/defect/report/${reportId}`)
}

/** 缺陷类型统计 */
export function getDefectTypeStats() {
  return request.get('/api/qc/defect/stats/type')
}

/** 缺陷严重程度统计 */
export function getDefectSeverityStats() {
  return request.get('/api/qc/defect/stats/severity')
}

/** 人工确认缺陷 */
export function confirmDefect(id) {
  return request.put(`/api/qc/defect/${id}/confirm`)
}
/** 批次号/生产线 模糊查询 */
export function searchDefects(params){
  return request.get('/api/qc/defect/search',{params})
}
/** 3D可视化数据 */
export function getDefects3D(reportId) {
  return request.get(`/api/qc/defect/3d/${reportId}`)
}

// ==================== 生产参数 ====================

/** 根据报告ID查生产参数 */
export function getParamByReportId(reportId) {
  return request.get(`/api/qc/param/report/${reportId}`)
}
