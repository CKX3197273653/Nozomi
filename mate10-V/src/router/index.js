import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/index.vue'

const routes = [
    {
      path:'/login',
      name:'Login',
      component: ()=> import('@/views/Login.vue'),
      meta:{title:'登录'}
    },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/qc/Dashboard.vue'),
        meta: { title: '质检仪表盘', icon: 'Odometer' }
      },
      {
        path: 'report',
        name: 'Report',
        component: () => import('@/views/qc/Report.vue'),
        meta: { title: '报告管理', icon: 'Document' }
      },
      {
        path: 'defect',
        name: 'Defect',
        component: () => import('@/views/qc/Defect.vue'),
        meta: { title: '缺陷分析', icon: 'Warning' }
      },
      {
        path: 'trend',
        name: 'Trend',
        component: () => import('@/views/qc/Trend.vue'),
        meta: { title: '趋势分析', icon: 'TrendCharts' }
      },
      {
        path: 'visual3d',
        name: 'Visual3D',
        component: () => import('@/views/qc/Visual3D.vue'),
        meta: { title: '3D可视化', icon: 'View' }
      },
      {
        path: 'qa',
        name: 'QA',
        component: () => import('@/views/qc/QA.vue'),
        meta: { title: '知识问答', icon: 'ChatDotRound' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})
//未登录则跳转登录页（return 风格，替代已弃用的 next() 回调）
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.path === '/login') {
    // 已登录则跳转首页
    return token ? '/' : true
  }
  // 未登录跳转登录页
  return token ? true : '/login'
})

export default router
