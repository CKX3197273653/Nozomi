<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { agentStream } from '@/api/agent.js'

const question = ref('')
const running = ref(false)
const steps = ref([])
const answer = ref('')
const errorMsg = ref('')
const listRef = ref(null)

const statusText = (s) => ({ running: '调用中…', done: '完成', error: '失败' }[s] || s)
const statusType = (s) => ({ running: 'warning', done: 'success', error: 'danger' }[s] || 'info')

function reset() {
  steps.value = []
  answer.value = ''
  errorMsg.value = ''
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

async function send() {
  const q = question.value.trim()
  if (!q) {
    ElMessage.warning('请输入问题')
    return
  }
  if (running.value) return

  reset()
  running.value = true

  try {
    await agentStream(q, {
      onTool(payload) {
        if (payload.status === 'running') {
          steps.value.push({
            id: Date.now() + '-' + Math.random(),
            name: payload.name,
            args: payload.args || '',
            status: 'running',
            result: ''
          })
        } else {
          for (let i = steps.value.length - 1; i >= 0; i--) {
            if (steps.value[i].name === payload.name && steps.value[i].status === 'running') {
              steps.value[i].status = payload.status
              steps.value[i].result = payload.result || ''
              break
            }
          }
        }
        scrollToBottom()
      },

      onMessage(text) {
        answer.value = text
        scrollToBottom()
      },

      onError(msg) {
        errorMsg.value = msg
      }
    })
  } catch (e) {
    errorMsg.value = '请求失败：' + e.message
  } finally {
    running.value = false
    scrollToBottom()
  }
}
</script>

<template>
  <div class="agent-page">
    <!-- 标题 -->
    <div class="page-head">
      <h2>质检分析</h2>
    </div>

    <!-- 输入区 -->
    <div class="input-bar">
      <el-input
        v-model="question"
        :disabled="running"
        @keyup.enter="send" />
      <el-button type="primary" :loading="running" @click="send">
        {{ running ? '分析中' : '开始分析' }}
      </el-button>
    </div>

    <!-- 轨迹区 -->
    <div ref="listRef" class="trace-area">
      <!-- 工具调用时间线 -->
      <div v-if="steps.length" class="steps">
        <div class="steps-title">
          <el-icon><Tools /></el-icon>
          工具调用轨迹（{{ steps.length }} 次）
        </div>

        <div v-for="(s, i) in steps" :key="s.id" class="step" :class="s.status">
          <div class="step-head">
            <span class="step-index">{{ i + 1 }}</span>
            <span class="step-name">{{ s.name }}</span>
            <el-tag :type="statusType(s.status)" size="small" effect="dark">
              {{ statusText(s.status) }}
            </el-tag>
          </div>
          <div v-if="s.args" class="step-args">参数：{{ s.args }}</div>
          <pre v-if="s.result" class="step-result">{{ s.result }}</pre>
        </div>
      </div>

      <!-- 最终答案 -->
      <div v-if="answer" class="answer">
        <div class="answer-title">
          <el-icon><Document /></el-icon>
          分析结论
        </div>
        <pre class="answer-body">{{ answer }}</pre>
      </div>

      <!-- 错误 -->
      <el-alert v-if="errorMsg" :title="errorMsg" type="error" :closable="false" show-icon />

      <!-- 等待提示 -->
      <div v-if="running && !steps.length" class="hint">
        <el-icon class="is-loading"><Loading /></el-icon>
        模型正在思考…
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-page {
  max-width: 960px;
  margin: 0 auto;
}

.page-head {
  margin-bottom: 16px;
}
.page-head h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: #4a9eff;
}
.sub {
  margin: 0;
  font-size: 13px;
  color: #7a7f9a;
}

.input-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.trace-area {
  max-height: calc(100vh - 260px);
  overflow-y: auto;
  padding-right: 6px;
}

.steps-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #7a7f9a;
  margin-bottom: 10px;
}

/* 工具卡片 */
.step {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-left: 3px solid #4a9eff;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 10px;
  background: rgba(255, 255, 255, 0.03);
}
.step.running {
  border-left-color: #e6a23c;
}
.step.done {
  border-left-color: #67c23a;
}
.step.error {
  border-left-color: #f56c6c;
}

.step-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.step-index {
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  border-radius: 50%;
  background: rgba(74, 158, 255, 0.18);
  color: #4a9eff;
  font-size: 12px;
}
.step-name {
  font-family: Consolas, Monaco, monospace;
  font-size: 14px;
  color: #e0e0e0;
  flex: 1;
}

.step-args {
  margin-top: 6px;
  font-size: 12px;
  color: #7a7f9a;
  font-family: Consolas, Monaco, monospace;
}

.step-result {
  margin: 8px 0 0;
  padding: 8px 10px;
  max-height: 220px;
  overflow-y: auto;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #a8b0c8;
  white-space: pre-wrap;
  word-break: break-word;
}

.answer {
  margin-top: 18px;
  border: 1px solid rgba(74, 158, 255, 0.3);
  border-radius: 6px;
  background: rgba(74, 158, 255, 0.05);
  overflow: hidden;
}
.answer-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  font-size: 14px;
  color: #4a9eff;
  border-bottom: 1px solid rgba(74, 158, 255, 0.2);
}
.answer-body {
  margin: 0;
  padding: 14px;
  font-size: 13.5px;
  line-height: 1.8;
  color: #d8dce8;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}

.hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 0;
  font-size: 14px;
  color: #7a7f9a;
}
</style>
