package com.jdcloud;

import java.util.HashMap;
import java.util.Map;

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
}
