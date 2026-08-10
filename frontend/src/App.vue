<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ChatDotRound,
  CircleCheckFilled,
  Close,
  Connection,
  DataAnalysis,
  DocumentChecked,
  Loading,
  Monitor,
  Refresh,
  Search,
  Setting,
  UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'

const phase = ref('login')
const login = ref({ status: 'NOT_INITIALIZED', detail: '正在检查本地浏览器资料' })
const qrImage = ref('')
const friends = ref([])
const avatarUrls = ref({})
const selectedFriends = ref([])
const persistedSelectedFriends = ref([])
const refreshedAt = ref('')
const search = ref('')
const activeView = ref('friends')
const syncing = ref(false)
const savingSelection = ref(false)
const sendingAll = ref(false)
const tasks = ref([])
const history = ref({ date: '', records: [] })
const individualDialogVisible = ref(false)
const browserMonitorVisible = ref(false)
const floatingMonitorVisible = ref(false)
const loggingOut = ref(false)
const noVncUrl = '/novnc/vnc.html?autoconnect=1&resize=scale&view_only=0&path=novnc/websockify'
const individualTarget = ref('')
const individualMessage = ref('续火花')
const runtimeSettings = ref({
  autoSendEnabled: true,
  scheduleTime: '00:02',
  message: '续火花',
  sendMessage: true,
  allowRepeatedSend: false,
  headless: false,
  loginExpiryNotificationEmail: '',
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  smtpFromEmail: '',
  smtpStarttls: true,
  loginExpiryEmailSubject: 'TiktokSparkFlow：抖音登录已失效',
  loginExpiryEmailContent: '自动任务检测到抖音登录已失效。为避免自动续火花失效，请尽快打开 TiktokSparkFlow 进行登录。',
})
const savedHeadlessMode = ref(false)
const savingRuntimeSettings = ref(false)
const sendingTestEmail = ref(false)
let statusTimer
let qrTimer
let qrRequestPending = false
let taskTimer
const notifiedVerificationTasks = new Set()
let verificationNoticeShown = false

const selectedSet = computed(() => new Set(selectedFriends.value))
const selectionDirty = computed(() => {
  const current = [...new Set(selectedFriends.value)].sort()
  const persisted = [...new Set(persistedSelectedFriends.value)].sort()
  return current.length !== persisted.length || current.some((name, index) => name !== persisted[index])
})
const filteredFriends = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return friends.value.filter((name) => !keyword || name.toLowerCase().includes(keyword))
})
const currentStatusLabel = computed(() => {
  const labels = {
    NOT_INITIALIZED: '等待初始化',
    LOGIN_REQUIRED: '需要扫码',
    LOGGED_IN: '已登录',
    BUSY: '浏览器繁忙',
  }
  return labels[login.value.status] ?? '状态未知'
})

async function request(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options.headers ?? {}) },
    ...options,
  })
  const body = await response.json().catch(() => null)
  if (!response.ok || !body || body.code !== 200) {
    throw new Error(body?.message || `请求失败（${response.status}）`)
  }
  return body.data
}

async function refreshLoginStatus() {
  try {
    login.value = await request('/api/session/status')
    if (login.value.status === 'LOGGED_IN') {
      phase.value = 'workspace'
      stopLoginPolling()
      await loadWorkspace()
      return
    }
    phase.value = 'login'
  } catch (error) {
    phase.value = 'login'
    login.value = { status: 'LOGIN_REQUIRED', detail: error.message }
    notifyRiskVerification(error.message)
  }
}

function notifyRiskVerification(detail) {
  if (!detail?.includes('身份验证') || verificationNoticeShown) return
  verificationNoticeShown = true
  browserMonitorVisible.value = true
  ElNotification.error({
    title: '需要手动身份验证',
    message: '抖音触发风控，请在已打开的浏览器实时画面中完成验证。',
    duration: 0,
  })
}

