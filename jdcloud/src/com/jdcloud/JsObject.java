package com.jdcloud;
import java.util.*;

@SuppressWarnings("serial")
public class JsObject extends LinkedHashMap<String, Object>
{
	public JsObject() {}
	public JsObject(Object ... args)
	{
		for (int i=0; i<args.length-1; i+=2)
		{
			this.put(args[i].toString(), args[i+1]);
		}
	}
	public JsObject(Map<String, Object> m)
	{
		this.putAll(m);
	}
}
