package com.jdcloud;
import java.util.*;

public class JsArray extends ArrayList<Object>
{
	public JsArray(Object ... args)
	{
		for (Object o : args)
		{
			this.add(o);
		}
	}
}

