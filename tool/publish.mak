#############
# 提交内容到发布目录(git库)并通过git push发布上线. 发布命令:
# 	make publish
#
# 初始化: 在源码目录同级创建online目录(也是git仓库), 创建Makefile示例
#
#	OUT_DIR=../myprj-online
#	PUBLISH_DEP=all
#	all:
#		$(DO_SYNC_PUBLISH)
#		编译到OUT_DIR中
#
#	include publish.mak
#
# 其中DO_SYNC_PUBLISH用于编译前先自动同步online目录. 
#############

ifneq ($(shell [[ -d $(OUT_DIR)/.git ]] && cd $(OUT_DIR) && git remote),)
define DO_SYNC_PUBLISH
	@echo "=== sync $(OUT_DIR)"
	@cd $(OUT_DIR) && git pull
endef
endif

publish: $(PUBLISH_DEP)
	@[[ ! -d $(OUT_DIR)/.git ]] && echo '*** ERROR: OUT_DIR=$(OUT_DIR) is not `online` git repo.' && exit 1 || true
	@lastlog=`git log -1 --oneline | tr \" \'`; \
	cd  $(OUT_DIR); \
	git add . ; \
	git commit -am "$$lastlog" ; \
	$(if $(DO_SYNC_PUBLISH),git push)
	@echo "=== publish: done"

