"""
爬虫服务 - Flask + SocketIO
提供WebSocket实时通信和HTTP API接口
"""
import os
import sys
import json
import time
import asyncio
import random
import re
from datetime import datetime
from flask import Flask, request, jsonify
from flask_socketio import SocketIO, emit
from flask_cors import CORS
import threading
import uuid

# 添加当前目录到Python路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from get_frist import Spider
from get_well_seller import TmallRankSpider
from data_clean import DataCleaner

# 创建 Flask 应用
app = Flask(__name__)
# 从环境变量读取 Flask Secret Key，避免将密钥写入代码或提交到 GitHub。
# 启动爬虫服务前必须设置 CRAWLER_SECRET_KEY。
app.config['SECRET_KEY'] = os.environ.get('CRAWLER_SECRET_KEY')
if not app.config['SECRET_KEY']:
    raise RuntimeError('CRAWLER_SECRET_KEY environment variable is required')
CORS(app, resources={r"/*": {"origins": "*"}})

# 创建SocketIO实例
socketio = SocketIO(app, cors_allowed_origins="*", async_mode='threading')

# 全局变量
account_sessions = {}  # 账号登录会话 {sessionId: {spider, accounts, status}}
crawl_tasks = {}       # 爬取任务 {taskId: {spider, status, ...}}
last_account_index = -1  # 记录上次使用的账号索引，下次自动使用下一个
app_start_time = time.time()  # 服务启动时间（用于健康检查）

@app.route('/api/account/start-login', methods=['POST'])
def start_login():
    """启动账号登录流程"""
    try:
        session_id = str(uuid.uuid4())
        
        # 创建爬虫实例
        spider = Spider()
        
        account_sessions[session_id] = {
            'spider': spider,
            'accounts': [],
            'status': 'waiting',
            'current_account_index': 0
        }
        
        # 在新线程中启动登录流程
        thread = threading.Thread(
            target=login_account_thread,
            args=(session_id,)
        )
        thread.daemon = True
        thread.start()
        
        return jsonify({
            'code': 200,
            'message': '登录流程已启动',
            'data': {'sessionId': session_id}
        })
        
    except Exception as e:
        return jsonify({
            'code': 500,
            'message': f'启动失败: {str(e)}'
        }), 500


def login_account_thread(session_id):
    """账号登录线程"""
    try:
        session = account_sessions.get(session_id)
        if not session:
            return
        
        spider = session['spider']
        
        # 推送消息：浏览器即将打开
        socketio.emit('login:browser-opening', {
            'sessionId': session_id,
            'message': '正在启动浏览器...'
        })

        time.sleep(1)

        # 调用原有的登录逻辑（需要修改为单账号登录）
        from DrissionPage import ChromiumPage, ChromiumOptions

        CRAWLER_PROFILE_DIR = os.path.join(os.path.dirname(__file__), "edge_crawler_profile")

        co = ChromiumOptions()
        edge_path = r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
        co.set_browser_path(edge_path)
        co.set_user_data_path(CRAWLER_PROFILE_DIR)
        co.set_argument('--start-maximized')
        co.set_argument('--disable-blink-features=AutomationControlled')

        page = ChromiumPage(co)

        # 推送消息：浏览器已打开
        socketio.emit('login:browser-opened', {
            'sessionId': session_id,
            'message': '浏览器已打开，请在浏览器中登录淘宝账号'
        })
        
        # 打开淘宝首页
        page.get("https://www.taobao.com")
        time.sleep(2)
        
        # 等待前端确认登录完成
        session['status'] = 'waiting_confirm'
        session['page'] = page
        
    except Exception as e:
        socketio.emit('login:error', {
            'sessionId': session_id,
            'message': f'登录失败: {str(e)}'
        })


@app.route('/api/account/confirm-login', methods=['POST'])
def confirm_login():
    """确认当前账号登录完成"""
    try:
        data = request.json
        session_id = data.get('sessionId')
        
        session = account_sessions.get(session_id)
        if not session:
            return jsonify({'code': 404, 'message': '会话不存在'}), 404
        
        page = session.get('page')
        if not page:
            return jsonify({'code': 400, 'message': '浏览器未打开'}), 400
        
        # 刷新页面获取最新cookies
        page.refresh()
        time.sleep(2)
        
        # 获取cookies
        cookies = page.cookies()
        cookies_str = '; '.join([f"{c['name']}={c['value']}" for c in cookies])
        
        # 验证token
        import re
        token = re.findall(r'_m_h5_tk=([^;]+)', cookies_str)
        if not token:
            return jsonify({
                'code': 400,
                'message': '未找到登录token，请确保已正确登录'
            }), 400
        
        # 保存账号信息
        cookies_list = []
        for c in cookies:
            cookie_item = {
                "name": c['name'],
                "value": c['value'],
                "domain": c.get('domain', '.taobao.com'),
                "path": c.get('path', '/')
            }
            cookies_list.append(cookie_item)
        
        account_index = len(session['accounts']) + 1
        account_info = {
            'accountId': str(uuid.uuid4()),
            'accountName': f'账号{account_index}',
            'cookies_str': cookies_str,
            'cookies_list': cookies_list,
            'addTime': datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }
        
        session['accounts'].append(account_info)
        session['page'] = page  # 保持page对象用于后续清除cookies
        
        # 推送成功消息
        socketio.emit('login:account-added', {
            'sessionId': session_id,
            'account': {
                'accountId': account_info['accountId'],
                'accountName': account_info['accountName'],
                'addTime': account_info['addTime']
            },
            'totalAccounts': len(session['accounts'])
        })
        
        return jsonify({
            'code': 200,
            'message': '账号添加成功',
            'data': {
                'accountId': account_info['accountId'],
                'accountName': account_info['accountName']
            }
        })
        
    except Exception as e:
        return jsonify({
            'code': 500,
            'message': f'确认失败: {str(e)}'
        }), 500


