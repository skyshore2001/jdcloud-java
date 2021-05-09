package com.jdcloud;
import java.util.*;
import org.junit.*;

@SuppressWarnings({"rawtypes"})

public class Test extends JDApiBase {
	@org.junit.Test
	public void getJsValueTest()
	{
		String str = "{ \"id\": 100, \"name\": \"f1\", \"persons\": [ {\"id\": 1001, \"name\": \"p1\"}, {\"id\": 1002, \"name\": \"p2\"} ] }";
		Object obj = jsonDecode(str);

		Object v1 = getJsValue(obj, "id"); // 相当于js的obj.id: 100. 注意: jsonDecode在未指定类型时, 所有数值均解析成Double类型
		Assert.assertEquals("100", v1);

		int id = intValue(v1);
		Assert.assertEquals(100, id);

		List persons = castOrNull(getJsValue(obj, "persons"), List.class); // 取List
		Assert.assertTrue(persons != null && persons.size() == 2);

		Map person = castOrNull(getJsValue(obj, "persons", 1), Map.class); // 取Map
		Assert.assertTrue(person != null);
		Assert.assertEquals("1002", person.get("id"));
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
		JsArray arr2 = pivot(arr, "cateId,cateName", null);
		System.out.println(jsonEncode(arr2, true));
		String res = "[{\"y\":2019,\"m\":11,\"1-衣服\":20000.0,\"2-食品\":12000.0},{\"y\":2019,\"m\":12,\"2-食品\":15000.0},{\"y\":2020,\"m\":2,\"1-衣服\":19000.0}]";
		Assert.assertEquals(jsonEncode(arr2), res);
	}

	@org.junit.Test
	public void getQueryCondTest() throws Exception
	{
		String rv = getQueryCond(100);
		Assert.assertEquals("id=100", rv);

		rv = getQueryCond("100");
		Assert.assertEquals("id=100", rv);

		rv = getQueryCond(asMap("id",1, "status","CR", "name","null", "dscr",null, "f1","", "f2","empty"));
		Assert.assertEquals("id=1 AND status='CR' AND name IS NULL AND f2=''", rv);

		rv = getQueryCond(asMap("id","<100", "tm",">2020-1-1", "status","!CR", "name","~wang%", "dscr","~aaa", "dscr2","!~aaa"));
		Assert.assertEquals("id<100 AND tm>'2020-1-1' AND status<>'CR' AND name LIKE 'wang%' AND dscr LIKE '%aaa%' AND dscr2 NOT LIKE '%aaa%'", rv);

		rv = getQueryCond(asMap("b","!null", "d","!empty"));
		Assert.assertEquals("b IS NOT NULL AND d<>''", rv);

		rv = getQueryCond(asMap("tm",">=2020-1-1 AND <2020-2-1", "tm2","<2020-1-1 OR >=2020-2-1"));
		Assert.assertEquals("(tm>='2020-1-1' AND tm<'2020-2-1') AND (tm2<'2020-1-1' OR tm2>='2020-2-1')", rv);

		rv = getQueryCond(asMap("id",">=1 AND <100", "status","CR OR PA", "status2","!CR AND !PA OR null"));
		Assert.assertEquals("(id>=1 AND id<100) AND (status='CR' OR status='PA') AND (status2<>'CR' AND status2<>'PA' OR status2 IS NULL)", rv);

		rv = getQueryCond(asMap("a","null OR empty", "b","!null AND !empty", "_or",1));
		Assert.assertEquals("(a IS NULL OR a='') OR (b IS NOT NULL AND b<>'')", rv);

		rv = getQueryCond(asList("id>=1", "id<100", "name LIKE 'wang%'"));
		Assert.assertEquals("id>=1 AND id<100 AND name LIKE 'wang%'", rv);

		rv = getQueryCond(asList("id>=1 AND id<100", asMap("name","~wang*")));
		Assert.assertEquals("(id>=1 AND id<100) AND name LIKE 'wang%'", rv);

		rv = getQueryCond(asList("id=1", "id=2", "_or"));
		Assert.assertEquals("id=1 OR id=2", rv);
	}

	@org.junit.Test
	public void genQueryTest() throws Exception
	{
		String name = "eric";
		String phone = "13700000001";

		String rv = genQuery("SELECT id FROM Vendor", asMap("name",name, "phone",phone));
		Assert.assertEquals("SELECT id FROM Vendor WHERE name='eric' AND phone='13700000001'", rv);

		rv = genQuery("SELECT id FROM Vendor", asMap("name",name, "phone","!null"));
		Assert.assertEquals("SELECT id FROM Vendor WHERE name='eric' AND phone IS NOT NULL", rv);

		rv = genQuery("SELECT id FROM Vendor", asMap("name",name, "phone",phone, "_or",true));
		Assert.assertEquals("SELECT id FROM Vendor WHERE name='eric' OR phone='13700000001'", rv);
	}

	@org.junit.Test
	public void jsEmptyTest() throws Exception
	{
		Assert.assertEquals(true, jsEmpty(null));
		Assert.assertEquals(true, jsEmpty(0));
		Assert.assertEquals(true, jsEmpty(0.0));
		Assert.assertEquals(true, jsEmpty(""));
		Assert.assertEquals(true, jsEmpty(false));
	}

	@org.junit.Test
	public void makeTreeTest() throws Exception
	{
		List ret = makeTree(asList(
			asMap("id",1),
			asMap("id",2, "fatherId",1),
			asMap("id",3, "fatherId",2),
			asMap("id",4, "fatherId",1)
		));
		String res = "[{\"id\":1,\"children\":[{\"id\":2,\"fatherId\":1,\"children\":[{\"id\":3,\"fatherId\":2}]},{\"id\":4,\"fatherId\":1}]}]";
		Assert.assertEquals(res, jsonEncode(ret));
	}

	@org.junit.Test
	public void myTest() throws Exception
	{
		String s = "a:123:ee:";
		String[] rv = s.split(":", 2);
		System.out.println(jsonEncode(rv));
	}
}
