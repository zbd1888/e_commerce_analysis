<template>
  <div class="ai-assistant">
    <section class="chat-panel">
      <!-- 顶部标题栏 -->
      <div class="chat-header">
        <div class="header-left">
          <div class="header-icon">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#00ffff" stroke-width="2">
              <path d="M12 2a4 4 0 014 4c0 2-2 3-4 3s-4-1-4-3 2-4 4-4z"/>
              <path d="M6 12a4 4 0 014-4h4a4 4 0 014 4v2a4 4 0 01-4 4h-4a4 4 0 01-4-4v-2z"/>
              <path d="M12 14v4M9 18h6"/>
              <circle cx="12" cy="20" r="2"/>
            </svg>
          </div>
          <div class="header-text">
            <h2>AI 选品分析</h2>
            <span class="header-subtitle">DeepSeek V4 Flash · 实时分析</span>
          </div>
          <span class="status-badge">实时数据</span>
        </div>
        <div class="header-actions">
          <el-tooltip content="清空当前对话" placement="bottom">
            <el-button text circle @click="clearMessages" :disabled="activeMessages.length === 0 && !loading">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
              </svg>
            </el-button>
          </el-tooltip>
        </div>
      </div>

      <!-- 对话区域 -->
      <div class="messages-area" ref="messagesRef">
        <!-- 空状态：快捷入口 -->
        <div v-if="activeMessages.length === 0" class="empty-state">
        <div class="welcome-graphic">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="#0066ff" stroke-width="1.5">
            <path d="M12 2a4 4 0 014 4c0 2-2 3-4 3s-4-1-4-3 2-4 4-4z"/>
            <path d="M6 12a4 4 0 014-4h4a4 4 0 014 4v2a4 4 0 01-4 4h-4a4 4 0 01-4-4v-2z"/>
            <path d="M12 14v4M9 18h6"/>
            <circle cx="12" cy="20" r="2"/>
            <path d="M17 9l2-2M7 9L5 7M12 5V3" stroke-linecap="round"/>
          </svg>
        </div>
        <h3 class="welcome-title">你好，我是你的 AI 选品分析</h3>
        <p class="welcome-desc">我可以分析品类、预测销量、推荐定价策略，帮你做出更好的选品决策</p>
        <div class="suggestion-grid">
          <div
            v-for="item in quickActions"
            :key="item.label"
            class="suggestion-chip"
            :title="item.prompt"
            @click="sendQuick(item.prompt)"
          >
            <span class="chip-icon">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="msg in activeMessages" :key="msg.id" class="message-row" :class="msg.role">
        <div class="message-avatar">
          <span v-if="msg.role === 'user'">👤</span>
          <span v-else class="ai-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#00ffff" stroke-width="2">
              <path d="M12 2a4 4 0 014 4c0 2-2 3-4 3s-4-1-4-3 2-4 4-4z"/>
              <path d="M6 12a4 4 0 014-4h4a4 4 0 014 4v2a4 4 0 01-4 4h-4a4 4 0 01-4-4v-2z"/>
              <circle cx="12" cy="20" r="2"/>
            </svg>
          </span>
        </div>
        <div class="message-content">
          <div class="message-sender">{{ msg.role === 'user' ? '我' : 'AI 选品分析' }}</div>
          <div class="message-bubble" v-html="msg.role === 'assistant' ? renderContent(msg.content) : escapeHtml(msg.content)"></div>
        </div>
      </div>

      <!-- 加载动画 -->
      <div v-if="loading && loadingConversationId === currentConversationId" class="message-row assistant">
        <div class="message-avatar">
          <span class="ai-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#00ffff" stroke-width="2">
              <path d="M12 2a4 4 0 014 4c0 2-2 3-4 3s-4-1-4-3 2-4 4-4z"/>
              <path d="M6 12a4 4 0 014-4h4a4 4 0 014 4v2a4 4 0 01-4 4h-4a4 4 0 01-4-4v-2z"/>
              <circle cx="12" cy="20" r="2"/>
            </svg>
          </span>
        </div>
        <div class="message-content">
          <div class="message-sender">AI 选品分析</div>
          <div class="message-bubble thinking">
            <div class="thinking-bar">
              <span class="bar-segment"></span>
              <span class="bar-segment"></span>
              <span class="bar-segment"></span>
              <span class="bar-text">正在分析数据...</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部输入区域 -->
    <div class="input-area">
      <div class="input-wrapper">
        <input
          v-model="input"
          type="text"
          placeholder="输入你的问题..."
          @keyup.enter="sendMessage"
          :disabled="loading"
          class="chat-input"
        />
        <button
          class="send-btn"
          :class="{ stopping: loading }"
          @click="loading ? stopCurrentRequest() : sendMessage()"
          :disabled="!loading && !input.trim()"
          :title="loading ? '停止生成' : '发送'"
        >
          <svg v-if="!loading" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
          <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="6" width="12" height="12" rx="2"/>
          </svg>
        </button>
      </div>
      <div class="input-hint">按 Enter 发送 · AI 分析可能需要 10-120 秒</div>
    </div>
    </section>

    <aside class="conversation-sidebar">
      <button class="new-chat-btn" @click="createNewConversation">
        <span>＋</span>
        <span>新对话</span>
      </button>
      <div class="conversation-list">
        <div
          v-for="conversation in conversations"
          :key="conversation.id"
          class="conversation-item"
          :class="{ active: conversation.id === currentConversationId }"
          @click="selectConversation(conversation.id)"
        >
          <button class="conversation-main" type="button">
            <span class="conversation-title">{{ conversation.title }}</span>
            <span class="conversation-time">{{ formatConversationTime(conversation.updatedAt) }}</span>
          </button>
          <button
            class="delete-conversation-btn"
            type="button"
            title="删除对话"
            @click.stop="deleteConversation(conversation.id)"
          >
            ×
          </button>
        </div>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, ref, nextTick, onMounted } from 'vue'