@app.route('/api/account/add-another', methods=['POST'])
def add_another_account():
    """添加下一个账号（清除cookies）"""
    try:
        data = request.json
        session_id = data.get('sessionId')
        
        session = account_sessions.get(session_id)
        if not session:
            return jsonify({'code': 404, 'message': '会话不存在'}), 404
        
        page = session.get('page')
        if not page:
            return jsonify({'code': 400, 'message': '浏览器未打开'}), 400
        
        # 清除所有cookies
        page.set.cookies.clear()
        time.sleep(1)
        
        # 刷新页面
        page.get("https://www.taobao.com")
        time.sleep(2)
        
        # 推送消息
        socketio.emit('login:cookies-cleared', {
            'sessionId': session_id,
            'message': 'Cookies已清除，请登录下一个账号'
        })
        
        session['status'] = 'waiting_confirm'
        
        return jsonify({
            'code': 200,
            'message': 'Cookies已清除，请登录下一个账号'
        })
        
    except Exception as e:
        return jsonify({
            'code': 500,
            'message': f'操作失败: {str(e)}'
        }), 500


@app.route('/api/account/finish', methods=['POST'])
def finish_account_setup():
    """完成账号池设置"""
    try:
        data = request.json
        session_id = data.get('sessionId')

        session = account_sessions.get(session_id)
        if not session:
            return jsonify({'code': 404, 'message': '会话不存在'}), 404

        # 关闭浏览器
        page = session.get('page')
        if page:
            try:
                page.quit()
            except:
                pass

        # 保存账号池到文件
        accounts = session['accounts']
        if accounts:
            save_accounts_to_file(accounts)

        session['status'] = 'completed'

        # 推送完成消息
        socketio.emit('login:setup-complete', {
            'sessionId': session_id,
            'totalAccounts': len(accounts)
        })

        return jsonify({
            'code': 200,
            'message': f'账号池设置完成，共添加{len(accounts)}个账号',
            'data': {'totalAccounts': len(accounts)}
        })

    except Exception as e:
        return jsonify({
            'code': 500,
            'message': f'操作失败: {str(e)}'
        }), 500


@app.route('/api/account/list', methods=['GET'])
def get_account_list():
    """获取账号池列表"""
    try:
        accounts = load_accounts_from_file()

        # 只返回基本信息，不返回cookies
        account_list = [
            {
                'accountId': acc.get('accountId'),
                'accountName': acc.get('accountName'),
                'addTime': acc.get('addTime')
            }
            for acc in accounts
        ]

        return jsonify({
            'code': 200,
            'data': account_list
        })

    except Exception as e:
        return jsonify({
            'code': 500,
            'message': f'获取失败: {str(e)}'
        }), 500


def save_accounts_to_file(accounts):
    """保存账号池到文件"""
    file_path = os.path.join(os.path.dirname(__file__), "account_pool.json")
    with open(file_path, 'w', encoding='utf-8') as f:
        json.dump(accounts, f, ensure_ascii=False, indent=2)


def load_accounts_from_file():
    """从文件加载账号池"""
    file_path = os.path.join(os.path.dirname(__file__), "account_pool.json")
    if not os.path.exists(file_path):
        return []

    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except:
        return []


@app.route('/api/crawler/health', methods=['GET'])
def crawler_health():
    """爬虫服务健康检查端点 - 供Java后端探测服务是否存活"""
    running_tasks = {tid: t for tid, t in crawl_tasks.items() if t.get('status') == 'running'}
    running_tmall = {tid: t for tid, t in tmall_tasks.items() if t.get('status') == 'running'}
    return jsonify({
        'code': 200,
        'status': 'running',
        'runningTasks': len(running_tasks) + len(running_tmall),
        'uptime': time.time() - app_start_time
    })


