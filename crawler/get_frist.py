import os
import time
import random
import re
import json
import asyncio
import requests
from hashlib import md5
from datetime import datetime
from urllib.parse import urlencode, urlparse, parse_qs

from openpyxl import workbook

# Cookie 共享文件路径
COOKIES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shared_cookies.json")

# DrissionPage 导入
from DrissionPage import ChromiumPage, ChromiumOptions

# crawl4ai 导入
from crawl4ai import AsyncWebCrawler, BrowserConfig, CrawlerRunConfig, CacheMode

# 爬虫专用配置目录（与日常Edge完全隔离）
CRAWLER_PROFILE_DIR = os.path.join(os.path.dirname(__file__), "edge_crawler_profile")

# 代理API配置（青果网络）- 固定格式模板，仅 key 需动态填写
PROXY_API_TEMPLATE = "https://share.proxy.qg.net/get?key={key}&num=1&area=&isp=0&format=txt&distinct=false"
DEFAULT_PROXY_KEY = "3FA463EE"


def extract_key_from_proxy_input(input_str):
    """从前端输入的代理URL或文本中提取 key 值

    支持以下输入格式：
    - 完整URL: https://share.proxy.qg.net/get?key=ABCD1234&num=1&...
    - key=xxx: key=ABCD1234 或 ?key=ABCD1234
    - 纯 key: ABCD1234

    Returns:
        str: 提取到的 key，无效时返回 None
    """
    if not input_str or not isinstance(input_str, str):
        return None

    s = input_str.strip()
    s = s.replace('\\r\\n', '').replace('\r\n', '').replace('%0D%0A', '')

    if not s:
        return None

    # 尝试从URL中解析 key 参数
    if 'key=' in s.lower():
        # 可能是完整URL或 key=xxx 片段
        if s.startswith('http'):
            parsed = urlparse(s)
            params = parse_qs(parsed.query)
            key_list = params.get('key') or params.get('KEY')
            if key_list:
                return key_list[0].strip()
        else:
            # 匹配 key=xxx（可能带 & 或结尾）
            match = re.search(r'key=([^&\s]+)', s, re.IGNORECASE)
            if match:
                return match.group(1).strip()

    # 纯 key：只含字母数字和常见符号，长度合理（青果 key 通常 8 位）
    if re.match(r'^[A-Za-z0-9_-]{4,32}$', s):
        return s

    return None


def build_proxy_api_url(proxy_input):
    """根据用户输入构建代理 API URL

    - 若能提取到 key：用 key 填充固定模板
    - 否则：使用默认 key
    """
    key = extract_key_from_proxy_input(proxy_input)
    key = key if key else DEFAULT_PROXY_KEY
    return PROXY_API_TEMPLATE.format(key=key)


