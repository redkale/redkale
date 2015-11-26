#!/bin/sh

ulimit -c unlimited

ulimit -n 1024000

export LANG="zh_CN.UTF-8"

APP_HOME=`dirname "$0"`

if [ ! -a "$APP_HOME"/conf/application.xml ]; then 
     APP_HOME="$APP_HOME"/..  
fi

lib="$APP_HOME"/lib
for jar in `ls $APP_HOME/lib/*.jar`
do
    lib=$lib:$jar
done
export CLASSPATH=$CLASSPATH:$lib

echo "$APP_HOME"
nohup  java -DAPP_HOME="$APP_HOME" com.wentch.redkale.boot.Application > "$APP_HOME"/log.out &