@app.route('/api/crawl/start', methods=['POST'])
def start_crawl():
    """启动爬取任务"""
    global last_account_index

    try:
        data = request.json
        keyword = data.get('keyword')
        page_count = data.get('pageCount', 10)
        min_interval = data.get('minInterval', 2)  # 最小间隔（秒）
        max_interval = data.get('maxInterval', 5)  # 最大间隔（秒）
        use_proxy = data.get('useProxy', True)     # 是否使用代理
        proxy_api = data.get('proxyApi', '')       # 代理API地址

        if not keyword:
            return jsonify({'code': 400, 'message': '关键词不能为空'}), 400

        # 加载账号池
        accounts = load_accounts_from_file()
        if not accounts:
            return jsonify({
                'code': 400,
                'message': '账号池为空，请先添加淘宝账号'
            }), 400

        # 自动使用下一个账号（轮换）
        last_account_index = (last_account_index + 1) % len(accounts)

        # 创建任务
        task_id = str(uuid.uuid4())

        crawl_tasks[task_id] = {
            'taskId': task_id,
            'keyword': keyword,
            'pageCount': page_count,
            'minInterval': min_interval,
            'maxInterval': max_interval,
            'useProxy': use_proxy,
            'proxyApi': proxy_api,
            'status': 'running',
            'currentRound': 0,
            'totalProducts': 0,
            'accounts': accounts,
            'currentAccountIndex': last_account_index,
            'startTime': datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }

        # 在新线程中启动爬取
        thread = threading.Thread(
            target=crawl_thread,
            args=(task_id, keyword, page_count)
        )
        thread.daemon = True
        thread.start()

        return jsonify({
            'code': 200,
            'message': '爬取任务已启动',
            'data': {'taskId': task_id}
        })

    except Exception as e:
        return jsonify({
            'code': 500,
            'message': f'启动失败: {str(e)}'
        }), 500


