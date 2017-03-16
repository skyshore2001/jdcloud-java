package com.jdcloud;

import java.sql.SQLException;

public class Demo {

}

class JDEnv extends JDEnvBase
{
	public int onGetPerms()
	{
		int perms = 0;
		if (api.getSession("uid") != null)
			perms |= JDApiBase.AUTH_USER;

		return perms;
	}

	public String onCreateAC(String table)
	{
		if (api.hasPerm(JDApiBase.AUTH_USER))
			return "AC1_" + table;
		return "AC_" + table;
	}
}

class Global extends JDApiBase
{
	public Object api_hello() throws Exception
	{
		Object ret = queryAll("SELECT id, uname FROM User LIMIT 20", true);
		return ret;
	}

	public Object api_fn() throws Exception
	{
		Object ret = null;
		String f = (String)mparam("f");
		if (f.equals("param"))
		{
			String name = (String)mparam("name");
			String coll = (String)param("coll");
			String defVal = (String)param("defVal");

			ret = param(name, defVal, coll);
		}
		else if (f.equals("mparam"))
		{
			String name = (String)mparam("name");
			String coll = (String)param("coll");
			ret = mparam(name, coll);
		}

		else if (f.equals("queryAll"))
		{
			String sql = (String)mparam("sql", null, false);
			boolean assoc = (boolean)param("assoc/b", false);
			ret = queryAll(sql, assoc);
		}
		else if (f.equals("queryOne"))
		{
			String sql = (String)mparam("sql", null, false);
			boolean assoc = (boolean)param("assoc/b", false);
			ret = queryOne(sql, assoc);
		}
		else if (f.equals("execOne"))
		{
			String sql = (String)mparam("sql", null, false);
			boolean getNewId = (boolean)param("getNewId/b", false);
			ret = execOne(sql, getNewId);
		}
		else
			throw new MyException(E_SERVER, "not implemented");
		return ret;
	}

	public Object api_login() throws Exception
	{
		String uname = (String)mparam("uname");
		String pwd = (String)mparam("pwd");

		String sql = String.format("SELECT id FROM User WHERE uname=%s", Q(uname));
		Object id = queryOne(sql);
		if (id.equals(false))
			throw new MyException(E_AUTHFAIL, "bad uname or pwd");
		setSession("uid", id);
		return new JsObject(
			"id", id
		);
	}

	public Object api_whoami()
	{
		checkAuth(AUTH_USER);
		int uid = (int)getSession("uid");
		return new JsObject(
			"id", uid
		);
	}
	public void api_logout()
	{
		// checkAuth(AUTH_LOGIN);
		destroySession();
	}
}
