package com.jdcloud;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.servlet.http.HttpSession;

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
	// 支持8种登录类型 0x1-0x80; 其它非登录权限应从0x100开始定义，且名称规范为PERM_XXX。
	public static final int AUTH_USER = 0x1; // 用户登录
	public static final int AUTH_EMP = 0x2;  // 员工登录
	public static final int AUTH_ADMIN = 0x4; // 超级管理员登录
	public static final int AUTH_LOGIN = 0xff; // 任意角色登录

	public static final int KB = 1024;
	public static final int MB = 1024 * KB;
	public static final int GB = 1024 * MB;

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
	

	public static String Q(Object s)
	{
		if (s == null)
			return "null";
		return "'" + s.toString().replace("'", "\\'") + "'";
	}
	
	public JsArray queryAll(String sql) throws SQLException
	{
		return this.queryAll(sql, false);
	}
	public JsArray queryAll(String sql, boolean assoc) throws SQLException
	{
		sql = env.getSqlForExec(sql);
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
	
	public Object queryOne(String sql) throws Exception
	{
		return this.queryOne(sql, false);
	}

/**<pre>
%fn queryOne(sql, assoc?=false) 

执行查询语句，只返回一行数据，如果行中只有一列，则直接返回该列数值。
如果查询不到，返回false，可以用Objects.equals(rv, false)来判断。注意返回值可能为null，不要用rv.equals(false)判断。

示例：查询用户姓名与电话，默认返回值数组(JsArray)：

	Object rv = queryOne("SELECT name,phone FROM User WHERE id=" + id);
	if (Objects.equals(rv, false))
		throw new MyException(E_PARAM, "bad user id");
	JsArray row = (JsArray)rv;
	// row = ["John", "13712345678"]

指定参数assoc=true时，返回关联数组:

	Object rv = queryOne("SELECT name,phone FROM User WHERE id=" + id, true);
	if (Objects.equals(rv, false))
		throw new MyException(E_PARAM, "bad user id");
	JsObject row = (JsObject)rv;
	// row = {"name": "John",  "phone":"13712345678"}

当查询结果只有一列且参数assoc=false时，直接返回该数值。

	Object phone = queryOne("SELECT phone FROM User WHERE id="+id);
	if (Objects.equals(phone, false))
		throw new MyException(E_PARAM, "bad user id");
	// phone = "13712345678"

%see queryAll
 */
	public Object queryOne(String sql, boolean assoc) throws Exception
	{
		sql = env.getSqlForExec(sql);
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

/**<pre>
%fn execOne(sql, getInsertId?=false)

%param getInsertId?=false 取INSERT语句执行后得到的id. 仅用于INSERT语句。

执行SQL语句，如INSERT, UPDATE等。执行SELECT语句请使用queryOne/queryAll.

	String token = (String)mparam("token");
	execOne("UPDATE Cinf SET appleDeviceToken=" . Q(token));

注意：在拼接SQL语句时，对于传入的String类型参数，应使用Q函数进行转义，避免SQL注入攻击。

对于INSERT语句，当设置参数getInsertId=true时, 可返回新加入数据行的id. 例：

	String sql = String.format("INSERT INTO Hongbao (userId, createTm, src, expireTm, vdays) VALUES (%s, '%s', '%s', '%s', %s)",
		userId, createTm, src, expireTm, vdays);
	int hongbaoId = execOne(sql, true);

 */
	public int execOne(String sql, boolean getNewId) throws SQLException
	{
		sql = env.getSqlForExec(sql);
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

/**<pre>
@fn dbInsert(table, kv) -> newId

字段值可以是数值、日期、字符串等类型的变量。
如果值为null或空串会被忽略。
e.g. 

	int orderId = dbInsert("Ordr", new JsObject(
		"tm", new Date(), // 支持Date类型
		"tm1", "=now()", // "="开头，表示是SQL表达式
		"amount", 100,
		"dscr", null // null字段会被忽略
	));

*/
	public int dbInsert(String table, Map<String,Object> kv) throws SQLException
	{
		StringBuffer keys = new StringBuffer();
		StringBuffer values = new StringBuffer();

		for (String k : kv.keySet())
		{
			if (!k.matches("\\w+"))
				throw new MyException(E_PARAM, String.format("bad property `%s`", k));

			Object oval = kv.get(k);
			if (oval == null)
				continue;
			String val = oval.toString();
			if (val.length() == 0)
				continue;
			if (keys.length() > 0)
			{
				keys.append(", ");
				values.append(", ");
			}
			keys.append(k);
			if (oval instanceof Number || val.matches("^[\\+-]?[0-9.]+$")) {
				values.append(val);
			}
			else if (oval instanceof Date) {
				values.append("'")
					.append(date(null, (Date)oval))
					.append("'");
			}
			else if (oval instanceof String && val.charAt(0) == '=') {
				values.append(val.substring(1));
			}
			else {
				val = htmlEscape(val);
				values.append(Q(val));
			}
		}
		
		if (keys.length() == 0)
			throw new MyException(E_PARAM, "no field found to be added");

		String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table, keys, values);
		int ret = execOne(sql, true);
		return ret;
	}
	
/**<pre>
@fn dbUpdate(table, kv, id_or_cond?) -> cnt

@param id_or_cond 查询条件，如果是数值比如100或"100"，则当作条件"id=100"处理；否则直接作为查询表达式，比如"qty<0"；如果未指定则无查询条件。

e.g.

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
*/
	public int dbUpdate(String table, Map<String,Object> kv, Object cond) throws SQLException
	{
		if (cond != null && cond instanceof Integer)
			cond = String.format("id=%s", cond);
		int cnt = 0;
		StringBuffer kvstr = new StringBuffer();
		for (String k : kv.keySet())
		{
			if (k.equals("id"))
				continue;
			// ignore non-field param
			//if (substr($k,0,2) == "p_")
				//continue;
			// TODO: check meta
			if (!k.matches("^(\\w+\\.)?\\w+$"))
				throw new MyException(E_PARAM, String.format("bad property `%s`", k));

			Object val = kv.get(k);
			if (val == null)
				continue;

			if (kvstr.length() > 0)
				kvstr.append(", ");
			// 空串或null置空；empty设置空字符串
			if (val.equals("") || val.equals("null")) {
				kvstr.append(k).append("=null");
			}
			else if (val.equals("empty")) {
				kvstr.append(k).append("=''");
			}
			else if (val instanceof Number) {
				kvstr.append(k).append("=").append(val);
			}
			else if (val instanceof String && val.toString().startsWith("=")) {
				kvstr.append(k).append(val);
			}
			else {
				kvstr.append(k).append("=").append(Q(htmlEscape(val.toString())));
			}
		}
		if (kvstr.length() == 0) 
		{
			addLog("no field found to be set");
		}
		else {
			String sql = null;
			if (cond != null)
				sql = String.format("UPDATE %s SET %s WHERE %s", table, kvstr, cond);
			else
				sql = String.format("UPDATE %s SET %s", table, kvstr);
			cnt = execOne(sql);
		}
		return cnt;
	}

	public static String jsonEncode(Object o)
	{
		return jsonEncode(o, false);
	}
/**<pre>
%fn jsonEncode(o, doFormat=false)

%param doFormat 设置为true则会对JSON输出进行格式化便于调试。

%see jsonDecode
 */
	public static String jsonEncode(Object o, boolean doFormat)
	{
		GsonBuilder gb = new GsonBuilder();
		gb.serializeNulls().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss");
		if (doFormat)
			gb.setPrettyPrinting();
		Gson gson = gb.create();
		return gson.toJson(o);
	}
	
/**<pre>
%fn jsonDecode(json, type) -> type

	Map m = jsonDecode(json, Map.class);
	List m = jsonDecode(json, List.class);
	User u = jsonDecode(json, User.class);

	Object o = jsonDecode(json);
	// the same as
	Object o = jsonDecode(json, Object.class);
	
%see jsonEncode
 */
	public static <T> T jsonDecode(String json, Class<T> type)
	{
		GsonBuilder gb = new GsonBuilder();
		gb.serializeNulls().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss");
		Gson gson = gb.create();
		return gson.fromJson(json, type);
	}
	public static Object jsonDecode(String json)
	{
		return jsonDecode(json, Object.class);
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

	public void logit(Object s) {
		logit(s, true, "trace");
	}
	public void logit(Object s, boolean addHeader) {
		logit(s, addHeader, "trace");
	}
/**
%fn logit(str, addHeader?=true, type?="trace")

记录日志到文件。type参数指定日志文件名，例如默认为trace，则日志写到trace.log。

 */
	// which?="trace"
	public void logit(Object s, boolean addHeader, String type) {
		String fname = env.baseDir + "/" + type + ".log";
		try (OutputStream out = new FileOutputStream(fname, true)) {
			if (addHeader) {
				String hdr = String.format("[%s] ", date());
				out.write(hdr.getBytes("utf-8"));
			}
			if (s instanceof byte[])
				out.write((byte[])s);
			else if (s instanceof String)
				out.write(((String)s).getBytes("utf-8"));
			else
				out.write(s.toString().getBytes("utf-8"));
			out.write('\n');
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

/**<pre>
%fn parseBoolean(val) -> boolVal

val支持多种类型。

- null或数值0表示false
- 字符串"0", "false", "off", "no"表示false 
- 字符串"1", "true", "on", "yes"表示true, 特别地, 空串("", empty)表示true

示例：

	boolean forTest = param("test/b", false); 
	boolean isTestMode = parseBoolean(getenv("P_TEST_MODE", "0")); // 仅用作示例，可以直接用 env.isTestMode
 */
	public static boolean parseBoolean(Object o)
	{
		boolean val = false;
		if (o == null) {
			return val;
		}
		if (o instanceof Double) {
			double d = (double)o;
			return d != 0.0;
		}
		String s = ((String)o).toLowerCase();
		if (s.equals("0") || s.equals("false") || s.equals("off") || s.equals("no"))
			val = false;
		else if (s.equals("") || s.equals("1") || s.equals("true") || s.equals("on") || s.equals("yes"))
			val = true;
		else
			throw new NumberFormatException();
		return val;
	}

	public static java.util.Date parseDate(String s) {
		return parseDate(s, false);
	}

/**<pre>
%fn parseDate(str, onlyDatePart?=false) -> dateVal

支持以下日期格式：

	"2010/1/1 10:10", "2011-2-1 8:8:8", "2010.3.4", "2011-02-01T10:10:10Z"

如果格式无法解析，返回null。
如果onlyDatePart=true，则只取日期部分，忽略时间部分。
 */
	@SuppressWarnings("deprecation")
	public static java.util.Date parseDate(String s, boolean onlyDatePart) {
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
			if (onlyDatePart) {
				dt.setSeconds(0);
				dt.setMinutes(0);
				dt.setHours(0);
			}
		} catch (ParseException e) {
		}
		return dt;
	}

/**<pre>
%fn parseDate(str, fmt) -> dateVal

	java.util.Date tm = parseDate("20180101121030", "yyyyMMddHHmmss");
	if (tm == null) // fail to parse
		return;
	String tmstr = date("yyyy-MM-dd HH:mm:ss", tm); // "2018-01-01 12:10:30"
	
如果格式无法解析，返回null。
 */
	public static java.util.Date parseDate(String s, String fmt) {
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

/**<pre>
%fn param(name, defVal?, col?, doHtmlEscape=true)

%param col null - (默认)先取URL参数(env._GET)再取POST参数(api._POST)，"G" - 从env.GET中取; "P" - 从env.POST中取

获取名为name的参数。
name中可以指定类型，返回值根据类型确定。如果该参数未定义或是空串，直接返回缺省值defVal。

name中指定类型的方式如下：
- 名为"id", 或以"Id"或"/i"结尾: 返回Integer类型
- 以"/b"结尾: 返回Boolean类型. 可接受的字符串值为: "1"/"true"/"on"/"yes"=>true, "0"/"false"/"off"/"no" => false
- 以"/dt"或"/tm"结尾: 返回java.util.Date类型。如果是"/dt"则只有日期部分。
- 以"/n"结尾: 数值型(numeric)，返回Double类型
- 以"/s"结尾（缺省）: 返回String类型. 缺省为防止XSS攻击会做html编码，如"a&b"处理成"a&amp;b"，设置参数doHtmlEscape=false可禁用这个功能。
- 复杂类型：以"/i+"结尾: 整数数组如"82,93,105"，常用于传输id列表，返回ArrayList<Integer>类型。
- TODO: 复杂类型：以"/js"结尾: json object
- 复杂类型：List类型（以","分隔行，以":"分隔列），类型定义如"/i:n:b:dt:tm" （列只支持简单类型，不可为复杂类型），返回JsArray类型。

示例：

	Integer id = (Integer)param("id");
	Integer svcId = (Integer)param("svcId/i", 99);
	Boolean wantArray = (Boolean)param("wantArray/b", false);
	Date startTm = (Date)param("startTm/dt", new Date());
	List<Integer> idList = (List<Integer>)param("idList/i+");

List类型示例。假设参数"items"类型在文档中定义为

	items
	: list(id/Integer, qty/Double, dscr/String)
	
这样取该参数，返回值为JsArray类型，数组中每一项又是JsArray类型（下面用中括号表示JsArray）：

	JsArray items = (JsArray)param("items/i:n:s");
	// 假设 items="100:1:洗车,101:1:打蜡"
	// 返回 [ [ 100, 1.0, "洗车"], [101, 1.0, "打蜡"] ]

如果某列可缺省，用"?"表示，如 `param("items/i:n?:s?")` 来获取值 `items=100:1,101::打蜡`
得到

	[ [ 100, 1.0, null], [101, null, "打蜡"] ]

TODO: 直接支持 param("items/(id,qty?/n,dscr?)"), 添加param_objarr函数，去掉parseList函数。上例将返回

	[
		[ "id"=>100, "qty"=>1.0, dscr=>null],
		[ "id"=>101, "qty"=>null, dscr=>"打蜡"]
	]
*/
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
				java.util.Date dt = parseDate(val, type.equals("dt"));
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
		else if (ret instanceof Double) {
			if (type.equals("i")) {
				ret = ((Double)ret).intValue();
			}
			else if (type.equals("b")) {
				ret = parseBoolean(ret);
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

/** <pre>
%fn mparam(name, coll?=null, htmlEscape?=true)

必填参数(mandatory param)

参考param函数，查看name如何支持各种类型。

示例：

	String svcId = (String)mparam("svcId");
	Integer svcId = (Integer)mparam("svcId/i");
	List<Integer> itts = (List<Integer>)mparam("itts/i+");
	
name也可以是一个数组，表示至少有一个参数有值，这时返回每个参数的值。

	JsArray rv = mparam(new String[] {"svcId/i", "itts/i+"}); // require one of the 2 params
	Integer svcId = rv.get(0);
	List<Integer> itts = rv.get(1); 
	
 */
	public Object mparam(String name, String coll, boolean htmlEscape) {
		Object val = param(name, null, coll, htmlEscape);
		if (val == null)
			throw new MyException(E_PARAM, "require param `" + name + "`");
		return val;
	}
	
/** <pre>
%fn mparam(names, coll?=null)

几个参数，必填其一(mandatory param)
names是一个数组，表示至少有一个参数有值，返回JsArray，包含每个参数的值，其中有且只有一个非null。

	JsArray rv = mparam(new String[] {"svcId/i", "itts/i+"}); // require one of the 2 params
	Integer svcId = rv.get(0);
	List<Integer> itts = rv.get(1); 
	if (svcId != null) {
	}
	else if (itts != null) {
	}
 */
	public JsArray mparam(String[] names, String coll) {
		JsArray ret = new JsArray();
		boolean found = false;
		Object rv = null;
		for (String name: names) {
			if (found)
				rv = null;
			else {
				rv = param(name, null, coll);
				if (rv != null)
					found = true;
			}
			ret.add(rv);
		}
		return ret;
	}
	public JsArray mparam(String[] names) {
		return mparam(names, null);
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
					java.util.Date dt = parseDate(v, t.type.equals("dt"));
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
/**<pre>
%fn getSession(name) -> Object

%see setSession
%see unsetSession
%see destroySession
 */
	public Object getSession(String name) {
		String name1 = env.appType + "-" + name;
		return env.request.getSession().getAttribute(name1);
	}
/**<pre>
%fn setSession(name, value)

%see getSession
%see unsetSession
%see destroySession
 */
	public void setSession(String name, Object value) {
		String name1 = env.appType + "-" + name;
		env.request.getSession().setAttribute(name1, value);
	}
/**<pre>
%fn unsetSession(name)

%see setSession
%see getSession
%see destroySession
 */
	public void unsetSession(String name) {
		String name1 = env.appType + "-" + name;
		env.request.getSession().removeAttribute(name1);
	}
/**<pre>
%fn destroySession()

%see getSession
%see setSession
%see unsetSession
 */
	public void destroySession() {
		HttpSession ses = env.request.getSession();
		boolean allRemoved = true;
		Enumeration<String> it = ses.getAttributeNames();
		List<String> toDel = new ArrayList<String>();
		String prefix = env.appType + "-";
		while (it.hasMoreElements()) {
			String key = it.nextElement();
			if (! key.startsWith(prefix)) {
				allRemoved = false;
				continue;
			}
			toDel.add(key);
		}
		if (allRemoved) {
			ses.invalidate();
		}
		else {
			toDel.forEach(e -> ses.removeAttribute(e));
		}
	}

	public void checkAuth(int perms)
	{
		if (hasPerm(perms))
			return;
		if (hasPerm(AUTH_LOGIN))
			throw new MyException(E_FORBIDDEN, "permission denied.");
		throw new MyException(E_NOAUTH, "need login");
	}

	int perms_ = -1;
	public boolean hasPerm(int perms)
	{
		perms_ = perms_!=-1? perms_: env.onGetPerms();
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

/**<pre>
%fn regexMatch(str, pat) -> Matcher

正则表达式匹配

	String phone = "13712345678";
	Matcher m = regexMatch(phone, "...(\\d{4})";
	if (m.find()) { // 如果要连续匹配可用 while (m.find()) 
		// m.group(1) 为中间4位数
	}

 */
	public static Matcher regexMatch(String str, String pat) {
		return Pattern.compile(pat).matcher(str);
	}

/**<pre>
%fn regexReplace(str, pat, str1) -> String
%alias regexReplace(str, pat, fn) -> String

用正则表达式替换字符串

%param fn(Matcher m) -> String

	String phone = "13712345678"; // 变成 "137****5678"
	String phone1 = regexReplace(phone, "(?<=^\\d{3})\\d{4}", "****");
	或者
	String phone1 = regexReplace(phone, "^(\\d{3})\\d{4}", m -> { return m.group(1) + "****"; } );

 */
	public static String regexReplace(String str, String pat, String str1) {
		Matcher m = regexMatch(str, pat);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			m.appendReplacement(sb, str1);
		}
		m.appendTail(sb);
		return sb.toString();
	}
	public static String regexReplace(String str, String pat, java.util.function.Function<Matcher, String> fn) {
		Matcher m = regexMatch(str, pat);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String str1 = fn.apply(m);
			m.appendReplacement(sb, str1);
		}
		m.appendTail(sb);
		return sb.toString();
	}
	
	public String join(String sep, List<?> ls) {
		StringBuffer sb = new StringBuffer();
		for (Object o : ls) {
			if (sb.length() > 0)
				sb.append(sep);
			sb.append(o);
		}
		return sb.toString();
	}

/**<pre>
%var T_SEC,T_MIN,T_HOUR,T_DAY

	Date dt = new Date();
	Date dt1 = parseDate(dtStr1);
	long hours = (dt1.getTime() - dt.getTime()) / T_HOUR;
	Date dt2 = new Date(dt1.getTime() + 4 * T_DAY);
	
%see time
 */
	public static final long T_SEC = 1000;
	public static final long T_MIN = 60*T_SEC;
	public static final long T_HOUR = 3600*T_SEC;
	public static final long T_DAY = 24*T_HOUR;
	
	public static final String FMT_DT = "yyyy-MM-dd HH:mm:ss";
/** <pre>
%fn date(fmt?="yyyy-MM-dd HH:mm:ss", dt?)

生成日期字符串。

	String dtStr1 = date(null, null);
	Date dt1 = parseDate(dtStr1);
	String dtStr2 = date("yyyy-MM-dd", dt1);
	
%see parseDate
*/
	public String date(String fmt, Date dt) {
		if (fmt == null)
			fmt = FMT_DT;
		if (dt == null)
			dt = new Date();
		return new java.text.SimpleDateFormat(fmt).format(dt);
	}
	public String date(String fmt, long dtval) {
		if (fmt == null)
			fmt = FMT_DT;
		Date dt = new Date(dtval);
		return new java.text.SimpleDateFormat(fmt).format(dt);
	}
	public String date() {
		return date(null, null);
	}
/**<pre>
%fn time()

系统当前时间（毫秒）。

	long t = time();
	long unixTimestamp = t / T_SEC
	Date dt1 = new Date();
	long diff_ms = dt1.getTime() - t;
 */
	public long time() {
		return System.currentTimeMillis();
	}
	
	public String getenv(String name) {
		return env.props.getProperty(name);
	}
/**<pre>
%fn getenv(name, defVal?)

取全局设置。

	String cred = getenv("P_ADMIN_CRED");
	boolean isTestMode = parseBoolean(getenv("P_TEST_MODE", "0")); // 仅用作示例，可以直接用 env.isTestMode
	int debugLevel = Integer.parseInt(getenv("P_DEBUG", "0"));  // 仅用作示例，可以直接用 env.debugLevel

 */
	public String getenv(String name, String defVal) {
		return env.props.getProperty(name, defVal);
	}

/**<pre>
%fn md5(s) -> String 

返回md5字符串(32字符)
*/
	public String md5(String s)
	{
		byte[] rv = md5Bytes(s); 
		return new java.math.BigInteger(1, rv).toString(16);
	}
/**<pre>
%fn md5Bytes(s) -> byte[] 

返回md5结果(16字节)
*/
	public byte[] md5Bytes(String s)
	{
		byte[] ret = null;
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
			md.update(s.getBytes());
			ret = md.digest();
		} catch (NoSuchAlgorithmException e) {
		}
		return ret;
	}
	
/**<pre>
%fn rand(from, to) -> int

生成[from, to]范围内随机整数
 */
	public int rand(int from, int to)
	{
		return from + (int)(Math.random() * (to-from+1));
	}
	
/**<pre>
%fn base64Encode(s) -> String
%param s String/byte[]
 */
	public String base64Encode(String s) {
		// TODO: utf-8
		return base64Encode(s.getBytes());
	}
	public String base64Encode(byte[] bs) {
		return Base64.getEncoder().encodeToString(bs);
	}
/**<pre>
%fn base64Decode(s) -> String
%fn base64DecodeBytes(s) -> byte[]

	String text = base64Decode(enc);
	
 */
	public String base64Decode(String s) {
		return new String(base64DecodeBytes(s));
	}
	public byte[] base64DecodeBytes(String s) {
		return Base64.getDecoder().decode(s);
	}


/** <pre>
%fn myEncrypt(data, op, key?="jdcloud") -> String

对字符串data加密，生成base64编码的字符串。
或者反过来，对base64编辑的data解密返回原文。
出错返回null。

算法：DES加密。

%param op "E"-加密(encrypt); "D"-解密(decrypt)
 */
	public String myEncrypt(String data, String op, String key)
	{
		boolean isEnc = op.equals("E");
		byte[] in = null;
		if (isEnc) {
			in = data.getBytes(); 
		}
		else {
			in = base64DecodeBytes(data);
		}
		if (in == null)
			return null;
		
		if (key == null)
			key = "jdcloud";
		byte[] keyBytes = md5Bytes(key); // 注意：仅使用前8个字节

		try {
			SecureRandom random = new SecureRandom();
			DESKeySpec desKey = new DESKeySpec(keyBytes);
			SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
			SecretKey secureKey = keyFactory.generateSecret(desKey);
			Cipher cipher = Cipher.getInstance("DES");
			cipher.init(isEnc? Cipher.ENCRYPT_MODE: Cipher.DECRYPT_MODE, secureKey, random);
			byte[] bs = cipher.doFinal(in);
			if (isEnc)
				return base64Encode(bs);
			return new String(bs);
		} catch (Exception e) {
			System.out.println(e);
		}
		return null;
	}
	
/**<pre>
%fn getCred(cred) -> [user, pwd]

cred为"{user}:{pwd}"格式，支持使用base64编码。
返回2元素字符数组，表示user和pwd。
出错时返回null。

示例：

	String[] cred = getCred(getenv("P_ADMIN_CRED"));
	if (cred == null) {
		// 未设置用户名密码
	}
	String user = cred[0], pwd = cred[1];

*/
	public String[] getCred(String cred) {
		if (cred == null)
			return null;
		if (cred.indexOf(':') < 0) {
			cred = new String(base64Decode(cred));
		}
		return cred.split(":", 2);
	}
	
/**<pre>
%fn hashPwd(pwd) -> pwd1

对密码进行加密处理。如果已加密过，则直接返回。
 */
	
	/*api和ac接口都需要用到，暂时放在此处*/
	public String hashPwd(String pwd)
	{
		if (pwd.length() == 32 || pwd.length() == 0)
			return pwd;
		return md5(pwd);
	}

/**<pre>
%fn indexOf(map, fn) -> key
%param fn(key, value) -> boolean

找符合fn条件的第一个key。找不到返回null。

	JsObject map = new JsObject("aa", 100, "bb", 300);
	String key = indexOf(map, (k,v)->{v>200}); // key="bb"
	
 */
	public static <K,V> K indexOf(Map<K,V> m, BiPredicate<K,V> fn) {
		K key = null;
		for (Map.Entry<K, V> e: m.entrySet()) {
			if (fn.test(e.getKey(), e.getValue())) {
				key = e.getKey();
				break;
			}
		}
		return key;
	}
	
/**<pre>
%fn indexOf(arr, e) -> index

数组查找。返回找到的索引，找不到返回-1。

	JsArray arr = new JsArray("aa", "bbb");
	int idx = indexOf(arr, "bbb"); // idx =1
	
*/
	public static <T> int indexOf(T[] arr, T e) {
		int idx = -1;
		for (int i=0; i<arr.length; ++i	) {
			if (arr[i].equals(e)) {
				idx = i;
				break;
			}
		}
		return idx;
	}

/**<pre>
%fn forEach(map, fn(k, v))

与map.forEach类似，但抛出Exception异常。
如果设置外部变量，示例：

	Map<String, Integer> m = new HashMap<>();
	// add values ...
	Integer[] minVal = {Integer.MAX_VALUE};
	forEach(m, (k, v) -> {
		if (minVal[0] > v)
			minVal[0] = v;
	});

*/
	@FunctionalInterface
	public interface MapForEachFn<K,V>
	{
		void exec(K k, V v) throws Exception;
	}
	public static <K,V> void forEach(Map<K,V> m, MapForEachFn<K,V> fn) throws Exception
	{
		for (Map.Entry<K,V> kv: m.entrySet()) {
			fn.exec(kv.getKey(), kv.getValue());
		}
	}

/**<pre>
@fn readFileBytes(file, maxLen=-1) -> byte[]

返回null表示读取失败。
 */
	public static byte[] readFileBytes(String file) throws IOException
	{
		return readFileBytes(file, -1);
	}
	public static byte[] readFileBytes(String file, int maxLen)
	{
		byte[] bs = null;
		try {
			File f = new File(file);
			if (! f.exists())
				return null;
			InputStream in = new FileInputStream(f);
			int len = (int)f.length();
			if (maxLen >0 && len > maxLen)
				len = maxLen;
			bs = new byte[len];
			in.read(bs);
			in.close();
		}
		catch (IOException ex) {
			
		}
		return bs;
	}

/**<pre>
%fn readFile(file, charset="utf-8") -> String

返回null表示读取失败。
 */
	public static String readFile(String file) throws IOException
	{
		return readFile(file, "utf-8");
	}
	public static String readFile(String file, String charset) throws IOException
	{
		byte[] bs = readFileBytes(file);
		if (bs == null)
			return null;
		return new String(bs, charset);
	}

/**<pre>
%fn writeFile(in, out, bufSize?)

复制输入到输出。输入、输出可以是文件或流。

%param in String/File/InputStream
%param out String/File/OutputStream
%param bufSize 指定buffer大小，设置0使用默认值(10K)
 */
	public static void writeFile(Object in, Object out, int bufSize) throws IOException
	{
		if (bufSize <= 0)
			bufSize = 10* KB;
		InputStream in1 = null;
		boolean closeIn = true;
		if (in instanceof String) {
			in1 = new FileInputStream((String)in);
		}
		else if (in instanceof File) {
			in1 = new FileInputStream((File)in);
		}
		else if (in instanceof InputStream) {
			in1 = (InputStream)in;
			closeIn = false;
		}
		else {
			throw new IllegalArgumentException("writeFile:in");
		}
		OutputStream out1 = null;
		boolean closeOut = true;
		if (out instanceof String) {
			out1 = new FileOutputStream((String)out);
		}
		else if (out instanceof File) {
			out1 = new FileOutputStream((File)out);
		}
		else if (out instanceof OutputStream) {
			out1 = (OutputStream)out;
			closeOut = false;
		}
		else {
			throw new IllegalArgumentException("writeFile:out");
		}

		byte[] buffer = new byte[bufSize];
		int len = 0;
		while ((len = in1.read(buffer)) != -1) {
			out1.write(buffer, 0, len);
		}
		if (closeOut)
			out1.close();
		if (closeIn)
			in1.close();
	}
	public static void writeFile(Object in, Object out) throws IOException {
		writeFile(in, out, 0);
	}

/**<pre>
%fn getPath(path, withSep) -> path

用于获得以"/"结尾或不以"/"结尾的路径。

	String path = getPath("dir1/dir2/", true); // "dir1/dir2/"
	String path = getPath("dir1/dir2", true); // "dir1/dir2/"
	String path = getPath("dir1/dir2", false); // "dir1/dir2"
	String path = getPath("dir1/dir2/", false); // "dir1/dir2"

*/
	public static String getPath(String path, boolean withSep) throws IOException {
		if (withSep) {
			if (! (path.endsWith("/") || path.endsWith("\\"))) {
				return path + "/";
			}
		}
		else {
			if (path.endsWith("/") || path.endsWith("\\")) {
				return path.substring(0, path.length()-1);
			}
		}
		return path;
	}

/**<pre>
%fn getBaseUrl(wantHost) -> String

返回项目的URL，以"/"结尾。

	String url = getBaseUrl(true); // "http://myserver/myapp/"
	String url1 = getBaseUrl(false); // "/myapp/"
	
 */
	public String getBaseUrl(boolean wantHost) {
		String ret = null;
		if (wantHost) {
			// "http(s)://server/app/"
			String s = env.request.getRequestURL().toString();
			int n = 0, pos = 8;
			do {
				pos = s.indexOf('/', pos) +1; 
			} while (++n < 2 && pos > 0);
			if (pos > 0)
				ret = s.substring(0, pos);
			else
				ret = s;
		}
		else {
			ret = env.request.getContextPath() + "/";
		}
		return ret;
	}
	
/**<pre>
%fn exit()

立即返回，不再处理，不自动输出任何内容。
如果想输出返回数据，请自行用echo输出后再调用exit，或直接用DirectReturn(val)返回`[0,val]`标准格式.

%see DirectReturn
 */
	public void exit() {
		throw new DirectReturn(0, null, false);
	}
	
/**<pre>
%fn safeClose(o)

Close without exception.
 */
	public void safeClose(AutoCloseable o) {
		try {
			if (o != null)
				o.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
/**
%fn tmCols(fieldName = "t0.tm")

为查询添加时间维度单位: y,m,w,d,wd,h (年，月，周，日，周几，时)。

- wd: 1-7表示周一到周日
- w: 一年中第一周，从该年第一个周一开始(mysql week函数模式7).

示例：

	this.vcolDefs = asList(
		new VcolDef().res(tmCols())
	);

	this.vcolDefs = asList(
		new VcolDef().res(tmCols("log_cr.tm")).require("createTm")
	);

 */
	public List<String> tmCols(String fieldName) {
		return asList("year(" + fieldName + ") y",
				"month(" + fieldName + ") m",
				"week(" + fieldName + ",7) w",
				"day(" + fieldName + ") d", 
				"weekday(" + fieldName + ")+1 wd",
				"hour(" + fieldName + ") h");
	}
	public List<String> tmCols() {
		return tmCols("t0.tm");
	}
	
/**
%fn issetval(fieldName)

判断POST内容中是否对该字段设置值，示例：
(在onValidate回调中)

	if (issetval("pwd")) {
		String pwd = (String)env._POST.get("pwd");
		env._POST.put("pwd", hashPwd(pwd));
	}

*/
	public boolean issetval(String field) {
		return env._POST.containsKey(field) && env._POST.get(field) != null;
	}

/**<pre>
@fn parseKvList(kvListStr, sep, sep2)

解析key-value列表字符串。如果出错抛出异常。
注意：sep, sep2为行、列分隔符的正则式。

示例：

	Map<String, Object> map = parseKvList("CR:新创建;PA:已付款", ";", ":");
	// map: {"CR": "新创建", "PA":"已付款"}
*/
	public static Map<String, Object> parseKvList(String str, String sep, String sep2)
	{
		Map<String, Object> map = new JsObject();
		for (String ln : str.split(sep)) {
			String[] kv = ln.split(sep2, 2);
			if (kv.length != 2)
				throw new MyException(E_PARAM, String.format("bad kvList: `%s'", str));
			map.put(kv[0], kv[1]);
		}
		return map;
	}
	
	public static String urlEncodeArr(Map<String,Object> params) throws Exception
	{
		StringBuffer sb = new StringBuffer();
		for (Map.Entry<String, Object> kv: params.entrySet()) {
			if (sb.length() > 0)
				sb.append('&');
			String k = URLEncoder.encode(kv.getKey(), "UTF-8");
			String v = URLEncoder.encode(kv.getValue().toString(), "UTF-8");
			sb.append(k).append('=').append(v);
		}
		return sb.toString();
	}
	public static String makeUrl(String ac, Map<String,Object> params) throws Exception
	{
		StringBuffer url = new StringBuffer();
		url.append(ac);
		if (params != null) {
			if (url.indexOf("?") <= 0)
				url.append('?');
			else
				url.append('&');
			url.append(urlEncodeArr(params));
		}
		/*
		if (hash != null)
			url.append(hash);
		*/
		return url.toString();
	}

/**
%fn httpCall(url, urlParams, postParams, opt)

postParams非空使用POST请求，否则使用GET请求。
postParams可以是字符串、map或list等数据结构。默认contentType为"x-www-form-urlencoded"格式。如果postParams为list等结构，则使用"json"格式。
如果要明确指定格式，可以设置opt.contentType参数，如

	String rv = httpCall(baseUrl, urlParams, postParams, asMap("contentType", "application/json"));
	

- opt: {contentType}

e.g.

	String baseUrl = "http://oliveche.com/echo.php";
	// 常用asMap或new JsObject
	Map<String, Object> urlParams = asMap("intval", 100, "floatval", 12.345, "strval", "hello");
	JsObject postParams = new JsObject("postintval", 100, "poststrval", "中文");
	String rv = httpCall(baseUrl, urlParams, postParams, null);

*/
	public static String httpCall(String url, Map<String,Object> getParams, Object postParams, Map<String,Object> opt) throws Exception
	{
		String url1 = makeUrl(url, getParams);
		URL oUrl = new URL(url1);
		HttpURLConnection conn = (HttpURLConnection)oUrl.openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(20000);

		byte[] postBytes = null;
		String charset = "UTF-8";
		if (postParams != null) {
			String postStr = null;
			String ct = null;
			if (opt != null) 
				ct = Objects.toString(opt.get("contentType"));
			if (ct == null) {
				if (postParams instanceof Map || postParams instanceof String) {
					ct = "application/x-www-form-urlencoded";
				}
				else {
					ct = "application/json";
				}
			}
			if (postParams instanceof String) {
				postStr = (String)postParams;
			}
			else if (ct.indexOf("/json") >0) {
				postStr = jsonEncode(postParams);
			}
			else {
				@SuppressWarnings("unchecked")
				Map<String,Object> postMap = (Map<String,Object>)postParams;
				postStr = urlEncodeArr(postMap);
			}
			conn.setRequestProperty("Content-Type", ct + ";charset=" + charset);
			conn.setDoOutput(true);
			conn.setDoInput(true);

			postBytes = postStr.getBytes(charset);
		}
		conn.connect();
		if (postBytes != null) {
			try (OutputStream out = conn.getOutputStream()) {
				out.write(postBytes);
			}
		}

		String ct = conn.getContentType();
		String resCharset = "UTF-8";
		if (ct != null) {
			Matcher m = regexMatch(ct, "(?i)charset=([\\w-]+)");
			if (m.find())
				resCharset = m.group(1);
			// System.out.println(ct);
		}

		String ret = null;
		try (
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			BufferedInputStream in = new BufferedInputStream(conn.getInputStream())
		) {
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) >= 0) {
				out.write(buf, 0, len);
			}
			ret = out.toString(resCharset);
		}
		return ret;
	}
}
