#!/bin/sh

export LC_ALL="zh_CN.UTF-8"
export MAVEN_GPG_PASSPHRASE=GPG密码

rm -fr redkale

rm -fr src
rm -fr bin
rm -fr conf

git clone https://github.com/redkale/redkale.git

cp -fr redkale/src ./
cp -fr redkale/bin ./
cp -fr redkale/conf ./

mvn clean
mvn deploy
