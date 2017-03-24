package com.jdcloud;

import java.io.IOException;
import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
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

	public static final Map<Integer, String> ERRINFO = asMap(
		E_AUTHFAIL, "认证失败",
		E_PARAM, "参数不正确",
		E_NOAUTH, "未登录",
		E_DB, "数据库错误",
		E_SERVER, "服务器错误",
		E_FORBIDDEN, "禁止操作"
	);

	@SuppressWarnings("unchecked")
	public static <K,V> Map<K,V> asMap(Object ... args) {
		Map<K,V> m = new LinkedHashMap<K, V>();
		for (int i=0; i<args.length-1; i+=2) {
			m.put((K)args[i], (V)args[i+1]);
		}
		return m; 
	}

	@SuppressWarnings("unchecked")
	public static <T> List<T> asList(T ... args) {
		List<T> ls = new ArrayList<>();
		for (T e: args) {
			ls.add(e);
		}
		return ls;
	}

	public static String GetErrInfo(int code)
	{
		if (ERRINFO.containsKey(code))
			return ERRINFO.get(code);
		return "未知错误";
	}
	

	public static String Q(String s)
	{
		return "'" + s.replace("'", "\\'") + "'";
	}
	
	public JsArray queryAll(String sql) throws SQLException
	{
		return this.queryAll(sql, false);
	}
	public JsArray queryAll(String sql, boolean assoc) throws SQLException
	{
		addLog(sql, 9);
		env.dbconn();
		Statement stmt = env.conn.createStatement();
		ResultSet rs = stmt.executeQuery(sql);
		ResultSetMetaData md = rs.getMetaData();
		JsArray ret = new JsArray();
		if (assoc) {
			while (rs.next()) {
				JsObject row = new JsObject();
				for (int i=0; i<md.getColumnCount(); ++i) {
					row.put(md.getColumnLabel(i+1), rs.getObject(i+1));
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
		addLog(sql, 9);
		env.dbconn();
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
				row.put(md.getColumnLabel(i+1), rs.getObject(i+1));
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
	
	public int execOne(String sql) throws SQLException {
		return execOne(sql, false);
	}
	// getNewId?=false
	public int execOne(String sql, boolean getNewId) throws SQLException
	{
		addLog(sql, 9);
		env.dbconn();
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
	
	static final Map<String, String> htmlEntityMapping = asMap(
		"<", "&lt;",
		">", "&gt;",
		"&", "&amp;"
	);
	public static String htmlEscape(String s)
	{
		StringBuffer sb = new StringBuffer();
		Matcher m = regexMatch(s, "<|>|&");
		while (m.find()) {
			m.appendReplacement(sb, (String)htmlEntityMapping.get(m.group(0)));
		}
		m.appendTail(sb);
		return sb.toString();
		//return StringEscapeUtils.unescapeHtml();
	}

	public void addLog(String s)
	{
		addLog(s, 0);
	}
	// level?=0
	public void addLog(String s, int level)
	{
		if (env.isTestMode && env.debugLevel >= level)
		{
			env.debugInfo.add(s);
		}
	}

	public void logit(String s) {
		logit(s, "trace");
	}
	// which?="trace"
	public void logit(String s, String which) {
		//TODO
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
		Object ret = env.getParam(name, coll);
		
		if (ret == null && defVal != null)
			return defVal;

		if (ret != null && ret instanceof String) {
			String val = (String)ret;
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
			else if (type.contains(":"))
			{
				ret = param_varr(val, type, name);
			}
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
	
	class ElemType
	{
		public String type; // type
		public boolean optional;
		public ElemType(String type, boolean optional) {
			this.type = type;
			this.optional = optional;
		}
	}
	public JsArray param_varr(String str, String type, String name)
	{
		JsArray ret = new JsArray();
		List<ElemType> elemTypes = new ArrayList<ElemType>();
		for (String t : type.split(":"))
		{
			int tlen = t.length();
			if (tlen == 0)
				throw new MyException(E_SERVER, String.format("bad type spec: `%s`", type));
			boolean optional = false;
			String t1= t;
			if (t.charAt(tlen-1) == '?')
			{
				t1 = t.substring(0, tlen-1);
				optional = true;
			}
			elemTypes.add(new ElemType(t1, optional));
		}
		int colCnt = elemTypes.size();

		for (String row0 : str.split(",")) {
			String[] row = row0.split(":", colCnt);

			JsArray row1 = new JsArray();
			for (int i=0; i<colCnt; ++i)
			{
				String e = i<row.length? row[i]: null;
				ElemType t = elemTypes.get(i);
				if (e == null || e.length() == 0)
				{
					if (t.optional)
					{
						row1.add(null);
						continue;
					}
					throw new MyException(E_PARAM, String.format("Bad Request - param `%s`: list(%s). require col: `%s`[%s]", name, type, row0, i));
				}
				String v = htmlEscape(e);
				if (t.type.equals("i")) 
				{
					try {
						int ival = Integer.parseInt(v);
						row1.add(ival);
					} catch (NumberFormatException ex) {
						throw new MyException(E_PARAM, String.format("Bad Request - param `%s`: list(%s). require integer col: `%s`[%s]=`%s`.", name, type, row0, i, v));
					}
				}
				else if (t.type.equals("n")) 
				{
					try {
						double n = Double.parseDouble(v);
						row1.add(n);
					} catch (NumberFormatException ex) {
						throw new MyException(E_PARAM, String.format("Bad Request - param `%s`: list(%s). require numberic col: `%s`[%s]=`%s`.", name, type, row0, i, v));
					}
				}
				else if (t.type.equals("b"))
				{
					try {
						boolean b = parseBoolean(v);
						row1.add(b);
					} catch (NumberFormatException ex) {
						throw new MyException(E_PARAM, String.format("Bad Request - param `%s`: list(%s). require boolean col: `%s`[%s]=`%s`.", name, type, row0, i, v));
					}
				}
				else if (t.type.equals("s"))
				{
					row1.add(v);
				}
				else if (t.type.equals("dt") || t.type.equals("tm")) {
					java.util.Date dt = parseDate(v);
					if (dt == null)
						throw new MyException(E_PARAM, String.format("Bad Request - param `%s`: list(%s). require datetime col: `%s`[%s]=`%s`.", name, t.type, row0, i, v));
					row1.add(dt);
				}
				else {
					throw new MyException(E_SERVER, String.format("unknown elem type `%s` for param `%s`: list(%s)", t.type, name, v));
				}
			}
			ret.add(row1);
		}
		if (ret.size() == 0)
			throw new MyException(E_PARAM, "Bad Request - list param `%s` is empty.", name);
		return ret;
	}


	public void header(String key, String value)
	{
		env.response.addHeader(key, value);
	}
	public void echo(Object... objs)
	{
		for (Object o: objs) {
			try {
				this.env.response.getWriter().print(o);
			} catch (IOException e) {
			}
		}
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

	public void checkAuth(int perms)
	{
		if (hasPerm(perms))
			return;
		if (hasPerm(AUTH_LOGIN))
			throw new MyException(E_FORBIDDEN, "permission denied.");
		throw new MyException(E_NOAUTH, "need login");
	}

	int perms_;
	public boolean hasPerm(int perms)
	{
		perms_ = env.onGetPerms();
		if ((perms_ & perms) != 0)
			return true;
		return false;
	}

	public static JsObject objarr2table(JsArray rs, int fixedColCnt /*=-1*/)
	{
		JsArray h = new JsArray();
		JsArray d = new JsArray();
		JsObject ret = new JsObject("h", h, "d", d);
		if (rs.size() == 0)
			return ret;

		JsObject row0 = (JsObject)rs.get(0);
		h.addAll(row0.keySet());
		if (fixedColCnt >= 0) {
			/*
			TODO
			foreach (rs as row) {
				h1 = array_keys(row);
				for (i=fixedColCnt; i<count(h1); ++i) {
					if (array_search(h1[i], h) === false) {
						h[] = h1[i];
					}
				}
			}
			*/
		}
		for (Object row : rs) {
			JsObject row1 = (JsObject)row;
			JsArray arr = new JsArray();
			d.add(arr);
			for (Object k : h) {
				arr.add(row1.get(k));
			}
		}
		return ret;
	}

	public static Matcher regexMatch(String str, String pat) {
		return Pattern.compile(pat).matcher(str);
	}

	public String join(String sep, List<?> ls) {
		StringBuffer sb = new StringBuffer();
		for (Object o : ls) {
			if (sb.length() > 0)
				sb.append(sep);
			sb.append(o.toString());
		}
		return sb.toString();
	}
}
