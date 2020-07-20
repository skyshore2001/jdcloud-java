package com.jdcloud;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@SuppressWarnings({"unchecked", "rawtypes"})
public class Common
{
	public static final int KB = 1024;
	public static final int MB = 1024 * KB;
	public static final int GB = 1024 * MB;

	public static <K,V> Map<K,V> asMap(Object ... args) {
		Map<K,V> m = new LinkedHashMap<K, V>();
		for (int i=0; i<args.length-1; i+=2) {
			m.put((K)args[i], (V)args[i+1]);
		}
		return m; 
	}

	@SafeVarargs
	public static <T> List<T> asList(T ... args) {
		List<T> ls = new ArrayList<>();
		for (T e: args) {
			ls.add(e);
		}
		return ls;
	}

/**<pre>
%fn indexOf(map, fn) -> key
%param fn(key, value) -> boolean

找符合fn条件的第一个key。找不到返回null。

	JsObject map = new JsObject("aa", 100, "bb", 300);
	String key = indexOf(map, (k,v)->{v>200}); // key="bb"
	
 */
	public static <K,V> K indexOf(Map<K,V> m, BiPredicate<K,V> fn) {
		K key = null;
		for (Map.Entry<K, V> e: m.entrySet()) {
			if (fn.test(e.getKey(), e.getValue())) {
				key = e.getKey();
				break;
			}
		}
		return key;
	}
	
/**<pre>
%fn indexOf(arr, e) -> index

数组查找。返回找到的索引，找不到返回-1。

	JsArray arr = new JsArray("aa", "bbb");
	int idx = indexOf(arr, "bbb"); // idx =1
	
*/
	public static <T> int indexOf(T[] arr, T e) {
		int idx = -1;
		for (int i=0; i<arr.length; ++i	) {
			if (arr[i].equals(e)) {
				idx = i;
				break;
			}
		}
		return idx;
	}

