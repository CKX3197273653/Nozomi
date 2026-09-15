<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  initQcKnowledge,
  uploadKnowledge,
  clearKnowledge
} from '@/api/rag'
import {createChat,chatRecord} from "@/api/chat.js";

// 对话消息列表
const messageList = ref([
  {
    role: 'assistant',
    content: '你好!我是质检知识问答助手。你可以问我关于质检、缺陷、报告等相关问题。\n\n💡 提示:使用"质检问答"模式前,请先点击左侧"初始化质检知识库"。'
  }
])
const chatId = ref(null)
const currentUserId = () => {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}').id
  }catch {
    return null
  }
}
// 首次发送时创建会话
const ensureChatId = async () => {
  if (chatId.value) return chatId.value
  const res = await createChat(currentUserId(), '新对话')
  chatId.value = res.data.id
  return chatId.value
}
// 新建会话
const handleNewChat = () => {
  chatId.value = null
  messageList.value = [{ role: 'assistant', content: '你好！我是质检知识问答助手。' }]
}
//输入框
const inputMessage = ref('')
const sending = ref(false)
//问答模式
const mode = ref('qc')  // 'qc' = 质检问答, 'general' = 通用问答
//知识库操作状态
const initLoading = ref(false)
const uploadLoading = ref(false)
const clearLoading = ref(false)
//上传对话框
const uploadVisible = ref(false)
const uploadFile = ref(null)
// 自动滚动
const chatBodyRef = ref(null)
//滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}
//发送消息
const handleSend = async () => {
  const question = inputMessage.value.trim()
  if (!question) {
    ElMessage.warning('请输入问题')
    return
  }
  if (sending.value) {
    ElMessage.warning('AI 正在思考中,请稍候')
    return
  }

  //添加用户消息
  messageList.value.push({
    role: 'user',
    content: question
  })
  inputMessage.value = ''
  await scrollToBottom()

  //AI占位消息
  const aiMessageIndex = messageList.value.length
  messageList.value.push({
    role: 'assistant',
    content: '',
    loading: true
  })
  await scrollToBottom()

  // 调用接口
  sending.value = true
  try {
    // 根据模式选择接口
    const cid = await ensureChatId()
    const res = await chatRecord({
      userId:currentUserId(),
      chatId:cid,
      message:question,
      mode:mode.value === 'qc'?'qc' : 'general'
    })
    // 更新 AI 消息内容
    messageList.value[aiMessageIndex].content = res.data || '暂无回复'
    messageList.value[aiMessageIndex].loading = false
  } catch (error) {
    messageList.value[aiMessageIndex].content = '抱歉,回答失败,请稍后重试'
    messageList.value[aiMessageIndex].loading = false
    console.error('问答失败:', error)
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}
const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}
// 初始化质检知识库
const handleInitKnowledge = async () => {
  initLoading.value = true
  try {
    await initQcKnowledge()
    ElMessage.success('质检知识库初始化成功')
  } catch (error) {
    console.error('初始化知识库失败:', error)
  } finally {
    initLoading.value = false
  }
}
//上传知识文件
const handleUploadClick = () => {
  uploadFile.value = null
  uploadVisible.value = true
}
const handleFileChange = (file) => {
  uploadFile.value = file.raw
}
const handleUploadSubmit = async () => {
  if (!uploadFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  uploadLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.value)
    await uploadKnowledge(formData)
    ElMessage.success('知识文件上传成功')
    uploadVisible.value = false
  } catch (error) {
    console.error('上传知识文件失败:', error)
  } finally {
    uploadLoading.value = false
  }
}
//清空知识库
const handleClearKnowledge = () => {
  ElMessageBox.confirm(
      '确定要清空知识库吗?清空后需要重新初始化或上传文件。',
      '清空确认',
      {
        confirmButtonText: '确定清空',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(async () => {
    clearLoading.value = true
    try {
      await clearKnowledge()
      ElMessage.success('知识库已清空')
    } catch (error) {
      console.error('清空知识库失败:', error)
    } finally {
      clearLoading.value = false
    }
  }).catch(() => {})
}
//切换模式
const handleModeChange = (value) => {
  const tip = value === 'qc'
      ? '已切换到「质检问答」模式,将基于质检知识库回答'
      : '已切换到「通用问答」模式,将使用通用 AI 回答'
  ElMessage.info(tip)
}
//清空对话
const handleClearChat = () => {
  ElMessageBox.confirm('确定要清空当前对话记录吗?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info'
  }).then(() => {
    messageList.value = [
      {
        role: 'assistant',
        content: '对话已清空,有什么可以帮你的吗?'
      }
    ]
  }).catch(() => {})
}
//初始化
onMounted(() => {
  scrollToBottom()
})
</script>

<template>
  <div class="qa-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>知识问答</h2>
      <p class="page-desc">基于 RAG 的智能问答,支持质检知识库与通用问答</p>
    </div>

    <!--左侧知识库管理 + 右侧对话区 -->
    <div class="qa-container">
      <!--知识库管理 -->
      <aside class="qa-sidebar">
        <div class="sidebar-title">知识库管理</div>

        <!-- 操作按钮 -->
        <div class="kb-actions">
          <el-button
              type="primary"
              :loading="initLoading"
              @click="handleInitKnowledge"
              style="width: 100%"
          >
            <el-icon><MagicStick /></el-icon>
            初始化质检知识库
          </el-button>

          <el-button
              type="success"
              :loading="uploadLoading"
              @click="handleUploadClick"
              style="width: 100%"
          >
            <el-icon><Upload /></el-icon>
            上传知识文件
          </el-button>

          <el-button
              type="danger"
              :loading="clearLoading"
              @click="handleClearKnowledge"
              style="width: 100%"
          >
            <el-icon><Delete /></el-icon>
            清空知识库
          </el-button>
        </div>

        <!-- 模式切换 -->
        <div class="mode-section">
          <div class="mode-title">问答模式</div>
          <el-radio-group v-model="mode" @change="handleModeChange" class="mode-group">
            <el-radio value="qc">质检问答</el-radio>
            <el-radio value="general">通用问答</el-radio>
          </el-radio-group>
          <div class="mode-tip">
            <span v-if="mode === 'qc'">
              💡 基于质检知识库回答,需先初始化
            </span>
            <span v-else>
              💡 使用通用 AI,无需知识库
            </span>
          </div>
        </div>

        <!-- 使用说明 -->
        <div class="usage-tip">
          <div class="tip-title">使用说明</div>
          <ul class="tip-list">
            <li>质检问答:先初始化知识库</li>
            <li>通用问答:可直接提问</li>
            <li>Enter 发送,Shift+Enter 换行</li>
          </ul>
        </div>
      </aside>

      <!-- 对话区 -->
      <section class="qa-chat">
        <!-- 对话头部 -->
        <div class="chat-header">
          <span class="chat-title">
            {{ mode === 'qc' ? '质检问答助手' : '通用问答助手' }}
          </span>
          <el-button text size="small" @click="handleClearChat">
            <el-icon><Delete /></el-icon>
            清空对话
          </el-button>
        </div>

        <!-- 消息列表 -->
        <div ref="chatBodyRef" class="chat-body">
          <div
              v-for="(msg, index) in messageList"
              :key="index"
              class="message-item"
              :class="msg.role === 'user' ? 'message-user' : 'message-ai'"
          >
            <!-- 头像 -->
            <div class="message-avatar">
              <el-icon v-if="msg.role === 'user'"><User /></el-icon>
              <el-icon v-else><ChatDotRound /></el-icon>
            </div>
            <!-- 消息内容 -->
            <div class="message-content">
              <div class="message-role">
                {{ msg.role === 'user' ? '我' : 'AI 助手' }}
              </div>
              <div class="message-text">
                <div v-if="msg.loading" class="loading-dots">
                  <span></span><span></span><span></span>
                  AI 正在思考...
                </div>
                <pre v-else>{{ msg.content }}</pre>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input">
          <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="2"
              placeholder="输入你的问题... (Enter 发送, Shift+Enter 换行)"
              resize="none"
              :disabled="sending"
              @keydown="handleKeydown"
          />
          <el-button
              type="primary"
              :loading="sending"
              :disabled="!inputMessage.trim()"
              @click="handleSend"
              class="send-btn"
          >
            <el-icon><Promotion /></el-icon>
            发送
          </el-button>
        </div>
      </section>
    </div>

    <!-- 上传知识文件对话框 -->
    <el-dialog v-model="uploadVisible" title="上传知识文件" width="500px">
      <el-upload
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          accept=".txt,.pdf,.doc,.docx,.md"
          drag
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处,或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 txt/pdf/doc/docx/md 格式
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploadLoading" @click="handleUploadSubmit">
          确认上传
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.qa-page {
  color: #e0e0e0;
  height: calc(100vh - 108px);
}

/* 页面标题 */
.page-header {
  margin-bottom: 20px;
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

/* 主体容器 */
.qa-container {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  height: calc(100% - 60px);
}

/* 左侧侧边栏 */
.qa-sidebar {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px;
  overflow-y: auto;
}
.sidebar-title {
  font-size: 16px;
  font-weight: 600;
  color: #4a9eff;
  margin-bottom: 20px;
}

/* 知识库操作按钮 */
.kb-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

/* 模式切换 */
.mode-section {
  padding: 16px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 8px;
  margin-bottom: 20px;
}
.mode-title {
  font-size: 14px;
  font-weight: 600;
  color: #e0e0e0;
  margin-bottom: 12px;
}
.mode-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.mode-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #a0a0a0;
}

/* 使用说明 */
.usage-tip {
  padding: 16px;
  background: rgba(74, 158, 255, 0.05);
  border: 1px solid rgba(74, 158, 255, 0.15);
  border-radius: 8px;
}
.tip-title {
  font-size: 13px;
  font-weight: 600;
  color: #4a9eff;
  margin-bottom: 8px;
}
.tip-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.tip-list li {
  font-size: 12px;
  color: #a0a0a0;
  padding: 4px 0;
  padding-left: 12px;
  position: relative;
}
.tip-list li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #4a9eff;
}

