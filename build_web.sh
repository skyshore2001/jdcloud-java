#!/bin/sh

export OUT_DIR=../jdcloud-online
export TOMCAT_HOME=d:/apache-tomcat-8.5.11
export GIT_PATH=server-pc:pdi-online

COMPILE_CMD=make ./tool/jdcloud-build.sh
