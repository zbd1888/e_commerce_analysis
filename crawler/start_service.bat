@echo off
chcp 65001 >nul
echo ============================================================
echo   电商数据爬虫服务 - 启动脚本
echo ============================================================
echo.

echo [1/3] 检查Python环境...
python --version
if errorlevel 1 (
    echo ✗ Python未安装或未添加到PATH
    pause
    exit /b 1
)
echo ✓ Python环境正常
echo.

echo [2/3] 检查依赖包...
python -c "import flask" 2>nul
if errorlevel 1 (
    echo ✗ Flask未安装，正在安装依赖包...
    pip install -r requirements_service.txt
    if errorlevel 1 (
        echo ✗ 依赖包安装失败
        pause
        exit /b 1
    )
)
echo ✓ 依赖包已安装
echo.

echo [3/3] 启动服务...
echo.
python crawler_service.py

pause

