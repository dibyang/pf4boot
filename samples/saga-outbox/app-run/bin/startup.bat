@echo off
setlocal
set APP_HOME=%~dp0..
cd /d "%APP_HOME%"
java -cp "lib/*" net.xdob.sample.saga.SagaOutboxSampleHost --spring.config.location=config/application.yml %*
