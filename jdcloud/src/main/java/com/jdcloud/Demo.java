package com.jdcloud;

import java.sql.SQLException;

public class Demo {

}

class Global extends JDApiBase
{
	public Object api_hello() throws Exception
	{
		Object ret = queryAll("SELECT id, uname FROM User LIMIT 20", true);
		return ret;
	}
}
