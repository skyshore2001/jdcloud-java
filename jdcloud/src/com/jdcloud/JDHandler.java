package com.jdcloud;

import java.io.IOException;
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
		JDEnvBase env;
		try {
			env = JDEnvBase.createEnv(request.getServletContext());
		} catch (Exception ex) {
			response.setContentType("text/plain; charset=utf-8");
			String msg = "Fail to create JDEnv";
			if (ex instanceof MyException) {
				MyException ex1 = (MyException)ex;
				msg += ": " + ex1.getDebugInfo().toString();
			}
			String ret = JDApiBase.jsonEncode(new JsArray(JDApiBase.E_SERVER, msg));
			response.getWriter().println(ret);
			ex.printStackTrace();
			return;
		}
		env.service(request, response);
	}
}
