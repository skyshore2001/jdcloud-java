package com.jdcloud.app;

import java.lang.reflect.Method;
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
		if ((perms | AUTH_EMP) != 0) {
			String[] permArr = (String[]) _SESSION("perms");
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
			String pwd = (String)_POST("pwd");
			_POST("pwd", hashPwd(pwd));
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
		Object uid = _SESSION("uid");
		_GET("id", uid);
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
			_POST("perms", "emp");
		}

		if (issetval("pwd")) {
			String pwd = (String)_POST("pwd");
			_POST("pwd", hashPwd(pwd));
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
			_GET("id", _SESSION("empId"));
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
			"orderLog", new SubobjDef().obj("OrderLog").AC("AccessControl").cond("orderId={id}"),
			"atts", new SubobjDef().obj("OrderAtt").AC("AccessControl").cond("orderId={id}").res("id, attId")
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
		Object uid = _SESSION("uid");
		this.addCond(String.format("t0.userId=%s", uid));
	}

	@Override
	protected void onValidate()
	{
		String logAction = null;
		if (this.ac.equals("add")) {
			Object userId = _SESSION("uid");
			_POST("userId", userId);
			_POST("status", "CR");
			logAction = "CR";
		}
		else {
			if (issetval("status")) {
				// TODO: validate status
				logAction = (String)_POST("status");
			}
		}

		if (logAction != null) {
			final String logAction1 = logAction;
			this.onAfterActions.add( e -> {
				Object orderId = this.id;
				dbInsert("OrderLog", asMap(
					"orderId", orderId,
					"action", logAction1,
					"tm", date()
				));
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
				Object status = _POST("status");
				if (status.equals("RE") || status.equals("CA")) {
					Object oldStatus = queryOne(String.format("SELECT status FROM Ordr WHERE id=%s", this.id));
					if (! oldStatus.equals("CR")) {
						jdRet(E_FORBIDDEN, String.format("forbidden to change status to %s", status));
					}
					this.onAfterActions.add(ret -> {
						Object orderId = this.id;
						dbInsert("OrderLog", asMap(
							"orderId", orderId,
							"action", status,
							"empId", _SESSION("empId"),
							"tm", date()
						));
					});
				}
				else {
					jdRet(E_FORBIDDEN, String.format("forbidden to change status to %s", status));
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
		Object empId = _SESSION("empId");
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
