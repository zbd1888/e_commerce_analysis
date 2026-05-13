"""
天猫榜单热销数据爬虫
使用 crawl4ai + stealth 模式爬取天猫榜单各分类热销榜数据
与淘宝商品爬虫保持一致的反爬策略
"""
import random
import json
import time
import re
import os
import asyncio
import requests
from hashlib import md5
from datetime import datetime
from urllib.parse import urlencode
from openpyxl import Workbook

# crawl4ai 导入
from crawl4ai import AsyncWebCrawler, BrowserConfig, CrawlerRunConfig, CacheMode

# 代理 key 动态填充逻辑（与 get_frist.py 共用）
from get_frist import build_proxy_api_url

# 用户数据目录
CRAWLER_PROFILE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "crawler_profile")

# Cookie 共享文件路径（与 get_frist.py 共用）
COOKIES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shared_cookies.json")


class ProxyManager:
    """代理IP管理器 - 与get_frist.py保持一致，支持动态 key"""

    def __init__(self, api_url=None):
        self.api_url = build_proxy_api_url(api_url)
        self.current_proxy = None
        self.proxy_config = None

    def fetch_new_proxy(self):
        """从API获取新的代理IP"""
        try:
            response = requests.get(self.api_url, timeout=10)
            text = response.text.strip()

            # JSON 格式解析
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
                from datetime import timedelta
                deadline = (datetime.now() + timedelta(minutes=1)).strftime("%Y-%m-%d %H:%M:%S")

            # 验证代理格式
            if server and ':' in server and 'FAILED' not in server.upper():
                parts = server.split(':')
                if len(parts) == 2 and parts[1].isdigit():
                    self.current_proxy = {"server": server, "deadline": deadline}
                    self.proxy_config = {"server": f"http://{server}"}
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

# ind2Id 与分类名称的映射
IND2ID_MAP = {
    "1000": "为你精选",
    "1001": "美妆护肤",
    "1002": "个人家清",
    "1003": "手机数码",
    "1004": "品质家电",
    "1005": "母婴亲子",
    "1006": "家居家装",
    "1008": "医药健康",
    "1009": "萌宠潮玩",
    "1010": "酷车出行",
    "1011": "食品生鲜",
    "1017": "运动户外",
    "1019": "天猫进口",
    "1021": "服装时尚",
    "1022": "图书音像",
}

