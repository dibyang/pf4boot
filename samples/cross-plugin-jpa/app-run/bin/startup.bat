@echo off
setlocal
cd /d "%~dp0.."
java -cp "lib/*" net.xdob.sample.host.CrossPluginJpaSampleHost --spring.config.location=config/application.yml
