# jdcloud-java - 筋斗云接口开发框架(java版)

筋斗云是一个Web接口开发框架，它基于模型驱动开发（MDD）的理念，提出极简化开发的“数据模型即接口”思想，用于快速实现基于数据模型的接口（MBI: Model Based Interface）。
它推崇以简约的方式在设计文档中描述数据模型，进而基于模型自动形成数据库表以及业务接口，称为“一站式数据模型部署”。

筋斗云提供对象型接口和函数型接口两类接口开发模式，前者专为对象的增删改查提供易用强大的编程框架，后者则更为自由。

筋斗云原本使用php语言开发，本项目为筋斗云的java实现版本，支持在java平台进行函数型、对象型接口开发。

注意筋斗云要求最低java 8版本。

筋斗云后端框架项目参考：

- [jdclud-php](https://github.com/skyshore2001/jdcloud-php) (筋斗云后端php版本)
- [jdclud-java](https://github.com/skyshore2001/jdcloud-java) (筋斗云后端java版本)
- [jdclud-cs](https://github.com/skyshore2001/jdcloud-cs) (筋斗云后端.net版本)

另外，筋斗云有优雅的前端框架，支持构建模块化的H5单页应用：

- [jdcloud-mui](https://github.com/skyshore2001/jdcloud-mui) 筋斗云移动端单页应用框架，用于创建手机H5应用程序。
- [jdcloud-wui](https://github.com/skyshore2001/jdcloud-wui) 筋斗云管理端单页应用框架，用于创建运营管理端H5应用程序。

**[对象型接口 - 数据模型即接口]**

假设数据库中已经建好一张记录操作日志的表叫"ApiLog"，包含字段id（主键，整数类型）, tm（日期时间类型）, addr（客户端地址，字符串类型）。

使用筋斗云后端框架，只要创建一个空的类，就可将这个表（或称为对象）通过HTTP接口暴露给前端，提供增删改查各项功能：
```java
package com.demo;
import com.jdcloud.*;

public class AC_ApiLog extends AccessControl
{
}
```

现在就已经可以对匿名访问者提供"ApiLog.add", "ApiLog.set", "ApiLog.get", "ApiLog.query", "ApiLog.del"这些标准对象操作接口了。

我们用curl工具来模拟前端调用，假设服务地址为`http://localhost/mysvc/`，我们就可以调用"ApiLog.add"接口来添加数据：

	curl http://localhost/mysvc/api/ApiLog.add -d "tm=2016-9-9 10:10" -d "addr=shanghai"

注意默认所有接口都在虚拟路径"/api/"之下。它输出一个JSON数组：

	[0,11338]

0表示调用成功，后面是成功时返回的数据，add操作返回的是新对象的id。

可以调用"ApiLog.query"来取列表：

	curl http://localhost/mysvc/api/ApiLog.query

列表支持分页，默认一次返回20条数据。query接口非常灵活，还可以指定返回字段、查询条件、排序方式，
比如查询2016年1月份的数据(cond参数)，结果只需返回id, addr字段(res参数)，按id倒序排列(orderby参数)：

	curl http://localhost/mysvc/api/ApiLog.query -d "res=id,addr" -d "cond=tm>='2016-1-1' and tm<'2016-2-1'" -d "orderby=id desc"

甚至可以做统计，比如查看2016年1月里，列出访问次数排名前10的地址，以及每个地址访问了多少次服务器，也可以通过query接口直接查出。

可见，用筋斗云后端框架开发对象操作接口，可以用非常简单的代码实现强大而灵活的功能。

**[函数型接口 - 简单直接]**

除了对象型接口，还有一类叫函数型接口，比如要实现一个接口叫"getInfo"用于返回一些信息，开发起来也非常容易，只要在名为Global的类中定义一个函数：
```java
package com.demo;
import com.jdcloud.*;

public class Global extends JDApiBase
{
	public Object api_getInfo()
	{
		return new JsObject(
			"id", 1001,
			"name", "jdcloud",
			"addr", "Shanghai"
		);
	}
}
```
于是便可以访问接口"getInfo":

	curl http://localhost/mysvc/api/getInfo

返回：

	[0, {"name": "jdcloud", "addr": "Shanghai"}]

**[权限控制]**

权限包括几种，比如根据登录类型不同，分为用户、员工、超级管理员等角色，每种角色可访问的数据表、数据列（即字段）有所不同，一般称为授权(auth)。
授权控制不同角色的用户可以访问哪些对象或函数型接口，比如getInfo接口只许用户登录后访问：
```java
public Object api_getInfo()
{
	checkAuth(AUTH_USER); // 在应用配置中，已将AUTH_USER定义为用户权限，在用户登录后获得
	...
}
```

再如ApiLog对象接口只允许员工登录后访问，且限制为只读访问（只允许get/query接口），不允许用户或游客访问，只要定义：
```java
// 不要定义AC_ApiLog，改为AC2_ApiLog
public class AC2_ApiLog extends AccessControl
{
	@Override
	protected void onInit()
	{
		this.allowedAc = asList("get", "query");
	}
}
```
框架默认配置为员工角色（登录权限为AUTH_EMP）对应类前缀为"AC2_"的类，类似地，"AC_"前缀表示游客角色，"AC1_"前缀表示用户角色（AUTH_USER）。
asList是框架提供的工具函数，生成一个列表，与Array.asList类似，类似地还有asMap。

通常权限还控制对同一个表中数据行的可见性，比如即使同是员工登录，普通员工只能看自己的操作日志，经理可以看到所有日志。
这种数据行权限，也称为Data ownership，一般通过在查询时追加限制条件来实现。假设已定义一个权限PERM_MGR，对应经理权限，然后实现权限控制：
```java
public class AC2_ApiLog extends AccessControl
{
	...
	@Override
	protected void onQuery()
	{
		if (! hasPerm(PERM_MGR)) {
			Object empId = getSession("empId");
			this.addCond("t0.empId=" + empId);
		}
	}
}
```

**[一站式数据模型部署]**

筋斗云框架重视设计文档，倡导在设计文档中用简约的方式定义数据模型与接口原型，
例如，上例中的ApiLog表，无需手工创建，只要设计文档中定义：

	@ApiLog: id, tm, addr

使用部署工具就可以自动创建数据表，由于数据模型即接口，也同时生成了相应的对象操作接口。
工具会根据字段的命名规则来确定字段类型，比如"id"结尾就用整型，"tm"结尾就用日期时间类型等。

当增加了表或字段，同样运行工具，数据库和后端接口也都会相应被更新。

**更多用法，请阅读教程《筋斗云接口编程》和参考文档。**

## 创建筋斗云Web接口项目

**[任务]**

用筋斗云框架创建一个Web接口项目叫mysvc，创建数据库，提供对ApiLog对象的操作接口。

开发环境：

- JDK8及以上版本
- Tomcat7及以上版本（框架开发目前用的是Tomcat8.5）
- 开发工具默认使用Eclipse，也可以换成InteliJ IDEA.

先从github上下载开源的筋斗云后端框架及示例应用：`https://github.com/skyshore2001/jdcloud-java`

建议安装git工具直接下载，便于以后更新，例如直接创建Web接口项目叫mysvc：

	git clone https://github.com/skyshore2001/jdcloud-java.git mysvc

如果github访问困难，也可以用这个git仓库： `http://dacatec.com/git/jdcloud-java.git`

下载后，里面有jdcloud, svc两个目录：

- jdcloud目录是应用框架（jar包项目）。可以将jdcloud目录以源码方式直接导入你的工程中，或用编译后的jar包在你的工程中引用。
- svc目录为示例java web项目（war包项目），它依赖jdcloud库，运行在tomcat上。
- svc/WebContent目录对应最终部署目录（Java web artifacts）中的内容。在WebContent/WEB-INF目录下，包含Java web项目配置文件web.xml，以及依赖的jar包都放在lib子目录下。
 这与Java web项目部署目录规范相同。

如果使用Eclipse，直接导入这两个目录下的工程，注意看下svc工程的属性：

- Java Build Path: Projects页中，设置有svc项目依赖jdcloud项目；Libraries页中，如果依赖的JDK版本或Tomcat版本不对，则相应做下调整。
- Deployment Assembly: svc工程部署时应包含jdcloud模块。
- Project Facets: 应有Dynamic web module

实际项目中我们一般先将svc项目改名（目录名不变，只修改项目名），在eclipse中右键项目选择`Refactor->Rename`，换成实际项目名，比如`mysvc`。
这样在Eclipse中项目部署到Tomcat后的默认访问地址是`http://localhost:8080/mysvc/`.

如果使用IDEA，导入目录后默认识别两个模块，注意查看`File->Project Structure`菜单，检查"Project Setting"中各项设置是否正确：

- Project栏: SDK版本与Project Language Level一致。
- Modules栏：两个模块均应依赖Tomcat(一般需要手工添加Tomcat依赖)，还有依赖Libraries.
- Libraries栏：svc/WebContent/lib目录下的所有jar包。
- Facets栏：svc模块为Web项目，且正确设置了项目描述文件为svc/WebContent/WEB-INF/web.xml，以及web资源目录根路径为svc/WebContent。
- Artifacts栏：应包含各模块的的编译结果，以及svc项目的web facet资源（即svc/WebContent目录）。
- 首次运行时创建一个Run Configuration，在deployment页中将web项目加入并指定url为"/mysvc"。

框架的git库中含有一个idea分支，可以用IDEA直接打开，其中去除了Eclipse的工程设置，可供参考。

再看如何配置数据库连接等信息。在目录svc/WebContent/WEB-INF下，有个web.properties.template文件，将它复制为web.properties文件。
先指定你项目中回调入口类，如示例项目配置为：

	JDEnv=com.demo.WebApi

这个下面会再详述。现在配置数据库选项。
数据库默认使用MySQL，在配置中定义如下：

	# P_DBTYPE=mysql # 如果未指定，默认是mysql
	P_DB_DRIVER=com.mysql.jdbc.Driver
	P_DB=jdbc:mysql://localhost:3306/jdcloud?characterEncoding=utf8
	P_DBCRED=demo:demo123

它也支持连接SQL Server，配置示例：

	P_DBTYPE=mssql
	P_DB_DRIVER=com.microsoft.sqlserver.jdbc.SQLServerDriver
	P_DB=jdbc:sqlserver://localhost:1433;instanceName=MSSQL2008;databaseName=jdcloud;integratedSecurity=false;
	P_DBCRED=sa:demo123

注意安装好sqlserver for jdbc的驱动包。如果使用的是默认的instance，则不用指定instanceName.

其它常用参数配置如：

	P_TEST_MODE=1
	P_DEBUG=9

一般在开发时，会设置测试模式(P_TEST_MODE=1)，可返回更多调试信息; 
可用P_DEBUG设置调试信息输出等级，当值为9（最高）时，可以查看SQL调用日志，这在调试SQL语句时很有用。
此外，测试模式还会开放某些内部接口，以及缺省允许跨域访问，便于通过web页面测试接口。
**注意线上生产环境绝不可设置为测试模式。**

参数P_DBTYPE用于提示数据库类型，值为"mssql"或"mysql"。

配置好后运行Web项目，检查接口是否可正常访问：`http://localhost:8080/mysvc/api/hello`

- 正常应返回`[0, 数据]`这样的数组，第一项0表示返回值，即接口处理成功。之前若设置了测试模式则会显示更多项。
- 如果返回404错，检查部署URL路径是否正确设置；
- 如果返回空或创建JDEnv错，检查Artifacts的设置，尤其是WEB-INF下是否正确包含了jdcloud库以web.properties等文件，以及web.properties是否正确配置JDEnv参数。
- 如果返回数据库连接错，则检查web.properties中数据库连接参数的设置。

下面讲解代码，我们从svc演示工程开始，在这个工程中，为了方便编码，把所有的类都放在文件com/demo/WebApi.java中了（所以那些接口类都没有加public前缀）。

public类com.demo.WebApi就是在web.properties配置文件中指定的入口(JDEnv=com.demo.WebApi)，它必须继承com.jdcloud.JDEnvBase类。为了学习接口编程，我们先清空这个文件，只留下这些代码：

```java
package com.demo;
import java.lang.reflect.Method;
import com.jdcloud.*;

public class WebApi extends JDEnvBase
{
	@Override
	protected Object onNewInstance(Class<?> t) throws Exception
	{
		return t.newInstance();
	}
	
	@Override
	protected Object onInvoke(Method mi, Object arg) throws Exception
	{
		return mi.invoke(arg);
	}
}

class AC_ApiLog extends AccessControl
{
}

```

在WebApi类中重载了onNewInstance和onInvoke函数，它们不是必需的，它们的作用是允许跨包调用非public类的方法，
以便定义的Global类或AC前缀类即使不标识成public也可以被筋斗云框架创建和调用。
当然你也可以按java的风格将每个实现类放在不同的文件中，但必须和WebApi在同一个包下，在本示例里必须是放在com.demo包中。

类AC_ApiLog用于将ApiLog表的标准接口暴露出来。这一行代码就提供了对ApiLog对象的标准增删改查(CRUD)接口如下：

	查询对象列表，支持分页、查询条件、统计等：
	ApiLog.query() -> table(id, tm, addr)

	添加对象，通过POST参数传递字段值，返回新对象的id
	ApiLog.add()(tm, addr) -> id

	获取对象
	ApiLog.get(id) -> {id, tm, addr}

	更新对象，通过POST参数传递字段值。
	ApiLog.set(id)(tm?, addr?)

	删除对象
	ApiLog.del(id)

上面query接口的地址为"api/ApiLog.query"，其它依次类推。

**[接口原型的描述方式]**

在上面的接口原型描述中，用了两个括号的比如add/set操作，表示第一个括号中的参数通过GET参数（也叫URL参数）传递，第二个括号中的参数用POST参数（也叫FORM参数）传递。
多数接口参数支持用GET方式或POST方式传递，除非在接口说明中特别指定。
带"?"表示参数或返回的属性是可选项，可以不存在。

接口原型中只描述调用成功时返回数据的数据结构，完整的返回格式是`[0, 返回数据]`；而在调用失败时，统一返回`[非0错误码, 错误信息]`。

在eclipse中启动tomcat，这时默认的服务地址就是`http://localhost:8080/mysvc/api/{接口名}`.
我们可以直接用curl工具来模拟前端调用，用add接口添加一行数据，使用HTTP POST请求：

	curl http://localhost:8080/mysvc/api/ApiLog.add -d "tm=2016-9-9 10:10" -d "addr=shanghai"

curl用"-d"参数指定参数通过HTTP body来传递，由于默认使用HTTP POST谓词和form格式(Content-Type=application/x-www-form-urlencoded)，
这种参数一般称为POST参数或FORM参数，与通过URL传递的GET参数相区别。
结果输出一个JSON数组：

	[0,11338]

0表示调用成功，后面是成功时返回的数据，add操作返回对象id，可供get/set/del操作使用。

用get接口取出这个对象出来看看：

	curl http://localhost:8080/mysvc/api/ApiLog.get?id=11338

输出：

	[0,{"id":11338,"tm":"2016-09-09 00:00:00","addr":"shanghai"}]

这里参数id是通过URL传递的。
前面说过，未显式说明时，接口的参数可以通过URL或POST参数方式来传递，所以本例中URL参数id也可以通过POST参数来传递：

	curl http://localhost:8080/mysvc/api/ApiLog.get -d "id=11338"

如果取一个不存在的对象，会得到错误码和错误信息，比如：

	curl http://localhost:8080/mysvc/api/ApiLog.get?id=999999

输出：

	[1,"参数不正确"]

注意: 如果出现中文乱码，可能是Eclipse工作区的默认编码不正确，应该为UTF-8。

再用set接口做一个更新，按接口要求，要将id参数放在URL中，要更新的字段及值用POST参数：

	curl http://localhost:8080/mysvc/api/ApiLog.set?id=11338 -d "addr=beijing"

输出：

	[0, "OK"]

再看很灵活的query接口，取下列表，默认支持分页，会输出一个nextkey字段：

	curl http://localhost:8080/mysvc/api/ApiLog.query

返回示例：

	[0,{
		"h":["id","tm","addr"],
		"d":[[11353,"2016-01-04 18:31:06","::1"],[11352,"2016-02-04 18:30:43","::1"],...],
		"nextkey":11349
	}]

返回的格式称为压缩表，"h"为表头字段，"d"为表的数据，在接口描述中用`table(id, 其它字段...)`表示。

query接口也支持常用的数组返回，需要加上`fmt=list`参数：

	curl http://localhost:8080/mysvc/api/ApiLog.query -d "fmt=list"

返回示例：

	[0,{
		"list": [
			{ "id": 11353, "tm": "2016-01-04 18:31:06", "addr": "::1" },
			{ "id": 11352, "tm": "2016-02-04 18:30:43", "addr": "::1" }, 
			...
		],
		"nextkey":11349
	}]

还可以将`fmt`参数指定为"csv", "excel", "txt"等，在浏览器访问时可直接下载相应格式的文件，读者可自己尝试。

返回的nextkey字段表示数据未完，可以用pagekey字段来取下一页，还可指定一次取的数据条数，用pagesz字段：

	curl "http://localhost:8080/mysvc/api/ApiLog.query?pagekey=11349&pagesz=5"

直到返回数据中没有nextkey字段，表示已到最后一页。

不仅支持分页，query接口非常灵活，可以指定返回字段、查询条件、排序方式，
比如查询2016年1月份的数据(cond参数)，结果只需返回id, addr字段(res参数，也可用于get接口)，按id倒序排列(orderby参数)：

	curl http://localhost:8080/mysvc/api/ApiLog.query -d "res=id,addr" -d "cond=tm>='2016-1-1' and tm<'2016-2-1'" -d "orderby=id desc"

甚至可以做统计，比如查看2016年1月里，列出访问次数排名前10的地址，以及每个地址访问了多少次服务器，也可以通过query接口直接查出。
做一个按addr字段的分组统计(gres参数)：

	curl http://localhost:8080/mysvc/api/ApiLog.query -d "gres=addr" -d "res=count(*) cnt" -d "cond=tm>='2016-1-1' and tm<'2016-2-1'" -d "orderby=cnt desc" -d "pagesz=10"

输出示例：

	[0,{
		"h":["addr","cnt"],
		"d":[["140.206.255.50",1],["101.44.63.119",73],["121.42.0.85",70],...],
		"nextkey": 3
	}]

**[接口调用的描述方式]**

在之后的示例中，我们将使用接口原型来描述一个调用，不再使用curl，比如上面的调用将表示成：

	ApiLog.query(gres=addr
		res="count(*) cnt"
		cond="tm>'2016-1-1' and tm<'2016-2-1'"
		orderby="cnt desc"
		pagesz=10
	)
	->
	{
		"h":["addr","cnt"],
		"d":[["140.206.255.50",1],["101.44.63.119",73],["121.42.0.85",70],...],
		"nextkey": 3
	}

返回数据如非特别声明，我们将只讨论调用成功时返回的部分，比如说返回"OK"实际上表示返回`[0, "OK"]`。

## 函数型接口

如果不是典型的对象增删改查操作，可以设计函数型接口，比如登录、修改密码、上传文件这些。

函数型接口实现在类Global下。假设有以下接口定义：

	获取登录信息(who am i?)

	whoami() -> {id}

	应用逻辑

	- 权限：AUTH_USER (必须用户登录后才可用)

我们使用模拟数据实现接口，函数名规范为`api_{接口名}`，写在类com.demo.Global下：
```java
class Global extends JDApiBase
{
	public Object api_whoami()
	{
		checkAuth(AUTH_USER);
		return new JsObject(
			"id", 100
		);
	}
}
```

类Global中用于包含所有的函数型接口，它应继承JDApiBase类（前面提到的对象型接口基类AccessControl也是继承自JDApiBase）。

JsObject是框架提供的通用对象，其原型接口是一个`Map<String, Object>`，类似的还有JsArray，其原型接口是`List<Object>`，两者相互组合，可模拟Javascript中的对象和数组，
例如创建一个Person对象具有复杂数据结构 `[ {id, name, addr={country, city}, carIds=[id,...] ]`，我们写个接口`getPersons`来测试：

```java
public Object api_getPersons() {
	JsArray personList = new JsArray(
		new JsObject(
			"id", 100,
			"name", "xiaohua",
			"addr", new JsObject(
				"country", "China",
				"city", "Beijing"
			),
			"carIds", new JsArray(1001, 1008)
		),
		new JsObject(
			"id", 101,
			"name", "xiaoming",
			"addr", new JsObject(
				"country", "China",
				"city", "Shanghai"
			),
			"carIds", new JsArray()
		)
	);
	return personList;
}
```

访问试试：

	http://localhost:8080/mysvc/api/getPersons

返回的JSON内容为：

```json
[
	{"id":100,"name":"xiaohua","addr": {"country":"China","city":"Beijing"},"carIds":[1001,1008]},
	{"id":101,"name":"xiaoming","addr": {"country":"China","city":"Shanghai"},"carIds":[]}
]
```

当然你也可以定义Person/Car这些类并使用持久化机制用于数据库存取（OR Mapping机制），但筋斗云的编程风格建议不要拘泥于此。

由于java没有提供方便的创建List/Map的方法，在JDApiBase类中还提供了asList/asMap函数来方便的创建列表和映射表，如
```java
List<String> allowdAc = asList("get", "query");  // json: ["get", "query"]
Map<String, Integer> m = asMap("E_OK", 0, "E_PARAM", 1, "E_NOAUTH", 2); // json: { E_OK: 0, E_PARAM: 1, E_NOAUTH: 2}
```

JDApiBase.asList与Array.asList类似，但返回的List是可添加、删除元素的。
asMap的参数是一个key跟一个value，牺牲了一点类型安全性，但使用很方便。
另外，不建议用双括号语法去初始化一个集合对象。

上面那个例子，也可以写成：
```java
public Object api_getPersons()
{
	List<Object> personList = asList(
		asMap(
			"id", 100,
			"name", "xiaohua",
			"addr", new JsObject(
				"country", "China",
				"city", "Beijing"
			),
			"carIds", new JsArray(1001, 1008)
		),
		asMap(
			"id", 101,
			"name", "xiaoming",
			"addr", new JsObject(
				"country", "China",
				"city", "Shanghai"
			),
			"carIds", new JsArray()
		)
	);
	return personList;
}
```

由于登录与权限定义密切相关，为了了解原理，我们清空这个文件，重新来写登录、退出接口。
同时学习获取参数、数据库操作等常用函数。

**[任务]**

本节要求实现登录、退出、取登录信息三个接口，设计如下：

登录接口

	login(uname, pwd, _app?=user) -> {id, _isNew?}

	用户或员工登录（通过_app参数区分），如果是用户登录且用户不存在，可自动创建用户。

	参数

	- _app: 前端应用名称，用于区分登录类型，"user"-用户端, "emp"-员工端。

	返回

	- _isNew: 如果是新注册用户，该字段为1，否则不返回此字段。

	应用逻辑

	- 权限: AUTH_GUEST
	- 对于用户登录(_app是"user")，如果用户不存在，则自动创建用户。
	- 密码采用md5加密保存

取登录信息

	whoami() -> {id}

	如果已登录，则返回与登录接口相同的信息，否则返回未登录错误。
	用户端或员工端均可用。
	客户端可调用本接口测试是否可以通过重用会话，实现免登录进入。

	应用逻辑

	- 权限：AUTH_USER | AUTH_EMP

退出接口

	logout()

	退出登录。用户端或员工端均可用。

	应用逻辑

	- 权限：AUTH_USER | AUTH_EMP

在接口定义中，一般包括接口原型，参数及返回数据说明，应用逻辑等。
对于含义清晰的参数和返回数据，也不必一一说明。
应用逻辑中应先规定该接口的权限。

在框架中，最重要的是JDEnvBase与JDApiBase两个类。
在创建应用时，继承JDEnvBase来配置自定义权限等；而JDApiBase类包含大量工具函数如获取参数、数据库查询等，实现接口的AC类和Global类都继承自JDApiBase。

### 权限定义

在实现接口前，我们先了解如何定义权限。

登录类型是一种特殊的权限，在框架类JDApiBase中已经按位定义了以下登录类型：

	// 支持8种登录类型 0x1-0x80; 其它非登录权限应从0x100开始定义，且名称规范为PERM_XXX。
	public static final int AUTH_USER = 0x1; // 用户登录
	public static final int AUTH_EMP = 0x2;  // 员工登录
	public static final int AUTH_ADMIN = 0x4; // 超级管理员登录

还有一个特别地任意登录权限：

	public static final int AUTH_LOGIN = 0xff; // 任意角色登录
	
由于AC类和Global类都继承自JDApiBase，在接口类实现时可直接使用如：

	checkAuth(AUTH_USER);
	if (hasPerm(AUTH_USER)) {
		...
	}

要检查非游客（即任意身份登录），可以用

	checkAuth(AUTH_LOGIN);

权限应按位定义，即用`0x1, 0x2, 0x4, 0x8, 0x10, 0x20, 0x40, 0x80, 0x100, 0x200, ...`这些来定义。
其中0x1到0x80这8个权限预留给登录类型。

假如你做一个在线诊疗平台应用，其中除了用户（病人）登录，还有医生登录，可以扩展一个登录类型AUTH_DOCTOR；
再如同是医生登录，某些人具有更高的管理权限，可以扩展一个权限PERM_MGR；
此外，测试模式也常常当作特殊的权限来对待。
```java
public class WebApi ...
{
	public static final int AUTH_DOCTOR = 0x8; // 自定义登录类型，在0x8与0x80间定义。

	public static final int PERM_MGR = 0x100; // 自定义权限，从0x100开始。
	public static final int PERM_TEST_MODE = 0x200;

	...
}
```

于是检查权限时可以用：

	checkAuth(WebApi.AUTH_DOCTOR); // 用checkAuth函数检查医生登录权限，如果没有权限则直接返回错误。
	checkAuth(WebApi.PERM_TEST_MODE);
	checkAuth(WebApi.PERM_TEST_MODE | WebApi.PERM_MGR); // 可以多个权限或，表示要求测试模式或管理者权限

	// 用hasPerm可直接检查权限
	if (hasPerm(WebApi.PERM_MGR)) { ... }

然后在JDEnv子类即WebApi类中定义一个重要的回调函数`onGetPerms`，它将根据登录情况或全局变量来取出所有当前可能有的权限，
常用的检查权限的函数hasPerm/checkAuth都将调用它:

```java
public class WebApi extends JDEnvBase
{
	// 自定义登录类型，从0x8开始。
	public static final int AUTH_DOCTOR = 0x8; // 自定义登录类型，从0x8开始。

	// 自定义权限，从0x100开始。
	public static final int PERM_MGR = 0x100;
	public static final int PERM_TEST_MODE = 0x200;

	@Override
	protected Object onNewInstance(Class<?> t) throws Exception
	{
		return t.newInstance();
	}
	
	@Override
	protected Object onInvoke(Method mi, Object arg) throws Exception
	{
		return mi.invoke(arg);
	}

	@Override
	protected int onGetPerms()
	{
		int perms = 0;
		if (api.getSession("uid") != null)
		{
			perms |= JDApiBase.AUTH_USER;
		}
		else if (api.getSession("doctorId") != null)
		{
			perms |= AUTH_DOCTOR;
			if (api.getSession("perms").toString().contains("mgr"))
				perms |= PERM_MGR;
		}
		if (this.isTestMode) {
			perms |= PERM_TEST_MODE;
		}

		return perms;
	}
}
```

在JDEnvBase的子类中，常常用属性"api"来调用JDApiBase中的工具函数，比如api.getSession用来获取一个会话变量。

在登录成功时，我们应设置相应的session变量，如用户登录成功设置`uid`，员工登录成功设置`empId`，等等。

后面讲对象型接口时，还会有另一个重要的回调函数`onCreateAC`，用于将权限与类名进行绑定。

### 登录与退出

上节我们已经了解到，登录与权限检查密切相关，需要将用户信息存入session中，登录接口的大致实现如下（在Global类中）：
```java
public Object api_login()
{
	String type = env.appType;
	if (type.equals("user")) {
		... 验证成功 ...
		setSession("uid", ...);
	}
	else if (type.equals("emp")) {
		... 验证成功 ...
		setSession("empId", ...);
	}
	...
}
```

定义一个函数型接口，函数名称一定要符合 `api_{接口名}` 的规范。接口名以小写字母开头。
在接口实现时，一般应根据接口中的权限说明，使用checkAuth函数进行权限检查。

```java
class Global extends JDApiBase
{
	public Object api_login() throws Exception
	{
		String uname = (String)mparam("uname");
		String pwd = (String)mparam("pwd");

		Object ret = null;
		if (env.appType.equals("user"))
		{
			String sql = String.format("SELECT id FROM User WHERE uname=%s", Q(uname));
			Object id = queryOne(sql);
			if (id.equals(false))
				throw new MyException(E_AUTHFAIL, "bad uname or pwd");
			setSession("uid", id);
			ret = new JsObject("id", id);
		}
		else
		{
			// 其它登录类型
		}
		return ret;
	}

	public Object api_whoami()
	{
		checkAuth(AUTH_USER);
		int uid = (int)getSession("uid");
		return new JsObject("id", uid);
	}
	public void api_logout()
	{
		// checkAuth(AUTH_LOGIN);
		destroySession();
	}
}
```

在api_login函数中，先使用env.appType获取到登录类型（也称应用类型），再按登录类型分别查验身份，并最终设置相关会话变量，
这里设置的变量与之前的权限回调函数`onGetPerms`中相对应。

这里使用了很多常用函数，比如获取必需参数使用mparam函数，数据库查询使用了queryOne, execOne函数，出错返回使用MyException等，之后章节将详细介绍。

在实现whoami接口时，返回保存在会话(session)中的变量即可，logout接口则更加简单，直接销毁会话。

**[应用标识与应用类型]**

在筋斗云中，URL参数`_app`称为前端应用标识(app)，缺省为"user"，表示用户端应用。

不同应用要求使用不同的应用标识，在与后端的会话中使用的cookie也会有所不同，因而不同的应用即使同时在浏览器中打开也不会相互干扰。

应用标识中的主干部分称为应用类型(app type)，例如有三个应用分别标识为"emp"（员工端）, "emp2"（经理端）和"emp-store"（商户管理端），
它们的主干部分(去除尾部数字，去除"-"及后面部分)是相同的，都是"emp"，即它们具有相同的应用类型"emp"。

在接口实现时，用env.appName, env.appType来获取应用标识和应用类型，它们是根据URL参数`_app`得到的。
不同的应用如果是相同的应用类型，则登录方式相同，比如上例中都是用员工登录。

### 获取参数

函数`mparam`用来取必传参数(m表示mandatory)，参数既可以用URL参数，也可以用POST参数传递。如果是取一个可选参数，可以用`param`函数。
这两个函数返回Object类型，与直接用JDApiBase的`_GET`或`_POST`等属性相比，param/mparam可指定参数类型，如

	// 后缀"/i"要求该参数为整数类型。第二个参数指定缺省值，如果请求中没有该参数就使用缺省值。
	Integer svcId = (Integer)param("svcId/i");  // 请求参数为"svcId=3", 返回3。注意返回值可能为null，应该用包装类型Integer不宜用int避免转换异常.
	// 必选参数或指定默认参数
	Integer svcId2 = (Integer)mparam("svcId/i");
	Integer svcId3 = (Integer)param("svcId/i", 1);

	// 不指定类型后缀时，默认均为string.
	String s = (String)param("name");

如果传来的svcId不是整型，则param/mparam可直接报错返回。
上面例子中用强制转换是安全的（不会抛出异常），因为"/i"标识让param返回整数值或null(即Integer类型)。
因为值可能为空，则应该用Integer这样的包装类型。

而使用mparam时或给定缺省值时，一定不会返回null，因而也可以直接转成int类型。

类似地：

	// 取id参数，特别地，对id参数一定要求是整数。
	Integer id = (Integer)param("id");  // 请求参数为"id=3", 返回3

	// 后缀"/b"要求该参数布尔型，为0或1，返回true/false
	Boolean wantArray = (bool)param("wantArray/b", false); // 请求参数为"wantArray=1", 返回true

	// 后缀"/dt"或"/tm"表示日期时间类型，格式如"2011-2-1 8:8:8", 也支持"2010/1/1 10:10", "2010.3.4", "2011-02-01T10:10:10Z"等（参考JDApiBase.parseDate函数）
	java.util.Date startTm = (java.util.Date)param("startTm/dt"); // 请求参数为"startTm=2016-9-10 10:10"
	
	// 后缀"/n"表示数值类型(numeric)，可以是小数，如"qty=3.14"。
	// 第三个参数为"P"指定从POST集合中取参数，也可用"G"指定从GET集合即URL中取参数。
	//   如果不指定这个参数，则默认先查GET再查POST，即客户端既可以用URL参数，也可以用POST参数
	Double qty = (Double)mparam("qty/n", "P");
	Double qty2 = (Double)param("qty2/n", null, "P");

param/mparam除了检查简单类型，还支持一些复杂类型，比如"/i+"用于整数列表：

	List<Integer> idList = (List<Integer>)mparam("idList/i+"); // 请求参数为"idList=3,4,5", 返回列表 {3, 4, 5}

更多用法，比如两个参数至少填写一个，传一个压缩子表（如"/i:s:n"），可查阅参考文档。

### 接口返回

函数应返回符合接口原型中描述的对象，框架会将其转为最终的JSON字符串。

比如登录接口要求返回`{id, _isNew}`：

	login(uname, pwd, _app?=user) -> {id, _isNew?}

因而在api_login中，返回结构相符的对象即可：

	ret = new JsObject(
		"id", id,
		"_isNew", true
	);
	return ret;

最终返回的JSON示例：

	[0, {"id": 1, "_isNew": true}]

如果接口原型中没有定义返回值，框架会自动返回字符串"OK"。比如接口api_logout没有调用return，则最终返回的JSON为：

	[0, "OK"]

**[异常返回]**

如果处理出错，应返回一个错误对象，这通过抛出MyException异常来实现，比如

	throw new MyException(E_AUTHFAIL, "bad password", "密码错误");

它最终返回的JSON为：

	[-1, "密码错误", "bad password"]

分别表示`[错误码, 显示给用户看的错误信息, 调试信息]`，一般调试信息用英文，在各种编码下都能显示，且内容会详细些；错误信息一般用中文，提示给最终用户看。

也可以忽略错误信息，这时框架返回错误码对应的默认错误信息，如

	throw new MyException(E_AUTHFAIL, "bad password");

最终返回JSON为：

	[-1, "认证失败", "bad password"]

甚至直接：

	throw new MyException(E_AUTHFAIL);

最终返回JSON为：

	[-1, "认证失败"]

常用的其它返回码还有E_PARAM（参数错）, E_FORBIDDEN（无权限操作）等:
```java
E_ABORT = -100; // 要求客户端不报错
E_PARAM=1; // 参数不合法
E_NOAUTH=2; // 未认证，一般要求前端跳转登录页面
E_DB=3; // 数据库错
E_SERVER=4; // 服务器错
E_FORBIDDEN=5; // 无操作权限，不允许访问
```

**[立即返回]**

接口可以用exit函数（或抛出DirectReturn异常），立即中断执行并返回结果，例如：
```java
public Object api_hello()
{
	// env.response.setContentType("application/json");
	// header("Content-Type", "application/json");
	echo("[0, {\"id\":100, \"_isNew\": true}]");
	exit(); // 等价于 throw new DirectReturn();
}
```
可以通过env.request和env.response来获得HttpServletRequest和HttpServletResponse对象。
echo是JDApiBase中的工具函数，相当于 `env.response.getWriter().print()`，而且可支持多个参数，如`echo("hello", "world");`.
而header函数则相当于`env.response.addHeader(k, v)`，为结果添加HTTP输出头。

示例：实现获取图片接口pic。

	pic() -> 图片内容
	
注意：该接口直接返回图片内容，不符合筋斗云`[0, JSON数据]`的返回规范，所以用DirectReturn立即返回，避免框架干预：
```java
public void api_hello() throws Exception
{
	String path = this.env.request.getServletContext().getRealPath("1.jpg");
	FileInputStream file = new FileInputStream(path);
	int sz  = file.available();
	byte data[]=new byte[sz];
	file.read(data);
	file.close();
	env.response.setContentType("image/jpeg");
	env.response.getOutputStream().write(data);
	throw new DirectReturn();
}
```
前端可以直接使用链接显示图片：

	<img src="http://localhost:8080/mysvc/api/pic">

### 数据库操作

数据库连接是在web.properties文件中配置的：

	P_DB_DRIVER=com.mysql.jdbc.Driver
	P_DB=jdbc:mysql://localhost:3306/jdcloud?characterEncoding=utf8
	P_DBCRED=demo:demo123

数据库查询的常用函数是`queryOne`和`queryAll`，用来执行SELECT查询。
queryOne只返回首行数据，特别地，如果返回行中只有一列，则直接返回首行首列值：

	// 查到数据时，返回首行，例如 [100, "hello"]，类型为JsArray
	// 没有查到数据时，返回 false
	Object rv = queryOne("SELECT id, dscr FROM Ordr WHERE id=1");
	if (rv.equals(false)) {
		// 无数据
	}
	JsArray row = (JsArray)rv;
	// row.get(0)为id字段, row.get(1)为dscr字段, 注意均可能为null

	// 查到数据时，由于SELECT语句只有一个字段cnt，因而返回值即是cnt.
	rv = queryOne("SELECT COUNT(*) cnt FROM Ordr");
	if (rv.equals(false)) {}  // 其实上面SQL语句不可能查不到，这个判断可以不要。
	Long id = (Long)rv;
		
如果字段较多，常设置第二个参数assoc为true，要求返回关联数组（JsObject）以增加可读性：

	// 操作成功时，返回关联数组JsObject，例如 {"id": 1, "name": "jdcloud"}
	Object rv = queryOne("SELECT id, name FROM User WHERE id=1", true);
	if (rv.equals(false)) {
		// 无数据
	}
	JsObject row = (JsObject)rv;
	Integer id = (Integer)row.get("id");
	String name = (String)row.get("name");

如果要查询所有行的数据，可以用`queryAll`函数，它返回JsArray对象：

	// 有数据时，返回二维数组 [[id, dscr], ...]
	// 没有数据时，返回空数组 []，而不是false
	JsArray rv = queryAll("SELECT id, dscr FROM Ordr WHERE userId=1");
	if (rv.size() == 0) { } // 无数据
	JsArray row = (JsArray)rv.get(0); // 第一行，可用row.get(0), row.get(1)引用id, dscr字段

也可指定参数assoc=true让返回行使用关联数组，如：

	JsArray rv = queryAll("SELECT id, dscr FROM Ordr WHERE userId=1", true);
	if (rv.size() == 0) { } // 无数据
	JsObject row = (JsObject)rv.get(0); // 第一行，可用row.get("id"), row.get("dscr")引用id, dscr字段

执行非查询语句可以用包装函数`execOne`，返回受到影响的记录数，如：

	int recCnt = execOne("DELETE ...");
	execOne("UPDATE ...");
	execOne("INSERT INTO ...");

对于insert语句，设置第二个参数为true, 可以取到执行后得到的新id值：

	int newId = execOne("INSERT INTO ...", true);

(v2) 对一般的插入和更新，可以用更方便的dbInsert/dbUpdate函数，如：

	int orderId = dbInsert("Ordr", new JsObject(
		"tm", new Date(), // 支持Date类型
		"tm1", "=now()", // "="开头，表示是SQL表达式
		"amount", 100,
		"dscr", null // null字段会被忽略
	));
	// 等价于：
	String sql = String.format("INSERT INTO Ordr (tm, tm1, amount, dscr) VALUES ('%s', now(), %f, %s)", date(FMT_DT, tm), amount, Q(dscr)); // date函数为框架提供，用于转日期字符串
	int orderId = execOne(sql, true);

	// UPDATE Ordr SET ... WHERE id=100
	int cnt = dbUpdate("Ordr", new JsObject(
		"amount", 30,
		"dscr", "test dscr",
		"tm", "null", // 用""或"null"对字段置空；用"empty"对字段置空串。
		"tm1", null // null会被忽略
	), 100);

	// UPDATE Ordr SET tm=now() WHERE tm IS NULL
	int cnt = dbUpdate("Ordr", new JsObject(
		"tm", "=now()"  // "="开头，表示是SQL表达式
	), "tm IS NULL);
	
**[防备SQL注入]**

要特别注意的是，所有外部传入的字符串参数都不应直接用来拼接SQL语句，
下面登录接口的实现就包含一个典型的SQL注入漏洞：

	String uname = (String)mparam("uname");
	String pwd = (String)mparam("pwd");
	Object id = queryOne(String.format("SELECT id FROM User WHERE uname='%s' AND pwd='%s'", uname, pwd));
	if (id.equals(false))
		throw new MyException(E_AUTHFAIL, "bad uname/pwd", "用户名或密码错误");
	// 登录成功
	setSession("uid", id);
	
如果黑客精心准备了参数 `uname=a&pwd=a' or 1=1`，这样SQL语句将是

	SELECT id FROM User WHERE uname='a' AND pwd='a' or 1=1

总可以查询出结果，于是必能登录成功。
修复方式很简单，可以用Q函数进行转义：

	String sql = String.format("SELECT id FROM User WHERE uname=%s AND pwd=%s", Q(uname), Q(pwd));
	Object id = queryOne(sql);

(v2)对于插入和更新语句，尽量用dbInsert/dbUpdate方法，它们会安全地处理处理参数。

**[支持数据库事务]**

假如有一个用户用帐户余额给订单付款的接口，先更新订单状态，再更新用户帐户余额：
```java
public Object api_payOrder()
{
	execOne("UPDATE Ordr SET status='已付款'...");
	...
	execOne("UPDATE User SET balance=...");
	...
}
```

在更新之后，假如因故抛出了异常返回，订单状态或用户余额会不会状态错误？

有经验的开发者知道应使用数据库事务，让多条数据库查询要么全部执行(事务提交/commit)，要么全部取消掉(事务回滚/rollback)。
而筋斗云已经帮我们自动使用了事务确保数据一致性。

**筋斗云一次接口调用中的所有数据库查询都在一个事务中。** 开发者一般不必自行使用事务，除非为了优化并发和数据库锁。

要处理数据库连接细节，可以自行使用env.conn访问到Connection接口，例如做SQL编译优化等。

## 对象型接口

为了简化接口对象到数据库表的映射，我们在数据库中创建的表名和字段名就按上述大小写相间的风格来，表名或对象名的首字母大写，表字段或对象属性的首字母小写。

某些版本的MySQL/MariaDB在Windows等系统上表和字段名称全部用大写字母，遇到这种情况，可在配置文件my.ini中加上设置：

	[mysqld]
	lower_case_table_names=0 

然后重启MySQL即可。

### 定制操作类型和字段

对象接口通过继承AccessControl类来实现，默认允许5个标准对象操作，可以在onInit回调中改写属性`allowedAc`来限定允许的操作：
```java
class AC_ApiLog extends AccessControl
{
	@Override
	protected void onInit()
	{
		this.allowedAc = asList("get", "query", "add", "del");
		// 可以为 {"add", "get", "set", "del", "query"}中任意几个。
	}
}
```

缺省get/query操作返回ApiLog的所有字段，可以用属性`hiddenFields`隐藏一些字段，比如不返回"addr"和"tm"字段：
```java
this.hiddenFields = asList("addr", "tm");
```

对于add/set接口，可用`requiredFields`设置必填字段，用`readonlyFields`设置只读字段。
特别地，"id"字段默认就是只读的，无须设置。

示例：实现下面控制逻辑

- "addr"字段为必填字段，即在add接口中必须填值，在set接口中不允许置空；
- "tm"字段为只读字段，即在add/set接口中如果填值则忽略（但不报错）；
- 在add操作中，由程序自动填写"tm"字段。

```java
class AC_ApiLog extends AccessControl
{
	@Override
	protected void onInit()
	{
		this.requiredFields = asList("addr");
		this.readonlyFields = asList("tm");
	}

	// 由add/set接口回调，用于验证字段(Validate)，或做自动补全(AutoComplete)工作。
	@Override
	protected void onValidate()
	{
		if (this.ac.equals("add"))
		{
			env._POST.put("tm", date());
		}
	}
}
```
例中使用回调onValidate来对tm字段自动填值。
`date()`函数是JDApiBase类提供的工具方法，返回当前日期字符串，相当于：
```java
	String nowStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
```
还可指定格式或日期，如：
```java
	Date dt1 = new Date(now() + 10 * T_DAY); // 工具类中还提供now()方法，返回long类型的毫秒数，以及 T_SEC/T_MIN/T_HOUR/T_DAY 常量
	String dtStr = date("yyyy-MM-dd", dt1);
```

如果某些字段是在添加时不是必填，但更新时不可置空，可以用`requiredFields2`来设置；
类似地，添加时可写，更新时只读的字段，用`readonlyFields2`来设置。

### 绑定访问控制类与权限

前面在讲函数型接口时，提到权限检查用checkAuth函数来实现。
在对象型接口中，通过绑定访问控制类与权限，来实现不同角色通过不同的类来控制。

比如前例中ApiLog对象接口允许员工登录(AUTH_EMP)后访问，只要定义：
```java
class AC2_ApiLog extends AccessControl
{
	...
}
```

那么为什么AC2前缀对应员工权限呢？
这是因为框架定义默认权限-类名绑定逻辑为：

- 访客(AUTH_GUEST): 对应AC前缀类
- 用户(AUTH_USER): 对应AC1/AC类，即优先用AC1类，当AC1类不存在时，使用AC类
- 员工(AUTH_EMP): 对应AC2类
- 超级管理员(AUTH_ADMIN): 对应AC0/AccessControl类，即优先用AC0类，如果不存在，则使用AccessControl类；
 由于AccessControl是框架提供的，因而对于超级管理员，不创建任何类也可以调用任何对象接口。

如果要重写这些规则，需要重载回调函数`JDEnvBase.onCreateAC`，由它来实现类与权限的绑定。
下面示例就是默认规则的实现，返回类名数组，表示依次找这些类：
```java

public class WebApi extends JDEnvBase
{
	...

	@Override
	public String[] onCreateAC(String table)
	{
		if (api.hasPerm(JDApiBase.AUTH_USER)) {
			return new String[] { "AC1_" + table, "AC_" + table };
		}
		else if (api.hasPerm(JDApiBase.AUTH_EMP)) {
			return new String[] { "AC2_" + table };
		}
		else if (api.hasPerm(JDApiBase.AUTH_ADMIN)) {
			return new String[] { "AC0_" + table, "AccessControl" };
		}
		return new String[] {"AC_" + table};
	}
}
```

该函数传入一个表名（或称对象名，比如"ApiLog"），根据当前用户的角色，返回一个类名，比如"AC1_ApiLog"，"AC2_ApiLog"这些。
如果发现指定的类不存在，则不允许访问该对象接口。

在该段代码中，定义了用户登录后用"AC1"前缀的类，如果类不存在，可以再尝试用"AC"前缀的类，如果再不存在则不允许访问接口；
如果是员工登录，则只用"AC2"前缀的类，如果类不存在，则不允许访问接口。

关于hasPerm的用法及权限定义，可以参考前面章节“权限定义”及“登录与退出”。

### 定制可访问数据

除了限制用户可以访问哪些表和字段，还常会遇到一类需求是限制用户只能访问自己的数据。

**[任务]**

用户登录后，可以添加订单、查看自己的订单。
我们在设计文档中设计接口如下：

	添加订单
	Ordr.add()(amount) -> id

	查看订单
	Ordr.query() -> tbl(id, userId, status, amount)
	Ordr.get(id) -> { 同query接口字段...}

	应用逻辑

	- 权限：AUTH_USER
	- 用户只能添加(add)、查看(get/query)订单，不可修改(set)、删除(del)订单
	- 用户只能查看(get/query)属于自己的订单。
	- 用户在添加订单时，必须设置amount字段，不可设置userId, status这些字段。
	  后端将userId字段自动设置为该用户编号，status字段自动设置为"CR"（已创建）

上面接口原型描述中，get接口用"..."省略了详细的返回字段，因为返回对象的字段与query接口是一样的，两者写清楚一个即可。

实现对象型接口如下：
```java
class AC1_Ordr extends AccessControl
{
	@Override
	protected void onInit()
	{
		this.allowedAc = asList("get", "query", "add");
		this.requiredFields = asList("amount");
		this.readonlyFields = asList("status", "userId");
	}

	// get/query接口会回调
	@Override
	protected void onQuery()
	{
		Object userId = getSession("uid");
		this.addCond("t0.userId=" + userId);
	}

	// add/set接口会回调
	@Override 
	protected void onValidate()
	{
		if (this.ac.equals("add"))
		{
			Object userId = getSession("uid");
			env._POST.put("userId", userId);
			env._POST.put("status", "CR");
		}
	}
}
```

- 在get/query操作中，会回调`onQuery`函数，在这里我们用`addCond`添加了一条限制：用户只能查看到自己的订单。
 `addCond`的参数可理解为SQL语句中WHERE子句的片段；字段用"t0.userId"来表示，其中"t0"表示当前操作表"Ordr"的别名(alias)。后面会讲到联合查询(join)其它表，就可能使用其它表的别名。

- add/set操作会回调`onValidate`函数（本例中`allowedAc`中未定义"set"，因而不会有"set"操作过来）。在这个回调中常常设置`env._POST.put(k,v)`来自动完成一些字段。

- 注意会话变量`uid`是在用户登录成功后设置的，由于"AC1"类是用户登录后使用的，所以必能取到该变量。

**[任务]**

我们把需求稍扩展一下，现在允许set/del操作，即用户可以更改和删除自己的订单。

可以这样实现：
```java
class AC1_Ordr extends AccessControl
{
	@Override
	protected void onInit()
	{
		this.allowedAc = asList( "get", "query", "add", "set", "del" );
		...
	}

	// get/set/del接口会回调
	@Override
	protected void onValidateId()
	{
		Object uid = getSession("uid");
		Object id = mparam("id");
		Object rv = queryOne(String.format("SELECT id FROM Ordr WHERE id=%s AND userId=%s", id, uid);
		if (rv.Equals(false))
			throw new MyException(E_FORBIDDEN, "not your order");
	}
}
```
可通过`onValidateId`回调来限制get/set/del操作时，只允许访问自己的订单。

函数`mparam`用来取必传参数(m表示mandatory)。
函数`queryOne`用来查询首行数据，如果查询只有一列，则返回首行首列数据，但如果查询不到数据，就返回false. 
这里如果返回false，既可能是订单id不存在，也可能是虽然存在但是是别人的订单，简单处理，我们都返回一个E_FORBIDDEN异常。

框架对异常会自动处理，一般不用特别再检查数据库操作失败之类的异常。如果返回错误对象，可抛出`MyException`异常：

	throw new MyException(E_FORBIDDEN);

错误码"E_FORBIDDEN"表示没有权限，不允许操作；常用的其它错误码还有"E_PARAM"，表示参数错误。

MyException的第二个参数是内部调试信息，第三个参数是对用户友好的报错信息，比如：

	throw new MyException(E_FORBIDDEN, String.format("order id %s does not belong to user %s", id, uid), "不是你的订单，不可操作");

### 虚拟字段

前面已经学习过怎样把一个数据库中的表作为对象暴露出去。
其中，表的字段就可直接映射为对象的属性。对于不在对象主表中定义的字段，统称为虚拟字段。

通过属性`vcolDefs`来定义虚拟字段，最简单的一类虚拟字段是字段别名，比如在`AC1_Ordr.onInit`中设置:
```java
this.vcolDefs = asList(
	new VcolDef().res("t0.id orderId", "t0.dscr description")
);
```

这样就为Ordr对象增加了orderId与description两个虚拟字段。
在get/query接口中，是可以用它们作为查询字段的，比如：

	Ordr.query(cond="orderId>100 and description like '红色'")

在query接口中，虚拟字段与真实字段使用起来几乎没有区别。对外接口只有对象名，没有表名的概念，比如不允许在cond参数中指定"t0.orderId>100"。

#### 关联字段

**[任务]**

在订单的query/get接口中，只有userId字段，为了方便显示用户姓名和手机号，需要增加虚拟字段userName, userPhone字段，它们关联到User表的name, phone字段。

设计文档中定义接口如下：

	Ordr.query() -> tbl(id, dscr, ..., userName?, userPhone?)

习惯上，我们在query或get接口的字段列表中加"..."表示参考数据表定义中的字段，而"..."之后描述的就是虚拟字段。
虚拟字段上的后缀"?"表示该字段默认不返回，仅当在res参数中指定才会返回，如：

	Ordr.query(res="*,userName")

一般虚拟字段都建议默认不返回，而是按需来取，以减少关联表或计算带来的开销。

在cond参数中可以直接使用虚拟字段，不管它是否在res参数中指定，如

	Ordr.query(cond="userName LIKE '%john%'", res="id,dscr")

实现时，通过设置属性`vcolDefs`实现这些关联字段：
```java
class AC1_Ordr extends AccessControl
{
	protected override void onInit()
	{
		this.vcolDefs = asList(
			new VcolDef().res("u.name AS userName", "u.phone AS userPhone")
				.join("INNER JOIN User u ON u.id=t0.userId")
				// .isDefault(false)  // 与接口原型中字段是否可缺省(是否用"?"标记)对应
		);
	}
}
```

- 以上很多表或字段指定了别名，比如表"User u"，字段"u.name AS userName"。在指定别名时，关键字"AS"可以省略。
- 表的别名不是必须的，除非有多个同名的表被引用。
- 如果指定"default"选项为true, 则调用Ordr.query()时如果未指定"res"参数，会默认会带上该字段。

#### 关联字段依赖

假设设计有“订单评价”对象，它与“订单”相关联：

	@Rating: id, orderId, content

现在要为Rating表增加关联字段订单描述(orderDscr)与客户姓名(userName), 设计接口为：

	Rating.query() -> tbl(id, orderId, content, ..., orderDscr, userName?)

注意：userName字段不直接与Rating表关联，而是通过Ordr表桥接到User表才能取到。

需要在vcolDefs定义"userName"字段时，使用require选项指定依赖字段：
```java
public class AC1_Ordr extends AccessControl
{
	@Override 
	protected void onInit()
	{
		this.vcolDefs = asList(
			new VcolDef().res("o.dscr AS orderDscr")
				.join("INNER JOIN Ordr o ON o.id=t0.orderId"),
			new VcolDef().res("u.name AS userName")
				.join("INNER JOIN User u ON o.userId=u.id")
				.require("userId") // *** 定义依赖，如果要用到res中的字段如userName，则自动添加orderDscr字段引入的表关联。
		);

		...
	}
}
```

#### 计算字段

在定义虚拟字段时，"res"也可以是一个计算值，或一个很复杂的子查询。

例如表OrderItem是Ordr对象的一个子表，表示订单中每一项产品的名称、数量、价格：

	@Ordr: id, userId, status(2), amount, dscr(l)
	@OrderItem: id, orderId, name, qty, price

	一个订单对应多个产品项：
	OrderItem(orderId) n<->1 Ordr

在添加订单时，同时将每个产品的数量、单价添加到OrderItem表中了。
订单中有一个amount字段表示金额，由于可能存在折扣或优惠，它不一定等于OrderItem中每个产品价格之和。
现在希望增加一个amount2字段表示原价，可以实现为：

```java
class AC1_Ordr extends AccessControl
{
	@Override
	protected void onInit()
	{
		this.vcolDefs = asList(
			new VcolDef().res("(SELECT SUM(qty*isnull(price2,0)) FROM OrderItem WHERE orderId=t0.id) AS amount2")
		);
	}
}
```

这里amount2在res中定义为一个复杂的子查询，其中还用到了t0表，也即是主表"Ordr"的固定别名。
可想而知，在这个例子中，取该字段的查询效率是比较差的。也不要把它用到cond条件中。

**[子表字段]**

上面Ordr与OrderItem表是典型的一对多关系，有时希望在返回一个对象时，同时返回一个子对象数组，比如获取一个订单像这样：

	{ id: 1, dscr: "换轮胎及洗车", ..., orderItem: [
		{id: 1, name: "洗车", price: 25, qty: 1}
		{id: 2, name: "换轮胎", price: 380, qty: 2}
	]}

后面章节"子表对象"将介绍其实现方式。但如果子对象相对简单，且预计记录数不会特别多，
我们也可以把子表压缩成一个字符串字段，表中每行以","分隔，行中每个字段以":"分隔，像这样返回：

	{ id: 1, dscr: "换轮胎及洗车", ..., itemsInfo: "洗车:25:1,换轮胎:380:2"}

设计接口原型如下，我们用List来描述这种紧凑列表的格式：

	Ordr.query() -> tbl(..., itemsInfo)

	返回
	- itemsInfo: List(name, price, qty). 格式例如"洗车:25:1,换轮胎:380:2", 表示两行记录，每行3个字段。注意字段内容中不可出现":", ","这些分隔符。

要将字段拼合成这种格式，在MySQL中一般用group_concat：

	SELECT group_concat(concat(oi.name, ':', oi.price, ':', oi.qty))
	FROM OrderItem oi
	WHERE ...

在MSSQL中，可以用"SELECT...FOR XML"方式来拼合, 生成的串最后会多带一个逗号，如"洗车:25:1,换轮胎:380:2,"

	SELECT oi.name + ':' + cast(oi.price as varchar) + ':' + cast(oi.qty as varchar) + ','
	FROM OrderItem oi
	WHERE ...
	FOR XML PATH('')

子表字段也是一种计算字段，可实现如下：

	VcolDef vcol = new VcolDef().res(
		"(SELECT oi.name + ':' + cast(oi.price as varchar) + ':' + cast(oi.qty as varchar) + ','\n" +
		"FROM OrderItem oi\n" +
		"WHERE oi.orderId=t0.id\n" +
		"FOR XML PATH('') ) itemsInfo"
	);

### 子表对象

前面提到过想在对象中返回子表时，可以使用压缩成一个字符串的子表字段，一般适合数据比较简单的场合。

另一种方式是用`subobj`来定义子表对象。

例如在获取订单时，同时返回订单日志，设计接口如下：

	Ordr.get() -> {id, ..., @orderLog?}

	返回
	orderLog: {id, tm, dscr, action} 订单日志子表。

	示例

	{id: 1, dscr: "换轮胎及洗车", ..., orderLog: [
		{id: 1, tm: "2016-1-1 10:10", action: "CR", dscr: "创建订单"},
		{id: 2, tm: "2016-1-1 10:20", action: "PA", dscr: "付款"}
	]}

上面接口原型描述中，字段orderLog前面的"@"标记表示它是一个数组，在返回值介绍中列出了它的数据结构。

实现：
```java
class AC1_Ordr extends AccessControl
{
	@Override
	protected void onInit()
	{
		this.subobj = asMap(
			"orderLog", new SubobjDef().sql("SELECT ol.* FROM OrderLog ol WHERE ol.orderId=%d")
		);
	}
}
```

用选项"sql"定义子表的查询语句，其中用"%d"来表示主表主键，这里即Ordr.id字段。

定义子表对象时，还可设置一些选项，比如上面设置等价于：

	"orderLog", new SubobjDef().sql(...)
		.wantOne(false)
		.isDefault(false)

- 选项"wantOne"表示是否只返回一行。默认是返回一个对象数组，如`[{id, tm, ...}]`。
  如果选项"wantOne"为true，则结果以一个对象返回即 `{id, tm, ...}`, 适用于主表与子表一对一的情况。

- 选项"isDefault"与虚拟字段(vcolDefs)上的"isDefault"选项一样，表示当get或query接口未指定"res"参数时，是否默认返回该字段。
  一般应使用默认值false，客户端需要时应通过res参数指定，如 `Ordr.query(res="*,orderLog")`.

注意：查询子表作为子对象字段是不支持分页的。如果子表可能很大，不要设计使用子表字段或列表字段，而应直接用子表的query方法来取，如开放接口"OrderLog.query"。

### 虚拟表和视图

假设表ApiLog中有一个字段叫app，表示前端应用名，当app="emp"时，就表示是员工端应用的操作日志。

	@ApiLog: id, tm, addr, app, userId

现在想对员工端操作日志进行查询，定义以下接口：

	EmpLog.query() -> tbl(id, tm, userId, ac, ..., empName?, empPhone?)

	返回
	- empName/empPhone: 关联字段，通过userId关联到Employee表的name/phone字段。

	应用逻辑
	- 权限：AUTH_EMP

EmpLog类似一个数据库视图，是一个虚拟对象或虚拟表，筋斗云可直接使用AccessControl创建虚拟表，代码如下：
```java
class AC2_EmpLog extends AccessControl
{
	@Override
	protected void onInit()
	{
		this.allowedAc = asList("query");
		this.table = "ApiLog";
		this.defaultRes = "id, tm, userId, ac, req, res, reqsz, ressz, empName, empPhone";
		this.defaultSort = "t0.id DESC";

		this.vcolDefs = asList(
			new VcolDef().res("e.name AS empName", "e.phone AS empPhone")
				.join("LEFT JOIN Employee e ON e.id=t0.userId")
		);
	}

	// get/query操作都会走这里
	@Override 
	protected void onQuery()
	{
		this.addCond("t0.app='emp' and t0.userId IS NOT NULL");
	}
}
```

其要点是：

- 重写`table`属性, 定义实际表
- 用属性`vcolDefs`定义虚拟字段
- 用`addCond`方法添加缺省查询条件

属性`defaultSort`和`defaultRes`可用于定义缺省返回字段及排序方式。

在get/query接口中可以用"res"指定返回字段，如果未指定，则会返回除了$hiddenFields定义的字段之外，所有主表中的字段，还会包括设置了`isDefault=true`的虚拟字段。
通过`defaultRes`可以指定缺省返回字段列表。

query接口中可以通过"orderby"来指定排序方式，如果未指定，默认是按id排序的，通过`defaultSort`可以修改默认排序方式。

### 非标准对象接口

对象的增删改查(add/set/get/query/del共5个)接口称为标准接口。
可以为对象增加其它非标准接口，例如取消订单接口：

	Ordr.cancel(id)

	应用逻辑

	- 权限: AUTH_USER
	- 用户只能操作自己的订单

只要在相应的访问控制类中，添加名为`api_{非标准接口名}`的函数即可：
```java
class AC1_Ordr extends AccessControl
{
	// "Ordr.cancel"接口
	public void api_cancel()
	{
		// 不需要checkAuth
		this.id = mparam("id");
		this.onValidateId();
		...
		execOne("UPDATE Ordr SET status='CA' WHERE id=" + this.id.toString());
	}
}
```

非标准对象接口与与函数型接口写法类似，返回Object或void均可。

### 接口返回前回调

示例：添加订单到Ordr表时，自动添加一条"创建订单"日志到OrderLog表，可以这样实现：
```java
class AC1_Ordr extends AccessControl
{
	@Override 
	protected void onValidate()
	{
		if (this.ac.equals("add"))
		{
			... 

			this.onAfterActions.add( (ret)-> {
				Object orderId = this.id;
				String sql = String.format("INSERT INTO OrderLog (orderId, action, tm) VALUES (%s,'CR','%s')", orderId, date());
				execOne(sql);
			});
		}
	}
}

```
属性`onAfterActions`是一个回调函数（lambda表达式）列表，在操作结束时被回调，在里面可以修改接口的返回对象`ret`。
属性`id`可用于取add操作结束时的新对象id，或get/set/del操作的id参数。

对象接口调用完后，还会回调onAfter函数，也可以在这个回调里面操作。
此外，如要在get/query接口返回前修改返回数据，用onHandleRow回调函数更加方便。

示例：实现接口

	Ordr.get(id) -> {id, status, ..., statusStr?}
	Ordr.query() -> tbl(同get接口字段...)

	- status: "CR" - 新创建, "PA" - 已付款
	- statusStr: 状态名称，用中文表示，当有status返回时则同时返回该字段

```java
class AC1_Ordr extends AccessControl
{
	public static final Map<String, String> statusStr = asMap(
		"CR", "未付款", 
		"PA", "待服务"
	);
	// get/query接口会回调
	@Override 
	protected void onHandleRow(JsObject rowData)
	{
		if (rowData.containsKey("status"))
		{
			String st = rowData.get("status").toString();
			String value = statusStr.get(st);
			if (value == null)
				value = st;
			rowData.put("statusStr", value);
		}
	}
}

```

## 框架功能

### 服务配置

服务端配置文件为`WEB-INF/web.properties`。一般习惯上提交web.properties.template文件到代码库中，在部署时，手工复制修改它来创建web.properties文件。

在配置文件中应通过JDEnv指定服务入口类，如：

	JDEnv=com.demo.WebApi

在参考文档中搜索web.properties查看所有配置选项。

配置选项还可以通过在JDEnv的onApiInit回调函数来初始化，如

	// class WebApi extends JDEnvBase
	protected void onApiInit() {
		// 关闭ApiLog
		this.props.setProperty("enableApiLog", "0");	
	}

### 会话管理

筋斗云使用cookie机制来维持与客户端的会话。
按DACA规范，后端服务应支持多种前端应用（appType不同）同时访问，例如在浏览器中同时登录客户端(appType="user")和管理端(appType="emp")，两者应互不干扰，比如一个应用退出登录不会造成另一应用退出。
前端通过URL参数`_app`传递appName（隐含了appType，如appName="emp-adm"时其appType为"emp"），服务在实现时需要对不同的appType进行会话隔离。

在jdcloud-php版本中，通过不同appType使用不同的cookie名来实现应用类型隔离。
例如客户端应用和管理端应用使用的cookie名称为"userid"和"empid"。

受限于javaweb机制，session使用的cookie名称(JSESSIONID)无法根据请求参数动态设置。因而不同的应用可能会共享同一session的，框架在实现时确保不同应用的session不重名且一个应用退出不影响其它应用。
开发者应调用getSession/setSession/unsetSession/destroySession这些方法来操作session，避免使用Servlet原生的 env.request.getSession 来操作。

如果想修改默认的session名称（JSESSIONID）或超时时间（30分钟），可在WEB-INF/web.xml中配置：

	<session-config>  
	  <session-timeout>30</session-timeout>  
	  <cookie-config>  
		<name>jdcloudId</name>  
	  </cookie-config>  
	</session-config>

### API调用监控

筋斗云php框架默认将接口调用记录到表ApiLog中供分析。
可通过配置选项`enableApiLog=0`关闭该特性。

### 批量请求

DACA协议定义了批量请求，即在一次请求中，包含多条接口调用，并可指定是否在一个事务中执行。

示例：

	POST api/batch
	
	[
		{
			"ac": "User.get",
			"get": {"res": "name,phone"}
		},
		{
			"ac": "ActionLog.add",
			"post": {"page": "home", "ver": "android", "userId": "{$-1.id}"},
			"ref": ["userId"]
		}
	]

主要特性：

- 一次请求包含多个调用。通过POST请求中的JSON数组指定，数组中每一项为一个调用，其格式为: `{ac, %get?, %post?, @ref?}`, 只有ac参数必须，其它均可省略。
- 事务。可通过URL参数useTrans来指定多个调用是否在一个事务中，即可指定一个调用失败是否回滚前面调用的改动。
- 前向引用。后面调用可引用前面调用的结果，如"{$-1}"就是一个前向引用，每个引用包含在`{}`中，含有引用的参数通过ref数组指定。

get
: URL请求参数。

post
: POST请求参数。

ref
: 使用了batch引用的参数列表。

如果使用事务，只是URL上加个参数：

	POST api/batch?useTrans=1

batch的返回内容是多条调用返回内容组成的数组，样例如下：

	[0, [
		[ 0, {id: 1, name: "用户1", phone: "13712345678"} ],  // 调用User.get的返回结果
		[ 0, "OK" ]  // 调用ActionLog.add的返回结果
	]]

具体可参考筋斗云前端文档及DACA协议文档。

### 筋斗云插件

目前在演示程序中提供了系统支持JDLogin和JDUpload两个插件。

插件类应以JD为前缀，应包括接口文档，具有独立的函数接口和对象接口。

使用插件时，先将插件源码复制入口类(JDEnv)相同包中。为了调用其中函数接口，应配置onCreateApi回调：

	// class WebApi extends JDEnvBase
	protected String[] onCreateApi()
	{
		return new String[] { "Global", "JDLogin", "JDUpload" };
	}

TODO: 插件工具

### 其它未实现功能

与筋斗云php版本相比，以下功能未实现。

- 工具集

包括数据库部署工具、初始化配置工具、上线工具等。

筋斗云php版中通过tool/upgrade.php，将主设计文档DESIGN.md中的数据模型部署到数据库中，.NET版本暂不支持。

- 函数

logit - 调试输出到文件。

- 版本管理及自动更新机制

参考 X-Daca-Server-Rev.

- 后台定时任务框架

- 模拟模式与第三方扩展接口