class ProxyManager:
    """代理IP管理器 - 管理代理获取、过期检测、自动刷新"""

    def __init__(self, api_url=None):
        # 从用户输入中提取 key，用固定模板构建代理 API URL（仅 key 动态变化）
        self.api_url = build_proxy_api_url(api_url)
        self.current_proxy = None  # {"server": "ip:port", "deadline": "过期时间"}
        self.proxy_config = None   # crawl4ai 格式的代理配置

    def fetch_new_proxy(self):
        """从API获取新的代理IP"""
        try:
            response = requests.get(self.api_url, timeout=10)
            text = response.text.strip()

            # 尝试 JSON 格式解析
            if text.startswith('{'):
                data = response.json()
                if data.get("code") == "SUCCESS" and data.get("data"):
                    proxy_info = data["data"][0]
                    server = proxy_info.get("server")
                    deadline = proxy_info.get("deadline")
                else:
                    print(f"✗ 获取代理失败: {data}")
                    return False
            else:
                # TXT 格式: 直接是 IP:PORT
                server = text
                # TXT格式没有过期时间，设置1分钟后过期
                from datetime import timedelta
                deadline = (datetime.now() + timedelta(minutes=1)).strftime("%Y-%m-%d %H:%M:%S")

            # 验证代理格式是否正确（应该是 IP:PORT 格式）
            if server and ':' in server and not 'FAILED' in server.upper():
                # 进一步验证是否为有效 IP:PORT
                parts = server.split(':')
                if len(parts) == 2 and parts[1].isdigit():
                    self.current_proxy = {
                        "server": server,
                        "deadline": deadline,
                    }
                    # 转换为 crawl4ai 格式
                    self.proxy_config = {
                        "server": f"http://{server}"
                    }
                    print(f"✓ 获取新代理: {server} (过期时间: {deadline})")
                    return True

            print(f"✗ 代理获取失败或格式错误: {text}")
            return False

        except Exception as e:
            print(f"✗ 获取代理异常: {e}")
            return False

    def is_expired(self):
        """检查当前代理是否过期"""
        if not self.current_proxy or not self.current_proxy.get("deadline"):
            return True

        try:
            deadline = datetime.strptime(self.current_proxy["deadline"], "%Y-%m-%d %H:%M:%S")
            # 提前10秒认为过期，避免临界问题
            return datetime.now() >= deadline
        except:
            return True

    def get_proxy(self):
        """获取可用代理（过期则自动刷新）"""
        if self.is_expired():
            print("代理已过期或不存在，正在获取新代理...")
            if not self.fetch_new_proxy():
                return None
        return self.proxy_config

    def force_refresh(self):
        """强制刷新代理（不管是否过期）"""
        print("🔄 强制刷新代理IP...")
        return self.fetch_new_proxy()


