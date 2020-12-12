package com.jdcloud;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.*;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

public class JDEnvBase extends JDApiBase implements Closeable
{
	public class CallSvcOpt
	{
		public boolean backupEnv = false;
		public boolean isCleanCall = false;
		public boolean asAdmin = false;
	}

	// used by batch call
	public static class CallCtx {
		public String ac;
		public JsObject get;
		public JsObject post;
		public String[] ref;
	}
/**<pre>
%var env.isTestMode

是否为测试模式。
 */
	public boolean isTestMode = false;

/**<pre>
%var env.debugLevel

0-9间的调试等级。
 */
	public int debugLevel = 0;
	public JsArray debugInfo = new JsArray();
	
/**<pre>
%var env.appName
%var env.appType
%key getAppType()

获取appName, appType
 */
	public String appName, appType;
	public String clientVer;
	
	// 上传文件配置选项
	public int uploadMemorySize = 500 * KB; // 默认内存保存500K，超过则保存到文件
	public long uploadSizeMax() { // 默认最大上传10M内容, 可在配置中修改
		String val = props.getProperty("uploadSizeMax");
		if (val == null)
			return 10 * MB;
		return Long.parseLong(val);
	}

	public Connection conn;
	protected ServletContext ctx;
	public HttpServletRequest request;
	public HttpServletResponse response;
	public JsObject _GET, _POST;
	public List<FileItem> _FILES = null;

	// JSON对象序列化后
	protected Object postData;

	public String dbType = "mysql";
	public DbStrategy dbStrategy;

/**<pre>
%var env.baseDir

应用程序的主目录，用于写文件。默认为 {user.home}/jd-data/{project}.

在CentOS下运行tomcat时，往往默认位置为 /usr/share/tomcat/jd-data/{project}
如果在web.properties中设置了

	baseDir=.

表示以项目部署目录为基本目录。
 */
// TODO: use static
	public String baseDir;
	protected String webRootDir;

/**<pre>
%var env.props
%key web.properties
%key jdcloud-config

配置选项。从WEB-INF/web.properties中加载，也可以在env.onApiInit回调函数中修改。

## 应用入口 JDEnv

- JDEnv: 类名。应继承于com.jdcloud.JDEnvBase。

作为WebApi应用程序入口。
所有的接口实现都应与该类在同一个包中。

## 数据库连接

- P_DBTYPE: String. 默认为"mysql"。还支持mssql
- P_DB_DRIVER: 类名. 驱动程序库。特别地，"DataSource"表示jdbc数据源及连接池（在context及web.xml中定义连接池资源）。
- P_DB: String. 数据库连接字符串
- P_DBCRED: String. 数据库用户密码，格式为 "{用户名:密码}"

示例：连接mysql, 使用库mysql-connector-java-5.1.34.jar

	P_DBTYPE=mysql
	P_DB_DRIVER=com.mysql.jdbc.Driver
	P_DB=jdbc:mysql://localhost:3306/jdcloud?characterEncoding=utf8
	P_DBCRED=demo:demo123

示例：连接sqlserver(mssql)，使用库sqljdbc42.jar

	P_DBTYPE=mssql
	P_DB_DRIVER=com.microsoft.sqlserver.jdbc.SQLServerDriver
	P_DB=jdbc:sqlserver://localhost:1433;instanceName=MSSQL1;databaseName=jdcloud;integratedSecurity=false;
	P_DBCRED=sa:demo123

示例：连接mysql, 且使用连接池，在web.properties中设置：

	P_DBTYPE=mysql
	P_DB_DRIVER=DataSource
	P_DB=jdbc/jdcloud
	
	在web.xml的<web-app>标签中增加：

	<resource-ref> 
	  <res-ref-name>jdbc/jdcloud</res-ref-name>  
	  <res-type>javax.sql.DataSource</res-type>  
	  <res-auth>Container</res-auth> 
	</resource-ref>

	在<Context>标签中增加Resource定义：
	
	<Resource name="jdbc/jdcloud" auth="Container" driverClassName="com.mysql.jdbc.Driver" factory="org.apache.tomcat.jdbc.pool.DataSourceFactory" 
	  maxActive="100" username="demo" password="demo123" type="javax.sql.DataSource" url="jdbc:mysql://server-pc:3306/jdcloud?characterEncoding=utf8"/> 

## 应用程序选项

- P_TEST_MODE: Boolean. 默认为0。测试模式。可用env.isTestMode获取。
- P_DEBUG: Integer. 0-9之间，默认为0. 调试等级。为9时输出SQL日志。可用env.debugLevel获取。

- enableApiLog: Boolean. 默认为1。记录ApiLog。
- baseDir: String. 应用数据目录，默认为 {user.home}/jd-data/{project}。在写文件时可用env.baseDir作为目录。注意目录分隔符用"/"，注意不以"/"结尾。
 一般用绝对路径，如"/home/data/myproject", "c:\data"等；也可以使用相对路径，如"data"，将以项目servlet目录作为当前路径。
 */
	public Properties props;

