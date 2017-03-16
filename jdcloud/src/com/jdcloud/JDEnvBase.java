package com.jdcloud;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.*;

public class JDEnvBase
{
	public class CallSvcOpt
	{
		public JsObject _GET, _POST;
		public boolean isCleanCall = false;
		public String ac;
		public boolean asAdmin = false;
	}

	public boolean isTestMode = true;
	public int debugLevel = 0;
	public JsArray debugInfo = new JsArray();
	public String appName, appType;
	
	public JDApiBase api = new JDApiBase();
	public JsObject _GET, _POST, _SERVER, _SESSION;
	
	public Object callSvc(String ac) throws Throwable
	{
		return callSvc(ac, null);
	}
	
	public Object callSvc(String ac, CallSvcOpt opt) throws Throwable
	{
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

		JDApiBase obj = null;
		Class<?> t;
		Method mi = null;
		Object ret = null;
		try {
			t = Class.forName("com.jdcloud." + clsName); // JDApi
			obj = (JDApiBase)t.newInstance();
			mi = t.getDeclaredMethod(methodName);
			if (clsName == "Global")
			{
				ret = mi.invoke(obj);
			}
			else if (obj instanceof AccessControl)
			{
				AccessControl accessCtl = (AccessControl)obj;
				/*
				accessCtl.init(table, ac1);
				accessCtl.before();
				*/
				Object rv = mi.invoke(obj);
				/*
				//ret[1] = t.InvokeMember(methodName, BindingFlags.InvokeMethod, null, obj, null);
				accessCtl.after(ref rv);
				*/
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
		obj.env = this;
/*
		NameValueCollection[] bak = null;
		if (opt != null)
		{
			if (opt.isCleanCall || opt._GET != null|| opt._POST != null)
			{
				bak = new NameValueCollection[] { this._GET, this._POST };
				if (opt.isCleanCall)
				{
					this._GET = new NameValueCollection();
					this._POST = new NameValueCollection();
				}
				if (opt._GET != null)
				{
					for (String k : opt._GET)
					{
						this._GET[k] = opt._GET[k];
					}
				}
				if (opt._POST != null)
				{
					for (String k : opt._POST)
					{
						this._POST[k] = opt._POST[k];
					}
				}
			}
		}
*/
		/*
		if (bak != null)
		{
			this._GET = bak[0] as NameValueCollection;
			this._POST = bak[1] as NameValueCollection;
		}
		*/
		return ret;
	}

	public String onCreateAC(String table)
	{
		return "AC_" + table;
	}

	public int onGetPerms()
	{
		return 0;
	}
}