	@FunctionalInterface
	public interface ConsumerEx<T> {
		void accept(T t) throws Exception;
	}
	@FunctionalInterface
	public interface ConsumerEx1<T> {
		boolean accept(T t) throws Exception;
	}
	@FunctionalInterface
	public interface BiConsumerEx<K,V> {
		void accept(K k, V v) throws Exception;
	}
	@FunctionalInterface
	public interface BiConsumerEx1<K,V> {
		boolean accept(K k, V v) throws Exception;
	}

/**<pre>
%fn forEach(ls, fn(e))

与ls.forEach类似，允许函数中抛出异常。调用它的函数应声明`throws Exception`
示例：

	List<Object> ids = new ArrayMap<>();
	List<Object> names = new ArrayMap<>();
	forEach(ids, id -> {
		names.put(queryOne("SELECT name FROM User WHERE id=" + id);
	});

	// 支持返回false退出遍历
	forEach(ids, id -> {
		if (id > 100)
			return false;
		names.put(queryOne("SELECT name FROM User WHERE id=" + id);
		return true;
	});
*/
	public static <T> void forEach(List<T> ls, ConsumerEx<T> fn) throws Exception {
		for (T e: ls) {
			fn.accept(e);
		}
	}
	public static <T> void forEach(List<T> ls, ConsumerEx1<T> fn) throws Exception {
		for (T e: ls) {
			boolean rv = fn.accept(e);
			if (rv == false)
				break;
		}
	}
/**<pre>
%fn forEach(map, fn(k, v))

与map.forEach类似，允许函数中抛出异常。调用它的函数应声明`throws Exception`
示例：

	Map<String, Integer> m = new HashMap<>();
	forEach(m, (k, v) -> {
		dbInsert("Conf", asMap("k", k, "v", v));
	});

	// 支持返回false退出遍历
	forEach(m, (k, v) -> {
		if (v > 100)
			return false;
		dbInsert("Conf", asMap("k", k, "v", v));
		return true;
	});

*/
	public static <K,V> void forEach(Map<K,V> m, BiConsumerEx<K,V> fn) throws Exception {
		for (Map.Entry<K, V> e: m.entrySet()) {
			fn.accept(e.getKey(), e.getValue());
		}
	}
	public static <K,V> void forEach(Map<K,V> m, BiConsumerEx1<K,V> fn) throws Exception {
		for (Map.Entry<K, V> e: m.entrySet()) {
			boolean rv = fn.accept(e.getKey(), e.getValue());
			if (rv == false)
				break;
		}
	}
/**<pre>
@fn castOrNull(obj)

强制类型转换, 无法转换返回null.
	Map map = castOrNull(obj, Map.class);

如果已自行先用instanceof测试可确保安全, 这时可以直接用cast:

	Object obj = m.get("list");
	if (obj instanceof List) {
		// 相比直接用(List)obj转, 不会报烦人的警告(unchecked, rawtypes等).
		List arr = cast(obj);
	}

	// 或者特别自信时直接转, 转换失败会抛出异常. 也可以直接用(Map)obj转换, 并设置忽略unchecked/rawtypes警告
	Map map = cast(obj);

 */
	public static<T> T castOrNull(Object obj, Class<T> clazz) {
		if (obj == null)
			return null;
		if (! clazz.isAssignableFrom(obj.getClass()) )
			return null;
		return clazz.cast(obj);
	}
/**<pre>
@fn cast(obj)

强制类型转换, 无法转换则抛出异常
*/
	public static<T> T cast(Object obj) {
		return (T)obj;
	}

/**<pre>
@fn intValue(obj) -> int

转换int工具, 如果转换失败返回0. 若想返回null可用IntValue

	int i = intVaulue(99); // 99
	int i = intVaulue(99.0); // 99
	int i = intVaulue("99"); // 99
	int i = intVaulue(null); // 0
	int i = intVaulue("N99"); // 0

	Map obj = cast(jsonDecode(str));
	int id = intValue(obj.get("id"));

@see IntValue
 */
	public static int intValue(Object obj)
	{
		Integer val = IntValue(obj);
		return val == null? 0: val;
	}

/**<pre>
@fn IntValue(obj) -> Integer

转换int工具, 如果转换失败返回null.

	Int i = IntVaulue(99); // 99
	Int i = IntVaulue(99.0); // 99
	Int i = IntVaulue("99"); // 99
	Int i = IntVaulue(null); // 0
	Int i = IntVaulue("N99"); // 0

	Map obj = cast(jsonDecode(str));
	Int id = IntValue(obj.get("id"));

@see IntValue
 */
	public static Integer IntValue(Object obj)
	{
		if (obj == null)
			return null;
		if (obj instanceof Integer) {
			return (Integer)obj;
		}
		if (obj instanceof Double) {
			return ((Double) obj).intValue();
		}
		try {
			return Integer.parseInt(obj.toString());
		}
		catch (Exception ex) {
			return null;
		}
	}

/**<pre>
@fn DoubleValue(obj) -> Double

转换数值Double工具, 如果转换失败返回null

	Double d = DoubleVaulue(99); // 99.0
	Double d = DoubleVaulue(99.0); // 99.0
	Double d = DoubleVaulue("99"); // 99.0
	Double d = DoubleVaulue(null); // 0.0
	Double d = DoubleVaulue("N99"); // 0.0
	
	Map obj = cast(jsonDecode(str));
	Double qty = DoubleValue(obj.get("qty"));
	if (qty == null) ...

@see doubleValue
 */
	public static Double DoubleValue(Object obj)
	{
		if (obj == null)
			return null;
		if (obj instanceof Integer) {
			return (double)(Integer)obj;
		}
		if (obj instanceof Double) {
			return (Double)obj;
		}
		try {
			return Double.parseDouble(obj.toString());
		}
		catch (Exception ex) {
			return null;
		}
	}

/**<pre>
@fn doubleValue(obj) -> double

转换数值double工具, 如果转换失败返回0.0. 若想返回null可用DoubleValue

	double d = doubleVaulue(99); // 99.0
	double d = doubleVaulue(99.0); // 99.0
	double d = doubleVaulue("99"); // 99.0
	double d = doubleVaulue(null); // 0.0
	double d = doubleVaulue("N99"); // 0.0
	
	Map obj = cast(jsonDecode(str));
	double qty = doubleValue(obj.get("qty"));

@see DoubleValue
 */
	public static double doubleValue(Object obj)
	{
		Double val = DoubleValue(obj);
		return val == null? 0.0: val;
	}

/**<pre>
@fn getJsValue(container, key1, key2, ...) -> Object

取json复合结构(即Map/List组合)中的值, 当索引为Integer时取数组List的值, 当索引为字符串时取字典Map的值.
返回值用cast转成Map/List类型(出错抛出异常,或用castOrNull出错返回null), 用intValue转成整数(出错返回0), 用doubleValue转成数值(出错返回0.0)

	String str = "{ \"id\": 100, \"name\": \"f1\", \"persons\": [ {\"id\": 1001, \"name\": \"p1\"}, {\"id\": 1002, \"name\": \"p2\"} ] }";
	Object obj = jsonDecode(str);
	Object v1 = getJsValue(obj, "id"); // 相当于js的obj.id: 100. 注意: jsonDecode在未指定类型时, 所有数值均解析成Double类型
	int id = castInt(v1);
	// 注意cast失败会抛出异常. 若想失败返回null应使用castOrNull
	List persons = cast(getJsValue(obj, "persons")); // 取List
	Map person = cast(getJsValue(obj, "persons", 1)); // 取Map
	Map person2 = cast(getJsValue(persons, 1)); // 结果同上
	Object v2 = getJsValue(obj, "persons", 1, "name"); // 相当于js的obj.persons[1].name: "p2"
	Object v21 = getJsValue(obj, new Object[] { "persons", 1, "name" }); // 同上面没有区别.
	Object v3 = getJsValue(person2, "name"); // 结果同上
	Object v4 = getJsValue(obj, "persons", 99, "name"); // 如果任意一步取不到值, 均返回 null

设置值:

	Map obj = asMap("id", 100);
	setJsValue(obj, "addr", "city", "Shanghai"); // 相当于obj.addr.city = "Shanghai", addr不存在则自动创建对象
	setJsValue(obj, "persons", 0, asMap("id", 1001, "name", "p1")); // 相当于obj.persons[0] = {id:1001, name:"p1"}, persons不存在或persons[0]不存在则自动创建
	setJsValue(obj, new Object[] {"persons", 0, asMap("id", 1001, "name", "p1"}); // 同上面没有区别
	setJsValue(obj, "persons2", 1, "name", "p1"); // 相当于obj.persons[1].name = "p1"; 中间环境不存在则自动创建

 */
	public static Object getJsValue(Object cont, Object ...keys)
	{
		Object ret = cont;
		for (Object k: keys) {
			if (k instanceof Integer) {
				List arr = castOrNull(ret, List.class);
				if (arr == null)
					ret = null;
				else {
					try {
						ret = arr.get((int) k);
					}
					catch (IndexOutOfBoundsException ex) {
						ret = null;
					}
				}
			}
			else if (k instanceof String) {
				Map map = castOrNull(ret, Map.class);
				if (map == null)
					ret = null;
				else
					ret = map.get(k);
			}
			if (ret == null)
				break;
		}
		return ret;
	}
	
/**
@fn setJsValue(container, ...keys) -> bool

整数key将访问数组List, 字符串key将访问字典Map, 如果不存在则创建相应对象.
keys中最后一个是value.

	setJsValue(obj, "persons", 0, asMap("id", 1001, "name", "p1")); // 相当于obj.persons[0] = {id:1001, name:"p1"}, persons不存在或persons[0]不存在则自动创建
	setJsValue(obj, "persons2", 1, "name", "p1"); // 相当于obj.persons[1].name = "p1"; 中间环境不存在则自动创建
	setJsValue(obj, "persons3", null, "name", "p1"); // 如果key为null, 表示新增加一个数组元素(push)

*/
	public static boolean setJsValue(Object cont, Object... keys)
	{
		if (keys.length < 2) // 无意义
			return false;
		if (keys.length == 2) {
			Object k = keys[0];
			Object v = keys[1];
			if (k instanceof Integer || k == null) {
				List arr = castOrNull(cont, List.class);
				if (arr == null)
					return false;
				if (k == null) {
					arr.add(v);
				}
				else {
					int k0 = (int)k;
					while (k0 >= arr.size())
						arr.add(null);
					arr.set(k0, v);
				}
			}
			else {
				Map map = castOrNull(cont, Map.class);
				if (map == null)
					return false;
				map.put(k, v);
			}
			return true;
		}
		Object k1 = keys[0];
		Object k2 = keys[1];
		boolean nextIsList = (k2 instanceof Integer || k2 == null);
		if (k1 instanceof Integer || k1 == null) { // 数组访问
			List arr = castOrNull(cont, List.class);
			if (arr == null)
				return false;
			if (k1 == null) {
				cont = nextIsList? asList(): asMap();
				arr.add(cont);
			}
			else {
				int k0 = (int)k1;
				while (k0 >= arr.size())
					arr.add(null);
				cont = arr.get(k0);
				if (cont == null) {
					cont = nextIsList? asList(): asMap();
					arr.set(k0, cont);
				}
			}
		}
		else { // 字典访问
			Map map = castOrNull(cont, Map.class);
			if (map == null)
				return false;
			cont = map.get(k1);
			if (cont == null) {
				cont = nextIsList? asList(): asMap();
				map.put(k1, cont);
			}
		}
		return setJsValue(cont, Arrays.copyOfRange(keys, 1, keys.length));
	}

/**<pre>
@fn readFileBytes(file, maxLen=-1) -> byte[]

返回null表示读取失败。
 */
	public static byte[] readFileBytes(String file) throws IOException
	{
		return readFileBytes(file, -1);
	}
	public static byte[] readFileBytes(String file, int maxLen)
	{
		byte[] bs = null;
		try {
			File f = new File(file);
			if (! f.exists())
				return null;
			InputStream in = new FileInputStream(f);
			int len = (int)f.length();
			if (maxLen >0 && len > maxLen)
				len = maxLen;
			bs = new byte[len];
			in.read(bs);
			in.close();
		}
		catch (IOException ex) {
			
		}
		return bs;
	}

/**<pre>
%fn readFile(file, charset="utf-8") -> String

返回null表示读取失败。
 */
	public static String readFile(String file) throws IOException
	{
		return readFile(file, "utf-8");
	}
	public static String readFile(String file, String charset) throws IOException
	{
		byte[] bs = readFileBytes(file);
		if (bs == null)
			return null;
		return new String(bs, charset);
	}

/**<pre>
%fn writeFile(in, out, bufSize?)

复制输入到输出。输入、输出可以是文件或流。

%param in String/File/InputStream
%param out String/File/OutputStream
%param bufSize 指定buffer大小，设置0使用默认值(10K)
 */
	public static void writeFile(Object in, Object out, int bufSize) throws IOException
	{
		if (bufSize <= 0)
			bufSize = 10* KB;
		InputStream in1 = null;
		boolean closeIn = true;
		if (in instanceof String) {
			in1 = new FileInputStream((String)in);
		}
		else if (in instanceof File) {
			in1 = new FileInputStream((File)in);
		}
		else if (in instanceof InputStream) {
			in1 = (InputStream)in;
			closeIn = false;
		}
		else {
			throw new IllegalArgumentException("writeFile:in");
		}
		OutputStream out1 = null;
		boolean closeOut = true;
		if (out instanceof String) {
			out1 = new FileOutputStream((String)out);
		}
		else if (out instanceof File) {
			out1 = new FileOutputStream((File)out);
		}
		else if (out instanceof OutputStream) {
			out1 = (OutputStream)out;
			closeOut = false;
		}
		else {
			throw new IllegalArgumentException("writeFile:out");
		}

		byte[] buffer = new byte[bufSize];
		int len = 0;
		while ((len = in1.read(buffer)) != -1) {
			out1.write(buffer, 0, len);
		}
		if (closeOut)
			out1.close();
		if (closeIn)
			in1.close();
	}
	public static void writeFile(Object in, Object out) throws IOException {
		writeFile(in, out, 0);
	}

