<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import ModelViewer from '@/components/3d/ModelViewer.vue'

// ==================== DOM ====================
const sceneContainerRef = ref(null)

// ==================== 标签页控制 ====================
const activeTab = ref('dots')

// ==================== Three.js 变量 ====================
let scene, camera, renderer, controls, timer
let dotMesh = null
let dotCount = 0
let car = null
let animationId = null

// ==================== 点阵参数 ====================
const GRID_N = 31
const GRID_SPACING = 0.85
const DOT_SIZE = 0.16
const WAVE_SPEED = 1.9
const RIPPLE_FREQ = Math.PI * 4
const FLOAT_AMP = 0.25

// ==================== 场景初始化 ====================
const initScene = () => {
  const container = sceneContainerRef.value
  const width = container.clientWidth
  const height = container.clientHeight

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x07112a)
  scene.fog = new THREE.Fog(0x07112a, 28, 60)

  camera = new THREE.PerspectiveCamera(55, width / height, 0.1, 200)
  camera.position.set(24, 16, 26)
  camera.lookAt(0, 0, 0)

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(width, height)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  container.appendChild(renderer.domElement)

  timer = new THREE.Timer()

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.autoRotate = true
  controls.autoRotateSpeed = 0.8
  controls.maxDistance = 55
  controls.minDistance = 10

  // 添加环境光
  const ambientLight = new THREE.AmbientLight(0x404040, 0.8)
  scene.add(ambientLight)

  // 添加方向光
  const directionalLight = new THREE.DirectionalLight(0xffffff, 1)
  directionalLight.position.set(10, 10, 5)
  scene.add(directionalLight)

  createDotMatrix()
  createCarModel()
}

// ==================== 创建点阵 ====================
const createDotMatrix = () => {
  const geometry = new THREE.SphereGeometry(DOT_SIZE, 16, 16)
  const material = new THREE.MeshPhongMaterial({
    color: 0x4a9eff,
    emissive: 0x4a9eff,
    emissiveIntensity: 0.2,
    transparent: true,
    opacity: 0.8
  })

  dotMesh = new THREE.Group()

  for (let i = 0; i < GRID_N; i++) {
    for (let j = 0; j < GRID_N; j++) {
      const dot = new THREE.Mesh(geometry, material)
      const x = (i - GRID_N / 2) * GRID_SPACING
      const z = (j - GRID_N / 2) * GRID_SPACING
      dot.position.set(x, 0, z)
      dot.userData = { originalY: 0, i, j }
      dotMesh.add(dot)
      dotCount++
    }
  }

  scene.add(dotMesh)
}

// ==================== 创建车辆模型 ====================
const createCarModel = () => {
  // 这里可以添加你的车辆模型创建逻辑
  // 或者等待3D模型加载
  car = new THREE.Group()

  // 临时添加一个简单的立方体作为占位符
  const geometry = new THREE.BoxGeometry(2, 1, 4)
  const material = new THREE.MeshPhongMaterial({ color: 0xff4444 })
  const cube = new THREE.Mesh(geometry, material)
  car.add(cube)

  car.position.set(0, 0.5, 0)
  scene.add(car)
}

// ==================== 动画循环 ====================
const animate = () => {
  animationId = requestAnimationFrame(animate)

  timer.update()
  const elapsedTime = timer.getElapsed()

  if (dotMesh) {
    dotMesh.children.forEach((dot, index) => {
      const { i, j } = dot.userData
      const distance = Math.sqrt(
          Math.pow(i - GRID_N / 2, 2) + Math.pow(j - GRID_N / 2, 2)
      )

      const wave = Math.sin(elapsedTime * WAVE_SPEED - distance * RIPPLE_FREQ) * FLOAT_AMP
      const float = Math.sin(elapsedTime * 2 + index * 0.1) * 0.1

      dot.position.y = dot.userData.originalY + wave + float
    })
  }

  if (car) {
    car.position.x = Math.sin(elapsedTime * 0.5) * 10
    car.rotation.y = Math.sin(elapsedTime * 0.3) * 0.2
  }

  controls.update()
  renderer.render(scene, camera)
}

// ==================== 处理窗口大小变化 ====================
const handleResize = () => {
  const container = sceneContainerRef.value
  const width = container.clientWidth
  const height = container.clientHeight

  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height)
}

// ==================== 生命周期钩子 ====================
// 点阵场景只初始化一次，切换标签时暂停/恢复，避免 WebGL 上下文耗尽
onMounted(() => {
  initScene()
  animate()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
  window.removeEventListener('resize', handleResize)
  if (renderer) {
    renderer.dispose()
    // 移除残留的 canvas DOM
    const canvas = renderer.domElement
    if (canvas && canvas.parentNode) {
      canvas.parentNode.removeChild(canvas)
    }
  }
})

// ==================== 标签页切换处理 ====================
// 切走暂停点阵动画，切回恢复（不销毁场景）
const handleTabChange = (tab) => {
  if (tab === 'dots') {
    // 切回点阵：恢复动画
    if (!animationId) {
      animate()
    }
  } else if (tab === 'model') {
    // 切到模型：暂停点阵动画，释放 GPU
    if (animationId) {
      cancelAnimationFrame(animationId)
      animationId = null
    }
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>3D可视化</h2>
      <p class="page-desc">点阵动画 & 3D模型展示</p>
    </div>

    <el-tabs v-model="activeTab" class="demo-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="点阵动画" name="dots">
        <div class="scene-container" ref="sceneContainerRef">
          <!-- 点阵动画将在这里渲染 -->
        </div>
      </el-tab-pane>

      <el-tab-pane label="3D模型" name="model" lazy>
        <div class="scene-container">
          <ModelViewer
              :active="activeTab === 'model'"
              :auto-rotate="true"
              :rotate-speed="0.3"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-container {
  padding: 24px;
  background: linear-gradient(135deg, #0a0e27 0%, #0a1636 100%);
  min-height: 100vh;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 600;
  color: #4a9eff;
  margin: 0 0 6px 0;
}

.page-desc {
  font-size: 13px;
  color: #a0a0a0;
  margin: 0;
}

.demo-tabs {
  background: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
  padding: 20px;
}

.scene-container {
  height: 600px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(74, 158, 255, 0.15);
  border-radius: 12px;
  overflow: hidden;
}
</style>