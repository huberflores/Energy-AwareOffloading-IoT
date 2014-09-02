#!/bin/sh
mvn clean package;
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5055 -cp ./contextaware-web/target/contextaware-web-1.0-full.jar ee.ut.mobile.contextaware.web.Main < /dev/null 2>&1 >> topLevel.log &
tail -f topLevel.log