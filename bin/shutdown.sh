#!/bin/sh

APP_HOME=`dirname "$0"`

if [ ! -a "$APP_HOME"/conf/application.xml ]; then 
     APP_HOME="$APP_HOME"/..  
fi

lib='.'
for jar in `ls $APP_HOME/lib/*.jar`
do
    lib=$lib:$jar
done
export CLASSPATH=$CLASSPATH:$lib
echo "$APP_HOME"
java -DSHUTDOWN=true  -DAPP_HOME="$APP_HOME"  com.wentch.redkale.boot.Application