	public static String jsonEncode(Object o)
	{
		return jsonEncode(o, false);
	}
/**<pre>
%fn jsonEncode(o, doFormat=false)

%param doFormat 设置为true则会对JSON输出进行格式化便于调试。

%see jsonDecode
 */
	public static String jsonEncode(Object o, boolean doFormat)
	{
		GsonBuilder gb = new GsonBuilder();
		gb.serializeNulls().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss");
		if (doFormat)
			gb.setPrettyPrinting();
		Gson gson = gb.create();
		return gson.toJson(o);
	}
	
/**<pre>
%fn jsonDecode(json, type) -> type

	Map m = jsonDecode(json, Map.class);
	List m = jsonDecode(json, List.class);
	User u = jsonDecode(json, User.class);

	Object o = jsonDecode(json);
	// the same as
	Object o = jsonDecode(json, Object.class);
	
%see jsonEncode
 */
	public static <T> T jsonDecode(String json, Class<T> type)
	{
		GsonBuilder gb = new GsonBuilder();
		gb.serializeNulls().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss");
		Gson gson = gb.create();
		return gson.fromJson(json, type);
	}
	public static Object jsonDecode(String json)
	{
		return jsonDecode(json, Object.class);
	}
	
	static final Map<String, String> htmlEntityMapping = asMap(
		"<", "&lt;",
		">", "&gt;",
		"&", "&amp;"
	);
/**<pre>
@fn htmlEscape(s)

用于防止XSS攻击。只转义字符"<", ">"，示例：
当用户保存`<script>alert(1)</script>`时，实际保存的是`&lt;script&gt;alert(1)&lt;/script&gt;`
这样，当前端以`$div.html($val)`来显示时，不会产生跨域攻击泄漏Cookie。

如果前端就是需要带"<>"的字符串（如显示在input中），则应自行转义。
 */
	public static String htmlEscape(String s)
	{
		StringBuffer sb = new StringBuffer();
		Matcher m = regexMatch(s, "<|>");
		while (m.find()) {
			m.appendReplacement(sb, (String)htmlEntityMapping.get(m.group(0)));
		}
		m.appendTail(sb);
		return sb.toString();
		//return StringEscapeUtils.unescapeHtml();
	}

/**<pre>
%fn regexMatch(str, pat) -> Matcher

正则表达式匹配

	String phone = "13712345678";
	Matcher m = regexMatch(phone, "...(\\d{4})";
	if (m.find()) { // 如果要连续匹配可用 while (m.find()) 
		// m.group(1) 为中间4位数
	}

正则式的标志可用`(?imsU)`的形式表示，放在pattern中。

-i: 忽略大小写，如"(?i)[a-z]+"
-U: 支持unicode匹配，如"(?U)\w+"可匹配中文字符

 */
	public static Matcher regexMatch(String str, String pat) {
		return Pattern.compile(pat).matcher(str);
	}

/**<pre>
%fn regexReplace(str, pat, str1, maxCnt=0) -> String
%alias regexReplace(str, pat, fn, maxCnt=0) -> String

用正则表达式替换字符串.
maxCnt大于零时，用于指定最大替换次数。

%param fn(Matcher m) -> String

	String phone = "13712345678"; // 变成 "137****5678"
	String phone1 = regexReplace(phone, "(?<=^\\d{3})\\d{4}", "****");
	或者
	String phone1 = regexReplace(phone, "^(\\d{3})\\d{4}", m -> { return m.group(1) + "****"; } );

 */
	public static String regexReplace(String str, String pat, String str1, int maxCnt) {
		Matcher m = regexMatch(str, pat);
		StringBuffer sb = new StringBuffer();
		int i = 0;
		while (m.find()) {
			m.appendReplacement(sb, str1);
			++ i;
			if (maxCnt > 0 && i >= maxCnt)
				break;
		}
		m.appendTail(sb);
		return sb.toString();
	}
	public static String regexReplace(String str, String pat, java.util.function.Function<Matcher, String> fn, int maxCnt) {
		Matcher m = regexMatch(str, pat);
		StringBuffer sb = new StringBuffer();
		int i = 0;
		while (m.find()) {
			String str1 = fn.apply(m);
			m.appendReplacement(sb, str1);
			++ i;
			if (maxCnt > 0 && i >= maxCnt)
				break;
		}
		m.appendTail(sb);
		return sb.toString();
	}
	public static String regexReplace(String str, String pat, String str1) {
		return regexReplace(str, pat, str1, 0);
	}
	public static String regexReplace(String str, String pat, java.util.function.Function<Matcher, String> fn) {
		return regexReplace(str, pat, fn, 0);
	}
	