import request from '@/api/request'

const AI_REQUEST_TIMEOUT = 120000
const CONVERSATION_STORAGE_KEY = 'ecommerce_ai_assistant_conversations'

const defaultQuickActions = [
  { icon: '📊', label: '分析市场机会', prompt: '请从当前数据中概括分析值得关注的市场机会，并给出数据支撑' },
  { icon: '💡', label: '推荐新手品类', prompt: '推荐3个最适合新手的品类，并说明原因' },
  { icon: '📈', label: '预测销量', prompt: '请说明做销量预测时应该重点看哪些指标，并结合当前数据给出示例判断' },
  { icon: '⚖️', label: '品类对比', prompt: '对比两个高潜力品类的市场竞争和入场建议' },
  { icon: '📍', label: '地域分析', prompt: '分析不同发货地区对商品销量和竞争力的影响' },
  { icon: '🎯', label: '综合建议', prompt: '预算200元，想做一个新商品，请给我综合选品建议' }
]

const conversations = ref([])
const currentConversationId = ref('')
const input = ref('')
const loading = ref(false)
const loadingConversationId = ref('')
const messagesRef = ref(null)
const quickActions = ref(defaultQuickActions)
let msgIdCounter = 0
let activeRequestId = 0
let currentAbortController = null

const activeConversation = computed(() => {
  return conversations.value.find(item => item.id === currentConversationId.value) || null
})

const activeMessages = computed(() => activeConversation.value?.messages || [])

const escapeHtml = (text) => {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

const isSafeUrl = (url) => {
  return /^(https?:\/\/|\/)/i.test(url)
}

const formatInline = (text) => {
  if (!text) return ''

  const tokens = []
  let html = text.replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, (match, alt, url) => {
    if (!isSafeUrl(url)) return escapeHtml(match)
    const token = `@@IMG_${tokens.length}@@`
    tokens.push(`<img class="md-image" src="${escapeHtml(url)}" alt="${escapeHtml(alt || '图片')}" loading="lazy" />`)
    return token
  })

  html = html.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (match, label, url) => {
    if (!isSafeUrl(url)) return escapeHtml(match)
    const token = `@@LINK_${tokens.length}@@`
    tokens.push(`<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(label)}</a>`)
    return token
  })

  html = escapeHtml(html)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')

  tokens.forEach((value, index) => {
    html = html.replace(`@@IMG_${index}@@`, value).replace(`@@LINK_${index}@@`, value)
  })

  return html
}

