# E-commerce Hot Product Analysis System

电商爆品分析系统是一个面向电商运营和选品分析的全栈项目，围绕商品采集、数据清洗、可视化分析、爆品发现、销售预测和 AI 选品分析构建。项目由 Vue 前端、Spring Boot 后端和 Python 爬虫服务组成，适合作为电商数据分析、后台管理系统、数据可视化大屏和智能选品场景的综合实践项目。

## 项目亮点

- 商品数据采集：通过 Python 爬虫服务采集淘宝商品和天猫榜单数据。
- 数据清洗处理：支持商品数据清洗、转换和质量监控。
- 多维数据分析：覆盖品类、价格、店铺、地域、销量、热度等维度。
- 爆品发现：结合商品指标和规则配置，辅助筛选高潜力商品。
- 可视化看板：基于 ECharts 展示趋势图、排行图、地域热力图等分析结果。
- 权限区分：提供管理员端和普通用户端，区分后台管理与分析使用场景。
- AI 选品分析：接入 DeepSeek 模型能力，为选品问题提供对话式分析建议。

## 系统架构

```text
┌────────────────────┐
│   Vue 3 Frontend   │
│  用户端 / 管理端    │
└─────────┬──────────┘
          │ HTTP / WebSocket
┌─────────▼──────────┐       ┌────────────────────┐
│ Spring Boot Backend │       │ Python Crawler API │
│ 业务 API / 数据分析  │       │ 采集 / 清洗 / 进度推送 │
└─────────┬──────────┘       └─────────┬──────────┘
          │                            │
┌─────────▼──────────┐       ┌─────────▼──────────┐
│       MySQL         │       │ Browser Automation │
│ 商品 / 用户 / 规则数据 │       │ DrissionPage / crawl4ai │
└────────────────────┘       └────────────────────┘
```

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、Element Plus、Pinia、Vue Router、Axios、Socket.IO Client、ECharts |
| 后端 | Spring Boot 3.2.1、Java 17、MyBatis-Plus、MySQL、JWT、Knife4j、EasyExcel、Hutool |
| 爬虫 | Python、Flask、Flask-SocketIO、DrissionPage、crawl4ai、pandas、openpyxl、pymysql |
| AI | DeepSeek API |

## 功能模块

### 管理员端

- 数据质量大屏
- 数据采集控制
- 数据清洗管理
- 商品数据管理
- 用户管理
- 爆品规则配置
- 系统监控

### 用户端

- 爆品总览
- 爆品发现
- 爆品详情分析
- 行业/品类分析
- 地域可视化
- 选品助手
- AI 选品分析

## 目录结构

```text
E-commerce-orgion/
  ecommerce-web/        Vue 3 前端项目
  ecommerce-analysis/   Spring Boot 后端项目
  crawler/             Python 爬虫与数据清洗服务
  docs/                 项目结构和整理说明
  china.js              根目录遗留地图资源
```

更多结构说明见 [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md)。

## 快速开始

### 环境要求

- Node.js 20+
- Java 17+
- Maven 3.8+
- Python 3.10+
- MySQL 8+

### 后端启动

```powershell
cd ecommerce-analysis
mvn spring-boot:run
```

默认后端地址：

```text
http://localhost:8080
```

接口文档地址：

```text
http://localhost:8080/swagger-ui.html
```

### 前端启动

```powershell
cd ecommerce-web
npm install
npm run dev
```

默认前端地址以 Vite 控制台输出为准。

### 爬虫服务启动

```powershell
cd crawler
pip install -r requirements_service.txt
python crawler_service.py
```

也可以在 Windows 环境中使用：

```powershell
crawler/start_service.bat
```

默认爬虫服务地址：

```text
http://localhost:5000
```

## 常用命令

前端构建：

```powershell
cd ecommerce-web
npm run build
```

后端测试：

```powershell
cd ecommerce-analysis
mvn test
```

爬虫语法检查：

```powershell
cd crawler
python -m py_compile crawler_service.py data_clean.py
```

## 开发说明

- 前端 API 封装位于 `ecommerce-web/src/api/`。
- 前端路由位于 `ecommerce-web/src/router/index.js`。
- 后端入口类为 `EcommerceAnalysisApplication.java`。
- 后端接口层位于 `ecommerce-analysis/src/main/java/com/example/ecommerce/controller/`。
- 爬虫服务入口为 `crawler/crawler_service.py`。
- 数据清洗逻辑位于 `crawler/data_clean.py`。

## 后续规划

- 完善数据库初始化 SQL 和示例数据。
- 增加统一的环境变量配置示例。
- 优化前端打包体积和路由级代码拆分。
- 为核心后端服务和爬虫清洗逻辑补充测试。
- 增加项目截图和演示流程说明。

## License

本项目用于学习、课程设计和技术实践展示。正式开源前建议补充明确的开源许可证。
