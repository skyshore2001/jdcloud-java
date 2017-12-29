package com.jdcloud;
import java.util.*;

@SuppressWarnings("serial")
public class JsArray extends ArrayList<Object>
{
	public JsArray() {}
	public JsArray(Object ... args)
	{
		for (Object o : args)
		{
			this.add(o);
		}
	}
}