class TmallRankSpider:
    def __init__(self, use_proxy=True):
        self.api_url = "https://h5api.m.taobao.com/h5/mtop.tmall.kangaroo.core.service.route.aldlampservice/1.0/"
        self.target_url = "https://huodong.taobao.com/wow/z/tbhome/tbpc-venue/ranklist?spm=a21bo.jianhua/a.channel_drawer.d6_1.32c52a896Knc5P"
        self.cookies_str = ""
        self.cookies_list = []  # 用于 crawl4ai 的 cookies 列表格式
        self.cookies_dict = {}

        # 代理管理器
        self.use_proxy = use_proxy
        self.proxy_manager = ProxyManager() if use_proxy else None

        # Excel 相关
        self.wb = Workbook()
        self.ws = self.wb.active
        # 添加爬取时间字段
        self.ws.append(['分类', '榜单名称', '热度描述', '爬取时间'])
        self.crawl_time = ''  # 爬取时间戳

        # 停止标志
        self.should_stop = False

    def get_sign(self, t, data):
        """生成sign签名（和get_frist.py逻辑一样）"""
        token = re.findall(r'_m_h5_tk=([^_]+)_', self.cookies_str)
        token = token[0] if token else ''
        sign_str = f"{token}&{t}&12574478&{data}"
        return md5(sign_str.encode()).hexdigest()

    def load_cookies_from_file(self):
        """从共享文件加载 cookies（由 get_frist.py 保存）"""
        if not os.path.exists(COOKIES_FILE):
            print(f"⚠ 共享 cookies 文件不存在: {COOKIES_FILE}")
            return False

        try:
            with open(COOKIES_FILE, 'r', encoding='utf-8') as f:
                cookies_data = json.load(f)

            self.cookies_str = cookies_data.get('cookies_str', '')
            self.cookies_dict = cookies_data.get('cookies_dict', {})
            update_time = cookies_data.get('update_time', '未知')

            # 验证 cookies 是否有效（检查 _m_h5_tk token）
            token = re.findall(r'_m_h5_tk=([^;]+)', self.cookies_str)
            if token:
                print(f"✓ 从共享文件加载 cookies 成功")
                print(f"   更新时间: {update_time}")
                print(f"   Token: {token[0][:30]}...")
                return True
            else:
                print(f"⚠ 共享 cookies 中未找到有效 token")
                return False

        except Exception as e:
            print(f"✗ 读取共享 cookies 失败: {e}")
            return False

    def _create_browser_config(self, proxy_config=None):
        """创建浏览器配置（使用stealth模式 + cookies + 代理）- 与淘宝商品爬虫保持一致"""
        browser_kwargs = {
            "browser_type": "chromium",
            "headless": True,
            "verbose": False,
            "text_mode": True,  # 只获取文本，提高速度
            "headers": {
                'referer': self.target_url,
            },
            "cookies": self.cookies_list,
            "viewport_width": 1920,
            "viewport_height": 1080
        }
        # 如果有代理配置，添加到浏览器配置中
        if proxy_config:
            browser_kwargs["proxy_config"] = proxy_config
            print(f"   🌐 使用代理: {proxy_config.get('server', '未知')}")
        return BrowserConfig(**browser_kwargs)

    def get_current_proxy(self):
        """获取当前可用的代理配置"""
        if not self.use_proxy or not self.proxy_manager:
            return None
        return self.proxy_manager.get_proxy()

    async def warmup_browser(self, crawler, run_config):
        """预热浏览器 - 先访问天猫榜单页面激活cookies"""
        print("   🔥 预热浏览器，访问天猫榜单页面...")
        try:
            result = await crawler.arun(url=self.target_url, config=run_config)
            if result.success:
                print("   ✓ 预热成功")
                await asyncio.sleep(2)  # 等待2秒让cookies生效
                return True
            else:
                print(f"   ⚠ 预热失败: {result.error_message}")
                return False
        except Exception as e:
            print(f"   ⚠ 预热异常: {e}")
            return False

    def build_data_param(self, ind2_id):
        """构建 data 参数 - 与浏览器请求完全一致"""
        # 完整的页面URL（包含spm参数）
        full_page_url = "https://huodong.taobao.com/wow/z/tbhome/tbpc-venue/ranklist?spm=a21bo.jianhua/a.channel_drawer.d6_1.32c52a896Knc5P"

        # extParam 需要是字符串格式的JSON
        ext_param_str = '{"rankOrder":"18,32,20,9,23","launchPoolId":"1866,2094,2120,2184,1907,2185,2512","importLaunchPoolIds":"1907,2185,2513"}'

        # 内层 params 对象
        inner_params = {
            "curPageUrl": full_page_url,
            "appId": "16881273",
            "bizId": "1111",
            "backupParams": "ind2Id,categoryId,type",
            "resId": "16881273",
            "ind2Id": str(ind2_id),
            "categoryId": "0",
            "type": "18",
            "bizType": "tmallTab",
            "page": 0,
            "pageSize": 10,
            "extParam": ext_param_str
        }

        # 外层 data 对象
        data = {
            "curPageUrl": full_page_url,
            "appId": "16881273",
            "bizId": "1111",
            "backupParams": "ind2Id,categoryId,type",
            "resId": "16881273",
            "ind2Id": str(ind2_id),
            "categoryId": "0",
            "type": "18",
            "bizType": "tmallTab",
            "page": 0,
            "pageSize": 10,
            "extParam": ext_param_str,
            "params": json.dumps(inner_params, separators=(',', ':'))
        }
        return json.dumps(data, separators=(',', ':'))


    def build_api_url(self, ind2_id):
        """构建完整的API请求URL"""
        t = str(int(time.time() * 1000))
        data = self.build_data_param(ind2_id)
        sign = self.get_sign(t, data)

        params = {
            "jsv": "2.7.4",  # 与淘宝商品爬虫保持一致
            "appKey": "12574478",
            "t": t,
            "sign": sign,
            "v": "1.0",
            "timeout": "20000",
            "type": "jsonp",  # 改为jsonp格式
            "dataType": "jsonp",
            "callback": "mtopjsonp1",  # 添加callback
            "api": "mtop.tmall.kangaroo.core.service.route.aldlampservice",
            "data": data
        }

        return f"{self.api_url}?{urlencode(params)}"

    async def fetch_category_data_async(self, crawler, ind2_id, run_config):
        """异步获取指定分类的榜单数据（使用crawl4ai）"""
        category_name = IND2ID_MAP.get(str(ind2_id), f"未知分类({ind2_id})")
        print(f"\n正在获取 [{category_name}] 的数据...")

        url = self.build_api_url(ind2_id)

        try:
            result = await crawler.arun(url=url, config=run_config)

            if not result.success:
                print(f"   ✗ 请求失败: {result.error_message}")
                return []

            # 从响应中提取JSON数据
            response_text = result.html

            # 尝试从 <pre> 标签中提取内容
            match = re.search(r'<pre[^>]*>(.*?)</pre>', response_text, re.DOTALL)
            if match:
                response_text = match.group(1)

            # 解析JSONP响应
            json_data = self._parse_jsonp_response(response_text)
            if not json_data:
                print(f"   ⚠ 无法解析响应数据")
                return []

            # 检查返回状态
            ret = json_data.get('ret', [])
            if ret and 'SUCCESS' not in str(ret):
                print(f"   ⚠ API返回错误: {ret}")
                return []

            # 解析数据路径: data.resultValue["16881273"].data[0].rankList
            result_value = json_data.get('data', {}).get('resultValue', {})
            data_16881273 = result_value.get('16881273', {})
            data_list = data_16881273.get('data', [])

            if not data_list:
                print(f"   ⚠ 未找到 data 数组")
                return []

            rank_list = data_list[0].get('rankList', [])

            if not rank_list:
                print(f"   ⚠ 未找到 rankList")
                return []

            print(f"   ✓ 获取到 {len(rank_list)} 条榜单数据")
            return rank_list

        except Exception as e:
            print(f"   ✗ 请求异常: {e}")
            return []

    def _parse_jsonp_response(self, response_text):
        """解析JSONP响应"""
        try:
            # 尝试直接解析JSON
            return json.loads(response_text)
        except:
            pass

        # 尝试解析JSONP格式: mtopjsonp1({...})
        try:
            if 'mtopjsonp' in response_text:
                start = response_text.find('(')
                end = response_text.rfind(')')
                if start != -1 and end != -1:
                    json_str = response_text[start+1:end]
                    return json.loads(json_str)
        except:
            pass

        return None

    def fetch_category_data(self, ind2_id):
        """同步版本的获取分类数据（兼容旧接口）"""
        return asyncio.run(self._fetch_single_category(ind2_id))

    async def _fetch_single_category(self, ind2_id):
        """单个分类的异步获取"""
        browser_config = self._create_browser_config()
        run_config = CrawlerRunConfig(cache_mode=CacheMode.BYPASS, page_timeout=30000)

        async with AsyncWebCrawler(config=browser_config) as crawler:
            return await self.fetch_category_data_async(crawler, ind2_id, run_config)

    def parse_and_save(self, rank_list, category_name):
        """解析榜单数据并保存到Excel"""
        count = 0
        for item in rank_list:
            kind_name = item.get('kindName', '')
            short_hot_desc = item.get('shortHotDesc', '')

            if kind_name:
                # 添加爬取时间
                self.ws.append([category_name, kind_name, short_hot_desc, self.crawl_time])
                count += 1

        print(f"   ✓ 保存 {count} 条数据")
        return count

    def crawl_all_categories(self):
        """爬取所有分类的数据（同步包装）"""
        asyncio.run(self.crawl_all_categories_async())

    async def crawl_all_categories_async(self, check_stop_fn=None):
        """异步爬取所有分类的数据（使用单个浏览器实例 + 代理IP）"""
        print("\n" + "=" * 50)
        print("开始爬取所有分类数据 (crawl4ai + stealth模式)")
        print("=" * 50)

        # 记录本次爬取的时间戳
        self.crawl_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"爬取时间: {self.crawl_time}\n")

        total_count = 0

        # 获取代理配置
        proxy_config = self.get_current_proxy()

        browser_config = self._create_browser_config(proxy_config)
        run_config = CrawlerRunConfig(cache_mode=CacheMode.BYPASS, page_timeout=30000)

        # 使用单个浏览器实例爬取所有分类
        async with AsyncWebCrawler(config=browser_config) as crawler:
            # 第一步：预热浏览器，访问天猫榜单页面激活cookies
            warmup_success = await self.warmup_browser(crawler, run_config)
            if not warmup_success:
                print("   ⚠ 预热失败，尝试继续爬取...")

            for ind2_id, category_name in IND2ID_MAP.items():
                # 检查是否应该停止
                if check_stop_fn and check_stop_fn():
                    print("   ⚠ 收到停止信号，终止爬取")
                    break

                if self.should_stop:
                    print("   ⚠ 收到停止信号，终止爬取")
                    break

                rank_list = await self.fetch_category_data_async(crawler, ind2_id, run_config)

                if rank_list:
                    count = self.parse_and_save(rank_list, category_name)
                    total_count += count

                # 每次请求间隔 3-5 秒（与淘宝商品爬虫一致）
                delay = random.uniform(3, 5)
                print(f"   ⏳ 等待 {delay:.1f} 秒...")
                await asyncio.sleep(delay)

        print("\n" + "=" * 50)
        print(f"✓ 爬取完成！共获取 {total_count} 条数据")
        print("=" * 50)

        return total_count

    def run(self):
        """主运行流程"""
        print("\n" + "=" * 50)
        print("天猫榜单热销数据爬虫 (crawl4ai版)")
        print("=" * 50)

        # 第一步：优先从共享文件加载 cookies（由 get_frist.py 生成）
        print("\n尝试从共享文件加载 cookies...")
        cookies_loaded = self.load_cookies_from_file()

        if not cookies_loaded:
            print("\n⚠ 共享 cookies 不可用")
            print("请先运行淘宝商品爬虫(get_frist.py)或通过Web界面添加账号")
            return

        if not self.cookies_str:
            print("获取 cookies 失败，请重试")
            return

        # 第二步：爬取所有分类数据
        self.crawl_all_categories()

        # 第三步：保存 Excel
        output_file = 'tmall_rank_data.xlsx'
        self.wb.save(output_file)
        print(f"\n✓ 数据保存完成！文件: {output_file}")


if __name__ == '__main__':
    spider = TmallRankSpider()
    spider.run()