/* 右侧对话区 */
.qa-chat {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 对话头部 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: #4a9eff;
}

/* 消息列表 */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

/* 单条消息 */
.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}
.message-user {
  flex-direction: row-reverse;
}
.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.message-ai .message-avatar {
  background: rgba(74, 158, 255, 0.15);
  color: #4a9eff;
}
.message-user .message-avatar {
  background: rgba(103, 194, 58, 0.15);
  color: #67c23a;
}

/* 消息内容 */
.message-content {
  max-width: 70%;
}
.message-user .message-content {
  text-align: right;
}
.message-role {
  font-size: 12px;
  color: #a0a0a0;
  margin-bottom: 4px;
}
.message-text {
  display: inline-block;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  text-align: left;
}
.message-ai .message-text {
  background: rgba(255, 255, 255, 0.05);
  color: #e0e0e0;
}
.message-user .message-text {
  background: rgba(74, 158, 255, 0.15);
  color: #e0e0e0;
}

/* 消息文本 pre 样式 */
.message-text pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  margin: 0;
  font-family: inherit;
}

/* 加载动画 */
.loading-dots {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #a0a0a0;
}
.loading-dots span {
  width: 6px;
  height: 6px;
  background: #4a9eff;
  border-radius: 50%;
  animation: dot-bounce 1.4s infinite;
}
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* 输入区 */
.chat-input {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  align-items: flex-end;
}
.send-btn {
  height: 60px;
}

/* 响应式:小屏幕 */
@media (max-width: 900px) {
  .qa-container {
    grid-template-columns: 1fr;
  }
  .qa-sidebar {
    display: none;
  }
}
</style>