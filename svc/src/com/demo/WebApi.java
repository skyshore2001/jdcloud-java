package com.demo;

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

	@Override
	public String[] onCreateApi()
	{
		return new String[] { "Global", "JDLogin", "JDUpload" };
	}

	@Override
	protected int onGetPerms() {
		int perms = super.onGetPerms();
		if ((perms | JDApiBase.AUTH_EMP) != 0) {
			String[] permArr = (String[]) api.getSession("perms");
			if (permArr != null) {
				for (String perm : permArr) {
					if (perm.equals("mgr")) {
						perms |= App.PERM_MGR;
					}
				}
			}
		}
		return perms;
	}
}

class App
{
	public static final int PERM_MGR = 0x100;  // 经理权限
}

class Global extends JDApiBase
{
	public Object api_hello() throws Exception
	{
		return new JsObject("id", 100, "name", "jdcloud");
	}
}

class AC0_User extends AccessControl
{
	@Override
	protected void onInit() throws Exception {
		this.vcolDefs = asList(
			// 为演示统计表，增加两个虚拟字段sex,addr
			new VcolDef().res("if(t0.id mod 3=1, 'F', 'M') sex", "if(t0.id mod 3=1, '女', '男') sexName", "if(t0.id mod 3=2, '北京', '上海') region"),
			new VcolDef().res(tmCols("t0.createTm"))
		);
	}

	@Override
	protected void onValidate()
	{
		if (issetval("pwd")) {
			String pwd = (String)env._POST.get("pwd");
			env._POST.put("pwd", hashPwd(pwd));
		}
	}
}

class AC1_User extends AccessControl
{
	@Override
	protected void onInit() throws Exception {
		this.allowedAc = asList("get", "set");
		this.readonlyFields = asList("pwd");
	}

	@Override
	protected void onValidateId()
	{
		Object uid = getSession("uid");
		env._GET.put("id", uid);
	}
}

class AC2_User extends AccessControl
{
	@Override
	protected void onInit() throws Exception {
		this.allowedAc = asList("query", "get");
	}
}

class AC0_Employee extends AccessControl
{
	@Override
	protected void onValidate()
	{
		if (this.ac.equals("add") && !issetval("perms")) {
			env._POST.put("perms", "emp");
		}

		if (issetval("pwd")) {
			String pwd = (String)env._POST.get("pwd");
			env._POST.put("pwd", hashPwd(pwd));
		}
	}
}

class AC2_Employee extends AC0_Employee
{
	@Override
	protected void onInit() {
		this.requiredFields = asList("uname", "pwd");

		if (! hasPerm(App.PERM_MGR)) {
			this.allowedAc = asList("query", "get", "set");
		}
		// else it has all perms
	}

	@Override
	protected void onValidateId()
	{
		Object id = param("id");
		if (!hasPerm(App.PERM_MGR) || id == null) {
			env._GET.put("id", getSession("empId"));
		}
	}
}

class AC0_Ordr extends AccessControl
{
	@Override
	protected void onInit() throws Exception {
		this.vcolDefs = asList(
			new VcolDef().res("u.name AS userName", "u.phone AS userPhone").join("INNER JOIN User u ON u.id=t0.userId"),
			new VcolDef().res("log_cr.tm AS createTm").join("LEFT JOIN OrderLog log_cr ON log_cr.action='CR' AND log_cr.orderId=t0.id"),
			new VcolDef().res(tmCols("log_cr.tm")).require("createTm")
		);
		
		this.subobj = asMap(
			"orderLog", new SubobjDef().sql("SELECT ol.*, e.uname AS empPhone, e.name AS empName FROM OrderLog ol LEFT JOIN Employee e ON ol.empId=e.id WHERE orderId=%d"),
			"atts", new SubobjDef().sql("SELECT id, attId FROM OrderAtt WHERE orderId=%d")
		);
	}
}

class AC1_Ordr extends AC0_Ordr
{
	@Override
	protected void onInit() throws Exception {
		super.onInit();
		this.allowedAc = asList("get", "query", "add", "set");
		this.readonlyFields = asList("userId");
	}

	@Override
	protected void onQuery()
	{
		Object uid = getSession("uid");
		this.addCond(String.format("t0.userId=%s", uid));
	}

	@Override
	protected void onValidate()
	{
		String logAction = null;
		if (this.ac.equals("add")) {
			Object userId = getSession("uid");
			env._POST.put("userId", userId);
			env._POST.put("status", "CR");
			logAction = "CR";
		}
		else {
			if (issetval("status")) {
				// TODO: validate status
				logAction = (String)env._POST.get("status");
			}
		}

		if (logAction != null) {
			final String logAction1 = logAction;
			this.onAfterActions.add( e -> {
				Object orderId = this.id;
				String sql = String.format("INSERT INTO OrderLog (orderId, action, tm) VALUES (%s,%s,'%s')", orderId, Q(logAction1), date());
				execOne(sql);
			});
		}
	}
}

class AC2_Ordr extends AC0_Ordr
{
	@Override
	protected void onInit() throws Exception
	{
		super.onInit();
		this.allowedAc = asList("get", "query", "set");
		this.readonlyFields = asList("userId");
	}

	@Override
	protected void onValidate() throws Exception
	{
		if (this.ac.equals("set")) {
			if (issetval("status")) {
				Object status = env._POST.get("status");
				if (status.equals("RE") || status.equals("CA")) {
					Object oldStatus = queryOne(String.format("SELECT status FROM Ordr WHERE id=%s", this.id));
					if (! oldStatus.equals("CR")) {
						throw new MyException(E_FORBIDDEN, String.format("forbidden to change status to %s", status));
					}
					this.onAfterActions.add(ret -> {
						Object orderId = this.id;
						Object empId = getSession("empId");
						String sql = String.format("INSERT INTO OrderLog (orderId, action, tm, empId) VALUES (%s,'%s','%s',%s)", orderId, status, date(), empId);
						execOne(sql);
					});
				}
				else {
					throw new MyException(E_FORBIDDEN, String.format("forbidden to change status to %s", status));
				}
			}
		}
	}

/*	delIf/setIf示例

	@Override
	public Object api_delIf() throws Exception {
		checkAuth(App.PERM_MGR);
		return super.api_delIf();
	}
	@Override
	public Object api_setIf() throws Exception {
		checkAuth(App.PERM_MGR);
		this.checkSetFields(asList("dscr", "cmt"));
		Object empId = getSession("empId");
		addCond("t0.id=" + empId);
		return super.api_setIf();
	}
*/
}

class AC0_ApiLog extends AccessControl
{
	@Override
	protected void onInit()
	{
		this.vcolDefs = asList(
			new VcolDef().res(tmCols())
		);
	}
}