	public String X_RET_STR;
	public JsArray X_RET;
	
/**<pre>
@var X_RET_FN

默认接口调用后输出筋斗云的`[0, data]`格式。
若想修改返回格式，可设置该回调函数。

- 如果返回对象，则输出json格式。
- 如果返回false，应自行用echo输出。注意API日志中仍记录筋斗云返回数据格式。

示例：返回 `{code, data}`格式：

	env.X_RET_FN = (ret) -> {
		int code = intValue(getJsValue(ret, 0));
		Object val = getJsValue(ret, 1);
		return new JsObject("code", code, "data", val);
	};
	_GET("fmt", "array", true); // true表示如果未指定fmt，则设置值为"array"
	return env.callSvc("User.query");

示例：返回xml格式：

	env.X_RET_FN = (ret) -> {
		header("Content-Type: application/xml");
		int code = intValue(getJsValue(ret, 0));
		Object val = getJsValue(ret, 1);
		echo(String.format("<xml><code>%d</code><data>%s</data></xml>", code, jsonEncode(val)));
		return false;
	};
	
*/
	public Fn1<Object, Object> X_RET_FN;

	public static JDEnvBase createEnv(ServletContext ctx) throws Exception
	{
		Properties props = new Properties();
		try (InputStream is = ctx.getResourceAsStream("/WEB-INF/web.properties");
			Reader rd = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		) {
			props.load(rd);
		}
		catch (Exception e) {
		}
		
		String clsEnv = props.getProperty("JDEnv");
		if (clsEnv == null) {
			throw null; 
		}
		JDEnvBase env = (JDEnvBase)Class.forName(clsEnv).newInstance();
		env.initEnv(ctx, props);
		return env;
	}

	public static JDEnvBase createEnv() throws Exception
	{
		Properties props = new Properties();
		try (InputStream is = new FileInputStream("web.properties");
			Reader rd = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		) {
			props.load(rd);
		}
		catch (Exception e) {
		}
		
		JDEnvBase env = new JDEnvBase();
		env.initEnv(null, props);
		return env;
	}

	private void initEnv(ServletContext ctx, Properties props) throws Exception
	{
		this.ctx = ctx;
		this.props = props;
		this.env = this; // NOTE: JDEnvBase extends JDApiBase to use app common functions. MUST set env

		String path = (ctx != null? ctx.getRealPath(""): System.getProperty("user.dir"));
		// end with "/"
		this.webRootDir = getPath(path, true);
		// TODO: static
		this.baseDir = props.getProperty("baseDir");
		if (this.baseDir == null) {
			if (ctx != null)
				this.baseDir = System.getProperty("user.home") + "/jd-data/" + ctx.getContextPath();
			else
				this.baseDir = System.getProperty("user.dir");
		}
		// 支持相对路径, 以项目servlet路径作为当前路径。
		else if (! this.baseDir.matches("^([/\\\\]|\\w:)")) { // /dir1 c:/dir1
			this.baseDir = this.webRootDir + this.baseDir;
		}
		this.baseDir = getPath(this.baseDir, false);
		new File(this.baseDir).mkdirs();
		
		this.isTestMode = parseBoolean(props.getProperty("P_TEST_MODE", "0"));
		// debugLevel可通过_debug参数(TODO)或P_DEBUG配置指定
		// this.debugLevel = (int)param("_debug/i", Integer.parseInt(props.getProperty("P_DEBUG", "0")), "G");
		this.debugLevel = Integer.parseInt(props.getProperty("P_DEBUG", "0"));
		this.dbType = props.getProperty("P_DBTYPE", "mysql");

		if (this.dbType.equals("mysql"))
			this.dbStrategy = new MySQLStrategy();
		else if (this.dbType.equals("mssql"))
			this.dbStrategy = new MsSQLStrategy();
		else
			throw new MyException(E_SERVER, "bad dbType=`" + this.dbType + "` in web.properties");
		
		this.dbStrategy.init(this);
	}
	
	public String getContentType() {
		return this.request.getContentType();
	}
	
	private String content;
	public String getHttpInput() throws Exception {
		if (content == null) {
			Reader rd = this.request.getReader();
			content = readFile(rd);
		}
		return content;
	}
	private byte[] contentBS;
	public byte[] getHttpInputBS() throws Exception {
		if (contentBS == null) {
			InputStream in = this.request.getInputStream();
			contentBS = readFileBytes(in);
		}
		return contentBS;
	}
	
