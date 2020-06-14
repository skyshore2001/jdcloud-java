#############
# 目录结构要求
# - $APP_HOME是构建后的根目录。其中主要包括 WEB-INF 和 META-INF两个目录。
# - 所有的jar包放在 $APP_HOME/WEB-INF/lib 下，添加到git中。
# - 定义Tomcat路径$(TOMCAT_HOME), 以便在$(TOMCAT_HOME)/lib下找到TOMCAT运行库。
#############

#### 可配置项
OUT_DIR?=../dist
TOMCAT_HOME?=d:/apache-tomcat-8.5.28

SRC_DIR?=jdcloud/src svc/src
APP_HOME?=svc/WebContent
VCS?=git
#############

ifeq ($(VCS), git)
 SRC:=$(shell git ls-files $(addsuffix *.java,$(SRC_DIR)))
 APP_FILES:=$(shell git ls-files $(APP_HOME))
 APP_FILES_1:=$(shell cd $(APP_HOME); git ls-files)
else
 SRC:=$(shell find $(SRC_DIR) -name '*.java')
 APP_FILES:=$(APP_HOME)/*
endif

JARS:=$(shell find $(APP_HOME)/WEB-INF/lib -name '*.jar')
ALL_JARS:=$(shell find $(TOMCAT_HOME)/lib -name '*.jar') $(JARS)

NUL:=
SPACE:=$(NUL) #
# support windows & linux 
SEP=:
ifeq ($(OS),Windows_NT)
	SEP=;
endif
CLASS_PATH=$(subst $(SPACE),$(SEP),$(ALL_JARS))

OUT_CLASS_DIR=$(OUT_DIR)/WEB-INF/classes

OUT_CLASS=$(OUT_CLASS_DIR)/com/jdcloud/AccessControl.class
OUT_FILE=$(OUT_DIR)/WEB-INF/web.xml

all: class copy

class: $(OUT_CLASS_DIR) $(OUT_CLASS)

$(OUT_CLASS): $(SRC)
	@echo "Build classes..."
	@javac -cp "$(CLASS_PATH)" -d $(OUT_CLASS_DIR) $(SRC)

copy: $(OUT_FILE)

$(OUT_FILE): $(APP_FILES)
ifeq ($(VCS), git)
	cd $(APP_HOME); cp --parents -r $(APP_FILES_1) $(shell pwd)/$(OUT_DIR)
else
	cp -r $(APP_FILES) $(OUT_DIR)
endif

$(OUT_CLASS_DIR):
	@mkdir -p $@

clean:
	-rm -rf $(OUT_CLASS_DIR)

clobber:
	-rm -rf $(OUT_DIR)/WEB-INF $(OUT_DIR)/META-INF

