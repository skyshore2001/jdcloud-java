# 产品设计

参考文档：

- [通用业务协议](http://oliveche.com/jdcloud-site/BQP.html)
- [技术文档目录](http://oliveche.com/jdcloud-site/)

## 概要设计

### 主要用例

定义用户使用本系统的主要场景。用于指导[系统建模]和[交互接口设计]。

系统用例图.
![](doc/pic/usecase.png)

### 系统建模

定义系统数据模型，描述基本概念。用于指导[数据库设计]。

系统类图或ER图.
![](doc/pic/datamodel.png)

## 数据库设计

根据[系统建模]设计数据库表结构。

参考[后端框架-数据库设计](http://oliveche.com/jdcloud-site/后端框架.html#数据库设计)查看定义表及字段类型的基本规则.

**[数据库信息]**

@Cinf: version, createTm, upgradeTm

产品配置信息表.

**[员工]**

@Employee: id, uname, phone(s), pwd, name(s), perms

雇员表, 登录后可用于查看和处理业务数据。

- phone/pwd: String. 员工登录用的用户名（一般用手机号）和密码. 密码采用md5加密。
- perms: List(perm/String). 逗号分隔的权限列表，如"emp,mgr". 可用值: emp,mgr, 对应权限AUTH_EMP, PERM_MGR。

**[用户]**

@User: id, uname, phone(s), pwd, name(s), createTm

- phone/pwd: 登录用的用户名和密码。密码采用md5加密。
- createTm: DateTime. 注册日期. 可用于分析用户数增长。

**[订单]**

@Ordr: id, userId, createTm, status(2), amount, dscr(l), cmt(l)

- status: Enum(CR-新创建,RE-已服务,CA-已取消. 其它备用值: PA-已付款(待服务), ST-开始服务, CL-已结算). 订单状态.

**[订单日志]**

@OrderLog: id, orderId, action, tm, dscr, empId

例如：某时创建订单，某时付款等。

- action: 与Ordr.status一致。
- empId: 操作该订单的员工号

**[订单-图片关联]**

@OrderAtt: id, orderId, attId

**[API调用日志]**

@ApiLog: id, tm, addr, ua(l), app, ses, userId, ac, t&, retval&, req(t), res(t), reqsz&, ressz&, ver, serverRev(10)

- app: "user"|"emp"|"store"...
- ua: userAgent
- ses: the php session id.
- t: 执行时间(单位：ms)
- ver: 客户端版本。格式为："web"表示通用网页(通过ua可查看明细浏览器)，"wx/{ver}"表示微信版本如"wx/6.2.5", "a/{ver}"表示安卓客户端及版本如"a/1", "ios/{ver}"表示苹果客户端版本如"ios/15".

@ApiLog1: id, apiLogId, ac, t&, retval&, req(t), res(t)

batch操作的明细表。

**[操作日志]**

@ObjLog: id, obj, objId, dscr, apiLogId, apiLog1Id

**[插件相关]**

- 登录

@include plugin/jdcloud-java-login.README.md

- 图片、附件与上传

@include plugin/jdcloud-java-upload.README.md

## 交互接口设计

# 专题设计