	@SuppressWarnings("unchecked")
	protected String initRequest() throws Exception
	{
		String origin = request.getHeader("Origin");
		if (origin != null)
		{
			header("Access-Control-Allow-Origin", origin);
			header("Access-Control-Allow-Credentials", "true");
			header("Access-Control-Expose-Headers", "X-Daca-Server-Rev, X-Daca-Test-Mode, X-Daca-Mock-Mode");

			// NOTE: 根据安全策略可限制val的范围
			String val = header("Access-Control-Request-Headers");
			if (val != null) {
				header("Access-Control-Allow-Headers", val);
			}
			// NOTE: 根据安全策略可限制val的范围
			val = header("Access-Control-Request-Method");
			if (val != null) {
				header("Access-Control-Allow-Methods", val);
			}
		}

		if (request.getMethod().equals("OPTIONS"))
			exit();

		String enc = request.getCharacterEncoding();
		if (enc == null)
			request.setCharacterEncoding("utf-8");
		response.setContentType("text/plain; charset=utf-8");

		String pathInfo = request.getPathInfo();
		if (pathInfo == null)
			throw new MyException(E_PARAM, "bad ac");
		// parse ac
		Pattern re = Pattern.compile("([\\w|.]+)$");
		Matcher m = re.matcher(pathInfo);
		if (! m.find()) {
			throw new MyException(E_PARAM, "bad ac");
		}
		String ac = m.group(1);

		// 支持POST为urlencoded或json格式
		String ct = this.request.getContentType();
		if (ct != null) {
			if (ct.indexOf("/json") > 0) {
				Type type = null;
				if (ac.equals("batch")) {
					type = new TypeToken<List<CallCtx>>() {}.getType();
				}
				else {
					type = Object.class;
				}
				try {
					Reader rd = this.request.getReader();
					this.postData = new Gson().fromJson(rd, type);
				}
				catch (Exception e) {
					throw new MyException(E_PARAM, "bad post content: " + e.getMessage());
				}
				if (this.postData instanceof Map) {
					this._POST = new JsObject();
					this._POST.putAll((Map<String, Object>)this.postData);
				}
			}
			else if (ct.indexOf("urlencoded") >= 0) {
				try {
					this._POST = QueryString.getPostParam(request);
				}
				catch (Exception e) {
					throw new MyException(E_PARAM, "bad post content: " + e.getMessage());
				}
			}
			else if (ct.indexOf("multipart/form-data") >= 0) {
				parseFiles();
			}
		}
		// e.g. "application/xml" 
		if (this._POST == null) {
			this._POST = new JsObject();
		}

		// 注意: request.getReader()只能读一次. 若在处理POST内容前调用getQueryString将导致POST内容无法读取.
		this._GET = QueryString.getUrlParam(request);

		this.appName = (String)param("_app", "user", "G");
		this.appType = this.appName.replaceFirst("(\\d+|-\\w+)$", "");

		this.clientVer = (String)param("_ver", null, "G");
		if (this.clientVer == null) {
			// Mozilla/5.0 (Linux; U; Android 4.1.1; zh-cn; MI 2S Build/JRO03L) AppleWebKit/533.1 (KHTML, like Gecko)Version/4.0 MQQBrowser/5.4 TBS/025440 Mobile Safari/533.1 MicroMessenger/6.2.5.50_r0e62591.621 NetType/WIFI Language/zh_CN
			String ua = this.request.getHeader("User-Agent");
			if (ua!=null && (m=regexMatch(ua, "MicroMessenger\\/([0-9.]+)")).find()) {
				this.clientVer = "wx/" + m.group(1);
			}
			else {
				this.clientVer = "web";
			}
		}

		this.onApiInit();

		if (this.isTestMode)
		{
			header("X-Daca-Test-Mode", "1");
		}

		// TODO: X-Daca-Mock-Mode
		// X-Daca-Server-Rev
		String rev = readFile(this.webRootDir + "revision.txt");
		if (rev != null) {
			rev = rev.substring(0, 6);
			header("X-Daca-Server-Rev", rev);
		}

		return ac;
	}
	
/*
取客户端上传上来的文件，类似php的$_FILES；同时也会设置_POST
 */
	private void parseFiles() throws Exception
	{
		if (_FILES != null)
			return;
		
		DiskFileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload(factory);
		//upload.setHeaderEncoding("UTF-8");
		factory.setSizeThreshold(this.uploadMemorySize);
		//File tmpDir = new File("c:/tmpdir"); // 当超过memorySize的时候，存到一个临时文件夹中
		//factory.setRepository(tmpDir);
		upload.setSizeMax(this.uploadSizeMax());
		List<FileItem> items = upload.parseRequest(new ServletRequestContext(request));
		this._FILES = asList();
		for (FileItem item : items) {
			if (! item.isFormField()) {
				String fileName = item.getName();
				if (fileName.length() == 0)
					continue;
				_FILES.add(item);
			}
			else {
				if (_POST == null)
					_POST = new JsObject();
				_POST.put(item.getName(), item.getString());
			}
		}
	}

	// 回调函数集。在事务结束时调用。
	public List<Action> onAfterActions = asList();

	public void service(HttpServletRequest request, HttpServletResponse response) {
		this.request = request;
		this.response = response;
		
		callSvcSafe(null, true);

		for (Action f: this.onAfterActions) {
			try {
				f.call();
			}
			catch (Exception ex) {
				StringWriter w = new StringWriter();
				ex.printStackTrace(new PrintWriter(w));
				logit(w.toString());
				ex.printStackTrace();
			}
		}
	}

