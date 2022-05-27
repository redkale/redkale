@ECHO OFF

SET APP_HOME=%~dp0

IF NOT EXIST "%APP_HOME%\conf\application.xml"  SET APP_HOME=%~dp0..

call "%APP_HOME%\bin\shutdown.cmd" 

call "%APP_HOME%\bin\start.cmd" 