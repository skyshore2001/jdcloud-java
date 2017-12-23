package com.jdcloud;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.*;
import javax.servlet.http.*;
import com.google.gson.*;

public class JDEnvBase
{
	public class CallSvcOpt
	{
		public boolean backupEnv = false;
		public boolean isCleanCall = false;
		public boolean asAdmin = false;
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
	
	// 用于内部调用JDApiBase的工具函数
	protected JDApiBase api = new JDApiBase();

	public Connection conn;
	public HttpServletRequest request;
	public HttpServletResponse response;
	public JsObject _GET, _POST;

	public String dbType = "mysql";
	public DbStrategy dbStrategy;

/**<pre>
%var env.baseDir

应用程序的主目录，用于写文件。默认为 {user.home}/jd-data/{project}.
 */
// TODO: use static
	public String baseDir;
	public Properties props;

	public String X_RET_STR;
	public JsArray X_RET;

	private void init(HttpServletRequest request, HttpServletResponse response, Properties props) throws Exception
	{
		this.request = request;
		this.response = response;
		this.props = props;
		
		this.api = new JDApiBase();
		this.api.env = this;
		
		this._GET = new JsObject();
		this._POST = new JsObject();
		String q = this.request.getQueryString();
		if (q != null) {
			Set<String> set = new HashSet<String>();
			for (String q1 : q.split("&")) {
				String[] kv = q1.split("=");
				set.add(kv[0]);
			}
			Enumeration<String> em = this.request.getParameterNames();
			while (em.hasMoreElements()) {
				String k = em.nextElement();
				if (set.contains(k))
					this._GET.put(k, request.getParameter(k));
				else
					this._POST.put(k, request.getParameter(k));
			}
		}
		// 支持POST为json格式
		if (this.request.getContentType() != null && this.request.getContentType().indexOf("/json") > 0) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>)new Gson().fromJson(this.request.getReader(), Map.class);
			if (m != null)
				_POST.putAll(m);
		}

		this.baseDir = System.getProperty("user.home") + "/jd-data/" + request.getContextPath();
		new File(this.baseDir).mkdirs();
		
		this.isTestMode = JDApiBase.parseBoolean(props.getProperty("P_TEST_MODE", "0"));
		this.debugLevel = Integer.parseInt(props.getProperty("P_DEBUG", "0"));
		this.dbType = props.getProperty("P_DBTYPE", "mysql");

		this.appName = (String)api.param("_app", "user", "G");
		this.appType = this.appName.replaceFirst("(\\d+|-\\w+)$", "");

		this.clientVer = (String)api.param("_ver", null, "G");
		if (this.clientVer == null) {
			// Mozilla/5.0 (Linux; U; Android 4.1.1; zh-cn; MI 2S Build/JRO03L) AppleWebKit/533.1 (KHTML, like Gecko)Version/4.0 MQQBrowser/5.4 TBS/025440 Mobile Safari/533.1 MicroMessenger/6.2.5.50_r0e62591.621 NetType/WIFI Language/zh_CN
			Matcher m;
			if ((m=JDApiBase.regexMatch(this.request.getHeader("User-Agent"), "MicroMessenger\\/([0-9.]+)")).find()) {
				this.clientVer = "wx/" + m.group(1);
			}
			else {
				this.clientVer = "web";
			}
		}

		if (this.dbType.equals("mysql"))
			this.dbStrategy = new MySQLStrategy();
		else if (this.dbType.equals("mssql"))
			this.dbStrategy = new MsSQLStrategy();
		else
			throw new MyException(JDApiBase.E_SERVER, "bad dbType=`" + this.dbType + "` in web.properties");
		