	// ac=null: auto init
	public JsArray callSvcSafe(String ac, boolean useTrans) {
		JsArray ret = new JsArray(0, null);
		boolean ok = false; // commit or rollback trans
		boolean output = true;
		ApiLog apiLog = null;
		boolean isDefaultCall = ac == null;
		try {
			if (isDefaultCall) {
				ac = initRequest();
				if (parseBoolean(props.getProperty("enableApiLog", "1"))) {
					apiLog = new ApiLog(ac);
					apiLog.logBefore();
				}
			}

			Object rv = null;
			if (! ac.equals("batch")) {
				if (useTrans)
					this.beginTrans();
				rv = this.callSvc(ac);
				ok = true;
			}
			else {
				boolean batchUseTrans = (boolean)param("useTrans/b", false, "G");
				if (useTrans && batchUseTrans)
					this.beginTrans();
				else
					useTrans = false;
				boolean[] ref_ok = new boolean[] {false};
				rv = this.batchCall(batchUseTrans, ref_ok);
				ok = ref_ok[0];
			}
			ret.set(1, rv);
		}
		catch (DirectReturn ex) {
			ok = true;
			ret.set(0, ex.retCode);
			ret.set(1, ex.retVal==null? "OK": ex.retVal);
			output = ex.output;
		}
		catch (MyException ex) {
			ret.set(0, ex.getCode());
			ret.set(1, ex.getMessage());
			ret.add(ex.getDebugInfo());
		}
		catch (Exception ex)
		{
			int code = ex instanceof SQLException? E_DB: E_SERVER;
			ret.set(0, code);
			ret.set(1, GetErrInfo(code));
			if (this.debugLevel > 0) 
			{
				String msg = ex.getMessage();
				if (msg == null)
					msg = ex.getClass().getName();
				this.debugInfo.add(msg);
				List<String> trace = new ArrayList<String>();
				for (StackTraceElement st: ex.getStackTrace()) {
					String cls = st.getClassName();
					if (cls.indexOf(".reflect.") >= 0 || cls.indexOf(".jdbc.") >= 0)
						continue;
					trace.add(String.format("at %s.%s (%s:%d)", st.getClassName(), st.getMethodName(), st.getFileName(), st.getLineNumber()));
					// 最多显示10层
					if (trace.size() >= 10)
						break;
				}
				this.debugInfo.add(trace);
			}
			logit(ex.toString());
			ex.printStackTrace();
		}

		if (useTrans)
			this.endTrans(ok);
		
		String debugLogStr = null;
		if (isDefaultCall) {
			String val = getenv("P_DEBUG_LOG", "0");
			if (val.equals("1") || (val.equals("2") && (int)ret.get(0) != 0)) {
				debugLogStr = "ac=" + ac + ", apiLogId=" + (apiLog!=null?apiLog.id:"null") + ", ret=" + jsonEncode(ret)
					+ ", dbgInfo=" + jsonEncode(this.debugInfo, true);
			}
		}
		if (this.isTestMode && this.debugInfo.size() > 0) {
			ret.add(this.debugInfo);
		}

		this.X_RET = ret;
		if (this.X_RET_FN == null) {
			this.X_RET_STR = jsonEncode(ret, this.isTestMode);
		}
		else {
			try {
				Object ret1 = this.X_RET_FN.call(ret);
				if (Objects.equals(ret1, false)) {
					output = false;
				}
				else {
					this.X_RET_STR = jsonEncode(ret1, this.isTestMode);
				}
			}
			catch (Exception ex) {
				logit(ex.toString());
			}
		}

		if (isDefaultCall) {
			if (apiLog != null)
				apiLog.logAfter();

			this.close();

			if (output) {
				echo(this.X_RET_STR);
			}

			if (debugLogStr != null)
				logit(debugLogStr, true, "debug");
		}
		return ret;
	}

	// ref_ok[0]: 是否提交事务
	private JsArray batchCall(boolean useTrans, boolean[] ref_ok) {
		if (! request.getMethod().equals("POST"))
			throw new MyException(E_PARAM, "batch MUST use `POST' method");

		if (! (this.postData instanceof List))
			throw new MyException(E_PARAM, "bad batch request");

		JsArray ret = new JsArray();
		int retCode = 0;
		@SuppressWarnings("unchecked")
		List<CallCtx> ctxList = (List<CallCtx>)this.postData;
		for (CallCtx ctx : ctxList) {
			if (useTrans && retCode != 0) {
				ret.add(new JsArray(E_ABORT, "事务失败，取消执行", "batch call cancelled."));
				continue;
			}
			if (ctx.ac == null) {
				ret.add(new JsArray(E_PARAM, "参数错误", "bad batch request: require `ac'"));
				continue;
			}

			_GET = ctx.get;
			if (_GET == null)
				_GET = new JsObject();
			_POST = ctx.post;
			if (_POST == null)
				_POST = new JsObject();
			if (ctx.ref != null) {
				handleBatchRef(ctx.ref, ret);
			}

			JsArray rv = callSvcSafe(ctx.ac, !useTrans);

			retCode = (int)rv.get(0);
			ret.add(rv);
			this.debugInfo = new JsArray();
		}
		ref_ok[0] = ! (useTrans && retCode != 0);
		return ret;
	}
	
	public Object tmpEnv(JsObject param, JsObject postParam, Fn<Object> fn) throws Exception
	{
		JsObject[] bak = new JsObject[] { this._GET, this._POST };
		this._GET = param != null? param: new JsObject();
		this._POST = postParam != null? postParam: new JsObject();
		
		Object ret = null;
		try {
			ret = fn.call();
		}
		finally {
			this._GET = bak[0];
			this._POST = bak[1];
		}
		return ret;
	}
	
