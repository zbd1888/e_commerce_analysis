"""
数据清洗模块
用于清洗爬虫采集的商品数据，并写入MySQL数据库

功能：
1. 数据质量检测 - 检查缺失值、重复数据、异常值
2. 标题清洗 - 去除HTML标签（如 <span class=H>鼠标</span>）
3. 销量转换 - 将 "2000+人付款"、"10万+" 转换为数值
4. 地域解析 - 从发货地提取省份和城市
5. 数据过滤 - 移除销量小于100的数据
6. 数据库写入 - 清洗后直接写入MySQL数据库
"""

import re
import os
import pandas as pd
from datetime import datetime
import pymysql
from pymysql.cursors import DictCursor

# MySQL 数据库配置从环境变量读取，避免把本机账号密码提交到 GitHub。
# 启动清洗脚本或爬虫服务前必须设置 DB_USERNAME 和 DB_PASSWORD。
DB_CONFIG = {
    'host': os.environ.get('DB_HOST', 'localhost'),
    'port': int(os.environ.get('DB_PORT', '3306')),
    'user': os.environ.get('DB_USERNAME'),
    'password': os.environ.get('DB_PASSWORD'),
    'database': os.environ.get('DB_NAME', 'ecommerce_analysis'),
    'charset': 'utf8mb4'
}


