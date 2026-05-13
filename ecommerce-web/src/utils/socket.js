/**
 * WebSocket工具类
 * 用于与Python爬虫服务建立实时通信
 */
import { io } from 'socket.io-client'

class SocketService {
  constructor() {
    this.socket = null
    this.connected = false
    this.listeners = new Map()
  }

  /**
   * 连接到WebSocket服务器
   */
  connect(url = 'http://localhost:5000') {
    if (this.socket && this.connected) {
      console.log('WebSocket已连接')
      return this.socket
    }

    console.log('正在连接WebSocket服务器:', url)

    this.socket = io(url, {
      transports: ['websocket', 'polling'],
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionAttempts: 5
    })

    // 连接成功
    this.socket.on('connect', () => {
      console.log('✓ WebSocket连接成功')
      this.connected = true
    })

    // 连接失败
    this.socket.on('connect_error', (error) => {
      console.error('✗ WebSocket连接失败:', error.message)
      this.connected = false
    })

    // 断开连接
    this.socket.on('disconnect', (reason) => {
      console.log('WebSocket已断开:', reason)
      this.connected = false
    })

    // 重新连接
    this.socket.on('reconnect', (attemptNumber) => {
      console.log(`WebSocket重新连接成功 (尝试${attemptNumber}次)`)
      this.connected = true
    })

    return this.socket
  }

  /**
   * 断开连接
   */
  disconnect() {
    if (this.socket) {
      this.socket.disconnect()
      this.socket = null
      this.connected = false
      this.listeners.clear()
      console.log('WebSocket已断开')
    }
  }

  /**
   * 监听事件
   */
  on(event, callback) {
    if (!this.socket) {
      console.error('WebSocket未连接，请先调用connect()')
      return
    }

    this.socket.on(event, callback)
    
    // 保存监听器引用，便于后续移除
    if (!this.listeners.has(event)) {
      this.listeners.set(event, [])
    }
    this.listeners.get(event).push(callback)
  }

  /**
   * 移除事件监听
   */
  off(event, callback) {
    if (!this.socket) return

    if (callback) {
      this.socket.off(event, callback)
      
      // 从监听器列表中移除
      const callbacks = this.listeners.get(event)
      if (callbacks) {
        const index = callbacks.indexOf(callback)
        if (index > -1) {
          callbacks.splice(index, 1)
        }
      }
    } else {
      // 移除该事件的所有监听器
      this.socket.off(event)
      this.listeners.delete(event)
    }
  }

  /**
   * 发送事件
   */
  emit(event, data) {
    if (!this.socket || !this.connected) {
      console.error('WebSocket未连接，无法发送消息')
      return false
    }

    this.socket.emit(event, data)
    return true
  }

  /**
   * 检查连接状态
   */
  isConnected() {
    return this.connected
  }
}

// 创建单例
const socketService = new SocketService()

export default socketService