	public JDApiBase createAC(String table, String ac, String cls, CallInfo callInfo) throws Exception
	{
		if (callInfo == null)
			callInfo = new CallInfo();
		String[] clsNames = null;
		if (cls != null) {
			clsNames = new String[] {cls};
		}
		else if (table == null) {
			clsNames = onCreateApi();
		}
		else if (Objects.equals(this.appType, "admin")) {
			if (hasPerm(AUTH_ADMIN))
				clsNames = new String[] { "AC0_" + table, "AccessControl" };
		}
		else {
			clsNames = onCreateAC(table);
		}
		if (clsNames == null || ! getCallInfo(clsNames, ac, callInfo)) {
			if (table == null || callInfo.cls != null)
				throw new MyException(E_PARAM, "bad ac=`" + ac + "` (no method)", "接口不支持");

			int code = !hasPerm(AUTH_LOGIN) ? E_NOAUTH : E_FORBIDDEN;
			throw new MyException(code, String.format("Operation is not allowed for current user on object `%s`", table));
		}

		JDApiBase api = (JDApiBase)this.onNewInstance(callInfo.cls);
		api.env = this;
		return api;
	}

	public Object callSvc(String ac) throws Exception
	{
		return callSvc(ac, null, null, null);
	}

	static class CallInfo {
		Class<?> cls;
		Method method;
	}
/**<pre>
%fn callSvc(ac)
%fn callSvc(ac, param, postParam, opt={backupEnv, isCleanCall, asAdmin})

调用接口，获得返回值。
如果指定了非空的param, postParam参数，则会并入当前环境的GET, POST参数中。
通过opt参数可调整行为。

%param opt.backupEnv 如果为true, 调用完成后恢复原先的GET, POST参数等。
%param opt.isCleanCall 如果为true，不使用原先环境，只用param, postParam作为GET/POST环境。
%param opt.asAdmin TODO: 以超级管理员权限调用。

	JsObject rv = (JsObject)callSvc("User.get");

它不额外处理事务、不写ApiLog。
*/
	public Object callSvc(String ac, JsObject param, JsObject postParam, CallSvcOpt opt) throws Exception
	{
		if (param!=null || postParam!=null) {
			return tmpEnv(param, postParam, () -> {
				return callSvc(ac);
			});
		}
		Matcher m = regexMatch(ac, "(?U)(\\w+)(?:\\.(\\w+))?$");
		if (!m.find())
			throw new MyException(E_PARAM, "bad ac=`" + ac + "`", "接口不支持");
		String ac1 = null;
		String table = null;
		String methodName = null;
		if (m.group(2) != null)
		{
			table = m.group(1);
			ac1 = m.group(2);
			methodName = "api_" + ac1;
		}
		else
		{
			methodName = "api_" + m.group(1);
		}

		CallInfo callInfo = new CallInfo();
		Object ret = null;
		try {
			JDApiBase api = (JDApiBase)this.createAC(table, methodName, null, callInfo);
			// JDApiBase api = (JDApiBase)this.onNewInstance(callInfo.cls);
			Method mi = callInfo.method;
			if (table == null)
			{
				//ret = mi.invoke(api);
				ret = this.onInvoke(mi, api);
			}
			else if (api instanceof AccessControl)
			{
				AccessControl accessCtl = (AccessControl)api;
				accessCtl.init(table, ac1);
				accessCtl.before();
				Object rv = this.onInvoke(mi, api);
				accessCtl.after(rv);
				ret = rv;
			}
			else
			{
				throw new MyException(E_SERVER, "misconfigured ac=`" + ac + "`");
			}
		}
		catch (Exception e) {
			if (e instanceof InvocationTargetException) {
				Throwable e1 = e;
				do {
					e1 = e1.getCause();
				}
				while (e1 != null && e1 instanceof InvocationTargetException);
				throw (Exception)e1;
			}
			throw e;
		}

		return ret;
	}

	private boolean getCallInfo(String[] clsNames, String methodName, CallInfo ci) {
		for (String clsName: clsNames) {
			Class<?> cls = null;
			try {
				String fullClsName = null;
				if (clsName.indexOf('.') < 0) {
					String pkg = clsName.equals("AccessControl")? "com.jdcloud": this.getClass().getPackage().getName();
					fullClsName = pkg + "." + clsName; 
				}
				else {
					fullClsName = clsName;
				}
				cls = Class.forName(fullClsName);
			}
			catch (ClassNotFoundException ex) {}
			if (cls == null)
				continue;
			ci.cls = cls;

			if (methodName != null) {
				Method method = null;
				try {
					method = cls.getMethod(methodName);
				}catch (NoSuchMethodException e) {}
				if (method == null)
					continue;
				ci.method = method;
			}
			return true;
		}
		return false;
	}

	private void handleBatchRef(String[] ref, JsArray retArr)
	{
		for (String k: ref) {
			if (_GET.containsKey(k)) {
				_GET.put(k, calcRefValue(_GET.get(k).toString(), retArr));
			}
			if (_POST.containsKey(k)) {
				_POST.put(k, calcRefValue(_POST.get(k).toString(), retArr));
			}
		}
	}

