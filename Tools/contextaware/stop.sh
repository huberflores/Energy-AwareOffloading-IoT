#!/bin/sh
pid=`ps -ef | grep contextaware-web | awk '/java/{print $2}'`
kill -9 $pid
