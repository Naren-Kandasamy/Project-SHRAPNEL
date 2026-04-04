@echo off
setlocal

echo =====================================================
echo 🚀 Starting Project SHRAPNEL Build ^& Run Sequence...
echo =====================================================

echo 🔨 Compiling Spring Boot Backend...
call mvn clean compile
if %errorlevel% neq 0 (
    echo ❌ Backend compilation failed! Aborting startup.
    exit /b %errorlevel%
)

echo 📊 Starting MLflow Tracking Server...
start "MLflow Server" /b call mlflow server --backend-store-uri sqlite:///mlflow.db --host 127.0.0.1 --port 5000 >nul 2>&1

:: Give MLflow a 2-second head start
timeout /t 2 /nobreak >nul

echo ⚙️  Starting Spring Boot Server...
start "SHRAPNEL Backend" /b call mvn spring-boot:run -Dmaven.test.skip=true

:: Give the backend a 3-second head start to initialize
timeout /t 3 /nobreak >nul

echo 🎨 Starting Next.js Frontend...
cd shrapnel-ui
if %errorlevel% neq 0 (
    echo ❌ shrapnel-ui directory not found!
    exit /b %errorlevel%
)
start "SHRAPNEL Frontend" /b call npm run dev
cd ..

echo.
echo =====================================================
echo ✅ SHRAPNEL is launching!
echo 🌐 Dashboard: http://localhost:3000
echo 🔌 API:       http://localhost:8080
echo 📈 MLflow:    http://localhost:5000
echo 🛑 Close this Command Prompt window to stop servers.
echo =====================================================
echo.

:: Keep window open
pause