	/*
	 * 计算"{$1}", "{$1.id}", "{$-1[0]}", "{$-1[0].id}"
	 * 如果计算错误，则以null填充
	 * retArr为筋斗云接口调用返回值数组，如 [ [0, ret1], [0, ret2], ... ]
	 */
	public static String calcRefValue(String val, JsArray retArr)
	{
		class RetArrayAccess {
			private Object val = retArr;

			private void myAssert(boolean cond) {
				if (! cond) {
					val = null;
					throw new RuntimeException("assert fail");
				}
			}
			@SuppressWarnings("rawtypes")
			void prop(String name) {
				myAssert(val instanceof Map);
				Map m = (Map)val;
				myAssert(m.containsKey(name));
				val = m.get(name);
			}
			// isRoot: true为$引用, 如$1, $-1; false为数组引用，如[0],[1]
			@SuppressWarnings("rawtypes")
			void index(int idx, boolean isRoot) {
				myAssert(val instanceof List);
				List arr = (List)val;
				if (idx < 0) {
					idx = arr.size() + idx;
					myAssert(idx >= 0);
					val = arr.get(idx);
				}
				else {
					if (isRoot) {
						-- idx; // $1 -> [0]
					}
					myAssert(idx < arr.size());
					val = arr.get(idx);
				}
				if (isRoot) {
					myAssert(val instanceof List);
					List val1 = (List)val;
					myAssert(val1.size() >= 2); // && Integer.parseInt(val1.get(0).toString()) == 0);
					val = val1.get(1);
				}
			}
			public String toString() {
				if (val == null)
					return "null";
				if (val instanceof Double) {
					DecimalFormat fmt = new DecimalFormat("#");
					val = fmt.format((Double)val);
				}
				return val.toString();
			}
		}

		String v1 = regexReplace(val, "\\{(.+?)\\}", m -> {
			String expr = m.group(1);
			RetArrayAccess a = new RetArrayAccess(); 
			regexReplace(expr, "\\$(-?\\d+)|\\[(\\d+)\\]|\\.(\\w+)", m1 -> {
				try {
					if (m1.group(1) != null) {
						int idx = Integer.parseInt(m1.group(1));
						a.index(idx, true);
					}
					else if (m1.group(2) != null) {
						int idx = Integer.parseInt(m1.group(2));
						a.index(idx, false);
					}
					else if (m1.group(3) != null) {
						String name = m1.group(3);
						a.prop(name);
					}
				} catch (Exception ex) {
					// bad access
				}
				return "";
			});
			return a.toString();
		});
		// TODO: addLog("### batch ref: `{$val}' -> `{$v1}'");
		return v1;
	}

	public void dbconn() throws Exception
	{
		if (this.conn == null) {
			String dbDriver = props.getProperty("P_DB_DRIVER", "com.mysql.jdbc.Driver");
			String url = props.getProperty("P_DB", "jdbc:mysql://localhost:3306/jdcloud?characterEncoding=utf8");
			
			// 连接池
			if (dbDriver.equalsIgnoreCase("DataSource")) {
				try {
					Context initContext = new InitialContext();
					Context envContext=(Context)initContext.lookup("java:comp/env");
					DataSource ds=(DataSource)envContext.lookup(url); // e.g. "jdbc/jdcloud"
					Connection connection=ds.getConnection();
					this.conn = connection;
					this.onDbconn();
				} catch (NamingException | SQLException e) {
					addLog(e.getMessage());
					throw new MyException(E_DB, "db connection fails", "数据库连接失败。");
				}
				return;
			}
		
			String dbcred = props.getProperty("P_DBCRED");
			if (dbcred == null)
				throw new MyException(E_DB, "P_DBCRED is not set");
			String[] arr = dbcred.split(":");
			String user = arr[0];
			String pwd = arr[1];

			try {
				Class.forName(dbDriver);
			} catch (ClassNotFoundException e) {
				throw new MyException(E_DB, "db driver not found");
			}
			try {
				Properties p = new Properties();
				p.setProperty("characterEncoding", "utf-8");
				p.setProperty("jdbcCompliantTruncation", "false"); // NOTE:不要默认开启strict模式，避免数据长于字段时抛出错误。
				p.setProperty("user", user);
				p.setProperty("password", pwd);
				this.conn = DriverManager.getConnection(url, p);
				this.onDbconn();
			} catch (SQLException e) {
				addLog(e.getMessage());
				throw new MyException(E_DB, "db connection fails", "数据库连接失败。");
			}
		}
	}

