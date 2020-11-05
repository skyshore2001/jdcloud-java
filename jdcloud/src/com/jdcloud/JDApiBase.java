package com.jdcloud;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.servlet.http.HttpSession;

public class JDApiBase extends Common
{
	public static class DbExpr
	{
		public String val;
		public DbExpr(String val) {
			this.val = val;
		}
	}

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

	public JDEnvBase env;
	
	public static class ForDel { }
	public static ForDel forDel = new ForDel();
	
	public Object _GET(String key) {
		return env._GET.get(key);
	}
	public void _GET(String key, Object val) {
		if (val == forDel) {
			env._GET.remove(key);
			return;
		}
		env._GET.put(key, val);
	}
	public Object _POST(String key) {
		return env._POST.get(key);
	}
	public void _POST(String key, Object val) {
		if (val == forDel) {
			env._POST.remove(key);
			return;
		}
		env._POST.put(key, val);
	}
	public Object _SESSION(String key) {
		return getSession(key);
	}
	public void _SESSION(String key, Object val) {
		if (val == forDel) {
			this.unsetSession(key);
			return;
		}
		setSession(key, val);
	}
	/*
	public NameValueCollection _SERVER
	{
		get { return env.ctx.Request.ServerVariables;}
	}
	*/

	public static final Map<Integer, String> ERRINFO = asMap(
		E_AUTHFAIL, "认证失败",
		E_PARAM, "参数不正确",
		E_NOAUTH, "未登录",
		E_DB, "数据库错误",
		E_SERVER, "服务器错误",
		E_FORBIDDEN, "禁止操作",
		E_ABORT, "中止执行"
	);

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
	
	public JsArray queryAll(String sql) throws Exception
	{
		return this.queryAll(sql, false);
	}
	public JsArray queryAll(String sql, boolean assoc) throws Exception
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

/**<pre>
%fn testConnection()

用于在单任务长连接数据库应用中, 每次处理前测试数据库连接是否断开, 若断开则尝试自动重连. 
MySQL5以上默认关闭8小时内无通讯的连接, 因而长时间放置后再使用数据库会出现查询异常 (MySQL选项wait_timeout=28800).
示例:

	JDEnvBase env = JDEnvBase.createEnv();
	while ( true ) {
		env.testConnection();
		Object cnt = env.queryOne("SELECT COUNT(*) From ApiLog");
		System.out.println("cnt=" + cnt.toString());
		Thread.sleep(6000);
	}

注意: 如果是多线程处理(是线程池管理的线程), 则每个线程里使用独立的db连接, 结束后等待回收, 相当于短连接处理, 
当线程被重用时，可能其中的数据库连接已超时断开，所以也会有连接长时间放置后断开的情况。
这种情况一般在处理完任务后调用`env.close()`来及时关闭DB连接即可（可使用DB连接池优化），也可在每次开头调用本函数测试（不建议）。

%key validationQuery 数据库连接池属性

如果使用Tomcat DBCP连接池, 应设置连接池属性用于在重用连接时先测试（其中可能发起重连）:

	validationQuery="SELECT 1"

示例: 在Tomcat的/etc/tomcat/context.xml中配置连接池：(假设DB名为pdi)

	<Context>
		<Resource name="jdbc/pdi"
			auth="Container"
			type="javax.sql.DataSource"
			factory="org.apache.commons.dbcp.BasicDataSourceFactory"
			username="demo"
			password="demo123"
			maxIdle="30"
			maxWait="10000"
			maxActive="100"
			driverClassName="com.mysql.jdbc.Driver"
			validationQuery="SELECT 1"
			url="jdbc:mysql://localhost:3306/pdi?characterEncoding=utf-8" />

	</Context>

在web.properties中使用连接池(参考web.properties.template中的示例):

	P_DBTYPE=mysql
	P_DB_DRIVER=DataSource
	P_DB=jdbc/pdi
	
*/
	public void testConnection() throws Exception
	{
		int tryCnt = 1;
		Object rv = null;
		do {
			try {
				rv = queryOne("SELECT 1");
			}
			catch (SQLException ex) {
				if (tryCnt <= 0)
					throw ex;
				logit("retry DB connection");
				-- tryCnt;
				env.close();
			}
		} while (rv == null);
	}
	