	public static String join(String sep, Collection<?> ls) {
		StringBuffer sb = new StringBuffer();
		for (Object o : ls) {
			if (sb.length() > 0)
				sb.append(sep);
			sb.append(o);
		}
		return sb.toString();
	}

/**<pre>
%var T_SEC,T_MIN,T_HOUR,T_DAY

	Date dt = new Date();
	Date dt1 = parseDate(dtStr1);
	long hours = (dt1.getTime() - dt.getTime()) / T_HOUR;
	Date dt2 = new Date(dt1.getTime() + 4 * T_DAY);
	
%see time
 */
	public static final long T_SEC = 1000;
	public static final long T_MIN = 60*T_SEC;
	public static final long T_HOUR = 3600*T_SEC;
	public static final long T_DAY = 24*T_HOUR;
	
	public static final String FMT_DT = "yyyy-MM-dd HH:mm:ss";
	public static final String FMT_D = "yyyy-MM-dd";

/** <pre>
%fn date(fmt?="yyyy-MM-dd HH:mm:ss", dt?)

生成日期字符串。

	String dtStr1 = date(null, null);
	Date dt1 = parseDate(dtStr1);
	String dtStr2 = date("yyyy-MM-dd HH:mm:ss", dt1); // FMT_DT / FMT_D

	long now = time();
	String nowStr = date(FMT_DT, now);
	String dayStr = date(FMT_D, now);
	String nextDayStr = date(FMT_D, now + T_DAY);
	
%see parseDate
*/
	public static String date(String fmt, Date dt) {
		if (fmt == null)
			fmt = FMT_DT;
		if (dt == null)
			dt = new Date();
		return new java.text.SimpleDateFormat(fmt).format(dt);
	}
	public static String date(String fmt, long dtval) {
		if (fmt == null)
			fmt = FMT_DT;
		Date dt = new Date(dtval);
		return new java.text.SimpleDateFormat(fmt).format(dt);
	}
	public static String date() {
		return date(null, null);
	}
/**<pre>
%fn time()

系统当前时间（毫秒）。

	long t = time();
	long unixTimestamp = t / T_SEC
	Date dt1 = new Date();
	long diff_ms = dt1.getTime() - t;
 */
	public static long time() {
		return System.currentTimeMillis();
	}

/**<pre>
%fn md5(s) -> String 

返回md5字符串(32字符)
*/
	public static String md5(String s)
	{
		byte[] rv = md5Bytes(s); 
		return new java.math.BigInteger(1, rv).toString(16);
	}
/**<pre>
%fn md5Bytes(s) -> byte[] 

返回md5结果(16字节)
*/
	public static byte[] md5Bytes(String s)
	{
		byte[] ret = null;
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
			md.update(s.getBytes());
			ret = md.digest();
		} catch (NoSuchAlgorithmException e) {
		}
		return ret;
	}
	
