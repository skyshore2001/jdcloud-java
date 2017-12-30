package com.demo.rtest;

import java.lang.reflect.Method;
import java.util.*;
import com.jdcloud.*;

public class WebApi extends JDEnvBase
{
	@Override
	protected Object onNewInstance(Class<?> t) throws Exception
	{
		return t.newInstance();
	}
	
	@Override
	protected Object onInvoke(Method mi, Object arg) throws Exception
	{
		return mi.invoke(arg);
	}
}

class Global extends JDApiBase
{
	public Object api_hello() throws Exception
	{
		return new JsObject("id", 100, "name", "jdcloud");
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
		@SuppressWarnings("unused")
		String pwd = (String)mparam("pwd");

		String key = Character.isDigit(uname.charAt(0))? "phone": "uname";
		String sql = String.format("SELECT id FROM User WHERE %s=%s", key, Q(uname));
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

class AC_ApiLog extends AccessControl
{
	@Override
	protected void onInit()
	{
		this.requiredFields = asList("ac");
		this.readonlyFields = asList("ac", "tm");
		this.hiddenFields = asList("ua");
	}
	@Override
	protected void onValidate()
	{
		if (this.ac.equals("add"))
		{
			env._POST.put("tm", date());
		}
	}
}

class AC1_UserApiLog extends AC_ApiLog
{
	private int uid;

	@Override
	protected void  onInit()
	{
		this.allowedAc = asList("get", "query", "add", "del");
		this.uid = (int)this.getSession("uid");

		this.table = "ApiLog";
		this.defaultSort = "id DESC";
		
		VcolDef vcol = null;
		if (env.dbType.equals("mssql")) {
			vcol = new VcolDef().res( 
"(SELECT TOP 3 cast(id as varchar) + ':' + ac + ',' " +
"FROM ApiLog log " +
"WHERE userId=" + this.uid + " ORDER BY id DESC FOR XML PATH('') " +
") last3LogAc");
		}
		else if (env.dbType.equals("mysql")) {
			vcol = new VcolDef().res(
"(SELECT group_concat(concat(id, ':', ac)) " + 
"FROM (" +
"SELECT id, ac " +
"FROM ApiLog " +
"WHERE userId=" + this.uid + " ORDER BY id DESC LIMIT 3) t" +
") last3LogAc");
		}
	
		this.vcolDefs = asList(
			new VcolDef().res("u.name userName")
				.join("INNER JOIN User u ON u.id=t0.userId")
				.isDefault(true),
			vcol
		);

		this.subobj = asMap(
			"user", new SubobjDef()
				.sql("SELECT id,name FROM User u WHERE id=" + this.uid)
				.wantOne(true),
			"last3Log", new SubobjDef()
				.sql(env.fixPaging("SELECT id,ac FROM ApiLog log WHERE userId=" + this.uid + " ORDER BY id DESC LIMIT 3"))
		);
	}

	@Override
	protected void onValidate()
	{
		super.onValidate();
		if (this.ac.equals("add"))
		{
			env._POST.put("userId", this.uid);
		}
	}

	@Override
	protected void onValidateId() throws Exception
	{
		if (this.ac.equals("del"))
		{
			int id = (int)mparam("id");
			Object rv = queryOne("SELECT id FROM ApiLog WHERE id=" + id + " AND userId=" + this.uid);
			if (!rv.equals(false))
				throw new MyException(E_FORBIDDEN, "not your log");
		}
	}

	@Override
	protected void onQuery() throws Exception
	{
		super.onQuery();
		this.addCond("userId=" + this.uid);
	}

	public Object api_listByAc() throws Exception
	{
		String ac = (String)mparam("ac", "G");
		JsObject param = new JsObject(
			"fmt", "list",
			"cond", "ac=" + Q(ac)
		);

		return env.callSvc("UserApiLog.query", param, null, null);
	}
}
