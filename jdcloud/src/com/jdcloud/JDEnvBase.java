package com.jdcloud;
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

	public boolean isTestMode = false;
	public int debugLevel = 0;
	public JsArray debugInfo = new JsArray();
	public String appName, appType;
	
	public JDApiBase api = new JDApiBase();

	public Connection conn;
	public HttpServletRequest request;
	public HttpServletResponse response;
	public JsObject _GET, _POST;

	public String dbType = "mysql";
	public DbStrategy dbStrategy;
	protected Properties props;
	
	public void init(HttpServletRequest request, HttpServletResponse response, Properties props) throws Exception
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
			Map m = new Gson().fromJson(this.request.getReader(), Map.class);
			if (m != null)
				_POST.putAll(m);
		}

		this.isTestMode = JDApiBase.parseBoolean(props.getProperty("P_TEST_MODE", "0"));
		this.debugLevel = Integer.parseInt(props.getProperty("P_DEBUG", "0"));
		this.dbType = props.getProperty("P_DBTYPE", "mysql");

		this.appName = (String)api.param("_app", "user", "G");
		this.appType = this.appName.replaceFirst("(\\d+|-\\w+)$", "");

		if (this.dbType.equals("mysql"))
			this.dbStrategy = new MySQLStrategy();
		else if (this.dbType.equals("mssql"))
			this.dbStrategy = new MsSQLStrategy();
		else
			throw new MyException(JDApiBase.E_SERVER, "bad dbType=`" + this.dbType + "` in web.properties");
		
		this.dbStrategy.init(this);

		if (this.isTestMode)
		{
			api.header("X-Daca-Test-Mode", "1");
		}
		// TODO: X-Daca-Mock-Mode, X-Daca-Server-Rev
	}
	
	public Object callSvc(String ac) throws Exception
	{
		return callSvc(ac, null, null, null);
	}

	// TODO: asAdmin
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
		String clsName = null;
		String methodName = null;
		if (m.group(2) != null)
		{
			table = m.group(1);
			ac1 = m.group(2);
			clsName = onCreateAC(table);
			methodName = "api_" + ac1;
		}
		else
		{
			clsName = "Global";
			methodName = "api_" + m.group(1);
		}

		JDApiBase api = null;
		Class<?> t;
		Method mi = null;
		Object ret = null;
		try {
			String pkg = this.getClass().getPackage().getName(); 
			t = Class.forName(pkg + "." + clsName); // JDApi

			api = this.onGetApi(t);
			//api = (JDApiBase)t.newInstance();
			api.env = this;
			mi = api.getClass().getMethod(methodName);
			if (clsName == "Global")
			{
				//ret = mi.invoke(api);
				ret = this.onInvoke(mi, api);
			}
			else if (api instanceof AccessControl)
			{
				AccessControl accessCtl = (AccessControl)api;
				accessCtl.init(table, ac1);
				accessCtl.before();
				// Object rv = mi.invoke(api);
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

		} catch (ClassNotFoundException e) {
			if (table == null)
				throw new MyException(JDApiBase.E_PARAM, "bad ac=`" + ac + "` (no Global)");

			int code = clsName.startsWith("AC_") ? JDApiBase.E_NOAUTH : JDApiBase.E_FORBIDDEN;
			throw new MyException(code, String.format("Operation is not allowed for current user on object `%s`", table));
		}
		catch (Exception e) {
			if (mi == null)
				throw new MyException(JDApiBase.E_PARAM, "bad ac=`" + ac + "` (no method)");
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
				// start transaction
				this.conn.setAutoCommit(false);
			} catch (SQLException e) {
				api.addLog(e.getMessage());
				throw new MyException(JDApiBase.E_DB, "db connection fails", "数据库连接失败。");
			}
		}
	}
	
	public void close(boolean ok)
	{
		if (this.conn == null)
			return;

		
		try {
			if (ok) {
				this.conn.commit();
			}
			else {
				this.conn.rollback();
			}
			this.conn.setAutoCommit(false);
			this.conn.close();
			this.conn = null;
		}
		catch (SQLException e) {
		}
	}
	
	public JDApiBase onGetApi(Class<?> t) throws Exception
	{
		JDApiBase api = (JDApiBase)t.newInstance();
		return api;
	}

	public Object onInvoke(Method mi, JDApiBase api) throws Exception
	{
		return mi.invoke(api);
	}

	public String onCreateAC(String table)
	{
		return "AC_" + table;
	}

	public int onGetPerms()
	{
		return 0;
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
