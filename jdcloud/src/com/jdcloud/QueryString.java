package com.jdcloud;
/*
Java对GET/POST参数的支持比较弱: 
1. request.getParameter(k)不区分GET/POST; 
2. 前端发送数组或对象时, 无法正确地解析. (仅支持部分简单情况, 如前端发送了cond数组, Java中可以用request.getParameterValues("cond[]")可以取数组)

QueryString.parseQuery仿照php解析参数的方法, 支持通过urlencoded方式发送任何JSON数据.
 */

import java.io.Reader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

public class QueryString {
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static void putParam(JsObject map, String k, String v)
	{
		if (! k.contains("[")) {
			map.put(k, v);
			return;
		}

		// 为兼容php, 支持通过urlencoded格式发送复杂格式如: "a[]=1", "b[c]=2", "b[d][]=3", "b[d][]=4", "c[0][d]=4", "c[1][d]=5" => {a: [1], b: {c:2, d:[3,4]}, c:[{d:4}, {d:5}]}
		String k1 = k.replaceAll("\\[.*$", "");
		List keys = new ArrayList();
		keys.add(k1);
		JDApiBase.regexReplace(k, "\\[(.*?)\\]", (m) -> {
			String k2 = m.group(1);
			if (k2.length() == 0) {
				keys.add(null);
			}
			else if (k2.matches("\\d+")) {
				keys.add(Integer.parseInt(k2));
			}
			else {
				keys.add(k2);
			}
			return m.group();
		});
		keys.add(v);
		Common.setJsValue(map, keys.toArray());
	}

	public static JsObject parseQuery(String q) throws Exception
	{
		JsObject ret = new JsObject();
		if (q != null) {
			for (String q1 : q.split("&")) {
				String[] kv = q1.split("=");
				String k = URLDecoder.decode(kv[0], "UTF-8");
				String v = kv.length > 1? URLDecoder.decode(kv[1], "UTF-8"): "";
				// ret.put(k, v);
				putParam(ret, k, v);
			}
		}
		return ret;
	}
	
	public static JsObject getUrlParam(HttpServletRequest request) throws Exception
	{
		String q = request.getQueryString();
		return parseQuery(q);
	}

	public static JsObject getPostParam(HttpServletRequest request) throws Exception
	{
		Reader rd = request.getReader();
		char[] buf = new char[request.getContentLength()];
		rd.read(buf);
		return parseQuery(new String(buf));
	}
}
