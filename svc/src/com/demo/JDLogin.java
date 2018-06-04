package com.demo;

import java.util.regex.*;
import com.jdcloud.*;

public class JDLogin extends JDApiBase 
{
	final String AUTO_PWD_PREFIX = "AUTO"; 

	/*String hashPwd(String pwd)
	{
		if (pwd.length() == 32 || pwd.length() == 0)
			return pwd;
		return md5(pwd);
	}*/
	
	char randChr(char t)
	{
		while (true) {
			char c = 0;
			// all digits (no 0)
			if (t == 'd') {
				c = (char)rand((int)'1', (int)'9');
				return c;
			}
			
			// A-Z (no O, I)
			c = (char)rand((int)'A', (int)'Z');
			if (c == 'O' || c == 'I')
				continue;
			return c;
		}
	}

	// e.g. genDynCode("d4") - 4 digits
	// e.g. genDynCode("w4") - 4 chars (capital letters)
	String genDynCode(String type)
	{
		char t = type.charAt(0);
		int n = Integer.parseInt(type.substring(1));
		if (n <= 0)
			throw new MyException(E_PARAM, String.format("Bad type '%s' for genCode", type));
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<n; ++i) {
			sb.append(randChr(t));
		}
		return sb.toString();
	}

	void validateDynCode(String code, String phone)
	{
		String code1 = (String)getSession("code");
		String phone1 = (String)getSession("phone");
		Long codetm = (Long)getSession("codetm");

		do {
			if (env.isTestMode && code.equals("080909"))
				break;
	
			//special number and code not verify
			if ( phone != null && phone.equals("12345678901") && code.equals("080909"))
				break;
	
			if (code1 == null || phone1 == null)
				throw new MyException(E_FORBIDDEN, "gencode required", "请先发送验证码!");
	
			long now = time();
			if (now - codetm > 5*T_MIN)
				throw new MyException(E_FORBIDDEN, "code expires (max 5min)", "验证码已过期(有效期5分钟)");
	
			if (! code.equals(code1))
				throw new MyException(E_PARAM, "bad code", "验证码错误");
	
			if (phone != null && ! phone.equals(phone1))
				throw new MyException(E_PARAM, String.format("bad phone number. expect for phone '%s'", phone1));
			
		} while (false);
	
		unsetSession("code");
		unsetSession("phone");
		unsetSession("codetm");
	}

	void sendSms(String phone, String msg)
	{
		// TODO
	}
	public Object api_genCode() throws Exception
	{
		String phone = (String)mparam("phone");
		Matcher m = null;
		if (phone != null && !(m=regexMatch(phone, "^\\d{11}$")).find()) 
			throw new MyException(E_PARAM, "bad phone number", "手机号不合法");

		String type = (String)param("type", "d6");
		boolean debug = env.isTestMode? (boolean)param("debug/b", false): false;
		Long codetm = (Long)getSession("codetm");
		// dont generate again in 60s
		long now = time();
		if (!debug && codetm != null && now - codetm < 55 * T_SEC) // 说60s, 其实不到, 避免时间差一点导致出错.
			throw new MyException(E_FORBIDDEN, "gencode is not allowed to call again in 60s", "60秒内只能生成一次验证码");
		// TODO: not allow to gencode again for the same phone in 60s

		setSession("phone", phone);
		setSession("code", genDynCode(type));
		setSession("codetm", now);

		Object ret = null;
		// send code via phone
		if (debug)
			ret = new JsObject("code", getSession("code"));
		else {
			sendSms(phone, "验证码" + getSession("code") + "，请在5分钟内使用。");
		}

		return ret;
	}

	JsObject regUser(String phone, String pwd) throws Exception
	{
		String phone1 = regexReplace(phone, "(?<=^\\d{3})\\d{4}", "****");
		String name = "用户" + phone1;

		String sql = String.format("INSERT INTO User (phone, pwd, name, createTm) VALUES (%s, %s, %s, '%s')",
			Q(phone),
			Q(hashPwd(pwd)),
			Q(name),
			date()
		);
		int id = execOne(sql, true);
		addToPwdTable(pwd);
		JsObject ret = new JsObject("id", id);

		return ret;
	}
	
	class Token {
		String uname;
		String pwd;
		long create;
		long expire;
	}
	String genLoginToken(JsObject ret, String uname, String pwd) throws Exception
	{
		Token token = new Token();
		token.uname = uname;
		token.pwd = pwd;
		token.create = time();
		token.expire = T_DAY * 500; // TODO
		String tokenStr = myEncrypt(jsonEncode(token), "E", null);
		ret.put("_token", tokenStr);
		ret.put("_expire", token.expire);
		return tokenStr;
	}

	Token parseLoginToken(String tokenStr) throws Exception
	{
		Token token = jsonDecode(myEncrypt(tokenStr, "D", null), Token.class);
		if (token == null)
			throw new MyException(E_AUTHFAIL, "Bad login token!");

		/*
		boolean isValid = data.keySet().containsAll(new JsArray("uname", "pwd", "create", "expire"));
		if (! isValid)
			throw new MyException(E_AUTHFAIL, "Bad login token (miss some fields)!");
		*/
		// TODO: check timeout
		long now = time();
		if (token.create + token.expire < now)
			throw new MyException(E_AUTHFAIL, "token exipres");

		return token;
	}

	public Object api_login() throws Exception
	{
		String type = env.appType;
		JsObject ret = null;

		if (! type.equals("user") && ! type.equals("emp") && ! type.equals("admin")) {
			throw new MyException(E_PARAM, String.format("Unknown type `%s`", type));
		}

		String token = (String)param("token");
		String uname, pwd, code = null;
		if (token != null) {
			Token rv = parseLoginToken(token);
			uname = rv.uname;
			pwd = rv.pwd;
		}
		else {
			uname = (String)mparam("uname");
			JsArray rv = mparam(new String[] {"pwd", "code"});
			pwd = (String)rv.get(0);
			code = (String)rv.get(1);
		}

		if (code != null && code.length() > 0)
		{
			validateDynCode(code, uname);
			pwd = null;
		}

		String key = "uname";
		if (Character.isDigit(uname.charAt(0)))
			key = "phone";

		String obj = null;
		// user login
		if (type.equals("user")) {
			obj = "User";
			String sql = String.format("SELECT id,pwd FROM User WHERE %s=%s", key, Q(uname));
			Object rv = queryOne(sql, true);

			if (rv.equals(false)) {
				// code通过验证，直接注册新用户
				if (code != null)
				{
					pwd = AUTO_PWD_PREFIX + genDynCode("d4");
					ret = regUser(uname, pwd);
					ret.put("_isNew", 1);
				}
			}
			else {
				JsObject row = (JsObject)rv;
				if (code != null || (pwd != null && hashPwd(pwd).equals(row.get("pwd")) ))
				{
					if (pwd == null)
						pwd = (String)row.get("pwd"); // 用于生成token
					ret = new JsObject("id", row.get("id"));
				}
			}
			if (ret == null)
				throw new MyException(E_AUTHFAIL, "bad uname or password", "手机号或密码错误");

			setSession("uid", ret.get("id"));
		}
		else if (type.equals("emp")) {
			obj = "Employee";
			String sql = String.format("SELECT id,pwd,perms FROM Employee WHERE %s=%s", key, Q(uname));
			Object rv = queryOne(sql, true);
			JsObject row = null;
			if (rv.equals(false) || (pwd != null && (row=(JsObject)rv)!=null && !hashPwd(pwd).equals(row.get("pwd"))) )
				throw new MyException(E_AUTHFAIL, "bad uname or password", "用户名或密码错误");

			setSession("empId", row.get("id"));
			String perms = (String) row.get("perms");
			if (perms != null) {
				String[] permArr = perms.split(",");
				setSession("perms", permArr);
			}

			ret = new JsObject("id", row.get("id"));
		}
		// admin login
		else if (type.equals("admin")) {
			String[] cred = getCred(getenv("P_ADMIN_CRED"));
			if (cred == null)
				throw new MyException(E_AUTHFAIL, "admin user is not enabled.", "超级管理员用户未设置，不可登录。");
			String uname1 = cred[0], pwd1 = cred[1]; 
			if (!uname.equals(uname1) || !pwd.equals(pwd1))
				throw new MyException(E_AUTHFAIL, "bad uname or password", "用户名或密码错误");
			int adminId = 1;
			setSession("adminId", adminId);
			ret = new JsObject("id", adminId, "uname", uname1);
		}

		if (obj != null)
		{
			JsObject rv = (JsObject)env.callSvc(obj + ".get");
			ret.putAll(rv);
		}

		if (token == null) {
			genLoginToken(ret, uname, pwd);
		}
		return ret;
	}

	public Object api_logout()
	{
		destroySession();
		return "OK";
	}

	Object setUserPwd(int userId, String pwd, boolean genToken) throws Exception
	{
		// change password
		String sql = String.format("UPDATE User SET pwd=%s WHERE id=%d", 
			Q(hashPwd(pwd)),
			userId);
		execOne(sql);

		if (genToken) {
			JsArray rv = (JsArray)queryOne("SELECT phone, pwd FROM User WHERE id=" + userId);
			JsObject ret = new JsObject();
			genLoginToken(ret, (String)rv.get(0), (String)rv.get(1));
			return ret;
		}
		return "OK";
	}

	Object setEmployeePwd(int empId, String pwd, boolean genToken) throws Exception
	{
		// change password
		String sql = String.format("UPDATE Employee SET pwd=%s WHERE id=%d", 
			Q(hashPwd(pwd)),
			empId);
		execOne(sql);

		if (genToken) {
			// [uname, pwd]
			JsArray rv = (JsArray)queryOne("SELECT phone, pwd FROM Employee WHERE id=" + empId);
			JsObject ret = new JsObject();
			genLoginToken(ret, (String)rv.get(0), (String)rv.get(1));
			return ret;
		}
		return "OK";
	}

	// 制作密码字典。
	void addToPwdTable(String pwd) throws Exception
	{
		if (pwd.startsWith(AUTO_PWD_PREFIX))
			return;
		String sql = "SELECT id FROM Pwd WHERE pwd=" + Q(pwd);
		Object id = queryOne(sql);
		if (id.equals(false)) {
			sql = String.format("INSERT INTO Pwd (pwd, cnt) VALUES (%s, 1)", Q(pwd));
			execOne(sql);
		}
		else {
			sql = "UPDATE Pwd SET cnt=cnt+1 WHERE id=" + id;
			execOne(sql);
		}
	}

	public Object api_chpwd() throws Exception
	{
		String type = env.appType;
		Integer uid = null;

		if (type.equals("user")) {
			checkAuth(AUTH_USER);
			uid = (Integer)getSession("uid");
		}
		else if(type.equals("emp")) {
			checkAuth(AUTH_EMP);
			uid = (Integer)getSession("empId");
		}
		String pwd = (String)mparam("pwd");
		JsArray rv = mparam(new String[] {"oldpwd", "code"});
		String oldpwd = (String)rv.get(0), code = (String)rv.get(1);
		String sql = null;
		if (oldpwd != null) {
			// validate oldpwd
			if (type.equals("user") && oldpwd.equals("_none")) { // 表示不要验证，但只限于新用户注册1小时内
				String dtStr = date(FMT_DT, time()-T_HOUR);
				sql = String.format("SELECT id FROM User WHERE id=%d and createTm>'%s'", uid, dtStr);
			}
			else if(type.equals("user")){
				sql = String.format("SELECT id FROM User WHERE id=%d and pwd=%s", uid, Q(hashPwd(oldpwd)));
			}
			else if(type.equals("emp")){
				sql = String.format("SELECT id FROM Employee WHERE id=%d and pwd=%s", uid, Q(hashPwd(oldpwd)));
			}
			Object rv1 = queryOne(sql);
			if (rv1.equals(false))
				throw new MyException(E_AUTHFAIL, "bad password", "密码验证失败");
		}
		Object ret = null;
		// change password
		if(type.equals("user")){
			ret = setUserPwd(uid, pwd, true);
		}
		else if(type.equals("emp")){
			ret = setEmployeePwd(uid, pwd, true);
		}

		addToPwdTable(pwd);
		return ret;
	}

}