const parseTable = (lines, startIndex) => {
  const tableLines = []
  let index = startIndex

  while (index < lines.length && /^\s*\|.*\|\s*$/.test(lines[index])) {
    tableLines.push(lines[index])
    index++
  }

  if (tableLines.length < 2 || !/^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(tableLines[1])) {
    return null
  }

  const rows = tableLines
    .filter((line, rowIndex) => rowIndex !== 1)
    .map(line => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim()))

  const header = rows[0] || []
  const body = rows.slice(1)
  const thead = `<thead><tr>${header.map(cell => `<th>${formatInline(cell)}</th>`).join('')}</tr></thead>`
  const tbody = `<tbody>${body.map(row => `<tr>${row.map(cell => `<td>${formatInline(cell)}</td>`).join('')}</tr>`).join('')}</tbody>`

  return {
    html: `<table>${thead}${tbody}</table>`,
    nextIndex: index
  }
}

const renderContent = (text) => {
  if (!text) return ''
  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const blocks = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    const trimmed = line.trim()

    if (!trimmed) {
      index++
      continue
    }

    const table = parseTable(lines, index)
    if (table) {
      blocks.push(table.html)
      index = table.nextIndex
      continue
    }

    if (/^-{3,}$/.test(trimmed)) {
      blocks.push('<hr />')
      index++
      continue
    }

    const heading = trimmed.match(/^(#{1,6})\s+(.+)$/)
    if (heading) {
      const level = Math.min(heading[1].length, 4)
      blocks.push(`<h${level} class="md-heading md-h${level}">${formatInline(heading[2])}</h${level}>`)
      index++
      continue
    }

    const tagHeading = trimmed.match(/^【([^】]+)】(.+)?$/)
    if (tagHeading) {
      const title = tagHeading[1]
      const rest = tagHeading[2]?.trim()
      blocks.push(`<h3 class="md-heading md-h3">${formatInline(title)}</h3>`)
      if (rest) blocks.push(`<p>${formatInline(rest)}</p>`)
      index++
      continue
    }

    if (/^[-*]\s+/.test(trimmed)) {
      const items = []
      while (index < lines.length && /^[-*]\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^[-*]\s+/, ''))
        index++
      }
      blocks.push(`<ul>${items.map(item => `<li>${formatInline(item)}</li>`).join('')}</ul>`)
      continue
    }

    blocks.push(`<p>${formatInline(trimmed)}</p>`)
    index++
  }

  return blocks.join('')
}

const makeConversation = () => {
  const now = Date.now()
  return {
    id: `conv_${now}_${Math.random().toString(16).slice(2)}`,
    title: '新对话',
    updatedAt: now,
    messages: []
  }
}

const persistConversations = () => {
  localStorage.setItem(CONVERSATION_STORAGE_KEY, JSON.stringify({
    currentConversationId: currentConversationId.value,
    conversations: conversations.value
  }))
}

const ensureConversation = () => {
  if (activeConversation.value) return activeConversation.value

  const conversation = makeConversation()
  conversations.value.unshift(conversation)
  currentConversationId.value = conversation.id
  persistConversations()
  return conversation
}

