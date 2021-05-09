#############
# 目录结构要求
# - $APP_HOME是构建后的根目录。其中主要包括 WEB-INF 和 META-INF两个目录。
# - 所有的jar包放在 $APP_HOME/WEB-INF/lib 下，添加到git中。
# - 定义Tomcat路径$(TOMCAT_HOME), 以便在$(TOMCAT_HOME)/lib下找到TOMCAT运行库。
#############

#### 可配置项
# 发布目录
# OUT_DIR=./dist

# TOMCAT_HOME

# java源码目录
SRC_DIR?=jdcloud/src svc/src

# webapp主目录
APP_HOME?=svc/WebContent

# 是否使用git. 是则自动编译/拷贝添加到git库中的文件
VCS?=git
#############

ifeq ($(TOMCAT_HOME),)
 $(error "TOMCAT_HOME is not defiend")
endif

ifeq ($(VCS), git)
 SRC:=$(shell git ls-files $(addsuffix *.java,$(SRC_DIR)))
 APP_FILES:=$(shell git ls-files $(APP_HOME))
 APP_FILES_1:=$(shell cd $(APP_HOME); git ls-files)
else
 APP_FILES:=$(APP_HOME)/*
endif

JARS:=$(shell find $(APP_HOME)/WEB-INF/lib -name '*.jar')
JARS2:=$(shell find $(TOMCAT_HOME)/lib -name '*.jar')

OUT_CLASS_DIR=$(OUT_DIR)/WEB-INF/classes
# 置空不拷贝库. 因为在COPY_DEP中已经一起拷贝了.
OUT_LIB_DIR=

COPY_DEP=$(OUT_DIR)/WEB-INF/web.xml
CLEAN_DEP=$(OUT_DIR)/WEB-INF $(OUT_DIR)/META-INF

WarMakDir=$(dir $(lastword $(MAKEFILE_LIST)))
include $(WarMakDir)jar.mak

$(COPY_DEP): $(APP_FILES)
ifeq ($(VCS), git)
	cd $(APP_HOME); cp --parents -r $(APP_FILES_1) $(shell pwd)/$(OUT_DIR)
else
	cp -r $(APP_FILES) $(OUT_DIR)
endif