		this.dbStrategy.init(this);
	}
	
	public void service(HttpServletRequest request, HttpServletResponse response, Properties props) {
		JsArray ret = new JsArray(0, null);
		boolean ok = false;
		boolean dret = false;
		ApiLog apiLog = null;
		try {
			init(request, response, props);
			String origin = request.getHeader("Origin");
			if (origin != null)
			{
				response.setHeader("Access-Control-Allow-Origin", origin);
				response.setHeader("Access-Control-Allow-Credentials", "true");
				response.setHeader("Access-Control-Allow-Headers", "Content-Type");
			}

			if (request.getMethod().equals("OPTIONS"))
				return;

			response.setContentType("text/plain; charset=utf-8");
			if (this.isTestMode)
			{
				api.header("X-Daca-Test-Mode", "1");
			}
			// TODO: X-Daca-Mock-Mode, X-Daca-Server-Rev

			this.onApiInit();

			Pattern re = Pattern.compile("([\\w|.]+)$");
			Matcher m = re.matcher(request.getPathInfo());
			if (! m.find()) {
				throw new MyException(JDApiBase.E_PARAM, "bad ac");
			}
			String ac = m.group(1);

			if (JDApiBase.parseBoolean(props.getProperty("enableApiLog", "1"))) {
				apiLog = new ApiLog(this, ac);
				apiLog.logBefore();
			}

			this.beginTrans();
			Object rv = this.callSvc(ac);
			if (rv == null)
				rv = "OK";
			ok = true;
			ret.set(1, rv);
		}
		catch (DirectReturn ex) {
			ok = true;
			if (ex.retVal != null) {
				ret.set(1, ex.retVal);
			}
			else {
				dret = true;
			}
		}
		catch (MyException ex) {
			ret.set(0, ex.getCode());
			ret.set(1, ex.getMessage());
			ret.add(ex.getDebugInfo());
		}
		catch (Exception ex)
		{
			int code = ex instanceof SQLException? JDApiBase.E_DB: JDApiBase.E_SERVER;
			ret.set(0, code);
			ret.set(1, JDApiBase.GetErrInfo(code));
			if (this.isTestMode) 
			{
				String msg = ex.getMessage();
				if (msg == null)
					msg = ex.getClass().getName();
				ret.add(msg);
				ret.add(ex.getStackTrace());
			}
			ex.printStackTrace();
		}

		this.endTrans(ok);
		if (this.debugInfo.size() > 0) {
			ret.add(this.debugInfo);
		}

		this.X_RET_STR = api.jsonEncode(ret, this.isTestMode);;
		this.X_RET = ret;

		if (apiLog != null)
			apiLog.logAfter();
		try {
			if (this.conn != null)
				this.conn.close();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
		this.conn = null;

		if (! dret) {
			api.echo(this.X_RET_STR);
		}
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
		JsObject[] bak = null;
		if (opt != null)
		{
			if (opt.backupEnv) {
				bak = new JsObject[] { this._GET, this._POST };
			}
			if (opt.isCleanCall) {
				this._GET = new JsObject();
				this._POST = new JsObject();
			}
		}
		if (param != null)
		{
			for (Map.Entry<String, Object> kv : param.entrySet())
			{
				this._GET.put(kv.getKey(), kv.getValue());
			}
		}
		if (postParam != null) {
			for (Map.Entry<String, Object> kv : postParam.entrySet()) {
				this._POST.put(kv.getKey(), kv.getValue());
			}
		}

		Matcher m = Pattern.compile("(\\w+)(?:\\.(\\w+))?$").matcher(ac);
		m.find();
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
			String[] clsNames = table==null? onCreateApi(): onCreateAC(table);
			if (! getCallInfo(clsNames, methodName, callInfo)) {
				if (table == null || callInfo.cls != null)
					throw new MyException(JDApiBase.E_PARAM, "bad ac=`" + ac + "` (no method)");

				int code = !this.api.hasPerm(JDApiBase.AUTH_LOGIN) ? JDApiBase.E_NOAUTH : JDApiBase.E_FORBIDDEN;
				throw new MyException(code, String.format("Operation is not allowed for current user on object `%s`", table));
			}

			JDApiBase api = (JDApiBase)this.onNewInstance(callInfo.cls);
			api.env = this;
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
				throw new MyException(JDApiBase.E_SERVER, "misconfigured ac=`" + ac + "`");
			}
			if (ret == null)
				ret = "OK";
		}
		catch (Exception e) {
			if (e instanceof InvocationTargetException && e.getCause() != null)
				throw (Exception)e.getCause();
			throw e;
		}
		finally {
			if (bak != null)
			{
				this._GET = bak[0];
				this._POST = bak[1];
			}
		}

		return ret;
	}

	private boolean getCallInfo(String[] clsNames, String methodName, CallInfo ci) {
		for (String clsName: clsNames) {
			Class<?> cls = null;
			try {
				String fullClsName = this.getClass().getPackage().getName() + "." + clsName; 
				cls = Class.forName(fullClsName);
			}
			catch (ClassNotFoundException ex) {}
			if (cls == null)
				continue;
			ci.cls = cls;

			Method method = null;
			try {
				method = cls.getMethod(methodName);
			}catch (NoSuchMethodException e) {}
			if (method == null)
				continue;
			ci.method = method;
			return true;
		}
		return false;
	}

	public void dbconn() throws MyException
	{
		if (this.conn == null) {
			String dbDriver = props.getProperty("P_DB_DRIVER", "com.mysql.jdbc.Driver");
			String url = props.getProperty("P_DB", "jdbc:mysql://localhost:3306/jdcloud?characterEncoding=utf8");
			String dbcred = props.getProperty("P_DBCRED", "");
			String[] arr = dbcred.split(":");
			String user = arr[0];
			String pwd = arr[1];

			try {
				Class.forName(dbDriver);
			} catch (ClassNotFoundException e) {
				throw new MyException(JDApiBase.E_DB, "db driver not found");
			}
			try {
				this.conn = DriverManager.getConnection(url, user, pwd);
			} catch (SQLException e) {
				api.addLog(e.getMessage());
				throw new MyException(JDApiBase.E_DB, "db connection fails", "数据库连接失败。");
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
			if (ok) {
				this.conn.commit();
			}
			else {
				this.conn.rollback();
			}
			this.conn.setAutoCommit(true);
		}
		catch (SQLException e) {
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

	protected String[] onCreateApi()
	{
		return new String[] { "Global" };
	}

	protected String[] onCreateAC(String table)
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

	protected int onGetPerms()
	{
		int perms = 0;
		if (api.getSession("uid") != null) {
			perms |= JDApiBase.AUTH_USER;
		}
		else if (api.getSession("empId") != null) {
			perms |= JDApiBase.AUTH_EMP;
		}
		else if (api.getSession("adminId") != null) {
			perms |= JDApiBase.AUTH_ADMIN;
		}
		return perms;
	}

/**<pre>
%fn env.onApiInit()

API调用前的回调函数。例如设置选项、检查客户版本等。
示例：关闭ApiLog

	this.props.setProperty("enableApiLog", "0");	

 */
	protected void onApiInit() {
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

	public String getSqlForExec(String sql)
	{
		sql = fixTableName(sql);
		api.addLog(sql, 9);
		return sql;
	}
	
	private String fixTableName(String sql)
	{
		String q = this.dbStrategy.quoteName("$1");
		Matcher m = JDApiBase.regexMatch(sql, "(?isx)(?<= (?:UPDATE | FROM | JOIN | INTO) \\s+ )([\\w|.]+)");
		return m.replaceAll(q);
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
				Object ret = this.env.api.queryOne("SELECT LAST_INSERT_ID()");
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
				Object ret = this.env.api.queryOne("SELECT SCOPE_IDENTITY()");
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
			// api.addLog(sql, 9); // 原始sql，复杂语句出问题时可打开看
			// for MSSQL: LIMIT -> TOP+ROW_NUMBER
			Matcher m = JDApiBase.regexMatch(sql, "(?isx)SELECT(.*) (?: " +
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
}

class ApiLog
{
	private JDEnvBase env;
	private JDApiBase api;
	long startTm;
	String ac;
	int id;

	public ApiLog(JDEnvBase env, String ac) {
		this.env = env;
		this.api = env.api;
		this.ac = ac;
	}

	// var: String/JsObject
	private String myVarExport(Object var, int maxLength)
	{
		if (var instanceof String) {
			String var1 = JDApiBase.regexReplace((String)var, "\\s+", " ");
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
			this.startTm = env.api.time();
	
			String type = env.appType;
			Object userId = null;
			// TODO: hard code
			if (type.equals("user")) {
				userId = api.getSession("uid");
			}
			else if (type.equals("emp")) {
				userId = api.getSession("empId");
			}
			else if (type.equals("admin")) {
				userId = api.getSession("adminId");
			}
			if (userId == null)
				userId = "NULL";
			String content = myVarExport(env._GET, 2000);
			String content2 = null;
			String ct = env.request.getContentType();
			if (ct != null)
				ct = ct.toLowerCase();
			if (!env._POST.isEmpty()) {
				content2 = myVarExport(env._POST, 2000);
			}
			if (content2 != null && content2.length() > 0)
				content += ";\n" + content2;
			String remoteAddr = env.request.getRemoteAddr();
			int reqsz = env.request.getRequestURI().length() + env.request.getContentLength();
			String query = env.request.getQueryString();
			if (query != null)
				reqsz += query.length();
	
			String ua = env.request.getHeader("User-Agent");
	
			String sql = String.format("INSERT INTO ApiLog (tm, addr, ua, app, ses, userId, ac, req, reqsz, ver) VALUES ('%s', %s, %s, %s, %s, %s, %s, %s, %s, %s)", 
				api.date(), JDApiBase.Q(remoteAddr), JDApiBase.Q(ua), JDApiBase.Q(env.appName), 
				JDApiBase.Q(env.request.getRequestedSessionId()), userId, JDApiBase.Q(this.ac), JDApiBase.Q(content), reqsz, JDApiBase.Q(env.clientVer)
			);
			this.id = api.execOne(sql, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void logAfter()
	{
		if (env.conn == null)
			return;
		try {
			long iv = api.time() - this.startTm;
			String content = myVarExport(env.X_RET_STR, 200);
	
			String userIdStr = "";
			if (this.ac.equals("login") && env.X_RET.get(1) instanceof JsObject) {
				userIdStr = ", userId=" + ((JsObject)env.X_RET.get(1)).get("id");
			}
			String sql = String.format("UPDATE ApiLog SET t=%d, retval=%d, ressz=%d, res=%s %s WHERE id=%s", 
					iv, env.X_RET.get(0), env.X_RET_STR.length(), JDApiBase.Q(content), userIdStr, this.id);

			@SuppressWarnings("unused")
			int rv = api.execOne(sql);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