const loadConversations = () => {
  try {
    const raw = localStorage.getItem(CONVERSATION_STORAGE_KEY)
    const saved = raw ? JSON.parse(raw) : null
    const savedConversations = Array.isArray(saved?.conversations) ? saved.conversations : []

    conversations.value = savedConversations.length > 0 ? savedConversations : [makeConversation()]
    currentConversationId.value = saved?.currentConversationId || conversations.value[0].id

    if (!conversations.value.some(item => item.id === currentConversationId.value)) {
      currentConversationId.value = conversations.value[0].id
    }
  } catch (e) {
    console.warn('读取 AI 对话记录失败，已创建新对话', e)
    conversations.value = [makeConversation()]
    currentConversationId.value = conversations.value[0].id
  }

  msgIdCounter = conversations.value.reduce((maxId, conversation) => {
    const ids = (conversation.messages || []).map(message => Number(message.id) || 0)
    return Math.max(maxId, ...ids, 0)
  }, 0)
  persistConversations()
}

const createNewConversation = () => {
  stopCurrentRequest(false)
  const conversation = makeConversation()
  conversations.value.unshift(conversation)
  currentConversationId.value = conversation.id
  persistConversations()
}

const selectConversation = (id) => {
  currentConversationId.value = id
  persistConversations()
  scrollToBottom()
}

const deleteConversation = (id) => {
  if (id === loadingConversationId.value) {
    stopCurrentRequest(false)
  }

  conversations.value = conversations.value.filter(item => item.id !== id)
  if (conversations.value.length === 0) {
    conversations.value = [makeConversation()]
  }

  if (currentConversationId.value === id) {
    currentConversationId.value = conversations.value[0].id
  }

  persistConversations()
}

const formatConversationTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

