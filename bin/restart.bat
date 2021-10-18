@ECHO OFF

SET APP_HOME=%~dp0

IF NOT EXIST "%APP_HOME%\conf\application.xml"  SET APP_HOME=%~dp0..

call "%APP_HOME%\bin\shutdown.bat" 

call "%APP_HOME%\bin\start.bat" 