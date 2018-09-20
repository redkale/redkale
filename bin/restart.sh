#!/bin/sh

export LC_ALL="zh_CN.UTF-8"

APP_HOME=`dirname "$0"`

cd "$APP_HOME"/..

APP_HOME=`pwd`

if [ ! -f "$APP_HOME"/conf/application.xml ]; then 
     APP_HOME="$APP_HOME"/..  
fi

cd "$APP_HOME"

"$APP_HOME"/bin/shutdown.sh

"$APP_HOME"/bin/start.sh

