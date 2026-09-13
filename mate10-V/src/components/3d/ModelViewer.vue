<template>
  <div class="model-wrapper">
    <div ref="modelContainer" class="model-container"></div>
    <!-- 操作提示 -->
    <div class="view-hint">🖱 拖拽旋转 &nbsp;·&nbsp; 滚轮缩放 &nbsp;·&nbsp; 右键平移</div>
    <!-- 控制按钮 -->
    <div class="view-controls">
      <el-button size="small" round @click="toggleAutoRotate">
        {{ autoRotating ? '⏸ 暂停旋转' : '▶ 自动旋转' }}
      </el-button>
      <el-button size="small" round @click="resetView">⟳ 重置视角</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import defaultModelUrl from './Aston_Martin.glb'

const props = defineProps({
  modelPath: {
    type: String,
    default: defaultModelUrl
  },
  autoRotate: {
    type: Boolean,
    default: true
  },
  rotateSpeed: {
    type: Number,
    default: 0.3
  },
  // 标签页是否激活：false 时暂停渲染，true 时恢复
  active: {
    type: Boolean,
    default: true
  }
})

const modelContainer = ref(null)
let scene, camera, renderer, controls, model, ground, animationId
// 保存初始视角（模型适配后），用于重置
let savedCameraPosition = null
let savedTarget = null
// 自动旋转开关（用户可通过按钮切换）
const autoRotating = ref(props.autoRotate)

const initScene = () => {
  const container = modelContainer.value
  const width = container.clientWidth
  const height = container.clientHeight

  // 容器不可见时（宽 0），下一帧重试
  if (width === 0 || height === 0) {
    requestAnimationFrame(initScene)
    return
  }

  // 创建场景
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x1a1a2e)

  // 创建相机
  camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 1000)
  camera.position.set(0, 2, 5)

  // 创建渲染器
  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(width, height)
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFShadowMap
  container.appendChild(renderer.domElement)

  // 添加控制器（支持鼠标拖拽旋转、滚轮缩放、右键平移）
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 1
  controls.maxDistance = 20
  // 自动旋转：相机环绕模型，用户拖拽时自动暂停，松手后恢复
  controls.autoRotate = autoRotating.value
  controls.autoRotateSpeed = props.rotateSpeed * 6

  // 添加灯光
  const ambientLight = new THREE.AmbientLight(0x404040, 0.6)
  scene.add(ambientLight)

  const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8)
  directionalLight.position.set(10, 10, 5)
  directionalLight.castShadow = true
  scene.add(directionalLight)

  // 添加地面（尺寸稍后随模型调整）
  const groundGeometry = new THREE.PlaneGeometry(20, 20)
  const groundMaterial = new THREE.MeshLambertMaterial({ color: 0x909090 })
  ground = new THREE.Mesh(groundGeometry, groundMaterial)
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -1
  ground.receiveShadow = true
  scene.add(ground)

  // 加载模型
  loadModel()
}

const loadModel = () => {
  const loader = new GLTFLoader()

  loader.load(
      props.modelPath,
      (gltf) => {
        model = gltf.scene

        // 启用阴影
        model.traverse((child) => {
          if (child.isMesh) {
            child.castShadow = true
            child.receiveShadow = true
          }
        })

        // ===== 自动适配：根据模型包围盒调整缩放、位置和相机 =====

        // 1. 先按原始尺寸计算缩放因子，把模型统一放大到目标展示尺寸
        const rawBox = new THREE.Box3().setFromObject(model)
        const rawSize = rawBox.getSize(new THREE.Vector3())
        const rawMaxDim = Math.max(rawSize.x, rawSize.y, rawSize.z)
        const targetSize = 2.5 // 目标展示尺寸（最大边约 2.5 单位）
        const scale = targetSize / rawMaxDim
        model.scale.set(scale, scale, scale)

        // 2. 缩放后重新计算包围盒（用于居中、落地、相机适配）
        const box = new THREE.Box3().setFromObject(model)
        const size = box.getSize(new THREE.Vector3())
        const center = box.getCenter(new THREE.Vector3())

        console.log('模型原始尺寸:', rawSize, '放大倍数:', scale)

        // 3. 把模型移到场景原点（居中）
        model.position.sub(center)

        // 4. 让模型底部贴到地面（地面在 y = -1）
        model.position.y += (size.y / 2 - 1)

        // 5. 根据模型大小计算合适的相机距离（视角抬高，俯视展示）
        const maxDim = Math.max(size.x, size.y, size.z)
        const fov = camera.fov * (Math.PI / 180)
        const distance = maxDim / (2 * Math.tan(fov / 2)) * 1.2 // 留 20% 边距
        camera.position.set(distance * 0.5, distance * 0.65, distance)
        camera.lookAt(0, 0, 0)
        // 视觉中心设在车身中部，让车在画面中处于合适高度
        controls.target.set(0, size.y * 0.3, 0)
        controls.update()

        // 保存初始视角，供"重置视角"按钮使用
        savedCameraPosition = camera.position.clone()
        savedTarget = controls.target.clone()

        // 6. 地面随模型大小调整
        const groundScale = Math.max(maxDim * 3, 20) / 20
        ground.scale.set(groundScale, groundScale, 1)

        scene.add(model)
        console.log('3D模型加载成功，相机距离:', distance)
      },
      (progress) => {
        console.log('模型加载进度:', (progress.loaded / progress.total * 100) + '%')
      },
      (error) => {
        console.error('模型加载失败:', error)
      }
  )
}

const animate = () => {
  animationId = requestAnimationFrame(animate)

  // 自动旋转由 OrbitControls 处理（用户拖拽时会自动暂停，松手恢复）
  controls.update()
  renderer.render(scene, camera)
}

// 暂停渲染（标签页切走时调用）
const pause = () => {
  if (animationId) {
    cancelAnimationFrame(animationId)
    animationId = null
  }
}

// 恢复渲染（标签页切回时调用）
const resume = () => {
  if (!renderer) {
    initScene()
  }
  if (!animationId) {
    animate()
  }
}

// 切换自动旋转
const toggleAutoRotate = () => {
  autoRotating.value = !autoRotating.value
  if (controls) {
    controls.autoRotate = autoRotating.value
  }
}

// 重置视角
const resetView = () => {
  if (camera && controls && savedCameraPosition) {
    camera.position.copy(savedCameraPosition)
    controls.target.copy(savedTarget)
    controls.update()
  }
}

const handleResize = () => {
  const container = modelContainer.value
  const width = container.clientWidth
  const height = container.clientHeight

  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height)
}

// 监听标签页激活状态：切走暂停，切回恢复（不销毁场景，避免 WebGL 上下文耗尽）
watch(() => props.active, (val) => {
  if (val) {
    resume()
  } else {
    pause()
  }
})

onMounted(() => {
  if (props.active) {
    initScene()
    animate()
    window.addEventListener('resize', handleResize)
  }
})

onBeforeUnmount(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
  window.removeEventListener('resize', handleResize)
  if (renderer) {
    renderer.dispose()
  }
})
</script>

<style scoped>
.model-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 400px;
}
.model-container {
  width: 100%;
  height: 100%;
}
.view-hint {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  pointer-events: none;
  user-select: none;
}
.view-controls {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 8px;
}
</style>
