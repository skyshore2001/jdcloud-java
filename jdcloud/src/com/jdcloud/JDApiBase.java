package com.jdcloud;

import java.sql.*;
import java.util.*;

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
	public Connection conn;
	
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
		if (this.conn == null) {
			String connStr = "jdbc:mysql://oliveche.com:3306/jdcloud2";
			try {
				Class.forName("com.mysql.jdbc.Driver");
			} catch (ClassNotFoundException e) {
				throw new MyException(E_DB, "db driver not found");
			}
			String user = "demo";
			String pwd = "tuuj7PNDC";
			try {
				this.conn = DriverManager.getConnection(connStr, user, pwd);
			} catch (SQLException e) {
				throw new MyException(E_DB, "db connection fails", "数据库连接失败。");
			}
		}
	}
	public void close() throws SQLException
	{
		conn.close();
	}
	
	public JsArray queryAll(String sql) throws SQLException, MyException
	{
		return this.queryAll(sql, false);
	}
	public JsArray queryAll(String sql, boolean assoc) throws SQLException, MyException
	{
		dbconn();
		Statement stmt = conn.createStatement();
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
		Statement stmt = conn.createStatement();
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
		Statement stmt = conn.createStatement();
		int rv = stmt.executeUpdate(sql, getNewId? Statement.RETURN_GENERATED_KEYS: Statement.NO_GENERATED_KEYS);
		if (getNewId) {
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next())
				rv = rs.getInt(0);
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
		gb.serializeNulls().disableHtmlEscaping();
		if (doFormat)
			gb.setPrettyPrinting();
		Gson gson = gb.create();
		return gson.toJson(o);
	}
}
