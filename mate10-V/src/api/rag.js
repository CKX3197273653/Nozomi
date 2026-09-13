import request from '@/utils/request'

/** 初始化质检知识库 */
export function initQcKnowledge() {
  return request.post('/rag/init-qc-knowledge')
}

/** 质检问答 */
export function qcAsk(question) {
  return request.post('/rag/qc-ask', JSON.stringify(question), {
    headers: { 'Content-Type': 'application/json' }
  })
}

/** 通用问答 */
export function ask(question) {
  return request.post('/rag/ask', JSON.stringify(question), {
    headers: { 'Content-Type': 'application/json' }
  })
}

/** 上传知识文件 */
export function uploadKnowledge(formData) {
  return request.post('/rag/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 清空知识库 */
export function clearKnowledge() {
  return request.delete('/rag/clear')
}
