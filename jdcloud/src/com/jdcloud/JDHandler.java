package com.jdcloud;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Properties;
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
		Properties props = null;
		try {
			props = new Properties();
			InputStream is = request.getServletContext().getResourceAsStream("/WEB-INF/web.properties");
			props.load(is);
		} catch (Exception e) {
		}
		
		JDEnvBase env = null;
		try {
			String clsEnv = props.getProperty("JDEnv");
			if (clsEnv == null) {
				throw null; 
			}
			env = (JDEnvBase)Class.forName(clsEnv).newInstance();
		} catch (Exception ex) {
			response.getWriter().format("[%d, \"%s\", \"%s\"]", JDApiBase.E_SERVER, "服务器错误", "Fail to create JDEnv");
			return;
		}
		env.service(request, response, props);
	}
}
