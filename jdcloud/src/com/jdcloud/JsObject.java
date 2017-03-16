package com.jdcloud;
import java.util.*;

public class JsObject extends LinkedHashMap<String, Object>
{
	public JsObject(Object ... args)
	{
		for (int i=0; i<args.length-1; i+=2)
		{
			this.put(args[i].toString(), args[i+1]);
		}
	}
}
