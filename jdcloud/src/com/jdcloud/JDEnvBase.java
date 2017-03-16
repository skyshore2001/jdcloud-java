package com.jdcloud;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.*;
import javax.servlet.http.*;

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

	public Connection conn;
	public HttpServletRequest request;
	public HttpServletResponse response;
	public JsObject _GET, _POST;
	
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

		JDApiBase api = null;
		Class<?> t;
		Method mi = null;
		Object ret = null;
		try {
			t = Class.forName("com.jdcloud." + clsName); // JDApi
			api = (JDApiBase)t.newInstance();
			api.env = this;
			mi = t.getDeclaredMethod(methodName);
			if (clsName == "Global")
			{
				ret = mi.invoke(api);
			}
			else if (api instanceof AccessControl)
			{
				AccessControl accessCtl = (AccessControl)api;
				/*
				accessCtl.init(table, ac1);
				accessCtl.before();
				*/
				Object rv = mi.invoke(api);
				/*
				//ret[1] = t.InvokeMember(methodName, BindingFlags.InvokeMethod, null, api, null);
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

	public void init(HttpServletRequest request, HttpServletResponse response)
	{
		this.request = request;
		this.response = response;
		this.api = new JDApiBase();
		this.api.env = this;
		
		String q = this.request.getQueryString();
		Set<String> set = new HashSet<String>();
		for (String q1 : q.split("&")) {
			for (String k: q1.split("=")) {
				set.add(k);
			}
		}
		this._GET = new JsObject();
		this._POST = new JsObject();
		Enumeration<String> em = this.request.getParameterNames();
		while (em.hasMoreElements()) {
			String k = em.nextElement();
			if (set.contains(k))
				this._GET.put(k, request.getParameter(k));
			else
				this._POST.put(k, request.getParameter(k));
		}
/* TODO 
		this.isTestMode = int.Parse(ConfigurationManager.AppSettings["P_TESTMODE"] ?? "0") != 0;
		this.debugLevel = int.Parse(ConfigurationManager.AppSettings["P_DEBUG"] ?? "0");

		this.appName = api.param("_app", "user", "G") as string;
		this.appType = Regex.Replace(this.appName, @"(\d+|-\w+)$", "");
*/
		if (this.isTestMode)
		{
			api.header("X-Daca-Test-Mode", "1");
		}
		// TODO: X-Daca-Mock-Mode, X-Daca-Server-Rev
	}
	
	// coll: "G"-GET, "P"-POST, null-BOTH
	public String getParam(String name, String coll) {
		if (coll == null) {
			return this.request.getParameter(name);
		}
		if (coll.equals("G")) {
			return (String)_GET.get(name);
		}
		if (coll.equals("P")) {
			return (String)_POST.get(name);
		}
		return null;
	}
}
