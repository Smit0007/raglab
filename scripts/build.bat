@echo off
echo ===================================
echo Building RAGLab (Backend + RCache)
echo ===================================
call mvn clean package
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven build failed!
    exit /b %ERRORLEVEL%
)

echo ===================================
echo Building RAGLab (Frontend)
echo ===================================
cd frontend
call npm run test
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Frontend test failed!
    cd ..
    exit /b %ERRORLEVEL%
)

call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Frontend build failed!
    cd ..
    exit /b %ERRORLEVEL%
)
cd ..

echo [SUCCESS] Full build completed successfully!