def crawl_thread(task_id, keyword, page_count):
    """爬取线程"""
    try:
        task = crawl_tasks.get(task_id)
        if not task:
            return

        accounts = task['accounts']
        account_index = task['currentAccountIndex']
        proxy_api = task.get('proxyApi', '')

        # 创建爬虫实例（传入代理API地址）
        spider = Spider(proxy_api_url=proxy_api if proxy_api else None)

        # 使用账号池中的cookies
        current_account = accounts[account_index % len(accounts)]

        # 转换账号数据格式（适配原始Spider类）
        session_data = {
            'cookies_str': current_account['cookies_str'],
            'cookies_list': current_account['cookies_list'],
            'account_name': current_account['accountName']  # 转换字段名
        }

        spider.session_pool = [session_data]
        spider.current_session_index = 0
        spider.use_current_session()

        # 设置爬取参数
        spider.current_keyword = keyword
        spider.crawl_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        task['currentRound'] += 1

        # 推送开始消息
        socketio.emit('crawl:started', {
            'taskId': task_id,
            'keyword': keyword,
            'pageCount': page_count,
            'round': task['currentRound'],
            'account': current_account['accountName']
        })

        # 执行爬取（需要修改为同步版本）
        asyncio.run(crawl_with_progress(spider, keyword, page_count, task_id))

        # 保存数据
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        output_file = f'taobao_{keyword}_{timestamp}.xlsx'
        spider.wb.save(output_file)

        task['totalProducts'] += len(spider.seen_ids)
        task['outputFile'] = os.path.abspath(output_file)

        # 推送消息：开始数据清洗
        socketio.emit('crawl:log', {
            'taskId': task_id,
            'message': f'📦 数据保存完成: {output_file}'
        })
        # 推送完成消息（不再自动入库，需要通过数据清洗模块手动入库）
        socketio.emit('crawl:log', {
            'taskId': task_id,
            'message': f'📁 数据已保存到文件: {output_file}'
        })
        socketio.emit('crawl:log', {
            'taskId': task_id,
            'message': '💡 请前往【数据清洗】模块进行清洗入库'
        })

        socketio.emit('crawl:round-complete', {
            'taskId': task_id,
            'round': task['currentRound'],
            'productCount': len(spider.seen_ids),
            'totalProducts': task['totalProducts'],
            'outputFile': output_file
        })

        # 标记任务完成并发送 crawl:completed 事件（前端监听此事件显示提示框）
        task['status'] = 'completed'
        task['endTime'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        socketio.emit('crawl:completed', {
            'taskId': task_id,
            'totalProducts': task['totalProducts'],
            'outputFile': output_file,
            'message': f'采集完成！共获取 {task["totalProducts"]} 条商品，请前往数据清洗模块入库'
        })

    except Exception as e:
        socketio.emit('crawl:error', {
            'taskId': task_id,
            'message': f'爬取失败: {str(e)}'
        })
        task['status'] = 'failed'


async def crawl_with_progress(spider, keyword, page_count, task_id):
    """带进度推送的爬取（使用原始Spider的反爬措施：代理IP + Stealth + 随机延迟）"""
    from crawl4ai import AsyncWebCrawler, BrowserConfig, CrawlerRunConfig, CacheMode
    import random

    run_config = CrawlerRunConfig(cache_mode=CacheMode.BYPASS, page_timeout=30000)

    # 获取任务配置
    task = crawl_tasks.get(task_id, {})
    min_interval = task.get('minInterval', 2)
    max_interval = task.get('maxInterval', 5)
    use_proxy = task.get('useProxy', True)

    # 定义停止检查函数
    def check_should_stop():
        task = crawl_tasks.get(task_id)
        return not task or task.get('status') == 'stopped'

    page = 1
    while page <= page_count:
        # 检查是否停止
        if check_should_stop():
            socketio.emit('crawl:log', {
                'taskId': task_id,
                'message': '⚠ 爬取已停止'
            })
            break

        # 根据配置决定是否使用代理
        proxy_config = None
        if use_proxy:
            spider.proxy_manager.force_refresh()
            proxy_config = spider.proxy_manager.get_proxy()

        # 计算本轮爬取页数（10-12页随机，然后换代理）
        pages_before_switch = random.randint(8,10)
        pages_this_round = min(pages_before_switch, page_count - page + 1)

        # 推送日志：显示代理信息
        proxy_server = proxy_config['server'] if proxy_config else '未使用代理'
        socketio.emit('crawl:log', {
            'taskId': task_id,
            'message': f'🚀 本轮爬取 {pages_this_round} 页 | 代理: {proxy_server} | 间隔: {min_interval}-{max_interval}秒'
        })

        # 创建浏览器配置（使用stealth模式 + 代理IP + cookies）
        browser_kwargs = {
            "browser_type": "chromium",
            "headless": True,
            "verbose": False,
            "enable_stealth": True,  # 反爬核心
            "headers": {'referer': 'https://s.taobao.com/'},
            "cookies": spider.cookies_list,
            "viewport_width": 1920,
            "viewport_height": 1080
        }
        if proxy_config:
            browser_kwargs["proxy_config"] = proxy_config

        browser_config = BrowserConfig(**browser_kwargs)

        try:
            async with AsyncWebCrawler(config=browser_config) as crawler:
                for _ in range(pages_this_round):
                    # 再次检查是否停止
                    if check_should_stop():
                        socketio.emit('crawl:log', {
                            'taskId': task_id,
                            'message': '⚠ 爬取已停止'
                        })
                        return

                    # 推送进度
                    socketio.emit('crawl:progress', {
                        'taskId': task_id,
                        'current': page,
                        'total': page_count,
                        'message': f'正在获取第{page}页...'
                    })

                    # 使用Spider的crawl_page方法（无需刷新页面）
                    try:
                        success = await spider.crawl_page(
                            crawler, page, keyword, run_config,
                            check_stop_fn=check_should_stop
                        )

                        # 爬取后再次检查停止状态
                        if check_should_stop():
                            socketio.emit('crawl:log', {
                                'taskId': task_id,
                                'message': '⚠ 爬取已停止'
                            })
                            return

                        if success:
                            # 推送日志
                            socketio.emit('crawl:log', {
                                'taskId': task_id,
                                'message': f'✓ 第{page}页爬取成功'
                            })

                            page += 1

                            # 随机延迟（反爬核心）- 使用配置的间隔范围
                            if page <= page_count:
                                delay = random.uniform(min_interval, max_interval)
                                socketio.emit('crawl:log', {
                                    'taskId': task_id,
                                    'message': f'   ⏱ 等待 {delay:.1f} 秒...'
                                })
                                # 分段延迟，便于响应停止信号
                                for _ in range(int(delay * 2)):
                                    if check_should_stop():
                                        socketio.emit('crawl:log', {
                                            'taskId': task_id,
                                            'message': '⚠ 爬取已停止'
                                        })
                                        return
                                    await asyncio.sleep(0.5)
                        else:
                            # 推送错误日志
                            socketio.emit('crawl:log', {
                                'taskId': task_id,
                                'message': f'✗ 第{page}页爬取失败，尝试换代理重试...'
                            })
                            break  # 跳出内层循环，换代理重试

                    except Exception as e:
                        socketio.emit('crawl:log', {
                            'taskId': task_id,
                            'message': f'✗ 第{page}页异常: {str(e)}'
                        })
                        break  # 跳出内层循环，换代理重试
        except Exception as e:
            socketio.emit('crawl:log', {
                'taskId': task_id,
                'message': f'✗ 浏览器异常: {str(e)}，尝试换代理重试...'
            })

        # 等待1秒让代理切换生效
        if page <= page_count and not check_should_stop():
            await asyncio.sleep(1)


@app.route('/api/crawl/stop', methods=['POST'])
def stop_crawl():
    """停止爬取"""
    try:
        data = request.json
        task_id = data.get('taskId')

        task = crawl_tasks.get(task_id)
        if not task:
            return jsonify({'code': 404, 'message': '任务不存在'}), 404

        task['status'] = 'stopped'
        task['endTime'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        # 推送停止消息
        socketio.emit('crawl:stopped', {
            'taskId': task_id,
            'totalRounds': task['currentRound'],
            'totalProducts': task['totalProducts']
        })

        return jsonify({
            'code': 200,
            'message': '爬取已停止',
            'data': {
                'totalRounds': task['currentRound'],
                'totalProducts': task['totalProducts']
            }
        })

    except Exception as e:
        return jsonify({
            'code': 500,
            'message': f'操作失败: {str(e)}'
        }), 500


@app.route('/api/crawl/status/<task_id>', methods=['GET'])
def get_crawl_status(task_id):
    """查询任务状态"""
    try:
        task = crawl_tasks.get(task_id)
        if not task:
            return jsonify({'code': 404, 'message': '任务不存在'}), 404

        return jsonify({
            'code': 200,
            'data': {
                'taskId': task['taskId'],
                'status': task['status'],
                'currentRound': task['currentRound'],
                'totalProducts': task['totalProducts'],
                'startTime': task['startTime'],
                'endTime': task.get('endTime')
            }
        })

    except Exception as e:
        return jsonify({
            'code': 500,
            'message': f'查询失败: {str(e)}'
        }), 500


tmall_tasks = {}

@app.route('/api/tmall/start', methods=['POST'])
def start_tmall_crawl():
    """启动天猫榜单爬取"""
    try:
        # 检查是否有共享cookies
        cookies_file = os.path.join(os.path.dirname(__file__), "shared_cookies.json")
        if not os.path.exists(cookies_file):
            return jsonify({
                'code': 400,
                'message': '请先添加淘宝账号（天猫榜单爬虫需要使用淘宝账号的cookies）'
            }), 400

        # 创建任务
        task_id = str(uuid.uuid4())
        tmall_tasks[task_id] = {
            'taskId': task_id,
            'status': 'running',
            'startTime': datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            'totalCount': 0
        }

        # 在新线程中执行爬取
        thread = threading.Thread(target=tmall_crawl_thread, args=(task_id,))
        thread.daemon = True
        thread.start()

        return jsonify({
            'code': 200,
            'message': '天猫榜单爬取任务已启动',
            'data': {'taskId': task_id}
        })

    except Exception as e:
        return jsonify({
            'code': 500,
            'message': f'启动失败: {str(e)}'
        }), 500


def tmall_crawl_thread(task_id):
    """天猫榜单爬取线程（使用crawl4ai + stealth模式）"""
    try:
        task = tmall_tasks.get(task_id)
        if not task:
            return

        # 从账号池加载最新的cookies
        accounts = load_accounts_from_file()
        if not accounts:
            socketio.emit('tmall:error', {
                'taskId': task_id,
                'message': '账号池为空，请先添加淘宝账号'
            })
            task['status'] = 'failed'
            return

        # 使用第一个账号的cookies
        account = accounts[0]

        # 创建爬虫实例
        spider = TmallRankSpider()

        # 直接设置cookies（使用账号池中的cookies）
        spider.cookies_str = account['cookies_str']
        spider.cookies_list = account['cookies_list']

        # 验证token是否存在
        token = re.findall(r'_m_h5_tk=([^_]+)_', spider.cookies_str)
        if not token:
            socketio.emit('tmall:error', {
                'taskId': task_id,
                'message': 'Cookies中未找到token，请重新添加账号'
            })
            task['status'] = 'failed'
            return

        print(f"📱 天猫榜单使用账号: {account['accountName']}")
        print(f"   ✓ Token: {token[0][:30]}...")
        print(f"   ✓ 使用 crawl4ai + stealth 模式")

        socketio.emit('tmall:started', {
            'taskId': task_id,
            'message': f"天猫榜单爬取已开始 (crawl4ai模式)，使用账号: {account['accountName']}"
        })

        # 记录爬取时间
        spider.crawl_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        # 使用异步方式爬取
        total_count = asyncio.run(tmall_crawl_async(task_id, spider, task))

        # 保存Excel
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        output_file = f'tmall_rank_{timestamp}.xlsx'
        spider.wb.save(output_file)

        task['totalCount'] = total_count
        task['status'] = 'completed'
        task['outputFile'] = os.path.abspath(output_file)

        socketio.emit('tmall:complete', {
            'taskId': task_id,
            'totalCount': total_count,
            'outputFile': output_file
        })

    except Exception as e:
        import traceback
        traceback.print_exc()
        socketio.emit('tmall:error', {
            'taskId': task_id,
            'message': f'爬取失败: {str(e)}'
        })
        if task:
            task['status'] = 'failed'


async def tmall_crawl_async(task_id, spider, task):
    """异步执行天猫榜单爬取"""
    from get_well_seller import IND2ID_MAP
    from crawl4ai import AsyncWebCrawler, BrowserConfig, CrawlerRunConfig, CacheMode

    total_count = 0
    categories = list(IND2ID_MAP.items())

    # 获取代理配置
    proxy_config = spider.get_current_proxy()
    if proxy_config:
        socketio.emit('tmall:log', {
            'taskId': task_id,
            'message': f'🌐 使用代理: {proxy_config.get("server", "未知")}'
        })
    else:
        socketio.emit('tmall:log', {
            'taskId': task_id,
            'message': '⚠ 未使用代理IP（可能被风控拦截）'
        })

    # 创建浏览器配置（包含代理）
    browser_config = spider._create_browser_config(proxy_config)
    run_config = CrawlerRunConfig(cache_mode=CacheMode.BYPASS, page_timeout=30000)

    # 使用单个浏览器实例爬取所有分类
    async with AsyncWebCrawler(config=browser_config) as crawler:
        # 第一步：预热浏览器，访问天猫榜单页面激活cookies
        socketio.emit('tmall:log', {
            'taskId': task_id,
            'message': '🔥 预热浏览器，访问天猫榜单页面...'
        })
        warmup_success = await spider.warmup_browser(crawler, run_config)
        if warmup_success:
            socketio.emit('tmall:log', {
                'taskId': task_id,
                'message': '✓ 预热成功，开始爬取数据...'
            })
        else:
            socketio.emit('tmall:log', {
                'taskId': task_id,
                'message': '⚠ 预热失败，尝试继续爬取...'
            })

        for i, (ind2_id, category_name) in enumerate(categories):
            # 检查是否应该停止
            if spider.should_stop:
                socketio.emit('tmall:log', {
                    'taskId': task_id,
                    'message': '⚠ 收到停止信号，终止爬取'
                })
                break

            # 推送进度
            socketio.emit('tmall:progress', {
                'taskId': task_id,
                'current': i + 1,
                'total': len(categories),
                'category': category_name
            })

            socketio.emit('tmall:log', {
                'taskId': task_id,
                'message': f'正在获取 [{category_name}] 的数据...'
            })

            # 使用crawl4ai异步获取数据
            rank_list = await spider.fetch_category_data_async(crawler, ind2_id, run_config)

            if rank_list:
                count = spider.parse_and_save(rank_list, category_name)
                total_count += count

                socketio.emit('tmall:log', {
                    'taskId': task_id,
                    'message': f'✓ [{category_name}] 获取 {count} 条数据'
                })
            else:
                socketio.emit('tmall:log', {
                    'taskId': task_id,
                    'message': f'✗ [{category_name}] 获取失败'
                })

            # 每次请求间隔 3-5 秒（与淘宝商品爬虫一致）
            delay = random.uniform(3, 5)
            socketio.emit('tmall:log', {
                'taskId': task_id,
                'message': f'⏳ 等待 {delay:.1f} 秒...'
            })
            await asyncio.sleep(delay)

    return total_count


def is_tmall_rank_file(filename):
    """判断是否为天猫榜单文件"""
    return filename.startswith('tmall_rank_')


def preview_tmall_rank_file(file_path):
    """预览天猫榜单文件"""
    import pandas as pd
    try:
        df = pd.read_excel(file_path)
        total = len(df)
        samples = []

        # 收集样本数据
        for i, row in df.head(5).iterrows():
            samples.append({
                'before': f"{row.get('分类', '')} - {row.get('榜单名称', '')}",
                'after': f"{row.get('分类', '')} | {row.get('榜单名称', '')} ({row.get('热度描述', '')})"
            })

        return {
            'total': total,
            'insert': total,
            'skip': 0,
            'titleCleaned': 0,
            'samples': samples,
            'fileType': 'tmall_rank'
        }
    except Exception as e:
        print(f"预览天猫榜单文件失败: {e}")
        return None


@app.route('/api/clean/preview', methods=['POST'])
def preview_clean():
    """预览清洗效果"""
    try:
        data = request.json
        files = data.get('files', [])
        config = data.get('config', {})

        if not files:
            return jsonify({'code': 400, 'message': '请选择要清洗的文件'}), 400

        # 获取爬虫目录
        crawler_dir = os.path.dirname(os.path.abspath(__file__))

        total = 0
        insert = 0
        skip = 0
        title_cleaned = 0
        samples = []
        file_type = 'taobao'

        for filename in files:
            file_path = os.path.join(crawler_dir, filename)
            if not os.path.exists(file_path):
                continue

            # 判断文件类型
            if is_tmall_rank_file(filename):
                file_type = 'tmall_rank'
                result = preview_tmall_rank_file(file_path)
                if result:
                    total += result['total']
                    insert += result['insert']
                    samples.extend(result['samples'])
            else:
                cleaner = DataCleaner(file_path)
                if cleaner.load_data():
                    # 执行清洗预览（不入库）
                    cleaner.detect_quality()
                    cleaner.clean_data(min_sales=config.get('minSales', 10))

                    if cleaner.cleaned_df is not None:
                        total += len(cleaner.cleaned_df)
                        # 预估新增和跳过数量
                        insert += len(cleaner.cleaned_df)

                        # 收集清洗样本
                        if cleaner.df is not None and len(samples) < 5:
                            for i, row in cleaner.df.head(3).iterrows():
                                original_title = str(row.get('title', ''))
                                cleaned_title = cleaner.clean_title(original_title)
                                if original_title != cleaned_title:
                                    samples.append({
                                        'before': original_title[:50],
                                        'after': cleaned_title[:50]
                                    })
                                    title_cleaned += 1

        return jsonify({
            'code': 200,
            'data': {
                'total': total,
                'insert': insert,
                'skip': skip,
                'titleCleaned': title_cleaned,
                'samples': samples[:5],
                'fileType': file_type
            }
        })

    except Exception as e:
        print(f"预览清洗失败: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'code': 500, 'message': f'预览失败: {str(e)}'}), 500


def save_tmall_rank_to_database(file_path, operator='admin'):
    """保存天猫榜单数据到数据库（tb_rank表）"""
    import pandas as pd
    import pymysql
    import re

    # MySQL 数据库配置从环境变量读取，避免把本机账号密码提交到 GitHub。
    # 启动爬虫服务前必须设置 DB_USERNAME 和 DB_PASSWORD。
    DB_CONFIG = {
        'host': os.environ.get('DB_HOST', 'localhost'),
        'port': int(os.environ.get('DB_PORT', '3306')),
        'user': os.environ.get('DB_USERNAME'),
        'password': os.environ.get('DB_PASSWORD'),
        'database': os.environ.get('DB_NAME', 'ecommerce_analysis'),
        'charset': 'utf8mb4'
    }

    try:
        df = pd.read_excel(file_path)
        print(f"  ✓ 加载天猫榜单文件: {len(df)} 条数据")

        connection = pymysql.connect(**DB_CONFIG)
        cursor = connection.cursor()

        inserted = 0

        insert_sql = """
            INSERT INTO tb_rank (category, rank_name, hot_desc, hot_value, created_at)
            VALUES (%s, %s, %s, %s, NOW())
        """

        for idx, row in df.iterrows():
            category = str(row.get('分类', ''))
            rank_name = str(row.get('榜单名称', ''))
            hot_desc = str(row.get('热度描述', ''))

            # 解析热度数值
            hot_value = 0
            if hot_desc:
                match = re.search(r'([\d.]+)\s*(万)?', hot_desc)
                if match:
                    hot_value = float(match.group(1))
                    if match.group(2) == '万':
                        hot_value *= 10000
                    hot_value = int(hot_value)

            if category and rank_name:
                cursor.execute(insert_sql, (category, rank_name, hot_desc, hot_value))
                inserted += 1

        connection.commit()
        connection.close()

        print(f"  ✓ 天猫榜单入库完成: 新增 {inserted} 条")
        return {'success': True, 'inserted': inserted, 'updated': 0, 'skipped': 0}

    except Exception as e:
        print(f"  ✗ 天猫榜单入库失败: {e}")
        return {'success': False, 'error': str(e)}


@app.route('/api/clean/execute', methods=['POST'])
def execute_clean():
    """执行清洗入库"""
    try:
        data = request.json
        files = data.get('files', [])
        config = data.get('config', {})
        operator = data.get('operator', 'admin')

        if not files:
            return jsonify({'code': 400, 'message': '请选择要清洗的文件'}), 400

        # 获取爬虫目录
        crawler_dir = os.path.dirname(os.path.abspath(__file__))

        total_inserted = 0
        total_updated = 0
        total_skipped = 0
        results = []

        for filename in files:
            file_path = os.path.join(crawler_dir, filename)
            if not os.path.exists(file_path):
                results.append({'file': filename, 'status': 'error', 'message': '文件不存在'})
                continue

            try:
                # 判断文件类型
                if is_tmall_rank_file(filename):
                    # 天猫榜单文件 -> 入库到 tb_rank 表
                    print(f"  📊 处理天猫榜单文件: {filename}")
                    result = save_tmall_rank_to_database(file_path, operator)

                    if result.get('success'):
                        total_inserted += result.get('inserted', 0)
                        results.append({
                            'file': filename,
                            'status': 'success',
                            'inserted': result.get('inserted', 0),
                            'updated': 0,
                            'skipped': 0,
                            'fileType': 'tmall_rank'
                        })
                        # 入库成功后删除源文件
                        try:
                            os.remove(file_path)
                            print(f"  ✓ 已删除源文件: {filename}")
                        except Exception as del_e:
                            print(f"  ⚠ 删除源文件失败: {del_e}")
                    else:
                        results.append({
                            'file': filename,
                            'status': 'error',
                            'message': result.get('error', '入库失败')
                        })
                else:
                    # 淘宝商品文件 -> 原有清洗逻辑
                    cleaner = DataCleaner(file_path)
                    if cleaner.load_data():
                        cleaner.detect_quality()
                        cleaner.clean_data(min_sales=config.get('minSales', 10))

                        # 检查清洗后是否有数据
                        if cleaner.cleaned_df is None or len(cleaner.cleaned_df) == 0:
                            print(f"  ⚠ 文件 {filename} 清洗后无数据")
                            results.append({'file': filename, 'status': 'error', 'message': '清洗后无数据（可能被销量过滤）'})
                            continue

                        print(f"  ✓ 文件 {filename} 清洗后 {len(cleaner.cleaned_df)} 条数据，准备入库...")

                        # 保存到数据库
                        result = cleaner.save_to_database(operator=operator)
                        print(f"  ✓ 入库结果: {result}")

                        if result.get('success'):
                            total_inserted += result.get('inserted', 0)
                            total_updated += result.get('updated', 0)
                            total_skipped += result.get('skipped', 0)
                            results.append({
                                'file': filename,
                                'status': 'success',
                                'inserted': result.get('inserted', 0),
                                'updated': result.get('updated', 0),
                                'skipped': result.get('skipped', 0)
                            })
                            # 入库成功后删除源文件
                            try:
                                os.remove(file_path)
                                print(f"  ✓ 已删除源文件: {filename}")
                            except Exception as del_e:
                                print(f"  ⚠ 删除源文件失败: {del_e}")
                        else:
                            print(f"  ✗ 入库失败: {result.get('error')}")
                            results.append({
                                'file': filename,
                                'status': 'error',
                                'message': result.get('error', '清洗失败')
                            })
                    else:
                        results.append({'file': filename, 'status': 'error', 'message': '加载文件失败'})

            except Exception as e:
                results.append({'file': filename, 'status': 'error', 'message': str(e)})

        # 入库成功后刷新统计缓存
        if total_inserted > 0 or total_updated > 0:
            try:
                import requests as req_lib
                refresh_resp = req_lib.post('http://localhost:8080/api/admin/stats/refresh', timeout=10)
                if refresh_resp.status_code == 200:
                    print("  ✓ 统计缓存已刷新")
                else:
                    print(f"  ⚠ 统计缓存刷新失败: {refresh_resp.status_code}")
            except Exception as refresh_e:
                print(f"  ⚠ 统计缓存刷新失败: {refresh_e}")

        return jsonify({
            'code': 200,
            'data': {
                'totalInserted': total_inserted,
                'totalUpdated': total_updated,
                'totalSkipped': total_skipped,
                'results': results
            },
            'message': f'清洗完成：新增{total_inserted}条，更新{total_updated}条，跳过{total_skipped}条'
        })

    except Exception as e:
        print(f"清洗入库失败: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'code': 500, 'message': f'清洗失败: {str(e)}'}), 500


@socketio.on('connect')
def handle_connect():
    """客户端连接"""
    print(f'客户端已连接: {request.sid}')
    emit('connected', {'message': '连接成功'})


@socketio.on('disconnect')
def handle_disconnect():
    """客户端断开"""
    print(f'客户端已断开: {request.sid}')


@socketio.on('ping')
def handle_ping():
    """心跳检测"""
    emit('pong', {'timestamp': datetime.now().isoformat()})


@socketio.on('crawl:start')
def handle_crawl_start(data):
    """通过WebSocket启动爬取任务"""
    global last_account_index

    try:
        crawl_type = data.get('type', 'taobao')
        keyword = data.get('keyword', '')
        page_count = data.get('pages', 10)
        min_interval = data.get('minInterval', 2)
        max_interval = data.get('maxInterval', 5)
        use_proxy = data.get('useProxy', True)
        proxy_api = data.get('proxyApi', '')

        if crawl_type == 'taobao':
            if not keyword:
                emit('crawl:error', {'message': '关键词不能为空'})
                return

            # 加载账号池
            accounts = load_accounts_from_file()
            if not accounts:
                emit('crawl:error', {'message': '账号池为空，请先添加淘宝账号'})
                return

            # 自动使用下一个账号（轮换）
            last_account_index = (last_account_index + 1) % len(accounts)

            # 创建任务
            task_id = str(uuid.uuid4())

            crawl_tasks[task_id] = {
                'taskId': task_id,
                'keyword': keyword,
                'pageCount': page_count,
                'minInterval': min_interval,
                'maxInterval': max_interval,
                'useProxy': use_proxy,
                'proxyApi': proxy_api,
                'status': 'running',
                'currentRound': 0,
                'totalProducts': 0,
                'accounts': accounts,
                'currentAccountIndex': last_account_index,
                'startTime': datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            }

            # 在新线程中启动爬取
            thread = threading.Thread(
                target=crawl_thread,
                args=(task_id, keyword, page_count)
            )
            thread.daemon = True
            thread.start()

            emit('crawl:started', {'taskId': task_id, 'keyword': keyword})

        elif crawl_type == 'tmall':
            # 天猫榜单爬取
            task_id = str(uuid.uuid4())
            tmall_tasks[task_id] = {
                'taskId': task_id,
                'status': 'running',
                'startTime': datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                'totalCount': 0
            }

            thread = threading.Thread(target=tmall_crawl_thread, args=(task_id,))
            thread.daemon = True
            thread.start()

            emit('crawl:started', {'taskId': task_id, 'type': 'tmall'})

    except Exception as e:
        emit('crawl:error', {'message': f'启动失败: {str(e)}'})


@socketio.on('crawl:stop')
def handle_crawl_stop(data):
    """通过WebSocket停止爬取任务"""
    task_id = data.get('taskId')

    task = crawl_tasks.get(task_id)
    if task:
        task['status'] = 'stopped'
        task['endTime'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        emit('crawl:stopped', {
            'taskId': task_id,
            'totalRounds': task['currentRound'],
            'totalProducts': task['totalProducts']
        })


if __name__ == '__main__':
    print("电商数据爬虫服务启动中...")
    print(f"  启动时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  服务地址: http://localhost:5000")
    print(f"  WebSocket: ws://localhost:5000/socket.io/")
    print("=" * 60)
    print()

    # 启动服务
    socketio.run(
        app,
        host='0.0.0.0',
        port=5000,
        debug=True,
        allow_unsafe_werkzeug=True
    )