class Spider:
    def __init__(self, proxy_api_url=None):
        """初始化爬虫实例

        Args:
            proxy_api_url: 可选的代理API地址，如果提供则使用该地址获取代理
        """
        self.api_url = 'https://h5api.m.taobao.com/h5/mtop.relationrecommend.wirelessrecommend.recommend/2.0/'
        self.cookies_str = ''
        self.cookies_list = []  # crawl4ai 格式的 cookies
        self.wb = workbook.Workbook()
        self.ws = self.wb.active
        # 添加爬取时间字段
        self.ws.append(['ID', 'pic_url', 'title', 'price', 'sale_num', 'store', 'shop_url', '搜索关键词', '发货地', '店铺标签', '卷后价', '爬取时间'])
        self.proxy_manager = ProxyManager(api_url=proxy_api_url)
        self.seen_ids = set()  # 用于去重,记录已爬取的商品ID
        self.current_keyword = ''  # 当前爬取的搜索关键词
        self.crawl_time = ''  # 当前爬取批次的时间戳

        # Session 池：存放多个账号的 cookie 信息
        self.session_pool = []  # [{cookies_str, cookies_list, account_name}, ...]
        self.current_session_index = 0  # 当前使用的 session 索引
        self.round_count = 0  # 爬取轮数计数

    def random_sleep(self, min_sec=1, max_sec=3):
        time.sleep(random.uniform(min_sec, max_sec))

    def init_session_pool(self, num_accounts=2):
        """初始化 Session 池，登录多个账号获取 cookies"""
        print(f"\n{'='*50}")
        print(f"【Session 池初始化】需要登录 {num_accounts} 个账号")
        print(f"{'='*50}")

        # 配置 DrissionPage 使用 Edge 浏览器
        co = ChromiumOptions()
        edge_path = r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
        co.set_browser_path(edge_path)
        co.set_user_data_path(CRAWLER_PROFILE_DIR)
        co.set_argument('--start-maximized')
        co.set_argument('--disable-blink-features=AutomationControlled')

        # 使用同一个浏览器实例完成所有账号登录
        print("\n正在启动 Edge 浏览器...")
        page = ChromiumPage(co)
        print("✓ Edge 浏览器启动成功")

        try:
            for i in range(num_accounts):
                print(f"\n{'='*50}")
                print(f"📱 正在获取第 {i+1} 个账号的 cookies...")
                print(f"{'='*50}")

                page.get("https://www.taobao.com")
                self.random_sleep(2, 3)

                # 提示用户确认登录状态
                print(f"\n请在浏览器中登录第 {i+1} 个淘宝账号")
                print("【重要】请确保右上角显示的是正确的账号名")
                print("确认登录完成后，按回车键继续...")
                input()

                # 刷新页面确保获取最新 cookies
                page.refresh()
                self.random_sleep(2, 3)

                # 获取 cookies
                cookies = page.cookies()
                cookies_str = '; '.join([f"{c['name']}={c['value']}" for c in cookies])

                # 验证是否有关键的 _m_h5_tk token
                token = re.findall(r'_m_h5_tk=([^;]+)', cookies_str)
                if not token:
                    print(f"   ⚠ 警告: 未找到 _m_h5_tk token")
                    print("   请确保已正确登录淘宝账号，然后按回车重试...")
                    input()
                    page.refresh()
                    self.random_sleep(2, 3)
                    cookies = page.cookies()
                    cookies_str = '; '.join([f"{c['name']}={c['value']}" for c in cookies])

                cookies_list = []
                for c in cookies:
                    cookie_item = {
                        "name": c['name'],
                        "value": c['value'],
                        "domain": c.get('domain', '.taobao.com'),
                        "path": c.get('path', '/')
                    }
                    cookies_list.append(cookie_item)

                # 存入 session 池
                account_name = f"账号{i+1}"
                self.session_pool.append({
                    'cookies_str': cookies_str,
                    'cookies_list': cookies_list,
                    'account_name': account_name
                })

                # 显示 token 信息
                token = re.findall(r'_m_h5_tk=([^;]+)', cookies_str)
                if token:
                    print(f"✓ {account_name} Cookies获取成功 (共{len(cookies_list)}个)")
                    print(f"   Token: {token[0][:30]}...")
                else:
                    print(f"✓ {account_name} Cookies获取成功 (共{len(cookies_list)}个) [⚠无Token]")

                # 如果还需要登录下一个账号，清除 cookies 并让用户重新登录
                if i < num_accounts - 1:
                    print(f"\n⚠ 准备切换到第 {i+2} 个账号...")
                    print("   正在清除当前 cookies...")

                    # 清除所有 cookies
                    page.set.cookies.clear()
                    self.random_sleep(1, 2)

                    # 刷新页面，会变成未登录状态
                    page.get("https://www.taobao.com")
                    self.random_sleep(2, 3)

                    print(f"   ✓ Cookies 已清除")
                    print(f"\n请登录第 {i+2} 个淘宝账号")
                    print("完成后按回车键继续...")
                    input()

        finally:
            page.quit()

        print(f"\n{'='*50}")
        print(f"✓ Session 池初始化完成！共 {len(self.session_pool)} 个账号")

        # 显示所有账号的 token（用于验证是否不同）
        print("\n账号 Token 验证:")
        for session in self.session_pool:
            token = re.findall(r'_m_h5_tk=([^;]+)', session['cookies_str'])
            token_str = token[0][:20] if token else "无"
            print(f"   {session['account_name']}: {token_str}...")
        print(f"{'='*50}\n")

        # 保存 cookies 到共享文件，供其他爬虫使用
        self.save_cookies_to_file()

    def save_cookies_to_file(self):
        """保存 cookies 到共享文件，供其他爬虫使用"""
        if not self.session_pool:
            return

        # 使用第一个账号的 cookies 作为共享 cookies
        session = self.session_pool[0]
        cookies_data = {
            'cookies_str': session['cookies_str'],
            'cookies_dict': {c['name']: c['value'] for c in session['cookies_list']},
            'update_time': datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }

        try:
            with open(COOKIES_FILE, 'w', encoding='utf-8') as f:
                json.dump(cookies_data, f, ensure_ascii=False, indent=2)
            print(f"✓ Cookies 已保存到共享文件: {COOKIES_FILE}")
        except Exception as e:
            print(f"⚠ 保存 cookies 失败: {e}")

    def switch_session(self):
        """切换到下一个 session（账号）"""
        if not self.session_pool:
            return False

        self.current_session_index = (self.current_session_index + 1) % len(self.session_pool)
        session = self.session_pool[self.current_session_index]
        self.cookies_str = session['cookies_str']
        self.cookies_list = session['cookies_list']
        print(f"🔄 切换到 {session['account_name']}")
        return True

    def use_current_session(self):
        """使用当前 session 的 cookies"""
        if not self.session_pool:
            return False

        session = self.session_pool[self.current_session_index]
        self.cookies_str = session['cookies_str']
        self.cookies_list = session['cookies_list']
        print(f"📱 当前使用: {session['account_name']}")

        # 检查是否有关键的 _m_h5_tk token
        token = re.findall(r'_m_h5_tk=([^;]+)', self.cookies_str)
        if token:
            print(f"   ✓ Token: {token[0][:20]}...")
        else:
            print(f"   ⚠ 警告: 未找到 _m_h5_tk token，可能需要重新登录")

        return True

    def get_sign(self, t, data):
        """生成sign签名"""
        token = re.findall(r'_m_h5_tk=([^_]+)_', self.cookies_str)
        token = token[0] if token else ''
        sign_str = f"{token}&{t}&12574478&{data}"
        return md5(sign_str.encode()).hexdigest()

    def build_api_url(self, page, shopping_info):
        """构建API请求URL - 使用与浏览器一致的完整参数"""
        t = str(int(time.time() * 1000))
        page_size = 48

        # 使用与浏览器完全一致的参数结构
        params_inner = {
            "device": "HMA-AL00",
            "isBeta": "false",
            "grayHair": "false",
            "from": "nt_history",
            "brand": "HUAWEI",
            "info": "wifi",
            "index": "4",
            "rainbow": "",
            "schemaType": "auction",
            "elderHome": "false",
            "isEnterSrpSearch": "true",
            "newSearch": "false",
            "network": "wifi",
            "subtype": "",
            "hasPreposeFilter": "false",
            "prepositionVersion": "v2",
            "client_os": "Android",
            "gpsEnabled": "false",
            "searchDoorFrom": "srp",
            "debug_rerankNewOpenCard": "false",
            "homePageVersion": "v7",
            "searchElderHomeOpen": "false",
            "search_action": "initiative",
            "sugg": "_4_1",
            "sversion": "13.6",
            "style": "list",
            "ttid": "600000@taobao_pc_10.7.0",
            "needTabs": "true",
            "areaCode": "CN",
            "vm": "nw",
            "countryNum": "156",
            "m": "pc",
            "page": page,
            "n": page_size,
            "q": shopping_info,  # 原始关键词，urlencode会自动处理编码
            "qSource": "url",
            "pageSource": "a21bo.jianhua/a.search_manual.0",
            "channelSrp": "",
            "tab": "all",  # 改为 "all"，与浏览器一致
            "pageSize": page_size,
            "totalPage": 100,
            "totalResults": 4800,
            "sourceS": "0",
            "sort": "_coefp",
            "bcoffset": "",
            "ntoffset": "",
            "filterTag": "",
            "service": "",
            "prop": "",
            "loc": "",
            "start_price": None,
            "end_price": None,
            "startPrice": None,
            "endPrice": None,
            "itemIds": None,
            "p4pIds": None,
            "p4pS": None,
            "categoryp": "",
            "ha3Kvpairs": None,
            "myCNA": "",
            "screenResolution": "1920x1080",
            "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
            "couponUnikey": "",
            "subTabId": "",
            "np": "",
            "clientType": "h5",
            "isNewDomainAb": "false",
            "forceOldDomain": "false"
        }

        data = json.dumps({
            "appId": "34385",
            "params": json.dumps(params_inner, separators=(',', ':'))
        }, separators=(',', ':'))

        params = {
            'jsv': '2.7.4',  # 更新版本号
            'appKey': '12574478',
            't': t,
            'sign': self.get_sign(t, data),
            'api': 'mtop.relationrecommend.wirelessrecommend.recommend',
            'v': '2.0',
            'timeout': '10000',  # 添加超时参数
            'type': 'jsonp',
            'dataType': 'jsonp',
            'callback': 'mtopjsonp6',
            'data': data
        }

        return f"{self.api_url}?{urlencode(params)}"

    async def crawl_page(self, crawler, page, shopping_info, run_config, is_first_page=False, check_stop_fn=None):
        """使用crawl4ai爬取单页数据

        Args:
            crawler: AsyncWebCrawler实例
            page: 页码
            shopping_info: 搜索关键词
            run_config: 爬取配置
            is_first_page: 保留参数但不再使用（无需刷新页面）
            check_stop_fn: 检查是否应该停止的回调函数
        """
        # 检查是否应该停止
        if check_stop_fn and check_stop_fn():
            print(f"   ⚠ 收到停止信号，终止爬取")
            return False

        # 随机延迟 3-5 秒，防止被风控
        if page > 1:
            delay = random.uniform(3, 5)
            print(f"   ⏳ 等待 {delay:.1f} 秒...")
            await asyncio.sleep(delay)

            # 等待后再次检查停止信号
            if check_stop_fn and check_stop_fn():
                print(f"   ⚠ 收到停止信号，终止爬取")
                return False

        url = self.build_api_url(page, shopping_info)
        print(f"正在获取第{page}页...")

        try:
            result = await crawler.arun(url=url, config=run_config)
            if result.success:
                # 从 <pre> 标签中提取 JSONP 内容
                response_text = result.html
                match = re.search(r'<pre[^>]*>(.*?)</pre>', response_text, re.DOTALL)
                if match:
                    response_text = match.group(1)

                # 调试：检查返回数据中是否包含不同的商品
                self._debug_response(response_text, page)

                # 解析数据，使用返回值判断是否成功
                parse_success = self.parse_response_data(response_text)
                if not parse_success:
                    print(f"   ✗ 第{page}页数据解析失败（API可能返回错误）")
                return parse_success
            else:
                print(f"   ✗ 请求失败: {result.error_message}")
                return False
        except Exception as e:
            print(f"   ✗ 请求异常: {e}")
            return False

    def _debug_response(self, response_text, page):
        """调试：打印返回数据的关键信息"""
        try:
            if 'mtopjsonp' in response_text:
                start, end = response_text.find('('), response_text.rfind(')')
                if start != -1 and end != -1:
                    json_str = response_text[start+1:end]
                    json_data = json.loads(json_str)

                    # 检查 ret 状态
                    ret = json_data.get('ret', [])
                    if ret:
                        print(f"   [DEBUG] API返回状态: {ret}")

                    # 获取 data 字段
                    data = json_data.get('data', {})

                    # 如果 data 是字符串，需要再次解析
                    if isinstance(data, str):
                        print(f"   [DEBUG] data是字符串，长度: {len(data)}")
                        if len(data) < 200:
                            print(f"   [DEBUG] data内容: {data}")
                        try:
                            data = json.loads(data)
                        except:
                            print(f"   [DEBUG] data二次解析失败")
                            return

                    # 打印 data 的 keys
                    if isinstance(data, dict):
                        print(f"   [DEBUG] data.keys: {list(data.keys())[:10]}")

                    items = data.get('itemsArray', []) if isinstance(data, dict) else []
                    if items:
                        first_id = items[0].get('item_id', 'N/A')
                        last_id = items[-1].get('item_id', 'N/A') if len(items) > 1 else 'N/A'
                        print(f"   [DEBUG] 第{page}页: {len(items)}条, 首ID={first_id}, 尾ID={last_id}")
        except Exception as e:
            print(f"   [DEBUG] 解析调试信息失败: {e}")



    def _create_browser_config(self, proxy_config):
        """创建浏览器配置"""
        browser_kwargs = {
            "browser_type": "chromium",
            "headless": True,
            "verbose": False,
            "enable_stealth": True,  # 反爬核心
            "headers": {'referer': 'https://s.taobao.com/'},
            "cookies": self.cookies_list,
            "viewport_width": 1920,
            "viewport_height": 1080
        }
        if proxy_config:
            browser_kwargs["proxy_config"] = proxy_config
        return BrowserConfig(**browser_kwargs)

    async def crawl_with_crawl4ai(self, shopping_info, num_pages):
        """使用crawl4ai进行爬取，每10-12页自动换代理"""
        run_config = CrawlerRunConfig(cache_mode=CacheMode.BYPASS, page_timeout=30000)

        page = 1
        while page <= num_pages:
            # 每轮开始时强制获取全新代理
            self.proxy_manager.force_refresh()
            proxy_config = self.proxy_manager.get_proxy()

            # 计算本轮爬取页数（10-12页随机）
            pages_before_switch = random.randint(10, 12)
            pages_this_round = min(pages_before_switch, num_pages - page + 1)

            print(f"\n🚀 开始爬取 | Stealth: ✓ | 代理: {proxy_config['server'] if proxy_config else '未使用'}")
            print(f"   本轮计划爬取 {pages_this_round} 页 (第{page}页 - 第{page + pages_this_round - 1}页)\n")

            browser_config = self._create_browser_config(proxy_config)

            async with AsyncWebCrawler(config=browser_config) as crawler:
                is_first_in_round = True  # 每轮代理切换后的第一页需要预刷新
                for _ in range(pages_this_round):
                    success = await self.crawl_page(crawler, page, shopping_info, run_config, is_first_page=is_first_in_round)
                    is_first_in_round = False  # 后续页面不需要预刷新

                    if not success:
                        print(f"   ⚠ 第{page}页失败，尝试换代理重试...")
                        break  # 跳出内层循环，换代理重试

                    page += 1
                    if page <= num_pages:
                        delay = random.uniform(2, 5)
                        print(f"   等待 {delay:.1f} 秒...")
                        await asyncio.sleep(delay)

            # 等待1秒让代理切换生效
            if page <= num_pages:
                await asyncio.sleep(1)

    def parse_start_url(self):
        """主流程：先获取cookies，再用crawl4ai爬取"""
        # 第一步：初始化 Session 池（登录 2 个账号）
        self.init_session_pool(num_accounts=2)

        if not self.session_pool:
            print("Session 池初始化失败，请重试")
            return

        # 循环爬取
        while True:
            self.round_count += 1
            print(f"\n{'='*50}")
            print(f"【第 {self.round_count} 轮爬取】")
            print(f"{'='*50}")

            # 使用当前 session
            self.use_current_session()

            # 输入搜索信息
            shopping_info = input('请输入需要搜索的商品:')
            self.current_keyword = shopping_info  # 保存当前搜索关键词
            num = int(input("请输入获取多少页数据:"))

            # 记录本次爬取的时间戳
            self.crawl_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            print(f"爬取时间: {self.crawl_time}")

            # 使用 crawl4ai 爬取数据
            asyncio.run(self.crawl_with_crawl4ai(shopping_info, num))

            # 保存数据
            self.wb.save('taobao_frist.xlsx')
            print(f'\n✓ 第 {self.round_count} 轮爬取完成！数据已保存到 taobao_frist.xlsx')

            # 询问是否继续
            print(f"\n{'='*50}")
            continue_choice = input("是否继续爬取？(y/n): ").strip().lower()
            if continue_choice != 'y':
                print("\n爬取结束，感谢使用！")
                break

            # 切换到下一个账号
            self.switch_session()

    def parse_response_data(self, response):
        """解析API响应数据

        Returns:
            bool: True 表示成功获取数据，False 表示API返回错误或解析失败
        """
        try:
            response = response.strip()
            # 提取 JSONP 中的 JSON: mtopjsonp6({...})
            if 'mtopjsonp' in response:
                start, end = response.find('('), response.rfind(')')
                if start != -1 and end != -1:
                    response = response[start+1:end]

            json_data = json.loads(response)

            # 检查 API 是否返回错误
            ret = json_data.get('ret', [])
            if ret and 'SUCCESS' not in str(ret):
                # 检测常见的反爬错误
                ret_str = str(ret)
                if 'RGV587' in ret_str or 'ERROR' in ret_str.upper() or 'FAIL' in ret_str.upper():
                    print(f"   ✗ API返回错误（可能被风控拦截）: {ret}")
                    return False
                print(f"   ⚠ API返回警告: {ret}")
                return False

            # 获取 data 字段，可能是字符串需要再次解析
            data_field = json_data.get('data', {})
            if isinstance(data_field, str):
                try:
                    data_field = json.loads(data_field)
                except json.JSONDecodeError:
                    print(f"   ⚠ data字段解析失败，尝试其他方式...")
                    data_field = {}

            # 尝试多种数据路径获取商品列表
            items = None

            # 路径1: data.itemsArray (常见格式)
            if isinstance(data_field, dict):
                items = data_field.get('itemsArray', [])

            # 路径2: data.resultValue.searchDO (另一种格式)
            if not items and isinstance(data_field, dict):
                result_value = data_field.get('resultValue', {})
                if isinstance(result_value, str):
                    try:
                        result_value = json.loads(result_value)
                    except:
                        result_value = {}
                search_do = result_value.get('searchDO', {})
                if isinstance(search_do, dict):
                    item_list_type = search_do.get('itemlisttype', {})
                    if isinstance(item_list_type, dict):
                        items = item_list_type.get('itemlist', [])

            # 路径3: 直接在 data 中查找 items 或 itemlist
            if not items and isinstance(data_field, dict):
                items = data_field.get('items', []) or data_field.get('itemlist', [])

            if not items:
                print(f"   未找到商品数据 (data类型: {type(data_field).__name__})")
                return

            count = 0
            skipped = 0
            for item in items:
                # 跳过自定义卡片
                if isinstance(item, dict) and ('customCardType' in item or 'customCard' in item):
                    continue

                # 兼容不同的数据格式
                if isinstance(item, dict):
                    # 去重：检查商品ID是否已存在
                    item_id = item.get('item_id', '') or item.get('itemId', '') or item.get('nid', '')
                    if item_id in self.seen_ids:
                        skipped += 1
                        continue
                    self.seen_ids.add(item_id)

                    # 对 auctionURL 进行完全解码（处理多重编码）
                    auction_url = item.get('auctionURL', '') or item.get('url', '') or item.get('item_url', '')
                    while '&amp;' in auction_url:
                        auction_url = auction_url.replace('&amp;', '&')

                    # 获取店铺信息（兼容不同格式）
                    shop_info = item.get('shopInfo', {})
                    if isinstance(shop_info, str):
                        shop_title = shop_info
                    elif isinstance(shop_info, dict):
                        shop_title = shop_info.get('title', '') or shop_info.get('name', '')
                    else:
                        shop_title = item.get('nick', '') or item.get('shopName', '')

                    # 获取价格信息（兼容不同格式）
                    price_show = item.get('priceShow', {})
                    if isinstance(price_show, dict):
                        coupon_price = price_show.get('price', '')
                    else:
                        coupon_price = item.get('couponPrice', '') or item.get('promotion_price', '')

                    # 处理图片URL，确保有协议头
                    pic_url = item.get('pic_path', '') or item.get('pic_url', '') or item.get('img', '')
                    if pic_url and pic_url.startswith('//'):
                        pic_url = 'https:' + pic_url

                    self.ws.append([
                        item_id,
                        pic_url,
                        item.get('title', '') or item.get('raw_title', ''),
                        item.get('price', '') or item.get('view_price', ''),
                        item.get('realSales', '') or item.get('view_sales', '') or item.get('sales', ''),
                        shop_title,
                        auction_url,
                        self.current_keyword,  # 搜索关键词
                        item.get('procity', '') or item.get('item_loc', ''),  # 发货地
                        item.get('shopTag', '') or item.get('shop_tag', ''),  # 店铺标签
                        coupon_price,  # 卷后价
                        self.crawl_time  # 爬取时间
                    ])
                    count += 1

            msg = f"   ✓ 本页获取 {count} 条商品数据"
            if skipped > 0:
                msg += f" (跳过 {skipped} 条重复)"
            print(msg)
            return True  # 成功获取数据

        except json.JSONDecodeError as e:
            print(f"   解析失败: JSON格式错误 - {e}")
            return False
        except Exception as e:
            print(f"   解析失败: {e}")
            return False


if __name__ == '__main__':
    Spider().parse_start_url()