const addMessage = (role, content, conversationId = currentConversationId.value) => {
  const conversation = conversations.value.find(item => item.id === conversationId) || ensureConversation()
  conversation.messages.push({
    id: ++msgIdCounter,
    role,
    content
  })

  if (role === 'user' && conversation.title === '新对话') {
    conversation.title = content.length > 16 ? `${content.slice(0, 16)}...` : content
  }
  conversation.updatedAt = Date.now()

  conversations.value = [
    conversation,
    ...conversations.value.filter(item => item.id !== conversation.id)
  ]
  persistConversations()

  if (conversation.id === currentConversationId.value) {
    scrollToBottom()
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

const sendMessage = async () => {
  const text = input.value.trim()
  if (!text || loading.value) return

  const conversation = ensureConversation()
  const conversationId = conversation.id
  const requestId = ++activeRequestId
  currentAbortController = new AbortController()

  input.value = ''
  addMessage('user', text, conversationId)

  loading.value = true
  loadingConversationId.value = conversationId

  try {
    const res = await request.post('/ai/chat', { message: text }, {
      timeout: AI_REQUEST_TIMEOUT,
      signal: currentAbortController.signal
    })
    if (requestId !== activeRequestId) return
    const reply = res.data?.reply || '抱歉，暂时无法获取回答。'
    addMessage('assistant', reply, conversationId)
  } catch (e) {
    if (requestId !== activeRequestId) return
    if (e.code === 'ERR_CANCELED' || e.name === 'CanceledError') return
    const errMsg = e.response?.data?.message || e.message || '网络异常，请稍后重试'
    addMessage('assistant', '⚠️ ' + errMsg, conversationId)
  } finally {
    if (requestId === activeRequestId) {
      loading.value = false
      loadingConversationId.value = ''
      currentAbortController = null
    }
  }
}

const sendQuick = (text) => {
  input.value = text
  sendMessage()
}

const clearMessages = () => {
  stopCurrentRequest(false)

  const conversation = activeConversation.value
  if (conversation) {
    conversation.messages = []
    conversation.title = '新对话'
    conversation.updatedAt = Date.now()
    persistConversations()
  }
}

const stopCurrentRequest = (appendMessage = true) => {
  if (!loading.value && !currentAbortController) return

  const stoppedConversationId = loadingConversationId.value || currentConversationId.value
  activeRequestId++
  if (currentAbortController) {
    currentAbortController.abort()
  }
  currentAbortController = null
  loading.value = false
  loadingConversationId.value = ''

  if (appendMessage) {
    addMessage('assistant', '已停止本次生成。', stoppedConversationId)
  }
}

onMounted(() => {
  loadConversations()
})
</script>

<style scoped>
.ai-assistant {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: row;
  background:
    linear-gradient(180deg, #14182b 0%, #17172a 45%, #151827 100%);
  color: #e0e0e0;
  position: relative;
  overflow: hidden;
}

.conversation-sidebar {
  width: 248px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 14px;
  background: rgba(12, 18, 34, 0.78);
  border-left: 1px solid rgba(66, 153, 225, 0.14);
}

.new-chat-btn {
  width: 100%;
  height: 40px;
  border: 1px solid rgba(0, 255, 255, 0.22);
  border-radius: 8px;
  background: rgba(0, 255, 255, 0.08);
  color: #dff8ff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.new-chat-btn:hover {
  background: rgba(0, 255, 255, 0.13);
  border-color: rgba(0, 255, 255, 0.38);
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
}

.conversation-list::-webkit-scrollbar {
  width: 4px;
}

.conversation-list::-webkit-scrollbar-thumb {
  background: rgba(96, 165, 250, 0.24);
  border-radius: 999px;
}

.conversation-item {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 0;
  background: transparent;
  color: #aeb8cc;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
  display: flex;
  align-items: stretch;
  overflow: hidden;
}

.conversation-item:hover,
.conversation-item.active {
  background: rgba(31, 52, 92, 0.72);
  border-color: rgba(96, 165, 250, 0.22);
  color: #f2f7ff;
}

.conversation-main {
  min-width: 0;
  flex: 1;
  border: none;
  background: transparent;
  color: inherit;
  text-align: left;
  padding: 10px 8px 10px 11px;
  cursor: pointer;
}

.delete-conversation-btn {
  width: 30px;
  border: none;
  background: transparent;
  color: #66758f;
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
  opacity: 0;
  transition: all 0.2s;
}

.conversation-item:hover .delete-conversation-btn,
.conversation-item.active .delete-conversation-btn {
  opacity: 1;
}

.delete-conversation-btn:hover {
  color: #ff8a8a;
  background: rgba(255, 107, 107, 0.08);
}

.conversation-title {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.4;
}

.conversation-time {
  display: block;
  margin-top: 4px;
  color: #67738b;
  font-size: 11px;
}

.chat-panel {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* ====== 顶部标题栏 ====== */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 28px;
  background: rgba(18, 27, 50, 0.92);
  border-bottom: 1px solid rgba(66, 153, 225, 0.18);
  box-shadow: 0 10px 30px rgba(5, 10, 25, 0.18);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  width: 44px;
  height: 44px;
  background: linear-gradient(145deg, rgba(0, 255, 255, 0.14), rgba(0, 102, 255, 0.18));
  border: 1px solid rgba(0, 255, 255, 0.16);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-text h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0;
}

.header-subtitle {
  font-size: 12px;
  color: #8794ad;
}

.status-badge {
  margin-left: 4px;
  padding: 4px 8px;
  border: 1px solid rgba(0, 255, 255, 0.18);
  border-radius: 999px;
  color: #7dd3fc;
  background: rgba(0, 255, 255, 0.07);
  font-size: 12px;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.header-actions .el-button {
  color: #6b7280;
  border: none;
}
.header-actions .el-button:hover {
  color: #00ffff;
  background: rgba(0, 255, 255, 0.1);
}

/* ====== 对话区域 ====== */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px;
  scroll-behavior: smooth;
}
.messages-area::-webkit-scrollbar {
  width: 6px;
}
.messages-area::-webkit-scrollbar-track {
  background: transparent;
}
.messages-area::-webkit-scrollbar-thumb {
  background: rgba(0, 102, 255, 0.3);
  border-radius: 3px;
}

/* ====== 空状态 ====== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 460px;
  text-align: center;
  padding: 48px 20px;
  max-width: 760px;
  margin: 0 auto;
}

.welcome-graphic {
  margin-bottom: 20px;
  opacity: 0.92;
  filter: drop-shadow(0 12px 28px rgba(0, 102, 255, 0.28));
}

.welcome-title {
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 10px;
}

.welcome-desc {
  color: #8d99ae;
  font-size: 14px;
  line-height: 1.8;
  margin: 0 0 34px;
  max-width: 460px;
}

.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  max-width: 640px;
  width: 100%;
}

.suggestion-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 54px;
  padding: 13px 16px;
  background: rgba(26, 42, 76, 0.72);
  border: 1px solid rgba(70, 140, 220, 0.22);
  border-radius: 8px;
  color: #d5def0;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}
.suggestion-chip:hover {
  background: rgba(32, 56, 96, 0.9);
  border-color: rgba(0, 255, 255, 0.34);
  transform: translateY(-1px);
}
.chip-icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  background: rgba(0, 255, 255, 0.08);
  font-size: 15px;
  flex-shrink: 0;
}

/* ====== 消息样式 ====== */
.message-row {
  display: flex;
  gap: 12px;
  width: min(100%, 1160px);
  margin: 0 auto 22px;
  animation: fadeIn 0.3s ease;
}
.message-row.user {
  flex-direction: row-reverse;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}
.message-row.assistant .message-avatar {
  background: rgba(0, 255, 255, 0.12);
  border: 1px solid rgba(0, 255, 255, 0.12);
}
.message-row.user .message-avatar {
  background: rgba(0, 102, 255, 0.15);
  border: 1px solid rgba(96, 165, 250, 0.14);
}

.ai-icon {
  display: flex;
  align-items: center;
}

.message-content {
  max-width: min(82%, 960px);
  min-width: 0;
}
.message-row.user .message-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-sender {
  font-size: 12px;
  color: #8290aa;
  margin-bottom: 6px;
  padding: 0 4px;
}

.message-bubble {
  padding: 16px 18px;
  border-radius: 10px;
  line-height: 1.7;
  font-size: 14px;
  word-wrap: break-word;
  overflow-wrap: break-word;
  overflow-x: auto;
}
.message-row.assistant .message-bubble {
  background: rgba(34, 35, 56, 0.92);
  border: 1px solid rgba(0, 255, 255, 0.11);
  color: #d5deeb;
  border-top-left-radius: 4px;
  box-shadow: 0 12px 28px rgba(3, 8, 22, 0.14);
}
.message-row.user .message-bubble {
  background: linear-gradient(145deg, rgba(0, 102, 255, 0.18), rgba(35, 73, 142, 0.3));
  border: 1px solid rgba(96, 165, 250, 0.26);
  color: #edf4ff;
  border-top-right-radius: 4px;
}

/* 消息内容样式 */
.message-bubble :deep(p) {
  margin: 0 0 10px;
}
.message-bubble :deep(p:last-child) {
  margin-bottom: 0;
}
.message-bubble :deep(.md-heading) {
  margin: 18px 0 10px;
  color: #00ffff;
  font-weight: 700;
  line-height: 1.35;
}
.message-bubble :deep(.md-heading:first-child) {
  margin-top: 0;
}
.message-bubble :deep(.md-h1) {
  font-size: 18px;
}
.message-bubble :deep(.md-h2) {
  font-size: 16px;
}
.message-bubble :deep(.md-h3),
.message-bubble :deep(.md-h4) {
  font-size: 15px;
}
.message-bubble :deep(hr) {
  border: none;
  border-top: 1px solid rgba(0, 255, 255, 0.12);
  margin: 14px 0;
}
.message-bubble :deep(.section-tag) {
  display: inline-block;
  font-weight: 700;
  color: #00ffff;
  font-size: 15px;
  margin: 8px 0 4px;
  letter-spacing: 0.5px;
}
.message-bubble :deep(.section-tag:first-child) {
  margin-top: 0;
}

.message-bubble :deep(strong) {
  color: #fff;
  font-weight: 600;
}
.message-bubble :deep(code) {
  padding: 2px 5px;
  border-radius: 4px;
  background: rgba(0, 102, 255, 0.16);
  color: #dce8ff;
  font-size: 12px;
}
.message-bubble :deep(a) {
  color: #60a5fa;
  text-decoration: none;
}
.message-bubble :deep(a:hover) {
  text-decoration: underline;
}
.message-bubble :deep(ul) {
  margin: 8px 0 12px;
  padding-left: 20px;
}
.message-bubble :deep(li) {
  margin: 4px 0;
}

.message-bubble :deep(table) {
  border-collapse: collapse;
  margin: 10px 0 14px;
  width: 100%;
  min-width: 560px;
  font-size: 13px;
}
.message-bubble :deep(th),
.message-bubble :deep(td) {
  padding: 6px 10px;
  border: 1px solid rgba(0, 255, 255, 0.1);
  color: #c0d0e0;
  text-align: left;
  vertical-align: top;
}
.message-bubble :deep(th) {
  border-top: 1px solid rgba(0, 255, 255, 0.22);
  background: rgba(0, 255, 255, 0.06);
  color: #00ffff;
  font-weight: 600;
}
.message-bubble :deep(.md-image) {
  display: block;
  max-width: min(100%, 720px);
  max-height: 360px;
  object-fit: contain;
  margin: 10px 0 14px;
  border-radius: 8px;
  border: 1px solid rgba(0, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.04);
}

/* ====== 思考动画 ====== */
.thinking-bar {
  display: flex;
  align-items: center;
  gap: 6px;
}
.bar-segment {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #0066ff;
  animation: pulse 1.4s ease-in-out infinite;
}
.bar-segment:nth-child(2) { animation-delay: 0.2s; }
.bar-segment:nth-child(3) { animation-delay: 0.4s; }
.bar-text {
  font-size: 13px;
  color: #6b7280;
  margin-left: 8px;
}

@keyframes pulse {
  0%, 80%, 100% { opacity: 0.4; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.2); }
}

/* ====== 输入区域 ====== */
.input-area {
  flex-shrink: 0;
  padding: 16px 32px 18px;
  border-top: 1px solid rgba(66, 153, 225, 0.16);
  background: rgba(17, 27, 49, 0.96);
  box-shadow: 0 -12px 30px rgba(5, 10, 25, 0.18);
}

.input-wrapper {
  display: flex;
  gap: 8px;
  width: min(100%, 1160px);
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid rgba(96, 165, 250, 0.24);
  border-radius: 10px;
  padding: 4px;
  transition: border-color 0.2s;
}
.input-wrapper:focus-within {
  border-color: rgba(0, 255, 255, 0.45);
}

.chat-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  padding: 10px 14px;
  font-size: 14px;
  color: #e0e0e0;
}
.chat-input::placeholder {
  color: #4b5563;
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  border: none;
  background: linear-gradient(145deg, #0b74ff, #0057d9);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}
.send-btn:hover:not(:disabled) {
  background: #0052cc;
  transform: scale(1.05);
}
.send-btn.stopping {
  background: linear-gradient(145deg, #ff5f6d, #d93a4b);
}
.send-btn.stopping:hover:not(:disabled) {
  background: #c92f41;
}
.send-btn:disabled {
  background: rgba(0, 102, 255, 0.2);
  color: #4b5563;
  cursor: not-allowed;
}

.input-hint {
  text-align: center;
  font-size: 11px;
  color: #6c7892;
  margin-top: 8px;
}

/* ====== 响应式 ====== */
@media (max-width: 640px) {
  .ai-assistant {
    flex-direction: column;
  }
  .conversation-sidebar {
    width: auto;
    padding: 10px 12px;
    border-left: none;
    border-bottom: 1px solid rgba(66, 153, 225, 0.14);
    order: -1;
  }
  .conversation-list {
    flex-direction: row;
    overflow-x: auto;
    overflow-y: hidden;
  }
  .conversation-item {
    min-width: 150px;
  }
  .suggestion-grid {
    grid-template-columns: 1fr;
  }
  .message-content {
    max-width: calc(100% - 48px);
  }
  .chat-header {
    padding: 10px 16px;
  }
  .messages-area {
    padding: 16px;
  }
  .input-area {
    padding: 12px 16px 14px;
  }
  .status-badge {
    display: none;
  }
}
</style>