/**<pre>
%fn rand(from, to) -> int

生成[from, to]范围内随机整数
 */
	public static int rand(int from, int to)
	{
		return from + (int)(Math.random() * (to-from+1));
	}
	
/**<pre>
%fn base64Encode(s) -> String
%param s String/byte[]
 */
	public static String base64Encode(String s) {
		// TODO: utf-8
		return base64Encode(s.getBytes());
	}
	public static String base64Encode(byte[] bs) {
		return Base64.getEncoder().encodeToString(bs);
	}
/**<pre>
%fn base64Decode(s) -> String
%fn base64DecodeBytes(s) -> byte[]

	String text = base64Decode(enc);
	
 */
	public static String base64Decode(String s) {
		return new String(base64DecodeBytes(s));
	}
	public static byte[] base64DecodeBytes(String s) {
		return Base64.getDecoder().decode(s);
	}

/**<pre>
%fn safeClose(o)

Close without exception.
 */
	public static void safeClose(AutoCloseable o) {
		try {
			if (o != null)
				o.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

/**<pre>
@fn addToStr(str, str1, sep)

添加字符串到str.

	StringBuilder atts = new StringBuilder();
	addToStr(atts, "100", ",");
	addToStr(atts, "200", ",");
	// atts.toString() = "100,200"

*/
	public static void addToStr(StringBuilder str, String str1, String sep)
	{
		if (str1 == null || str1.length() == 0)
			return;
		if (str.length() > 0)
			str.append(sep);
		str.append(str1);
	}
}