class DataCleaner:
    def __init__(self, input_file='taobao_frist.xlsx'):
        self.input_file = input_file
        self.df = None
        self.cleaned_df = None
        self.report = {}  # 清洗报告

    def load_data(self):
        """加载原始数据"""
        print(f"\n{'='*50}")
        print("【数据加载】")
        print(f"{'='*50}")
        
        try:
            self.df = pd.read_excel(self.input_file)
            print(f"✓ 成功加载 {self.input_file}")
            print(f"  原始数据量: {len(self.df)} 条")
            self.report['原始数据量'] = len(self.df)
            return True
        except FileNotFoundError:
            print(f"✗ 文件不存在: {self.input_file}")
            return False
        except Exception as e:
            print(f"✗ 加载失败: {e}")
            return False

    def detect_quality(self):
        """数据质量检测"""
        print(f"\n{'='*50}")
        print("【数据质量检测】")
        print(f"{'='*50}")

        if self.df is None:
            print("✗ 请先加载数据")
            return

        # 1. 缺失值检测
        print("\n1. 缺失值统计:")
        missing = self.df.isnull().sum()
        for col, count in missing.items():
            if count > 0:
                print(f"   {col}: {count} 条缺失 ({count/len(self.df)*100:.1f}%)")
        if missing.sum() == 0:
            print("   ✓ 无缺失值")
        self.report['缺失值总数'] = int(missing.sum())

        # 2. 重复数据检测
        duplicates = self.df.duplicated().sum()
        print(f"\n2. 重复数据: {duplicates} 条")
        self.report['重复数据'] = int(duplicates)

        # 3. 标题中的HTML标签检测（包括转义形式）
        def has_html_tag(x):
            text = str(x)
            # 检测普通HTML标签和转义的HTML标签
            return bool(re.search(r'<[^>]+>', text) or '&lt;' in text or '&gt;' in text)

        html_count = self.df['title'].apply(has_html_tag).sum()
        print(f"\n3. 标题含HTML标签: {html_count} 条")
        self.report['含HTML标签'] = int(html_count)

        # 4. 销量分布检测
        print(f"\n4. 销量分布检测:")
        sale_stats = self._analyze_sale_distribution()
        for key, value in sale_stats.items():
            print(f"   {key}: {value} 条")
        self.report['销量分布'] = sale_stats

        # 5. 价格异常检测
        print(f"\n5. 价格异常检测:")
        zero_price = len(self.df[self.df['price'] == 0])
        print(f"   价格为0: {zero_price} 条")
        print(f"   价格范围: {self.df['price'].min()} - {self.df['price'].max()} 元")
        self.report['价格为0'] = zero_price

    def _analyze_sale_distribution(self):
        """分析销量分布"""
        stats = {}
        
        def extract_sale_num(sale_str):
            """提取销量数值"""
            if not sale_str or pd.isna(sale_str):
                return 0
            sale_str = str(sale_str)
            match = re.search(r'([\d.]+)\s*(万)?', sale_str)
            if not match:
                return 0
            num = float(match.group(1))
            if match.group(2) == '万':
                num *= 10000
            return int(num)
        
        sale_values = self.df['sale_num'].apply(extract_sale_num)
        
        stats['销量<100'] = int((sale_values < 100).sum())
        stats['销量100-1000'] = int(((sale_values >= 100) & (sale_values < 1000)).sum())
        stats['销量1000-1万'] = int(((sale_values >= 1000) & (sale_values < 10000)).sum())
        stats['销量≥1万'] = int((sale_values >= 10000).sum())
        
        return stats

    def clean_title(self, title):
        """清洗标题 - 去除HTML标签"""
        if not title or pd.isna(title):
            return ''
        title = str(title)
        # 先处理转义的HTML标签 &lt;span class=H&gt; -> <span class=H>
        title = title.replace('&lt;', '<').replace('&gt;', '>')
        # 去除所有HTML标签，如 <span class=H>鼠标</span>
        title = re.sub(r'<[^>]+>', '', title)
        # 去除多余空格
        title = re.sub(r'\s+', ' ', title).strip()
        return title

    def extract_sale_value(self, sale_str):
        """提取销量数值"""
        if not sale_str or pd.isna(sale_str):
            return 0
        sale_str = str(sale_str)
        match = re.search(r'([\d.]+)\s*(万)?', sale_str)
        if not match:
            return 0
        num = float(match.group(1))
        if match.group(2) == '万':
            num *= 10000
        return int(num)

    def parse_location(self, location):
        """解析发货地，提取省份和城市"""
        if not location or pd.isna(location):
            return '', ''
        location = str(location).strip()
        # 格式: "广东 深圳" 或 "北京" 或 "广东"
        parts = location.split()
        if len(parts) >= 2:
            return parts[0], parts[1]
        elif len(parts) == 1:
            return parts[0], ''
        return '', ''

    def parse_coupon_price(self, price_str):
        """解析券后价"""
        if not price_str or pd.isna(price_str):
            return None
        try:
            # 处理可能的字符串格式
            price_str = str(price_str).replace('￥', '').replace(',', '').strip()
            return float(price_str)
        except:
            return None

    def clean_data(self, min_sales=0):
        """执行数据清洗

        Args:
            min_sales: 最小销量阈值，销量低于此值的数据将被过滤（默认0表示不过滤）
        """
        print(f"\n{'='*50}")
        print("【数据清洗】")
        print(f"{'='*50}")

        if self.df is None:
            print("✗ 请先加载数据")
            return

        self.cleaned_df = self.df.copy()

        # 1. 清洗标题 - 去除HTML标签
        print("\n1. 清洗标题（去除HTML标签）...")
        self.cleaned_df['title'] = self.cleaned_df['title'].apply(self.clean_title)
        print("   ✓ 标题清洗完成")

        # 2. 提取销量数值（保留此列用于数据库）
        print("\n2. 转换销量为数值...")
        self.cleaned_df['sale_value'] = self.cleaned_df['sale_num'].apply(self.extract_sale_value)
        print(f"   ✓ 销量转换完成")

        # 3. 解析发货地为省份和城市
        print("\n3. 解析发货地...")
        location_col = '发货地' if '发货地' in self.cleaned_df.columns else 'location'
        if location_col in self.cleaned_df.columns:
            parsed = self.cleaned_df[location_col].apply(self.parse_location)
            self.cleaned_df['province'] = parsed.apply(lambda x: x[0])
            self.cleaned_df['city'] = parsed.apply(lambda x: x[1])
            print(f"   ✓ 发货地解析完成")
        else:
            self.cleaned_df['province'] = ''
            self.cleaned_df['city'] = ''
            print(f"   ⚠ 未找到发货地字段")

        # 4. 解析券后价
        print("\n4. 解析券后价...")
        coupon_col = '卷后价' if '卷后价' in self.cleaned_df.columns else 'coupon_price'
        if coupon_col in self.cleaned_df.columns:
            self.cleaned_df['coupon_price'] = self.cleaned_df[coupon_col].apply(self.parse_coupon_price)
            print(f"   ✓ 券后价解析完成")

        # 5. 过滤低销量数据（可选）
        if min_sales > 0:
            print(f"\n5. 过滤低销量数据（销量<{min_sales}）...")
            before_count = len(self.cleaned_df)
            self.cleaned_df = self.cleaned_df[self.cleaned_df['sale_value'] >= min_sales]
            removed_count = before_count - len(self.cleaned_df)
            print(f"   ✓ 移除 {removed_count} 条低销量数据")
            self.report['移除低销量'] = removed_count
        else:
            print("\n5. 跳过销量过滤（保留所有数据）")

        # 6. 去除重复数据（按商品ID去重）
        print("\n6. 去除重复数据...")
        before_count = len(self.cleaned_df)
        id_col = 'ID' if 'ID' in self.cleaned_df.columns else 'product_id'
        if id_col in self.cleaned_df.columns:
            self.cleaned_df = self.cleaned_df.drop_duplicates(subset=[id_col])
        else:
            self.cleaned_df = self.cleaned_df.drop_duplicates()
        removed_dup = before_count - len(self.cleaned_df)
        print(f"   ✓ 移除 {removed_dup} 条重复数据")
        self.report['移除重复'] = removed_dup

        print(f"\n✓ 清洗完成！剩余 {len(self.cleaned_df)} 条数据")
        self.report['清洗后数据量'] = len(self.cleaned_df)

    def save_cleaned_data(self, output_file=None):
        """保存清洗后的数据到Excel文件"""
        if self.cleaned_df is None:
            print("✗ 请先执行数据清洗")
            return None

        if output_file is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            output_file = f"taobao_cleaned_{timestamp}.xlsx"

        print(f"\n{'='*50}")
        print("【保存到文件】")
        print(f"{'='*50}")

        try:
            self.cleaned_df.to_excel(output_file, index=False)
            print(f"✓ 清洗后数据已保存到: {output_file}")
            print(f"  数据量: {len(self.cleaned_df)} 条")
            self.report['输出文件'] = output_file
            return output_file
        except Exception as e:
            print(f"✗ 保存失败: {e}")
            return None

    def _generate_batch_id(self):
        """生成清洗批次ID"""
        import uuid
        return uuid.uuid4().hex[:16]

    def _create_clean_log(self, cursor, batch_id, file_name, keyword, operator='system'):
        """创建清洗日志记录"""
        sql = """
            INSERT INTO tb_clean_log (batch_id, file_name, keyword, operator, status, started_at, created_at)
            VALUES (%s, %s, %s, %s, 'RUNNING', NOW(), NOW())
        """
        cursor.execute(sql, (batch_id, file_name, keyword, operator))

    def _update_clean_log(self, cursor, batch_id, status, inserted, updated, skipped, error_msg=None):
        """更新清洗日志状态"""
        sql = """
            UPDATE tb_clean_log SET
                status = %s,
                inserted_count = %s,
                updated_count = %s,
                skipped_count = %s,
                total_count = %s,
                error_msg = %s,
                finished_at = NOW()
            WHERE batch_id = %s
        """
        total = inserted + updated + skipped
        cursor.execute(sql, (status, inserted, updated, skipped, total, error_msg, batch_id))

    def save_to_database(self, db_config=None, operator='system'):
        """保存清洗后的数据到MySQL数据库

        核心逻辑：
        1. 新商品 → 插入商品
        2. 已存在商品：
           - updated_at与当前时间间隔 < 24小时 → 跳过
           - 间隔 ≥ 24小时 → 只更新sale_value和updated_at

        Args:
            db_config: 数据库配置字典，默认使用 DB_CONFIG
            operator: 操作人（管理员用户名）

        Returns:
            dict: 包含处理结果的字典
        """
        if self.cleaned_df is None or len(self.cleaned_df) == 0:
            print("✗ 无数据可保存")
            return {'success': False, 'error': '无数据可保存'}

        config = db_config or DB_CONFIG
        batch_id = self._generate_batch_id()

        print(f"\n{'='*50}")
        print("【保存到数据库】")
        print(f"{'='*50}")
        print(f"数据库: {config['host']}:{config['port']}/{config['database']}")
        print(f"批次ID: {batch_id}")
        print(f"待处理: {len(self.cleaned_df)} 条数据")

        connection = None
        inserted_count = 0
        updated_count = 0
        skipped_count = 0

        # 提取关键词
        keyword_col = '搜索关键词' if '搜索关键词' in self.cleaned_df.columns else 'keyword'
        file_keyword = self.cleaned_df[keyword_col].iloc[0] if keyword_col in self.cleaned_df.columns else 'unknown'

        try:
            connection = pymysql.connect(**config)
            cursor = connection.cursor(DictCursor)

            # 创建清洗日志
            self._create_clean_log(cursor, batch_id, self.input_file, file_keyword, operator)
            connection.commit()

            # 查询商品SQL
            query_sql = "SELECT product_id, sale_value, updated_at, created_at FROM tb_product WHERE product_id = %s"

            # 插入新商品SQL
            insert_sql = """
                INSERT INTO tb_product (
                    product_id, pic_url, title, price, sale_num, sale_value,
                    store, shop_url, keyword, province, city, location,
                    shop_tag, coupon_price, is_cleaned, created_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 1, NOW())
            """

            # 更新销量SQL（只更新sale_value和updated_at）
            update_sql = """
                UPDATE tb_product SET sale_value = %s, sale_num = %s, updated_at = NOW() WHERE product_id = %s
            """

            for idx, row in self.cleaned_df.iterrows():
                try:
                    product_id = str(row.get('ID', row.get('product_id', '')))
                    if not product_id or product_id == 'nan':
                        continue

                    pic_url = str(row.get('pic_url', ''))[:500]
                    title = str(row.get('title', ''))[:500]
                    price = self._safe_float(row.get('price'))
                    sale_num = str(row.get('sale_num', ''))[:50]
                    sale_value = int(row.get('sale_value', 0))
                    store = str(row.get('store', ''))[:200]
                    shop_url = str(row.get('shop_url', ''))[:500]
                    keyword = str(row.get('搜索关键词', row.get('keyword', '')))[:100]
                    province = str(row.get('province', ''))[:50]
                    city = str(row.get('city', ''))[:50]
                    location = str(row.get('发货地', row.get('location', '')))[:100]
                    shop_tag = str(row.get('店铺标签', row.get('shop_tag', '')))[:100]
                    coupon_price = self._safe_float(row.get('coupon_price'))

                    if pd.isna(shop_tag) or shop_tag == 'nan':
                        shop_tag = None

                    # 查询商品是否存在
                    cursor.execute(query_sql, (product_id,))
                    existing = cursor.fetchone()

                    if not existing:
                        # 情况1：新商品 → 插入
                        cursor.execute(insert_sql, (
                            product_id, pic_url, title, price, sale_num, sale_value,
                            store, shop_url, keyword, province, city, location,
                            shop_tag, coupon_price
                        ))
                        inserted_count += 1
                    else:
                        # 情况2：商品已存在 → 检查时间间隔
                        last_update = existing.get('updated_at') or existing.get('created_at')
                        if last_update:
                            now = datetime.now()
                            if isinstance(last_update, str):
                                last_update = datetime.strptime(last_update, "%Y-%m-%d %H:%M:%S")
                            hours_diff = (now - last_update).total_seconds() / 3600
                            if hours_diff < 24:
                                skipped_count += 1
                                continue

                        # 情况3：间隔 ≥ 24小时 → 更新销量
                        cursor.execute(update_sql, (sale_value, sale_num, product_id))
                        updated_count += 1

                except Exception as e:
                    print(f"   ⚠ 行 {idx} 处理失败: {e}")
                    continue

            # 更新清洗日志为成功
            self._update_clean_log(cursor, batch_id, 'SUCCESS', inserted_count, updated_count, skipped_count)
            connection.commit()

            print(f"\n✓ 数据库写入完成！")
            print(f"  批次ID: {batch_id}")
            print(f"  新增商品: {inserted_count} 条")
            print(f"  更新销量: {updated_count} 条")
            print(f"  跳过(间隔<24h): {skipped_count} 条")

            self.report['批次ID'] = batch_id
            self.report['数据库新增'] = inserted_count
            self.report['数据库更新'] = updated_count
            self.report['跳过数量'] = skipped_count

            return {
                'success': True,
                'batch_id': batch_id,
                'inserted': inserted_count,
                'updated': updated_count,
                'skipped': skipped_count
            }

        except pymysql.Error as e:
            error_msg = str(e)
            print(f"✗ 数据库错误: {error_msg}")
            if connection:
                try:
                    cursor = connection.cursor()
                    self._update_clean_log(cursor, batch_id, 'FAILED', inserted_count, updated_count, skipped_count, error_msg)
                    connection.commit()
                except:
                    pass
                connection.rollback()
            return {'success': False, 'error': error_msg}
        finally:
            if connection:
                connection.close()

    def _safe_float(self, value):
        """安全转换为浮点数"""
        if value is None or pd.isna(value):
            return None
        try:
            return float(value)
        except:
            return None

    def get_report(self):
        """获取清洗报告"""
        return self.report

    def run(self, output_file=None, save_to_db=False, min_sales=0):
        """执行完整的清洗流程

        Args:
            output_file: Excel输出文件名，None则自动生成
            save_to_db: 是否保存到数据库
            min_sales: 最小销量阈值

        Returns:
            str: 输出文件路径
        """
        print("\n" + "="*50)
        print("     电商商品数据清洗工具")
        print("="*50)

        # 1. 加载数据
        if not self.load_data():
            return None

        # 2. 质量检测
        self.detect_quality()

        # 3. 数据清洗
        self.clean_data(min_sales=min_sales)

        # 4. 保存到文件
        result_file = self.save_cleaned_data(output_file)

        # 5. 保存到数据库（可选）
        if save_to_db:
            self.save_to_database()

        # 6. 输出报告
        print(f"\n{'='*50}")
        print("【清洗报告】")
        print(f"{'='*50}")
        for key, value in self.report.items():
            if isinstance(value, dict):
                print(f"{key}:")
                for k, v in value.items():
                    print(f"  {k}: {v}")
            else:
                print(f"{key}: {value}")

        return result_file

    def run_and_save_to_db(self, min_sales=0, operator='system'):
        """执行清洗并保存到数据库（不保存文件）

        这是爬虫完成后自动调用的方法

        Args:
            min_sales: 最小销量阈值
            operator: 操作人

        Returns:
            dict: 处理结果字典
        """
        print("\n" + "="*50)
        print("     电商商品数据清洗 -> 入库（销量历史追踪模式）")
        print("="*50)

        # 1. 加载数据
        if not self.load_data():
            return {'success': False, 'error': '加载数据失败'}

        # 2. 数据清洗
        self.clean_data(min_sales=min_sales)

        # 3. 保存到数据库（包含销量历史追踪逻辑）
        result = self.save_to_database(operator=operator)

        # 4. 输出清洗报告
        print(f"\n{'='*50}")
        print("【清洗报告】")
        print(f"{'='*50}")
        for key, value in self.report.items():
            if isinstance(value, dict):
                print(f"{key}:")
                for k, v in value.items():
                    print(f"  {k}: {v}")
            else:
                print(f"{key}: {value}")

        return result


def main():
    """命令行入口"""
    import sys

    input_file = 'taobao_frist.xlsx'
    save_to_db = False

    # 解析命令行参数
    # 用法: python data_clean.py [input_file] [--db]
    for arg in sys.argv[1:]:
        if arg == '--db':
            save_to_db = True
        elif not arg.startswith('--'):
            input_file = arg

    cleaner = DataCleaner(input_file)

    if save_to_db:
        # 清洗并写入数据库
        cleaner.run_and_save_to_db()
    else:
        # 只清洗保存到文件
        cleaner.run()


if __name__ == '__main__':
    main()
