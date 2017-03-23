package com.jdcloud;

import java.io.IOException;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class JDHandler
 */
@WebServlet("/api/*")
public class JDHandler extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#service(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		JsArray ret = new JsArray(0, null);
		response.setContentType("text/plain; charset=utf-8");
		JDEnvBase env = null;
		boolean ok = false;
		boolean dret = false;
		try {
			env = (JDEnvBase)Class.forName("com.jdcloud.JDEnv").newInstance(); // TODO: new JDEnvBase();
			env.init(request, response);
			String origin = request.getHeader("Origin");
			if (env.isTestMode && origin != null)
			{
				response.setHeader("Access-Control-Allow-Origin", origin);
				response.setHeader("Access-Control-Allow-Credentials", "true");
			}

			Pattern re = Pattern.compile("([\\w|.]+)$");
			Matcher m = re.matcher(request.getPathInfo());
			if (! m.find()) {
				throw new MyException(JDApiBase.E_PARAM, "bad ac");
			}
			String ac = m.group(1);
			Object rv = env.callSvc(ac);
			if (rv == null)
				rv = "OK";
			ok = true;
			ret.set(1, rv);
		}
		catch (DirectReturn ex) {
			ok = true;
			dret = true;
		}
		catch (MyException ex) {
			ret.set(0, ex.getCode());
			ret.set(1, ex.getMessage());
			ret.add(ex.getDebugInfo());
		}
		catch (Exception ex)
		{
			int code = ex instanceof SQLException? JDApiBase.E_DB: JDApiBase.E_SERVER;
			ret.set(0, code);
			ret.set(1, JDApiBase.GetErrInfo(code));
			if (env.isTestMode) 
			{
				String msg = ex.getMessage();
				if (msg == null)
					msg = ex.getClass().getName();
				ret.add(msg);
				ret.add(ex.getStackTrace());
				ex.printStackTrace();
			}
		}

		if (env != null) {
			env.close(ok);
			if (env.debugInfo.size() > 0)
				ret.add(env.debugInfo);
		}
		else {
			env = new JDEnvBase();
		}
		
		if (dret)
			return;
		
		String retStr = env.api.jsonEncode(ret, env.isTestMode);
		response.getWriter().write(retStr);
	}

}