	public int execOne(String sql) throws Exception {
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
	public int execOne(String sql, boolean getNewId) throws Exception
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
		"tm1", dbExpr("now()"), // 使用dbExpr直接提供SQL表达式
		"amount", 100,
		"dscr", null // null字段会被忽略
	));

*/
	public int dbInsert(String table, Map<String,Object> kv) throws Exception
	{
		StringBuffer keys = new StringBuffer();
		StringBuffer values = new StringBuffer();

		for (String k : kv.keySet())
		{
			if (!k.matches("(?U)\\w+"))
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
			else if (oval instanceof DbExpr) {
				values.append(((DbExpr)oval).val);
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
		"tm", dbExpr("now()")  // 使用dbExpr，表示是SQL表达式
	), "tm IS NULL);
*/
	public int dbUpdate(String table, Map<String,Object> kv, Object cond) throws Exception
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
			else if (val instanceof DbExpr) {
				kvstr.append(k).append("=").append(((DbExpr)val).val);
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

/**<pre>
@fn dbExpr($val)

用于在dbInsert/dbUpdate(插入或更新数据库)时，使用表达式：

	int id = dbInsert("Ordr", asMap(
		"tm", dbExpr("now()") // 使用dbExpr直接提供SQL表达式
	));

*/
	public static DbExpr dbExpr(String val)
	{
		return new DbExpr(val);
	}

	public void addLog(String s)
	{
		addLog(s, 0);
	}
	// level?=0
	public void addLog(String s, int level)
	{
		if (env.debugLevel >= level)
		{
			if (env.isTestMode)
				System.err.println(s);
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
	public Object param(String name, Object defVal, Object coll, boolean doHtmlEscape) {
		String[] a = parseType(name);
		String type = a[0];
		name = a[1];
		@SuppressWarnings("unchecked")
		Object ret = (coll == null || coll instanceof String)? 
				env.getParam(name, (String)coll)
				: (coll instanceof Map)? ((Map<String,Object>)coll).get(name): null;
		
		if (ret == null || ret.equals(""))
			return defVal;

		if (ret != null && ret instanceof String) {
			String val = (String)ret;
			// avoid XSS attack
			if (doHtmlEscape)
				ret = htmlEscape(val);
			if (type.equals("s"))
			{
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
			else if (type == "js" || type == "tbl") {
				Object ret1 = jsonDecode((String)ret);
				if (ret1 == null)
					throw new MyException(E_PARAM, String.format("Bad Request - invalid json param `%s`=`%s`.", name, ret));

				if (type == "tbl") {
					ret1 = table2objarr(ret1);
					if (Objects.equals(ret1, false))
						throw new MyException(E_PARAM, String.format("Bad Request - invalid table param `%s`=`%s`.", name, ret));
				}
				ret = ret1;
			}
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
			throw new MyException(E_PARAM, "require param `" + name + "`", "缺少参数:" + name);
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

/**
%fn header(key)
%fn header(key, val)
%fn header(key, val, true)

获取或设置header. 第三种形式表示追加header
*/
	public String header(String key)
	{
		return env.request.getHeader(key);
	}
	public void header(String key, String value)
	{
		header(key, value, false);
	}
	public void header(String key, String value, boolean isAppend)
	{
		if (isAppend)
			env.response.addHeader(key, value);
		else
			env.response.setHeader(key, value);
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
			for (Object row: rs) {
				JsObject row1 = (JsObject)row;
				String[] h1 = row1.keySet().toArray(new String[0]);
				for (int i=fixedColCnt; i<h1.length; ++i) {
					if (! h.contains(h1[i])) {
						h.add(h1[i]);
					}
				}
			}
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
@fn table2objarr

将table格式转为 objarr, 即前端的rs2Array, 如：

	table2objarr(
		[
			"h"=>["id", "name"],
			"d"=>[ 
				[100,"A"], 
				[101,"B"]
			   ] 
		]
	) -> [ ["id"=>100, "name"=>"A"], ["id"=>101, "name"=>"B"] ]

 */
	public static List<Object> table2objarr(Object tbl)
	{
		List<Object> emptyArr = new ArrayList<Object>();
		if (!(tbl instanceof Map))
			return emptyArr;
		Map<String, Object> m = cast(tbl);
		if (! (m.get("h") instanceof List && m.get("d") instanceof List))
			return emptyArr;
		List<String> h = cast(m.get("h"));
		List<List<Object>> d = cast(m.get("d"));
		if (d.size() == 0 || h.size() != d.get(0).size())
			return emptyArr;
		return varr2objarr(d, h);
	}

/** <pre>
@fn varr2objarr(d, h)

将值数组类型 d (仅有值的二维数组, elem=[$col1, $col2] ) 转为对象数组objarr, elem={col1=>cell1, col2=>cell2})

例：

	varr2objarr(
		[ [100, "A"], [101, "B"] ], 
		["id", "name"] )
	-> [ ["id"=>100, "name"=>"A"], ["id"=>101, "name"=>"B"] ]

 */
	public static List<Object> varr2objarr(List<List<Object>> d, List<String> h)
	{
		List<Object> ret = new ArrayList<Object>();
		for (List<Object> row: d) {
			Map<String, Object> m = new HashMap<String, Object>();
			int i = 0;
			for (String col: h) {
				Object val = i<row.size()? row.get(i): null;
				m.put(col, val);
				++ i;
			}
			ret.add(m);
		}
		return ret;
	}

/**<pre>
@fn list2varr(ls, colSep=':', rowSep=',')

- ls: 代表二维表的字符串，有行列分隔符。
- colSep, rowSep: 列分隔符，行分隔符。

将字符串代表的压缩表("v1:v2:v3,...")转成值数组。

e.g.

	$users = "101:andy,102:beddy";
	$varr = list2varr($users);
	// $varr = [["101", "andy"], ["102", "beddy"]];
	
	$cmts = "101\thello\n102\tgood";
	$varr = list2varr($cmts, "\t", "\n");
	// $varr=[["101", "hello"], ["102", "good"]]
 */
	public static List<List<Object>> list2varr(String ls, String colSep, String rowSep)
	{
		List<List<Object>> ret = new ArrayList<>();
		for (String rowStr: ls.split(rowSep)) {
			String [] row1 = rowStr.trim().split(colSep);
			List<Object> row = new ArrayList<>(Arrays.asList(row1));
			ret.add(row);
		}
		return ret;
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
	
/**
%fn tmCols(fieldName = "t0.tm")

为查询添加时间维度单位: y,q,m,w,d,wd,h (年，季度，月，周，日，周几，时)。

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
				"quarter(" + fieldName + ") q",
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
	public String makeUrl(String ac, Map<String,Object> params) throws Exception
	{
		StringBuffer url = new StringBuffer();
		if (ac.matches("^[\\w\\.]+$"))
			url.append(getBaseUrl(false) + "/" + ac);
		else
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

- opt: {contentType, async}

e.g.

	String baseUrl = "http://oliveche.com/echo.php";
	// 常用asMap或new JsObject
	Map<String, Object> urlParams = asMap("intval", 100, "floatval", 12.345, "strval", "hello");
	JsObject postParams = new JsObject("postintval", 100, "poststrval", "中文");
	String rv = httpCall(baseUrl, urlParams, postParams, null);

- opt.async: 当设置为true时，不等服务端响应就关闭连接。

*/
	public String httpCall(String url, Map<String,Object> getParams, Object postParams, Map<String,Object> opt) throws Exception
	{
		String url1 = makeUrl(url, getParams);
		URL oUrl = new URL(url1);
		String ct = null;

		byte[] postBytes = null;
		String charset = "UTF-8";
		if (postParams != null) {
			String postStr = null;
			if (opt != null) 
				ct = (String)opt.get("contentType");
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

			postBytes = postStr.getBytes(charset);
		}
		boolean isAsync = opt != null && (boolean) opt.get("async");
		if (isAsync) {
			String host = oUrl.getHost();
			int port = oUrl.getPort();
			if (port == -1)
				port = oUrl.getDefaultPort();

			try (
				Socket sock = new Socket(host, port);
				OutputStream out = sock.getOutputStream()
			) {
				StringBuilder sb = new StringBuilder();
				sb.append(String.format("%s %s HTTP/1.1\r\nHost: %s\r\n", postBytes==null? "GET": "POST", url1, host));
				if (postBytes != null) {
					sb.append(String.format("Content-Type: %s;charset=%s\r\nContent-Length: %s\r\n", ct, charset, postBytes.length));
				}
				sb.append("Connection: Close\r\n\r\n");
				out.write(sb.toString().getBytes(charset));
				if (postBytes != null) {
					out.write(postBytes);
				}
			}
			return null;
		}

		HttpURLConnection conn = (HttpURLConnection)oUrl.openConnection();
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(20000);
		conn.setUseCaches(false);

		if (postBytes != null) {
			conn.setRequestProperty("Content-Type", ct + ";charset=" + charset);
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("POST");
		}
		conn.connect();
		if (postBytes != null) {
			try (OutputStream out = conn.getOutputStream()) {
				out.write(postBytes);
			}
		}
		ct = conn.getContentType();
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
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void callSvcAsync(String ac, Map params, Map postParams) throws Exception
	{
		env.onAfterActions.add( () -> {
			httpCall(ac, params, postParams, asMap("async", true));
		});
	}

/**<pre>
#fn pivit(objArr, gcols, xcolCnt=null)

将行转置到列。一般用于统计分析数据处理。

- gcols为转置字段，可以是一个或多个字段。可以是个字符串("f1" 或 "f1,f2")，也可以是个数组（如["f1","f2"]）
- objArr是对象数组，最后一列是统计列。

示例：

	JsArray arr = new JsArray(
		new JsObject("y",2019, "m",11, "cateId",1, "cateName","衣服", "sum",20000),
		new JsObject("y",2019, "m",11, "cateId",2, "cateName","食品", "sum",12000),
		new JsObject("y",2019, "m",12, "cateId",2, "cateName","食品", "sum",15000),
		new JsObject("y",2020, "m",2, "cateId",1, "cateName","衣服", "sum",19000)
	);

	// 将类别转到列
	JsArray arr2 = JDApiBase.pivot(arr, "cateId,cateName", null);

得到：

	[
	  { "y": 2019, "m": 11, "1-衣服": 20000.0, "2-食品": 12000.0 },
	  { "y": 2019, "m": 12, "2-食品": 15000.0 },
	  { "y": 2020, "m": 2, "1-衣服": 19000.0 }
	]

*/
	static JsArray pivot(JsArray objArr, String gcol, int[] out_xcolCnt) throws Exception
	{
		if (objArr.size() == 0)
			return objArr;

		List<String> gcols = asList(gcol.split("\\s*,\\s*"));

		if (gcols.size() == 0) {
			throw new MyException(E_PARAM, "bad gcols: no data", "未指定分组列");
		}
		JsObject row0 = cast(getJsValue(objArr, 0));
		Set<String> cols = row0.keySet(); // LinkedHashMap返回的set可保持字段顺序
		forEach(gcols, col -> {
			if (! cols.contains(col))
				throw new MyException(E_PARAM, "bad gcol " + col + ": not in cols", "分组列不正确: " + col);
		});

		// xcols = cols - ycol(最后一列) - gcols
		List<String> xcols = asList();
		int i =0;
		int colLen = cols.size();
		for (String col: cols) {
			++ i;
			if (i == colLen)
				continue;
			if (gcols.contains(col))
				continue;
			xcols.add(col);
		}
		if (out_xcolCnt != null)
			out_xcolCnt[0] = xcols.size();
		
		JsObject xMap = new JsObject(); // {x=>新行}

		forEach(objArr, rowA -> {
			JsObject row = cast(rowA);
			// x = xtext(row);
			JsObject xarr = new JsObject();
			for (String col: xcols) {
				xarr.put(col, row.get(col));
			}
			String x = join("-", xarr.values());

			JsArray garr = new JsArray();
			for (String col: gcols) {
				garr.add(row.get(col));
			}
			String g = join("-", garr);

			if (! xMap.containsKey(x)) {
				xMap.put(x, xarr);
			}
			Object[] lastOne = new Object[] {null}; // row中最后一列，且应是数值
			forEach(row, (k, v) -> {
				lastOne[0] = v;
			});
			double y = doubleValue(lastOne[0]);

			JsObject row1 = cast(xMap.get(x));
			if (! row1.containsKey(g))
				row1.put(g, y);
			else
				row1.put(g, (Double)row1.get(g) + y);
		});

		JsArray ret = new JsArray();
		forEach(xMap, (k, v) -> {
			ret.add(v);
		});
		return ret;
	}
}
