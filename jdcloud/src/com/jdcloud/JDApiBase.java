package com.jdcloud;

import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JDApiBase
{
	public static final int E_ABORT = -100;
	public static final int E_AUTHFAIL = -1;
	public static final int E_OK = 0;
	public static final int E_PARAM = 1;
	public static final int E_NOAUTH = 2;
	public static final int E_DB = 3;
	public static final int E_SERVER = 4;
	public static final int E_FORBIDDEN = 5;

	public static final int PAGE_SZ_LIMIT = 10000;

	// 登录类型定义：
	public static final int AUTH_USER = 0x1;
	public static final int AUTH_EMP = 0x2;
	// 支持8种登录类型 0x1-0x80; 其它权限应从0x100开始定义。
	public static final int AUTH_LOGIN = 0xff; 

	public JDEnvBase env;
	
	/*
	public NameValueCollection _GET 
	{
		get { return env._GET;}
	}
	public NameValueCollection _POST
	{
		get { return env._POST;}
	}
	public NameValueCollection _SERVER
	{
		get { return env.ctx.Request.ServerVariables;}
	}
	public HttpSessionState _SESSION
	{
		get { return env.ctx.Session;}
	}
	*/

	public static final Map<Integer, String> ERRINFO = new HashMap<Integer, String>(){{
		put(E_AUTHFAIL, "认证失败");
		put(E_PARAM, "参数不正确");
		put(E_NOAUTH, "未登录");
		put(E_DB, "数据库错误");
		put(E_SERVER, "服务器错误");
		put(E_FORBIDDEN, "禁止操作");
	}};

	public static String GetErrInfo(int code)
	{
		if (ERRINFO.containsKey(code))
			return ERRINFO.get(code);
		return "未知错误";
	}
	
	public void dbconn() throws MyException
	{
		if (this.env.conn == null) {
			String connStr = "jdbc:mysql://oliveche.com:3306/jdcloud2";
			try {
				Class.forName("com.mysql.jdbc.Driver");
			} catch (ClassNotFoundException e) {
				throw new MyException(E_DB, "db driver not found");
			}
			String user = "demo";
			String pwd = "tuuj7PNDC";
			try {
				this.env.conn = DriverManager.getConnection(connStr, user, pwd);
			} catch (SQLException e) {
				throw new MyException(E_DB, "db connection fails", "数据库连接失败。");
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
	public void close() throws SQLException
	{
	/*
		if (cnn_ != null)
		{
			if (ok)
				cnn_.Commit();
			else
				cnn_.Rollback();
			cnn_.Dispose();
		}
		*/
		env.conn.close();
	}
	public static String Q(String s)
	{
		return "'" + s.replace("'", "\\'") + "'";
	}
	
	public JsArray queryAll(String sql) throws SQLException, MyException
	{
		return this.queryAll(sql, false);
	}
	public JsArray queryAll(String sql, boolean assoc) throws SQLException, MyException
	{
		dbconn();
		Statement stmt = env.conn.createStatement();
		ResultSet rs = stmt.executeQuery(sql);
		ResultSetMetaData md = rs.getMetaData();
		JsArray ret = new JsArray();
		if (assoc) {
			while (rs.next()) {
				JsObject row = new JsObject();
				for (int i=0; i<md.getColumnCount(); ++i) {
					row.put(md.getColumnName(i+1), rs.getObject(i+1));
				}
				ret.add(row);
			}
		}
		else {
			while (rs.next()) {
				JsArray row = new JsArray();
				for (int i=0; i<md.getColumnCount(); ++i) {
					row.add(rs.getObject(i+1));
				}
				ret.add(row);
			}
		}
		rs.close();
		stmt.close();
		return ret;
	}
	
	public Object queryOne(String sql) throws SQLException, MyException
	{
		return this.queryOne(sql, false);
	}
	public Object queryOne(String sql, boolean assoc) throws SQLException, MyException
	{
		dbconn();
		Statement stmt = env.conn.createStatement();
		ResultSet rs = stmt.executeQuery(sql);
		ResultSetMetaData md = rs.getMetaData();
		if (! rs.next()) {
			rs.close();
			stmt.close();
			return false;
		}
		Object ret;
		if (assoc) {
			JsObject row = new JsObject();
			for (int i=0; i<md.getColumnCount(); ++i) {
				row.put(md.getColumnName(i+1), rs.getObject(i+1));
			}
			ret = row;
		}
		else {
			JsArray row = new JsArray();
			for (int i=0; i<md.getColumnCount(); ++i) {
				row.add(rs.getObject(i+1));
			}
			if (row.size() == 1)
				ret = row.get(0);
			else
				ret = row;
		}
		rs.close();
		stmt.close();
		return ret;
	}
	
	public int execOne(String sql, boolean getNewId) throws SQLException, MyException
	{
		dbconn();
		Statement stmt = env.conn.createStatement();
		int rv = stmt.executeUpdate(sql, getNewId? Statement.RETURN_GENERATED_KEYS: Statement.NO_GENERATED_KEYS);
		if (getNewId) {
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next())
				rv = rs.getInt(1);
		}
		return rv;
	}

	public String jsonEncode(Object o)
	{
		return jsonEncode(o, false);
	}
	public String jsonEncode(Object o, boolean doFormat)
	{
		GsonBuilder gb = new GsonBuilder();
		gb.serializeNulls().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss");
		if (doFormat)
			gb.setPrettyPrinting();
		Gson gson = gb.create();
		return gson.toJson(o);
	}
	
	static final JsObject htmlEntityMapping = new JsObject(
		"<", "&lt;",
		">", "&gt;",
		"&", "&amp;"
	);
	public String htmlEscape(String s)
	{
		StringBuffer sb = new StringBuffer();
		Matcher m = Pattern.compile("<|>|&").matcher(s);
		while (m.find()) {
			m.appendReplacement(sb, (String)htmlEntityMapping.get(m.group(0)));
		}
		m.appendTail(sb);
		return sb.toString();
		//return StringEscapeUtils.unescapeHtml();
	}


	public static boolean parseBoolean(String s)
	{
		boolean val = false;
		if (s == null)
		{
			return val;
		}
		s = s.toLowerCase();
		if (s.equals("0") || s.equals("false") || s.equals("off") || s.equals("no"))
			val = false;
		else if (s.equals("1") || s.equals("true") || s.equals("on") || s.equals("yes"))
			val = true;
		else
			throw new NumberFormatException();
		return val;
	}

	// "2010/1/1 10:10", "2011-2-1 8:8:8", "2010.3.4", "2011-02-01T10:10:10Z"
	// return null if fails
	public static java.util.Date parseDate(String s) {
		String fmt;
		//s = "2010-10-10T10:10:10Z";
		if (s.indexOf('T') > 0) {
			if (s.endsWith("Z")) {
				s = s.replaceFirst("Z$", "+0000");
			}
			if (s.indexOf('.') > 0)
				fmt= "yyyy-MM-dd'T'HH:mm:ss.SSSz";
			else
				fmt= "yyyy-MM-dd'T'HH:mm:ssz";
		}
		else {
			s = s.replaceAll("[./]", "-");
			String[] s1 = s.split(":");
			if (s1.length == 1)
				fmt = "yyyy-MM-dd";
			else if (s1.length == 2)
				fmt = "yyyy-MM-dd HH:mm";
			else
				fmt = "yyyy-MM-dd HH:mm:ss";
		}
		
		DateFormat ofmt = new SimpleDateFormat(fmt);
		java.util.Date dt = null;
		try {
			dt = ofmt.parse(s);
		} catch (ParseException e) {
		}
		return dt;
	}

	// retrun: [type, name]
	private String[] parseType(String name)
	{
		String type = null;
		int n;
		if ((n=name.indexOf('/')) >= 0)
		{
			type = name.substring(n+1);
			name = name.substring(0, n);
		}
		else {
			if (name.equals("id") || name.endsWith("Id")) {
				type = "i";
			}
			else {
				type = "s";
			}
		}
		return new String[] {type, name};
	}

	public Object param(String name) {
		return param(name, null);
	}
	public Object param(String name, Object defVal) {
		return param(name, defVal, null);
	}
	public Object param(String name, Object defVal, String coll) {
		return param(name, defVal, coll, true);
	}
	// defVal?=null, coll?=null, doHtmlEscape?=true
	public Object param(String name, Object defVal, String coll, boolean doHtmlEscape) {
		String[] a = parseType(name);
		String type = a[0];
		name = a[1];
		Object ret = null;
		
		String val = env.getParam(name, coll);
		/*
		if (coll == null || coll == "G")
			val = _GET[name];
		if ((val == null && coll == null) || coll == "P")
			val = _POST[name];
			*/
		if (val == null && defVal != null)
			return defVal;

		if (val != null) 
		{
			if (type.equals("s"))
			{
				// avoid XSS attack
				if (doHtmlEscape)
					ret = htmlEscape(val);
				else
					ret = val;
			}
			else if (type.equals("i"))
			{
				try {
					int i = Integer.parseInt(val);
					ret = i;
				} catch (NumberFormatException ex) {
					throw new MyException(E_PARAM, String.format("Bad Request - integer param `%s`=`%s`.", name, val));
				}
			}
			else if (type.equals("n"))
			{
				try {
					double n = Double.parseDouble(val);
					ret = n;
				} catch (NumberFormatException ex) {
					throw new MyException(E_PARAM, String.format("Bad Request - numeric param `%s`=`%s`.", name, val));
				}
			}
			else if (type.equals("b"))
			{
				try {
					boolean b = parseBoolean(val);
					ret = b;
				} catch (NumberFormatException ex) {
					throw new MyException(E_PARAM, String.format("Bad Request - bool param `%s`=`%s`.", name, val));
				}
			}
			else if (type.equals("i+"))
			{
				ArrayList<Integer> arr = new ArrayList<Integer>();
				for (String e : val.split(","))
				{
					try {
						arr.add(Integer.parseInt(e));
					}
					catch (NumberFormatException ex) {
						throw new MyException(E_PARAM, String.format("Bad Request - int array param `%s` contains `%s`.", name, e));
					}
				}
				if (arr.size() == 0)
					throw new MyException(E_PARAM, String.format("Bad Request - int array param `%s` is empty.", name));
				ret = arr;
			}
			else if (type.equals("dt") || type.equals("tm"))
			{
				java.util.Date dt = parseDate(val);
				if (dt == null)
					throw new MyException(E_PARAM, String.format("Bad Request - invalid datetime param `%s`=`%s`.", name, val));
				ret = dt;
			}
			/*
			else if (type == "js" || type == "tbl") {
				ret1 = json_decode(ret, true);
				if (ret1 == null)
					throw new MyException(E_PARAM, "Bad Request - invalid json param `name`=`ret`.");
				if (type == "tbl") {
					ret1 = table2objarr(ret1);
					if (ret1 == false)
						throw new MyException(E_PARAM, "Bad Request - invalid table param `name`=`ret`.");
				}
				ret = ret1;
			}
			*/
			/* TODO
			else if (type.Contains(':'))
			{
				ret = param_varr(val, type, name);
			}
			*/
			else
			{
				throw new MyException(E_SERVER, String.format("unknown type `%s` for param `%s`", type, name));
			}
		}
		return ret;
	}

	public Object mparam(String name) {
		return mparam(name, null);
	}
	public Object mparam(String name, String coll) {
		return mparam(name, coll, true);
	}
	// coll?=null, htmlEscape?=true
	public Object mparam(String name, String coll, boolean htmlEscape) {
		Object val = param(name, null, coll, htmlEscape);
		if (val == null)
			throw new MyException(E_PARAM, "require param `" + name + "`");
		return val;
	}

	public void header(String key, String value)
	{
		env.response.addHeader(key, value);
	}

	public Object getSession(String name) {
		return env.request.getSession().getAttribute(name);
	}
	public void setSession(String name, Object value) {
		env.request.getSession().setAttribute(name, value);
	}
	public void destroySession() {
		env.request.getSession().invalidate();
	}
}
