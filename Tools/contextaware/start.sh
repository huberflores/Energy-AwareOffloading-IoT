#!/bin/sh
mvn clean package;
java -cp ./contextaware-web/target/contextaware-web-1.0-full.jar ee.ut.mobile.contextaware.web.Main < /dev/null 2>&1 >> topLevel.log &
tail -f topLevel.log