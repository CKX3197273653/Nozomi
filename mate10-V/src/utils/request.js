import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from "@/router/index.js";

const service = axios.create({
  baseURL: '',
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
      if (token){
          config.headers['Authorization'] = 'Bearer ' + token
      } return config
  },
    error => {
      return Promise.reject(error)
    }
)

// 响应拦截器
service.interceptors.response.use(
    response => {
        const res = response.data
        if (res.code !== 200) {
            ElMessage.error(res.msg || '请求失败')
            return Promise.reject(new Error(res.msg || '请求失败'))
        }
        return res
    },
    error => {
        // 401 = Token 过期或未授权，跳转登录页
        if (error.response && error.response.status === 401) {
            ElMessage.warning('登录已过期，请重新登录')
            localStorage.removeItem('token')
            localStorage.removeItem('user')
            router.push('/login')
        } else {
            ElMessage.error(error.message || '网络错误')
        }
        return Promise.reject(error)
    }
)
//重试机制
service.interceptors.response.use(
    response => response,
    async error => {
        if (error.response?.status === 429){
            await new Promise(resolve => setTimeout(resolve, 1000))
            return service(error.config)
        }
        return Promise.reject(error)
    }
)

export default service