async function logout() {
  try {
    await ElMessageBox.confirm(
      '退出后会关闭当前浏览器并清除本地浏览器资料，需要重新扫码登录。是否继续？',
      '确认退出登录',
      { confirmButtonText: '退出登录', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  loggingOut.value = true
  try {
    login.value = await request('/api/session/logout', { method: 'POST' })
    browserMonitorVisible.value = false
    floatingMonitorVisible.value = false
    qrImage.value = ''
    phase.value = 'login'
    startLoginPolling()
    ElMessage.success('已退出登录，请重新扫码')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loggingOut.value = false
  }
}

async function refreshQr() {
  if (phase.value !== 'login' || qrRequestPending) return
  qrRequestPending = true
  try {
    const data = await request('/api/session/qr')
    if (data.status === 'BUSY') {
      login.value = { status: data.status, detail: data.detail }
      return
    }
    login.value = { status: data.status, detail: data.detail }
    if (data.status === 'LOGGED_IN') {
      qrImage.value = ''
      await refreshLoginStatus()
      return
    }
    if (data.imageData) qrImage.value = data.imageData
  } catch (error) {
    login.value = { status: 'LOGIN_REQUIRED', detail: error.message }
    notifyRiskVerification(error.message)
  } finally {
    qrRequestPending = false
  }
}

function startLoginPolling() {
  stopLoginPolling()
  refreshQr()
  qrTimer = window.setInterval(refreshQr, 3500)
}

function stopLoginPolling() {
  window.clearInterval(statusTimer)
  window.clearInterval(qrTimer)
  statusTimer = undefined
  qrTimer = undefined
}

async function loadWorkspace() {
  try {
    await Promise.all([loadCachedFriends(), loadTasks(), loadRuntimeSettings()])
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function loadCachedFriends() {
  const data = await request('/api/friends/local')
  applyFriends(data)
}

function applyFriends(data) {
  friends.value = data.friends ?? []
  avatarUrls.value = data.avatars ?? {}
  selectedFriends.value = data.selectedFriends ?? []
  persistedSelectedFriends.value = [...selectedFriends.value]
  refreshedAt.value = data.refreshedAt ?? ''
}

async function syncFriends() {
  syncing.value = true
  try {
    const data = await request('/api/friends')
    applyFriends(data)
    ElMessage.success(`已同步 ${data.friends.length} 位好友`)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    syncing.value = false
  }
}

function isSelected(name) {
  return selectedSet.value.has(name)
}

function toggleFriend(name, checked) {
  const next = new Set(selectedFriends.value)
  checked ? next.add(name) : next.delete(name)
  selectedFriends.value = [...next]
}

async function saveSelection() {
  savingSelection.value = true
  try {
    const data = await request('/api/friends/selection', {
      method: 'PUT',
      body: JSON.stringify({ selectedFriends: selectedFriends.value }),
    })
    selectedFriends.value = data.selectedFriends
    persistedSelectedFriends.value = [...data.selectedFriends]
    ElMessage.success('续火花好友已保存')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    savingSelection.value = false
  }
}

async function sendToAll() {
  if (!selectedFriends.value.length) {
    ElMessage.warning('请先选择需要续火花的好友')
    return
  }
  const message = runtimeSettings.value.message.trim()
  if (!message) {
    ElMessage.warning('请输入要发送的消息内容')
    return
  }
  try {
    await ElMessageBox.confirm(
      `即将向 ${selectedFriends.value.length} 个已选会话发送“${message}”，是否继续？`,
      '确认创建发送任务',
      { confirmButtonText: '创建任务', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  sendingAll.value = true
  try {
    const task = await request('/api/send-tasks', {
      method: 'POST',
      body: JSON.stringify({ message, sendMessage: true }),
    })
    ElMessage.success(`发送任务已创建：${task.taskId}`)
    await loadTasks()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    sendingAll.value = false
  }
}

function openIndividualSend(name) {
  individualTarget.value = name
  individualMessage.value = '续火花'
  individualDialogVisible.value = true
}

async function sendIndividually() {
  if (!individualTarget.value || !individualMessage.value.trim()) return
  const target = individualTarget.value
  try {
    const result = await request('/api/messages/send', {
      method: 'POST',
      body: JSON.stringify({
        targetNickname: target,
        message: individualMessage.value.trim(),
        sendMessage: true,
      }),
    })
    individualDialogVisible.value = false
    if (result.sent) {
      ElMessage.success(`已成功发送给 ${target}`)
      await loadHistory()
      return
    }
    ElMessage.warning(result.detail || `未向 ${target} 发送消息`)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function loadTasks() {
  try {
    tasks.value = await request('/api/send-tasks')
    const waiting = tasks.value.find((task) => task.status === 'WAITING_FOR_VERIFICATION')
    if (waiting && !notifiedVerificationTasks.has(waiting.taskId)) {
      notifiedVerificationTasks.add(waiting.taskId)
      notifyRiskVerification('身份验证')
    }
  } catch (error) {
    if (phase.value === 'workspace') ElMessage.error(error.message)
  }
}

async function loadHistory() {
  try {
    history.value = await request('/api/send-history')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function switchView(view) {
  activeView.value = view
  if (view === 'friends' || view === 'tasks') await loadTasks()
  if (view === 'history') await loadHistory()
  if (view === 'settings') await loadRuntimeSettings()
}

function selectAllFriends() {
  selectedFriends.value = [...friends.value]
}

function clearSelection() {
  selectedFriends.value = []
}

async function loadRuntimeSettings() {
  const data = await request('/api/runtime-settings')
  runtimeSettings.value = {
    ...runtimeSettings.value,
    ...data,
    smtpPort: data.smtpPort || 587,
    loginExpiryEmailSubject: data.loginExpiryEmailSubject || 'TiktokSparkFlow：抖音登录已失效',
    loginExpiryEmailContent: data.loginExpiryEmailContent
      || '自动任务检测到抖音登录已失效。为避免自动续火花失效，请尽快打开 TiktokSparkFlow 进行登录。'
  }
  savedHeadlessMode.value = runtimeSettings.value.headless ?? false
}

async function saveRuntimeSettings() {
  const message = runtimeSettings.value.message.trim()
  if (!message) {
    ElMessage.warning('请输入自动任务消息内容')
    return
  }
  const modeChanged = runtimeSettings.value.headless !== undefined
    && runtimeSettings.value.headless !== savedHeadlessMode.value
  const requiresRelogin = modeChanged
    && savedHeadlessMode.value === false
    && runtimeSettings.value.headless === true
  if (requiresRelogin) {
    try {
      await ElMessageBox.confirm(
        '从有头模式切换为无头模式会安全关闭当前 Playwright 会话并按新模式重启，且会导致登录失效，需要重新扫码登录。是否继续？',
        '确认切换浏览器模式',
        { confirmButtonText: '确认切换', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }
  savingRuntimeSettings.value = true
  try {
    runtimeSettings.value = await request('/api/runtime-settings', {
      method: 'PUT',
      body: JSON.stringify({ ...runtimeSettings.value, message }),
    })
    savedHeadlessMode.value = runtimeSettings.value.headless ?? false
    ElMessage.success('运行配置已保存并立即生效')
    if (requiresRelogin) {
      qrImage.value = ''
      login.value = { status: 'LOGIN_REQUIRED', detail: '浏览器模式已切换，请重新扫码登录' }
      phase.value = 'login'
      startLoginPolling()
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    savingRuntimeSettings.value = false
  }
}

async function sendTestEmail() {
  sendingTestEmail.value = true
  try {
    await request('/api/runtime-settings/test-email', { method: 'POST' })
    ElMessage.success('测试邮件已发送，请检查收件箱和垃圾邮件箱')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    sendingTestEmail.value = false
  }
}

function taskType(status) {
  return ({ COMPLETED: 'success', PARTIAL_FAILED: 'warning', FAILED: 'danger', RUNNING: 'primary', WAITING_FOR_LOGIN: 'warning', WAITING_FOR_VERIFICATION: 'danger', CANCELLED: 'info' })[status] || 'info'
}

onMounted(async () => {
  await refreshLoginStatus()
  taskTimer = window.setInterval(loadTasks, 3000)
  if (phase.value === 'login') startLoginPolling()
})

onBeforeUnmount(() => {
  stopLoginPolling()
  window.clearInterval(taskTimer)
})
</script>

<template>
  <main class="app-shell">
    <section v-if="phase === 'booting'" class="loading-screen">
      <el-icon class="spinner"><Loading /></el-icon>
      <p>正在连接续火花服务…</p>
    </section>

    <section v-else-if="phase === 'login'" class="login-workspace">
      <div class="login-copy">
        <div class="brand-mark"><ChatDotRound /></div>
        <p class="eyebrow">抖音好友自动续火花</p>
        <h1>浏览器资料<br /><em>还未就绪</em></h1>
        <p class="muted">无需单独创建账号。使用抖音 App 扫描右侧二维码后，浏览器资料会安全保存在本机。</p>
        <div class="login-status">
          <el-icon><Connection /></el-icon>
          <span>{{ currentStatusLabel }} · {{ login.detail }}</span>
        </div>
      </div>
      <div class="login-qr-column">
      <div class="qr-card">
        <div class="qr-card-head">
          <span>扫码连接</span>
          <el-tag size="small" effect="plain">自动刷新</el-tag>
        </div>
        <div class="qr-stage">
          <img v-if="qrImage" :src="qrImage" alt="抖音登录二维码" />
          <el-icon v-else class="spinner qr-spinner"><Loading /></el-icon>
        </div>
        <p>打开抖音 App，点击右上角扫一扫</p>
        <el-button plain :icon="Refresh" @click="refreshQr">刷新二维码</el-button>
      </div>
      <div class="login-monitor-card">
        <div>
          <strong>浏览器实时画面</strong>
          <span>扫码时可查看并直接操作容器中的浏览器。</span>
        </div>
        <el-button class="login-monitor-action" type="primary" size="large" @click="browserMonitorVisible = true">
          <span class="monitor-button-content">
            <el-icon><Monitor /></el-icon>
            <span>打开浏览器画面</span>
          </span>
        </el-button>
      </div>
      </div>
    </section>

    <section v-else class="workspace">
      <aside class="sidebar">
        <div class="brand">
          <div class="brand-mark"><ChatDotRound /></div>
          <div><strong>续火花</strong><span>好友管理台</span></div>
        </div>
        <nav>
          <button :class="{ active: activeView === 'friends' }" @click="switchView('friends')"><el-icon><UserFilled /></el-icon>好友管理</button>
          <button :class="{ active: activeView === 'tasks' }" @click="switchView('tasks')"><el-icon><DocumentChecked /></el-icon>任务中心</button>
          <button :class="{ active: activeView === 'history' }" @click="switchView('history')"><el-icon><DataAnalysis /></el-icon>发送历史</button>
          <button :class="{ active: activeView === 'monitor' }" @click="switchView('monitor')"><el-icon><Monitor /></el-icon>浏览器监控</button>
          <button :class="{ active: activeView === 'settings' }" @click="switchView('settings')"><el-icon><Setting /></el-icon>运行配置</button>
        </nav>
        <div class="sidebar-foot"><span class="online-dot"></span>浏览器已连接</div>
      </aside>

      <section class="content">
        <header class="topbar">
          <div>
            <p class="eyebrow">{{ activeView === 'friends' ? '好友管理' : activeView === 'tasks' ? '任务中心' : activeView === 'history' ? '发送历史' : activeView === 'monitor' ? '浏览器监控' : '运行配置' }}</p>
            <h2>{{ activeView === 'friends' ? '选择需要续火花的好友' : activeView === 'tasks' ? '查看任务状态与发送时间' : activeView === 'history' ? '查看实际成功发送流水' : activeView === 'monitor' ? '查看当前 Playwright 浏览器画面' : '单账号自动续火花服务' }}</h2>
          </div>
          <div class="topbar-actions" style="display: flex; align-items: center; gap: 12px">
            <div class="profile-pill"><el-icon><CircleCheckFilled /></el-icon>本地 Profile 已就绪</div>
            <el-button type="danger" plain :loading="loggingOut" @click="logout">退出登录</el-button>
          </div>
        </header>

        <template v-if="activeView === 'friends'">
        <div class="summary-grid">
          <article class="summary-card dark">
            <span>已缓存好友</span><strong>{{ friends.length }}</strong><small>{{ refreshedAt ? `更新于 ${refreshedAt}` : '尚未同步好友列表' }}</small>
          </article>
          <article class="summary-card accent">
            <span>续火花名单</span><strong>{{ selectedFriends.length }}</strong><small>保存后用于一键发送和每日任务</small>
          </article>
          <article class="summary-card light">
            <span>当前状态</span><strong>就绪</strong><small>抖音网页登录有效</small>
          </article>
        </div>

        <div class="dashboard-grid">
          <article class="panel friends-panel">
            <div class="panel-head">
              <div><h3>全部好友</h3><p>优先读取本地缓存；需要更新时再同步抖音网页。</p></div>
              <div class="panel-actions">
                <el-button :loading="syncing" :icon="Refresh" @click="syncFriends">同步好友</el-button>
              </div>
            </div>
            <div class="table-toolbar">
              <el-input v-model="search" :prefix-icon="Search" placeholder="搜索昵称" clearable />
              <span>{{ filteredFriends.length }} 位可见</span>
              <div class="selection-actions">
                <el-button text :disabled="!friends.length" @click="selectAllFriends">全部选择</el-button>
                <el-button text :disabled="!selectedFriends.length" @click="clearSelection">取消选择</el-button>
              </div>
            </div>
            <el-empty v-if="!friends.length" description="本地尚无好友缓存，请先同步好友列表">
              <el-button type="primary" :loading="syncing" @click="syncFriends">从抖音同步好友</el-button>
            </el-empty>
            <el-scrollbar v-else height="408px">
              <div class="friend-list">
                <div v-for="name in filteredFriends" :key="name" class="friend-row">
                  <el-avatar v-if="avatarUrls[name]" :src="avatarUrls[name]" class="friend-avatar" />
                  <div v-else class="friend-avatar">{{ name.slice(0, 1) }}</div>
                  <span>{{ name }}</span>
                  <div class="friend-row-actions">
                    <el-button link type="primary" @click="openIndividualSend(name)">单独发送</el-button>
                    <el-checkbox :model-value="isSelected(name)" @change="toggleFriend(name, $event)">续火花</el-checkbox>
                  </div>
                </div>
              </div>
            </el-scrollbar>
          </article>

          <aside class="right-column">
            <article class="panel selected-panel">
              <div class="panel-head">
                <div><h3>续火花名单</h3><p>已选 {{ selectedFriends.length }} 位好友{{ selectionDirty ? ' · 有未保存修改' : ' · 已保存' }}</p></div>
                <el-button type="primary" :disabled="!selectionDirty" :loading="savingSelection" @click="saveSelection">保存名单</el-button>
              </div>
              <el-scrollbar height="220px">
                <div v-if="selectedFriends.length" class="selected-grid">
                  <div v-for="name in selectedFriends" :key="name" class="selected-friend">
                    <el-avatar v-if="avatarUrls[name]" :src="avatarUrls[name]" class="selected-avatar" />
                    <span v-else class="selected-avatar">{{ name.slice(0, 1) }}</span>
                    <span>{{ name }}</span>
                    <button type="button" class="remove-mark" :title="`移除 ${name}`" @click="toggleFriend(name, false)"><el-icon><Close /></el-icon></button>
                  </div>
                </div>
                <el-empty v-else :image-size="58" description="还没有选择好友" />
              </el-scrollbar>
              <el-button type="primary" size="large" :loading="sendingAll" :icon="ChatDotRound" class="send-all" @click="sendToAll">一键发送给已选好友</el-button>
            </article>

            <article class="panel tasks-panel">
              <div class="panel-head"><div><h3>最近发送任务</h3><p>进入好友管理或点击刷新时更新</p></div><el-button link :icon="Refresh" @click="loadTasks">刷新</el-button></div>
              <el-empty v-if="!tasks.length" :image-size="56" description="暂无发送任务" />
              <div v-else class="task-list">
                <div v-for="task in tasks.slice(0, 4)" :key="task.taskId" class="task-row">
                  <div><strong>{{ task.message || '仅选择好友' }}</strong><span>{{ task.createdAt }} · {{ task.completed }}/{{ task.total }} · {{ task.currentTarget || task.detail }}</span></div>
                  <el-tag :type="taskType(task.status)" effect="plain">{{ task.status }}</el-tag>
                </div>
              </div>
            </article>
          </aside>
        </div>
        </template>

        <section v-else-if="activeView === 'tasks'" class="standalone-panel panel">
          <div class="panel-head"><div><h3>任务中心</h3><p>查看手动、重试和每日自动任务的执行状态与发送时间。</p></div><el-button :icon="Refresh" @click="loadTasks">刷新任务</el-button></div>
          <el-empty v-if="!tasks.length" description="暂无发送任务" />
          <el-table v-else :data="tasks" class="task-table">
            <el-table-column prop="taskId" label="任务编号" min-width="215" />
            <el-table-column prop="message" label="消息内容" min-width="120" />
            <el-table-column prop="createdAt" label="创建时间" min-width="180" />
            <el-table-column prop="startedAt" label="开始发送" min-width="180"><template #default="scope">{{ scope.row.startedAt || '-' }}</template></el-table-column>
            <el-table-column label="进度" width="110"><template #default="scope">{{ scope.row.completed }}/{{ scope.row.total }}</template></el-table-column>
            <el-table-column label="状态" width="140"><template #default="scope"><el-tag :type="taskType(scope.row.status)" effect="plain">{{ scope.row.status }}</el-tag></template></el-table-column>
            <el-table-column prop="detail" label="说明" min-width="220" />
          </el-table>
        </section>

        <section v-else-if="activeView === 'history'" class="standalone-panel panel">
          <div class="panel-head"><div><h3>发送历史</h3><p>{{ history.date ? `${history.date} 的实际成功发送流水（含单独发送）` : '默认显示今天的记录' }}</p></div><el-button :icon="Refresh" @click="loadHistory">刷新记录</el-button></div>
          <el-empty v-if="!history.records?.length" description="当天暂无成功发送记录" />
          <el-table v-else :data="history.records" class="task-table">
            <el-table-column prop="targetNickname" label="好友" min-width="160" />
            <el-table-column prop="message" label="消息内容" min-width="180" />
            <el-table-column prop="sentAt" label="发送时间" min-width="240" />
            <el-table-column prop="taskId" label="任务编号" min-width="220" />
          </el-table>
        </section>

        <section v-else-if="activeView === 'monitor'" class="panel browser-monitor-panel">
          <div class="panel-head">
            <div><h3>浏览器实时画面</h3><p>可在此观察Playwright浏览器；也可以打开悬浮窗后继续切换其他功能页。</p></div>
            <el-button type="primary" :icon="Monitor" @click="floatingMonitorVisible = true">打开悬浮窗</el-button>
          </div>
          <iframe v-if="!floatingMonitorVisible" class="browser-monitor-frame" :src="noVncUrl" title="浏览器实时画面" />
          <div v-else class="monitor-floating-hint">浏览器画面已在悬浮窗中打开，可切换到其他标签页继续查看。</div>
        </section>

        <section v-else class="panel runtime-settings-panel">
          <div class="panel-head"><div><h3>运行配置</h3><p>保存后立即写入本地运行配置并影响每日自动发送，无需编辑 application.yaml。</p></div></div>
          <el-form label-position="top" class="runtime-settings-form">
            <el-form-item label="启用每日自动任务"><el-switch v-model="runtimeSettings.autoSendEnabled" active-text="已启用" inactive-text="已关闭" /></el-form-item>
            <el-form-item label="每日执行时间"><el-time-picker v-model="runtimeSettings.scheduleTime" value-format="HH:mm" format="HH:mm" :clearable="false" /></el-form-item>
            <el-form-item label="自动发送消息"><el-input v-model="runtimeSettings.message" maxlength="100" show-word-limit /></el-form-item>
            <el-form-item label="真实发送消息"><el-switch v-model="runtimeSettings.sendMessage" active-text="真实发送" inactive-text="仅选择好友" /></el-form-item>
            <el-form-item label="允许当天重复发送">
              <el-switch v-model="runtimeSettings.allowRepeatedSend" active-text="允许重复" inactive-text="当天仅一次" />
              <div class="form-hint">关闭时，同一好友当天已确认发送成功后，后续单发、群发和定时任务都会自动跳过。</div>
            </el-form-item>
            <el-form-item label="浏览器模式">
              <el-switch v-model="runtimeSettings.headless" active-text="无头运行" inactive-text="有头运行" />
              <div class="form-hint">默认使用有头模式；从有头切换到无头会导致登录失效，需要重新扫码。</div>
            </el-form-item>
            <el-form-item label="登录失效提醒邮箱">
              <el-input v-model="runtimeSettings.loginExpiryNotificationEmail" placeholder="name@example.com" maxlength="254" />
              <div class="form-hint">检测到登录失效时仅发送一次提醒，重新登录后恢复下一次通知。</div>
            </el-form-item>
            <el-form-item label="SMTP 主机">
              <el-input v-model="runtimeSettings.smtpHost" placeholder="发送邮件服务器，例如 smtp.qq.com" />
            </el-form-item>
            <el-form-item label="SMTP 端口">
              <el-input-number v-model="runtimeSettings.smtpPort" :min="1" :max="65535" controls-position="right" />
              <div class="form-hint smtp-inline-hint">填写邮件服务商提供的端口，一般是 465 或 587。</div>
            </el-form-item>
            <el-form-item label="SMTP 用户名">
              <el-input v-model="runtimeSettings.smtpUsername" placeholder="通常是发件邮箱地址" />
            </el-form-item>
            <el-form-item label="SMTP 授权码">
              <el-input v-model="runtimeSettings.smtpPassword" type="password" show-password autocomplete="new-password" placeholder="请前往对应邮箱服务中获取授权码" />
            </el-form-item>
            <el-form-item label="发件邮箱（可选）">
              <el-input v-model="runtimeSettings.smtpFromEmail" placeholder="收件人看到的发件地址；通常与 SMTP 用户名相同" />
            </el-form-item>
            <el-form-item label="使用 STARTTLS">
              <el-switch v-model="runtimeSettings.smtpStarttls" active-text="启用" inactive-text="关闭" />
              <div class="form-hint smtp-inline-hint">按服务商要求决定是否开启；一般使用 587 端口时开启。</div>
            </el-form-item>
            <el-form-item label="登录失效邮件标题">
              <el-input v-model="runtimeSettings.loginExpiryEmailSubject" maxlength="120" show-word-limit />
            </el-form-item>
            <el-form-item label="登录失效邮件正文">
              <el-input v-model="runtimeSettings.loginExpiryEmailContent" type="textarea" :rows="4" maxlength="1000" show-word-limit />
            </el-form-item>
            <el-button type="primary" :loading="savingRuntimeSettings" @click="saveRuntimeSettings">保存运行配置</el-button>
            <el-button :loading="sendingTestEmail" @click="sendTestEmail">发送测试邮件</el-button>
          </el-form>
        </section>
      </section>
    </section>

    <el-dialog v-model="individualDialogVisible" title="单独发送消息" width="420px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="好友"><el-input v-model="individualTarget" disabled /></el-form-item>
        <el-form-item label="消息内容"><el-input v-model="individualMessage" maxlength="100" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="individualDialogVisible = false">取消</el-button><el-button type="primary" @click="sendIndividually">确认发送</el-button></template>
    </el-dialog>

    <el-dialog v-model="browserMonitorVisible" title="浏览器实时画面" width="min(1280px, 92vw)" draggable destroy-on-close>
      <p class="monitor-dialog-tip">可在此查看扫码登录过程和当前网页状态。关闭窗口不会停止浏览器。</p>
      <iframe class="browser-monitor-frame dialog-monitor-frame" :src="noVncUrl" title="浏览器实时画面" />
    </el-dialog>

    <el-dialog v-model="floatingMonitorVisible" class="floating-monitor-dialog" style="height: min(70vh, 720px)" title="浏览器悬浮监控" width="min(860px, 78vw)" top="10vh" draggable :modal="false" :modal-penetrable="true" :close-on-click-modal="false" destroy-on-close>
      <p class="monitor-dialog-tip">拖动标题栏可移动窗口；切换页面不会关闭此窗口。</p>
      <iframe class="browser-monitor-frame floating-monitor-frame" :src="noVncUrl" title="浏览器悬浮监控" />
    </el-dialog>
  </main>
</template>
