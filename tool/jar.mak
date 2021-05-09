#############
# 用法
#
# - make: 编译
# - make dist: 创建发布目录(将编译和资源复制到发布目录, 用于打包发布或推送线上)
# - make publish: 提交发布目录git库，并通过git推送发布
# - make clean: 清除内容
#
# - make run: 使用编译的包运行
# - make rundist: 使用发布目录下的包运行
#
# - make debug: 以调试模式运行java程序
#
# 最简单的 Makefile 样例: 
#
#	JAR_NAME=com.jdcloud.jar
#	MAIN_CLASS=com.jdcloud.App
#	# COPY_FILES=./web.properties.template
#	include ../tool/jar.mak
#
# 默认：源文件在src目录下(SRC_DIR), 依赖的jar包在./lib目录下(JARS)，编译到./classes目录, 生成jar包发布到./dist目录(OUT_DIR)
#############

#### 可配置项
# 发布目录
OUT_DIR?=./dist

# 使用SRC_DIR指定源码目录，默认为src；简单的情况也可以直接用SRC指定java源文件
SRC_DIR?=./src
SRC?=$(shell find $(SRC_DIR) -name "*.java")

# jar包名, 如果不指定则不生成, 比如直接用class文件发布, 这时一般设置OUT_CLASS_DIR在OUT_DIR下. 此时`make rundist`不可用.
# JAR_NAME=com.jdcloud.jar

# 依赖的jar包
JARS?=$(wildcard ./lib/*.jar)

# 入口类，如果是可执行程序，则必须指定
# MAIN_CLASS=com.jdcloud.App

# 编译时额外依赖的jar包, 但生成的jar包不包含这些
# JARS2=

# 打包时额外要复制的文件
# COPY_FILES=./web.properties.template

# 编译用
OUT_CLASS_DIR?=./classes

# 依赖库拷贝到此. 如果设置空, 则不拷贝库
OUT_LIB_DIR?=$(OUT_DIR)/lib

# make dist时文件拷贝扩展
# COPY_DEP=
# make clean扩展
# CLEAN_DEP=
#############

ifdef JAR_NAME
TARGET_JAR=$(OUT_DIR)/$(JAR_NAME)
else
TARGET_JAR=
endif

DEP_JARS?=$(addprefix ./lib/,$(notdir $(JARS)))

NUL:=
SPACE:=$(NUL) #
# support windows & linux 
SEP=$(if $(WINDIR),;,:)
#CLASS_PATH=$(subst $(SPACE),$(SEP),$(ALL_JARS))
CLASS_PATH=$(subst $(SPACE),$(SEP),$(JARS))
CLASS_PATH2=$(subst $(SPACE),$(SEP),$(JARS) $(JARS2))

# publish依赖
PUBLISH_DEP=dist

all: $(OUT_CLASS_DIR)
	$(DO_SYNC_PUBLISH)

dist: all $(TARGET_JAR) copy_done

.PHONY: dist

ifneq ($(MAIN_CLASS),)

$(TARGET_JAR): $(OUT_CLASS_DIR) manifest.mf
	@mkdir -p $(OUT_DIR)
	jar cfm $@ manifest.mf -C $(OUT_CLASS_DIR) .
	@echo "=== create jar: done. path=$(OUT_DIR)"

manifest.mf: # $(MAKEFILE_LIST)
	@echo Manifest-Version: 1.0 >$@
	@echo Created-By: jdcloud-java >>$@
	@echo Main-Class: $(MAIN_CLASS) >>$@
ifneq ($(DEP_JARS),)
	@echo $(DEP_JARS) | perl -pe 'BEGIN { print "Class-Path: " } s/ /\n  /g' >> $@
#	@echo Class-Path: $(DEP_JARS) >> $@
endif

run: all
	@java -cp "$(CLASS_PATH2)$(SEP)$(OUT_CLASS_DIR)" $(MAIN_CLASS)
	
debug: all
	@java -Xdebug -Xrunjdwp:transport=dt_socket,address=6666,server=y,suspend=n -cp "$(CLASS_PATH2)$(SEP)$(OUT_CLASS_DIR)" $(MAIN_CLASS)

rundist: dist
	cd $(OUT_DIR) && java -jar $(JAR_NAME)

else # no MAIN_CLASS

$(TARGET_JAR): $(OUT_CLASS_DIR)
	@mkdir -p $(OUT_DIR)
	jar cf $@ -C $(OUT_CLASS_DIR) .
	@echo "=== create jar: done. path=$(OUT_DIR)"

endif # MAIN_CLASS

$(OUT_CLASS_DIR): $(SRC) # $(MAKEFILE_LIST)
	@mkdir -p $(OUT_CLASS_DIR)
	@rm -rf $(OUT_CLASS_DIR)/*
	@echo "=== Build classes..."
	@javac -cp "$(CLASS_PATH2)" -d $(OUT_CLASS_DIR) $(SRC)
	@echo "=== compile done. path=$(OUT_CLASS_DIR)"
	@touch $@

copy_done: $(JARS) $(COPY_FILES) $(COPY_DEP) # $(MAKEFILE_LIST) 
ifneq ($(and $(OUT_LIB_DIR),$(JARS)),)
	@mkdir -p $(OUT_LIB_DIR)
	cp $(JARS) $(OUT_LIB_DIR)
endif
ifneq ($(COPY_FILES),)
	cp -r $(COPY_FILES) $(OUT_DIR)/
endif
	@touch $@
	@echo "=== copy: done"

clean:
	-rm -rf $(OUT_CLASS_DIR) $(TARGET_JAR) $(OUT_LIB_DIR) $(CLEAN_DEP)

JarMakDir=$(dir $(lastword $(MAKEFILE_LIST)))
-include $(JarMakDir)publish.mak

