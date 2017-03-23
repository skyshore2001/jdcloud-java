package com.jdcloud;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.*;
import javax.servlet.http.*;

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
	
	public void init(HttpServletRequest request, HttpServletResponse response)
	{
		this.request = request;
		this.response = response;
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
		
		try {
			Properties props = new Properties();
			InputStream is = request.getServletContext().getResourceAsStream("web.properties");
			props.load(is);
			this.isTestMode = JDApiBase.parseBoolean(props.getProperty("P_TEST_MODE"));
			this.debugLevel = Integer.parseInt(props.getProperty("P_DEBUG", "9"));
		} catch (Exception e) {
			// TODO
			this.isTestMode = true;
			this.debugLevel = 9;
		}
		this.appName = (String)api.param("_app", "user", "G");
		this.appType = this.appName.replaceFirst("(\\d+|-\\w+)$", "");

		if (this.isTestMode)
		{
			api.header("X-Daca-Test-Mode", "1");
		}
		// TODO: X-Daca-Mock-Mode, X-Daca-Server-Rev
	}
	
	public Object callSvc(String ac) throws Throwable
	{
		return callSvc(ac, null, null, null);
	}

	// TODO: asAdmin
	public Object callSvc(String ac, JsObject param, JsObject postParam, CallSvcOpt opt) throws Throwable
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
			if (param != null)
			{
				for (Entry<String, Object> kv : param.entrySet())
				{
					this._GET.put(kv.getKey(), kv.getValue());
				}
			}
			if (postParam != null) {
				for (Entry<String, Object> kv : postParam.entrySet()) {
					this._POST.put(kv.getKey(), kv.getValue());
				}
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
			t = Class.forName("com.jdcloud." + clsName); // JDApi
			api = (JDApiBase)t.newInstance();
			api.env = this;
			mi = t.getMethod(methodName);
			if (clsName == "Global")
			{
				ret = mi.invoke(api);
			}
			else if (api instanceof AccessControl)
			{
				AccessControl accessCtl = (AccessControl)api;
				accessCtl.init(table, ac1);
				accessCtl.before();
				Object rv = mi.invoke(api);
				//ret[1] = t.InvokeMember(methodName, BindingFlags.InvokeMethod, null, api, null);
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
		catch (Throwable e) {
			if (mi == null)
				throw new MyException(JDApiBase.E_PARAM, "bad ac=`" + ac + "` (no method)");
			if (e instanceof InvocationTargetException && e.getCause() != null)
				throw e.getCause();
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
			String connStr = "jdbc:mysql://oliveche.com:3306/jdcloud2";
			try {
				Class.forName("com.mysql.jdbc.Driver");
			} catch (ClassNotFoundException e) {
				throw new MyException(JDApiBase.E_DB, "db driver not found");
			}
			String user = "demo";
			String pwd = "tuuj7PNDC";
			try {
				this.conn = DriverManager.getConnection(connStr, user, pwd);
				this.conn.setAutoCommit(false);
			} catch (SQLException e) {
				throw new MyException(JDApiBase.E_DB, "db connection fails", "数据库连接失败。");
			}
			/*
			var dbType = ConfigurationManager.AppSettings["P_DBTYPE"];
			var connSetting = ConfigurationManager.ConnectionStrings["default"];
			if (connSetting == null)
				throw new MyException(JDApiBase.E_SERVER, "No db connectionString defined in web.config");

			cnn_ = new DbConn();
			cnn_.onExecSql += new DbConn.OnExecSql(delegate(string sql)
			{
				api.addLog(sql, 9);
			});
			cnn_.Open(connSetting.ConnectionString, connSetting.ProviderName, dbType);
			cnn_.BeginTransaction();
			*/
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
/* TODO support multiple database like mssql */
		return sql;
	}
}
