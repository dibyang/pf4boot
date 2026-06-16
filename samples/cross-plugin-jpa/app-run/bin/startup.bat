@echo off
setlocal
cd /d "%~dp0.."
if "%LOG_DIR%"=="" set LOG_DIR=logs
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
java %JAVA_OPTS% -Dsample.logging.dir="%LOG_DIR%" -cp "lib/*" net.xdob.sample.host.CrossPluginJpaSampleHost --spring.config.location=config/application.yml
