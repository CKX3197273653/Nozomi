<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login, register } from '@/api/auth'

const router = useRouter()
const activeTab = ref('login')
const loginFormRef = ref(null)
const registerFormRef = ref(null)
const loading = ref(false)

// 登录表单
const loginForm = reactive({
  username: '',
  password: ''
})
const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 注册表单
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone:'',
  nickname: ''
})
const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  phone:[
    { required: true, validator: validatePhone, trigger: 'blur' }
  ]
}

// 密码确认验证
function validateConfirmPassword(rule, value, callback) {
  if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}
//手机号验证
function validatePhone(rule, value, callback) {
  if(!value) {
    callback(new Error("请输入手机号"))
  } else if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号格式'))
  }else {
    callback()
  }
}

// 登录处理
const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await login(loginForm.username, loginForm.password)
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('user', JSON.stringify(res.data.user))
      ElMessage.success('登录成功')
      router.push('/')
    } catch (error) {
      console.log('登录失败', error)
    } finally {
      loading.value = false
    }
  })
}

// 注册处理
const handleRegister = async () => {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await register({
        username: registerForm.username,
        password: registerForm.password,
        email: registerForm.email,
        nickname: registerForm.nickname,
        phone:registerForm.phone
      })
      ElMessage.success('注册成功，请登录')
      // 清空表单
      registerForm.username = ''
      registerForm.password = ''
      registerForm.confirmPassword = ''
      registerForm.email = ''
      registerForm.nickname = ''
      registerForm.phone=''
      // 切换到登录标签
      activeTab.value = 'login'
    } catch (error) {
      console.log('注册失败', error)
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h2>智能质检</h2>
        <p>Quality Inspection Analytics System</p>
      </div>
      
      <!-- 登录/注册选项卡 -->
      <el-tabs v-model="activeTab" class="login-tabs">
        <!-- 登录选项卡 -->
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" @keyup.enter="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="请输入用户名或邮箱" :prefix-icon="User" size="large"/>
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" size="large"/>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
                登 录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 注册选项卡 -->
        <el-tab-pane label="注册" name="register">
          <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules">
            <el-form-item prop="username">
              <el-input v-model="registerForm.username" placeholder="请输入用户名" :prefix-icon="User" size="large"/>
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="registerForm.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" size="large"/>
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="请确认密码" :prefix-icon="Key" size="large"/>
            </el-form-item>
            <el-form-item prop="email">
              <el-input v-model="registerForm.email" placeholder="请输入邮箱" :prefix-icon="User" size="large"/>
            </el-form-item>
            <el-form-item prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="请输入昵称" :prefix-icon="User" size="large"/>
            </el-form-item>
            <el-form-item prop="phone">
              <el-input v-model="registerForm.phone" placeholder="请输入手机号" size="large"/>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="login-btn" :loading="loading" @click="handleRegister">
                注 册
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background:
      radial-gradient(ellipse at top left, rgba(30, 64, 175, 0.35) 0%, transparent 55%),
      radial-gradient(ellipse at bottom right, rgba(59, 130, 246, 0.25) 0%, transparent 50%),
      linear-gradient(180deg, #07112a 0%, #0a1636 100%);
}
.login-card {
  width: 420px;
  padding: 40px 36px;
  background: linear-gradient(145deg, rgba(15, 30, 70, 0.9) 0%, rgba(10, 22, 54, 0.95) 100%);
  border: 1px solid rgba(74, 158, 255, 0.3);
  border-radius: 16px;
  box-shadow:
      0 0 0 1px rgba(74, 158, 255, 0.12) inset,
      0 10px 40px rgba(0, 0, 0, 0.6),
      0 0 64px rgba(59, 130, 246, 0.18);
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.login-header h2 {
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(90deg, #60a5fa 0%, #a78bfa 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin: 0 0 8px 0;
}
.login-header p {
  font-size: 13px;
  color: #64748b;
  margin: 0;
}
.login-tabs {
  margin-top: 20px;
}
:deep(.el-tabs__nav) {
  width: 100%;
}
:deep(.el-tabs__item) {
  width: 50%;
  text-align: center;
}
.login-btn {
  width: 100%;
  height: 42px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 10px;
  background: linear-gradient(90deg, #1e3a8a 0%, #3b82f6 100%);
  border: none;
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.35);
}
.login-btn:hover {
  background: linear-gradient(90deg, #2563eb 0%, #60a5fa 100%);
  box-shadow: 0 6px 24px rgba(59, 130, 246, 0.5);
}

/* Element Plus 输入框深色适配 */
:deep(.el-input__wrapper) {
  background: rgba(15, 30, 70, 0.5) !important;
  box-shadow: 0 0 0 1px rgba(74, 158, 255, 0.25) inset !important;
  border-radius: 10px !important;
}
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px rgba(96, 165, 250, 0.9) inset, 0 0 0 3px rgba(59, 130, 246, 0.25) !important;
}
:deep(.el-input__inner) {
  color: #e0e7ff !important;
  height: 42px;
}
:deep(.el-input__inner::placeholder) {
  color: #64748b !important;
}
:deep(.el-tabs__active-bar) {
  background: linear-gradient(90deg, #60a5fa 0%, #a78bfa 100%);
}
:deep(.el-tabs__item.is-active) {
  color: #60a5fa;
}
</style>