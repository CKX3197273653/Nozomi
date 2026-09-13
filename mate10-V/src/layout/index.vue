<script setup>
import {ref} from 'vue'
import {useRoute} from 'vue-router'
import {useUserStore} from "@/stores/user.js";

const route = useRoute()
const sidebarOpen = ref(false)
//从路由配置获取菜单项
const menuItems = route.matched[0].children

const userStore = useUserStore()

</script>

<template>
<div class="layout">
  <!--顶栏-->
  <header class="layout-header">
    <div class="header-left">
      <span class="header-title">智能质检分析平台</span>
    </div>
    <div class="header-right">
      <span class="header-user">{{ userStore.user?.nickname || userStore.user?.username }}</span>
    </div>
  </header>
  <!--触发条-->
  <div class="sidebar-trigger" @mouseenter="sidebarOpen = true"></div>

  <!--隐藏式侧边栏-->
  <aside class="layout-sidebar" :class="{open: sidebarOpen}" @mouseleave="sidebarOpen=false" >
    <el-menu :default-active="route.path" router class="sidebar-menu">
      <el-menu-item v-for="item in menuItems" :key="item.path"
                    :index="'/' + item.path">
        <el-icon><component :is="item.meta.icon" /></el-icon>
        <span>{{item.meta.title}}</span>
      </el-menu-item>
    </el-menu>
  </aside>

  <!--主内容区-->
  <main class="layout-main">
    <router-view/>
  </main>
</div>
</template>

<style scoped>
.layout {
  height: 100vh;
  background: #0a0e27;
  color: #e0e0e0;
}
/*  顶栏 */
.layout-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.03);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #4a9eff;
}

.header-user {
  font-size: 14px;
  color: #a0a0a0;
}
/*  触发条  */
.sidebar-trigger {
  position: fixed;
  left: 0;
  top: 60px;
  width: 8px;
  height: calc(100vh - 60px);
  z-index: 101;
}
/*  侧边栏  */
.layout-sidebar {
  position: fixed;
  left: 0;
  top: 60px;
  width: 200px;
  height: calc(100vh - 60px);
  background: rgba(10, 14, 39, 0.95);
  backdrop-filter: blur(10px);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  transform: translateX(-100%);
  transition: transform 0.3s ease;
  z-index: 100;
}

.layout-sidebar.open {
  transform: translateX(0);
}
.sidebar-menu {
  background: transparent;
  border-right: none;
}

/*  主内容区  */
.layout-main {
  padding: 24px;
  height: calc(100vh - 60px);
  overflow-y: auto;
}
</style>