@echo off
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
    echo Install JDK 17 or newer and add its bin folder to PATH.
    pause
    exit /b 1
)
if not exist out mkdir out
javac -encoding UTF-8 -d out src\model\*.java src\service\*.java src\util\*.java src\Main.java
if errorlevel 1 (
    pause
    exit /b 1
)
java -cp out Main %*
pause
