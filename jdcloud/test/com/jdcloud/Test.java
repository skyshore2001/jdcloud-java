package com.jdcloud;
import java.util.*;
import org.junit.*;

@SuppressWarnings({"rawtypes"})

public class Test extends Common {
	@org.junit.Test
	public void getJsValueTest()
	{
		String str = "{ \"id\": 100, \"name\": \"f1\", \"persons\": [ {\"id\": 1001, \"name\": \"p1\"}, {\"id\": 1002, \"name\": \"p2\"} ] }";
		Object obj = jsonDecode(str);

		Object v1 = getJsValue(obj, "id"); // 相当于js的obj.id: 100. 注意: jsonDecode在未指定类型时, 所有数值均解析成Double类型
		Assert.assertEquals(100.0, v1);

		int id = intValue(v1);
		Assert.assertEquals(100, id);

		List persons = castOrNull(getJsValue(obj, "persons"), List.class); // 取List
		Assert.assertTrue(persons != null && persons.size() == 2);

		Map person = castOrNull(getJsValue(obj, "persons", 1), Map.class); // 取Map
		Assert.assertTrue(person != null);
		Assert.assertEquals(1002.0, person.get("id"));
		Map person2 = castOrNull(getJsValue(persons, 1), Map.class); // 结果同上
		Assert.assertTrue(person2 != null);
		Assert.assertEquals(person, person2);
		
		Object v2 = getJsValue(obj, "persons", 1, "name"); // 相当于js的obj.persons[1].name: "p2"
		Assert.assertEquals("p2", v2);
		Object v21 = getJsValue(obj, new Object[] { "persons", 1, "name" }); // 同上面没有区别.
		Assert.assertEquals("p2", v21);
		Object v3 = getJsValue(person2, "name"); // 结果同上
		Assert.assertEquals("p2", v3);

		Object v4 = getJsValue(obj, "persons", 99, "name"); // 如果任意一步取不到值, 均返回 null
		Assert.assertEquals(null, v4);
	}
	
	@org.junit.Test
	public void setJsValueTest()
	{
		Map obj = asMap("id", 100);
		setJsValue(obj, "addr", "city", "Shanghai"); // 相当于obj.addr.city = "Shanghai", addr不存在则自动创建对象
		Assert.assertEquals("Shanghai", getJsValue(obj, "addr", "city"));

		setJsValue(obj, "persons", 0, asMap("id", 1001, "name", "p1")); // 相当于obj.persons[0] = {id:1001, name:"p1"}, persons不存在或persons[0]不存在则自动创建
		Assert.assertTrue(getJsValue(obj, "persons") instanceof List);
		Assert.assertEquals("p1", getJsValue(obj, "persons", 0, "name"));

		setJsValue(obj, new Object[] {"persons2", 0, asMap("id", 1001, "name", "p1")}); // 同上面没有区别
		Assert.assertEquals("p1", getJsValue(obj, "persons2", 0, "name"));

		setJsValue(obj, "persons3", 1, "name", "p1"); // 相当于obj.persons[1].name = "p1"; 中间环境不存在则自动创建
		Assert.assertEquals("p1", getJsValue(obj, "persons3", 1, "name"));

		setJsValue(obj, "persons4", null, "name", "p1"); // 相当于obj.persons[新增].name = "p1"; 中间环境不存在则自动创建
		setJsValue(obj, "persons4", null, "name", "p2"); 
		Assert.assertEquals("p2", getJsValue(obj, "persons4", 1, "name"));
	}
	
	@org.junit.Test
	public void parseQueryTest() throws Exception
	{
		String q = "a=1&b=2&c=3";
		Map map = QueryString.parseQuery(q);
		Assert.assertEquals(map, asMap("a", "1", "b", "2", "c", "3"));
		
		q = "a[]=1&a[]=2&b=v1";
		map = QueryString.parseQuery(q);
		Assert.assertEquals(map, asMap("a", asList("1", "2"), "b", "v1"));

		q = "a[cond][]=id%3d1&a[cond][]=id%3d2";
		map = QueryString.parseQuery(q);
		Assert.assertEquals(map, asMap("a", asMap("cond", asList("id=1", "id=2"))));

		q = "a[cond][0][id]=1&a[cond][0][name]=v1&a[cond][1]=id>1";
		map = QueryString.parseQuery(q);
		Assert.assertEquals(map, asMap("a", asMap("cond", asList(asMap("id", "1", "name", "v1"), "id>1"))));
	}

	@org.junit.Test
	public void pivotTest() throws Exception
	{
		JsArray arr = new JsArray(
			new JsObject("y",2019, "m",11, "cateId",1, "cateName","衣服", "sum",20000),
			new JsObject("y",2019, "m",11, "cateId",2, "cateName","食品", "sum",12000),
			new JsObject("y",2019, "m",12, "cateId",2, "cateName","食品", "sum",15000),
			new JsObject("y",2020, "m",2, "cateId",1, "cateName","衣服", "sum",19000)
		);
		// 将类别转到列
		JsArray arr2 = JDApiBase.pivot(arr, "cateId,cateName", null);
		System.out.println(jsonEncode(arr2, true));
		String res = "[{\"y\":2019,\"m\":11,\"1-衣服\":20000.0,\"2-食品\":12000.0},{\"y\":2019,\"m\":12,\"2-食品\":15000.0},{\"y\":2020,\"m\":2,\"1-衣服\":19000.0}]";
		Assert.assertEquals(jsonEncode(arr2), res);
	}
}