	protected void beginTrans() throws SQLException {
		if (this.conn == null)
			return;
		this.conn.setAutoCommit(false);
	}
	protected void endTrans(boolean ok) {
		if (this.conn == null)
			return;
		try {
			// NOT in trans
			if (conn.getAutoCommit())
				return;
			if (ok) {
				this.conn.commit();
			}
			else {
				this.conn.rollback();
			}
			this.conn.setAutoCommit(true);
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	protected Object onNewInstance(Class<?> t) throws Exception
	{
		return t.newInstance();
	}

	protected Object onInvoke(Method mi, Object arg) throws Exception
	{
		return mi.invoke(arg);
	}

/**<pre>
%fn env.onCreateApi() -> String[]

对于函数型调用，返回接口实现类(应继承JDApiBase)列表。默认为"Global"：

	return new String[] { "Global" };

也可以从多个类加载，常用于添加插件，如：

	return new String[] { "Global", "JDLogin", "JDUpload" };

*/
	protected String[] onCreateApi()
	{
		return new String[] { "Global" };
	}

/**<pre>
%fn env.onCreateAC(table) -> String[]

对于对象型调用，根据对象名(table)返回一个类名数组或null(表示无权限或未登录)，用于绑定权限与AC类。注意类名不带包名。
默认逻辑作为示例：

		if (Objects.equals(this.appType, "user")) {
			if (hasPerm(AUTH_USER))
				return new String[] { "AC1_" + table, "AC_" + table };
			return new String[] {"AC_" + table};
		}
		else if (Objects.equals(this.appType, "emp")) {
			if (hasPerm(AUTH_EMP))
				return new String[] { "AC2_" + table };
		}
		return null;

它表示：

- 用户登录(AUTH_USER)尝试AC1和AC类
- 员工登录(AUTH_EMP)尝试AC2类
- 框架自动管理超级管理员登录(AUTH_ADMIN): 会尝试AC0类和AccessControl类。
 */
	protected String[] onCreateAC(String table)
	{
		if (Objects.equals(this.appType, "user")) {
			if (hasPerm(AUTH_USER))
				return new String[] { "AC1_" + table, "AC_" + table };
			return new String[] {"AC_" + table};
		}
		else if (Objects.equals(this.appType, "emp")) {
			if (hasPerm(AUTH_EMP))
				return new String[] { "AC2_" + table };
		}
		return null;
	}

/**<pre>
%fn env.onGetPerms() -> perms/i

返回权限集合。一般根据session来设置。默认检查uid, empId, adminId三个session变量，如果存在则认为具有用户、员工、超级管理员登录权限。

		int perms = 0;
		if (_SESSION("uid") != null) {
			perms |= AUTH_USER;
		}
		else if (_SESSION("empId") != null) {
			perms |= AUTH_EMP;
		}
		else if (_SESSION("adminId") != null) {
			perms |= AUTH_ADMIN;
		}
		return perms;
*/
	protected int onGetPerms()
	{
		int perms = 0;
		if (_SESSION("uid") != null) {
			perms |= AUTH_USER;
		}
		else if (_SESSION("empId") != null) {
			perms |= AUTH_EMP;
		}
		else if (_SESSION("adminId") != null) {
			perms |= AUTH_ADMIN;
		}
		return perms;
	}

/**<pre>
%fn env.onApiInit()

API调用前的回调函数。例如设置选项、检查客户版本等。
示例：关闭ApiLog

	this.props.setProperty("enableApiLog", "0");	

 */
	protected void onApiInit() throws Exception {
	}
	
/**<pre>
%fn env.onDbconn()

连接数据库后回调，用于设置连接选项，或切换数据库。示例：

	@Override
	protected void onDbconn() throws Exception {
		String project = this.ctx.getContextPath();
		if (project.indexOf("-hg") > 0) {
			this.conn.setCatalog("pdi_hg");
		}
	}

 */
	protected void onDbconn() throws Exception {
	}

	// coll: "G"-GET, "P"-POST, null-BOTH
	public Object getParam(String name, String coll) {
		// return this.request.getParameter(name);
		Object val = null;
		if (coll != null) {
			if (coll.equals("G")) 
				val = _GET.get(name);
			else if (coll.equals("P"))
				val = _POST.get(name);
		}
		else {
			val = _GET.get(name);
			if (val == null)
				val = _POST.get(name);
		}
		return val;
	}
	
	public String fixPaging(String sql) {
		return this.dbStrategy.fixPaging(sql);
	}

/**
@var JDEnvBase.skipLogCnt

如果想忽略输出一条SQL日志，可以在调用SQL查询前设置skipLogCnt，如：

	++ env.skipLogCnt; // 若要忽略两条就用 env.skipLogCnt+=2;
	execOne(...);

@see queryAll,execOne,dbconn
 */
	public int skipLogCnt = 0;
	public String getSqlForExec(String sql)
	{
		sql = fixTableName(sql);
		if (this.skipLogCnt <= 0)
			addLog(sql, 9);
		else
			-- this.skipLogCnt;
		return sql;
	}
	
	private String fixTableName(String sql)
	{
		return regexReplace(sql, "(?isx)(?<= (?:UPDATE | FROM | JOIN | INTO) \\s+ )(?:(\\w+)\\.)?(\\w+)", (m) -> {
			if (m.group(1) != null)
				return this.dbStrategy.quoteName(m.group(1)) + "." + this.dbStrategy.quoteName(m.group(2));
			return this.dbStrategy.quoteName(m.group(2));
		});
	}


	public abstract class DbStrategy
	{
		protected JDEnvBase env;

		public void init(JDEnvBase env)
		{
			this.env = env;
		}

		abstract public int getLastInsertId();

		// 处理LIMIT语句，转换成SQL服务器支持的语句
		abstract public String fixPaging(String sql);

		// 表名或字段名转义
		abstract public String quoteName(String s);

		// 在group-by, order-by中允许使用alias
		abstract boolean acceptAliasInOrderBy();
	}

	public class MySQLStrategy extends DbStrategy
	{
		public int getLastInsertId()
		{
			try {
				Object ret = this.env.queryOne("SELECT LAST_INSERT_ID()");
				return (int)ret;
			}
			catch (Exception ex) {
				return 0;
			}
		}

		public String quoteName(String s)
		{
			return "`" + s + "`";
		}

		public String fixPaging(String sql)
		{
			return sql;
		}

		public boolean acceptAliasInOrderBy()
		{
			return true;
		}
	}

	class MsSQLStrategy extends DbStrategy
	{
		public int getLastInsertId()
		{
			try {
				// or use "SELECT @@IDENTITY"
				Object ret = this.env.queryOne("SELECT SCOPE_IDENTITY()");
				return (int)ret;
			}
			catch (Exception ex) {
				return 0;
			}
		}

		public String quoteName(String s)
		{
			return "[" + s + "]";
		}

		public String fixPaging(String sql)
		{
			// addLog(sql, 9); // 原始sql，复杂语句出问题时可打开看
			// for MSSQL: LIMIT -> TOP+ROW_NUMBER
			Matcher m = regexMatch(sql, "(?isx)SELECT(.*) (?: " +
"	LIMIT\\s+(\\d+) " +
"	| (ORDER\\s+BY.*?)\\s*LIMIT\\s+(\\d+),(\\d+)" +
")\\s*$" );

			StringBuffer sb = new StringBuffer();
			while (m.find()) {
				String rep;
				if (m.group(2) != null)
				{
					rep = "SELECT TOP " + m.group(2) + " " + m.group(1);
					m.appendReplacement(sb, rep);
					continue;
				}
				int n1 = Integer.parseInt(m.group(4))+1;
				int n2 = n1+Integer.parseInt(m.group(5))-1;
				rep = String.format("SELECT * FROM (SELECT ROW_NUMBER() OVER(%s) _row, %s) t0 WHERE _row BETWEEN %s AND %s",
					m.group(3), m.group(1), n1, n2);
				m.appendReplacement(sb, rep);
			}
			m.appendTail(sb);
			return sb.toString();
		}

		public boolean acceptAliasInOrderBy()
		{
			return false;
		}
	}

	class ApiLog
	{
		long startTm;
		String ac;
		int id;

		public ApiLog(String ac) {
			this.ac = ac;
		}

		// var: String/JsObject
		private String myVarExport(Object var, int maxLength)
		{
			if (var instanceof String) {
				String var1 = regexReplace((String)var, "\\s+", " ");
				if (var1.length() > maxLength)
					var1 = var1.substring(0, maxLength) + "...";
				return var1;
			}
			if (var instanceof JsObject) {
				JsObject var1 = (JsObject)var;
				StringBuffer sb = new StringBuffer();
				int maxKeyLen = 30;
				for (Map.Entry<String, Object> e: var1.entrySet()) {
					String k = e.getKey();
					Object v = e.getValue();
					int klen = k.length();
					// 注意：有时raw http内容被误当作url-encoded编码，会导致所有内容成为key. 例如API upload.
					if (klen > maxKeyLen)
						return k.substring(0, maxKeyLen) + "...";
					int len = sb.length();
					if (len >= maxLength) {
						sb.append(k).append("=...");
						break;
					}
					if (k.contains("pwd")) {
						v = "?";
					}
					if (len > 0) {
						sb.append(", ");
					}
					sb.append(k).append("=").append(v);
				}
				return sb.toString();
			}
			return var.toString();
		}

		void logBefore()
		{
			try {
				this.startTm = time();
		
				String type = appType;
				Object userId = null;
				// TODO: hard code
				if (type.equals("user")) {
					userId = _SESSION("uid");
				}
				else if (type.equals("emp")) {
					userId = _SESSION("empId");
				}
				else if (type.equals("admin")) {
					userId = _SESSION("adminId");
				}
				if (userId == null)
					userId = "NULL";
				String content = myVarExport(_GET, 2000);
				String content2 = null;
				String ct = request.getContentType();
				if (ct != null)
					ct = ct.toLowerCase();
				if (!_POST.isEmpty()) {
					content2 = myVarExport(_POST, 2000);
				}
				if (content2 != null && content2.length() > 0)
					content += ";\n" + content2;
				String remoteAddr = request.getRemoteAddr();
				int reqsz = request.getRequestURI().length() + request.getContentLength();
				String query = request.getQueryString();
				if (query != null)
					reqsz += query.length();
		
				String ua = request.getHeader("User-Agent");
		
				String sql = String.format("INSERT INTO ApiLog (tm, addr, ua, app, ses, userId, ac, req, reqsz, ver) VALUES ('%s', %s, %s, %s, %s, %s, %s, %s, %s, %s)", 
					date(), Q(remoteAddr), Q(ua), Q(appName), 
					Q(request.getRequestedSessionId()), userId, Q(this.ac), Q(content), reqsz, Q(clientVer)
				);
				++ skipLogCnt;
				this.id = execOne(sql, true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		void logAfter()
		{
			if (conn == null)
				return;
			try {
				long iv = time() - this.startTm;
				String content = myVarExport(X_RET_STR, 200);
		
				String userIdStr = "";
				if (this.ac.equals("login") && X_RET.get(1) instanceof JsObject) {
					userIdStr = ", userId=" + ((JsObject)X_RET.get(1)).get("id");
				}
				String sql = String.format("UPDATE ApiLog SET t=%d, retval=%d, ressz=%d, res=%s %s WHERE id=%s", 
						iv, X_RET.get(0), X_RET_STR.length(), Q(content), userIdStr, this.id);

				++ skipLogCnt;
				@SuppressWarnings("unused")
				int rv = execOne(sql);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void close() {
		safeClose(this.conn);
		this.conn = null;
	}
}
