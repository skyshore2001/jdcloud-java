package com.jdcloud;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.*;

import org.apache.tomcat.util.http.fileupload.FileItem;

@SuppressWarnings({"rawtypes", "unchecked"})
public class AccessControl extends JDApiBase {

	public class VcolDef
	{
		public List<String> res;
		public String join;
		public String cond;
		public boolean isDefault;
		// 依赖另一列
		public String require;
		public boolean isExt;

		public VcolDef res(String... val) {
			this.res = JDApiBase.asList(val);
			return this;
		}
		public VcolDef res(List<String> val) {
			this.res = val;
			return this;
		}
		public VcolDef join(String val) {
			this.join = val;
			return this;
		}
		public VcolDef cond(String val) {
			this.cond = val;
			return this;
		}
		public VcolDef isDefault(boolean val) {
			this.isDefault = val;
			return this;
		}
		public VcolDef require(String val) {
			this.require = val;
			return this;
		}
		public VcolDef isExt(boolean val) {
			this.isExt = val;
			return this;
		}
	}
	public class SubobjDef
	{
		public String sql;
		public boolean wantOne;
		public boolean isDefault;
		public String relatedKey; // ="%d" in jdcloud-php

		public String obj;
		public String cond;
		public String AC;
		public String res;
		public Map<String, Object> params = asMap();
		
		public SubobjDef sql(String val) {
			this.sql = val;
			return this;
		}
		public SubobjDef wantOne(boolean val) {
			this.wantOne = val;
			return this;
		}
		public SubobjDef isDefault(boolean val) {
			this.isDefault = val;
			return this;
		}
		public SubobjDef obj(String val) {
			this.obj = val;
			return this;
		}
		public SubobjDef cond(String val) {
			this.cond = val;
			return this;
		}
		public SubobjDef AC(String val) {
			this.AC = val;
			return this;
		}
		public SubobjDef res(String val) {
			this.res = val;
			return this;
		}
		public SubobjDef put(String key, Object value) {
			this.params.put(key, value);
			return this;
		}
		public SubobjDef relatedKey(String val) {
			this.relatedKey = val;
			return this;
		}
		public Object get(String key) {
			return this.params.get(key);
		}
	}

	class SqlConf
	{
		public List<String> cond;
		public List<String> res;
		public List<String> resExt;
		public List<String> join;
		public String orderby;
		public String gres, gcond;
		public HashMap<String, SubobjDef> subobj;
		public boolean distinct;
		public String union;
	}
	class Vcol
	{
		// def0包含alias, def不包含alias
		public String def, def0;
		// 指向vcolDef中的index
		public int vcolDefIdx = -1;
		// 已应用到最终查询中
		public boolean added;
	}

	public static final List<String> stdAc = asList("add", "get", "set", "del", "query", "setIf", "delIf");
	protected List<String> allowedAc;
	protected String ac;
	protected String table;

	// 在add后自动设置; 在get/set/del操作调用onValidateId后设置。
	protected int id;

	// for add/set
	protected List<String> readonlyFields;
	// for set
	protected List<String> readonlyFields2;
	// for add/set
	protected List<String> requiredFields;
	// for set
	protected List<String> requiredFields2;
	// for get/query
	protected List<String> hiddenFields = asList();
	protected List<String> hiddenFields0 = asList(); // 待隐藏的字段集合，字段加到hiddenFields中则一定隐藏，加到hiddenFields0中则根据用户指定的res参数判断是否隐藏该字段
	protected Set<String> userRes = new HashSet<>(); // 外部指定的res字段集合
	
	protected Map<String, String> aliasMap = asMap(); // {col => alias}

/**<pre>

@var AccessControl.enumFields 枚举支持及自定义字段处理
@alias enumFields(fieldName, EnumFieldFn)

(版本v1.1)

格式为{field => map/fn(val, row) }

作为比onHandleRow/onAfterActions等更易用的工具，enumFields可对返回字段做修正。例如，想要对返回的status字段做修正，如"CR"显示为"Created"，可设置：

	Map<String, Object> map = asMap("CR","Created", "CA","Cancelled");
	this.enumFields("status", map)

也可以设置为自定义函数，如：

	this.enumFields("status", (v, row) -> {
		if (map.containsKey(v))
			return String.format("%s-%s", v, map.get(v));
		return v;
	});

此外，枚举字段可直接由请求方通过res参数指定描述值，如：

	Ordr.query(res="id, status =CR:Created;CA:Cancelled")
	或指定alias:
	Ordr.query(res="id 编号, status 状态=CR:Created;CA:Cancelled")

更多地，设置enumFields也支持逗号分隔的枚举列表，比如字段值为"CR,CA"，实际可返回"Created,Cancelled"。
 */

	@FunctionalInterface 
	public interface EnumFieldFn
	{
		Object call(Object val, Map row) throws Exception;
	}
	// 通过同名enumFields方法来添加
	protected Map<String, Object> enumFields; // elem: {field => {key=>val}} 或 {field => fn(val,row)}，与onHandleRow类似地去修改数据。TODO: 目前只支持map，不支持

	// for query
	protected String defaultRes = "*"; // 缺省为 "t0.*" 加  default=true的虚拟字段
	protected String defaultSort = "t0.id";
	// for query
	protected int maxPageSz = 1000;

	// for get/query
	// virtual columns definition
	protected List<VcolDef> vcolDefs; // elem: {res, join, default?=false}
	protected Map<String, SubobjDef> subobj; // elem: { name => {sql, wantOne, isDefault}}

	@FunctionalInterface
	public interface OnAfterAction
	{
		void exec(Object ret) throws Exception;
	}
	// 回调函数集。在after中执行（在onAfter回调之后）。
	protected List<OnAfterAction> onAfterActions = asList();

/**
@var AccessControl.delField

如果设置该字段(例如设置为disableFlag字段)，则把删除动作当作是设置该字段为1，且在查询接口中跟踪此字段增加过滤。
必须是flag字段（0/1值）。

示例：

	// onInit中：
	this.delField = "disableFlag"
 */
	protected String delField;

	// for get/query
	// 注意：sqlConf.res/.cond[0]分别是传入的res/cond参数, sqlConf.orderby是传入的orderby参数, 为空均表示未传值。
	private SqlConf sqlConf; // {@cond, @res, @join, orderby, @subobj, @gres}
	private boolean isAggregationQuery; // 是聚合查询，如带group by或res中有聚合函数

	// virtual columns
	private HashMap<String, Vcol> vcolMap; // elem: vcol => {def, def0, added?, vcolDefIdx?=-1}

	public void init(String table, String ac) throws Exception
	{
		if (this.table == null)
			this.table = table;
		this.ac = ac;
		this.onInit();
	}

	protected void onInit() throws Exception
	{
	}
	protected void onValidate() throws Exception
	{
	}
	protected void onValidateId() throws Exception
	{
	}
	protected void onHandleRow(JsObject rowData) throws Exception
	{
	}
	protected void onAfter(Object ret) throws Exception
	{
	}
	protected void onQuery() throws Exception
	{
	}
	protected int onGenId() throws Exception
	{
		return 0;
	}
	
/**<pre>
 * @throws Exception 
@var AccessControl::create($tbl, $ac = null, $cls = null) 

如果$cls非空，则按指定AC类创建AC对象。
否则按当前登录类型自动创建AC类（回调onCreateAC）。

示例：

	AccessControl::create("Ordr", "add");
	AccessControl::create("Ordr", "add", true);
	AccessControl::create("Ordr", null, "AC0_Ordr");

@see JDBaseEnv.createAC
*/
	public AccessControl createAC(String tbl, String ac, String cls) throws Exception
	{
		return (AccessControl)env.createAC(tbl, ac, cls, null);
	}
	
	final public Object callSvc(String tbl, String ac) throws Exception
	{
		return this.callSvc(tbl, ac, null, null);
	}

/**<pre>
@fn AccessControl::callSvc(tbl, ac, param=null, postParam=null)

直接调用指定类的接口，如内部直接调用"PdiRecord.query"方法：

	// 假如当前是AC2权限，对应的AC类为AC2_PdiRecord:
	AccessControl acObj = new AC2_PdiRecord();
	acObj.env = env; // 别忘记指定env
	acObj.callSvc("PdiRecord", "query");

这相当于调用`callSvc("PdiRecord.query")`。
区别是，用本方法可自由指定任意AC类，无须根据当前权限自动匹配类。

例如，"PdiRecord.query"接口不对外开放，只对内开放，我们就可以只定义`class PdiRecord extends AccessControl`（无AC前缀，外界无法访问），在内部访问它的query接口：

	AccessControl acObj = new PdiRecord();
	acObj.env = env;
	acObj.callSvc("PdiRecord", "query");

如果未指定param/postParam，则使用当前GET/POST环境参数执行，否则使用指定环境执行，并在执行后恢复当前环境。

也适用于AC类内的调用，这时可不传table，例如调用当前类的add接口：

	Object rv = this.callSvc(null, "add", null, postParam);

示例：通过手机号发优惠券时，支持批量发量，用逗号分隔的多个手机号，接口：

	手机号userPhone只有一个时：
	Coupon.add()(userPhone, ...) -> id

	如果userPhone包含多个手机号：（用逗号隔开，支持中文逗号，支持有空格）
	Coupon.add()(userPhone, ...) -> {cnt, idList}

重载add接口，如果是批量添加则通过callSvc再调用add接口：

	public Object api_add() {
		if (this._POST.containsKey("userPhone")) {
			String userPhone = (String)this._POST.get("userPhone");
			String[] arr = userPhone.split("(?U)[,，]"); // 支持中文逗号
			if (arr.length > 1) {
				List idList = new ArrayList();
				JsObject postParam = new JsObject();
				postParam.putAll(this._POST);
				for (String e: arr) {
					postParam.put("userPhone", e.trim());
					idList.add(this.callSvc(null, "add", null, postParam));
				}
				setRet(0, asMap(
					"cnt", idList.size(),
					"idList", idList
				);
				throw new DirectReturn();
			}
		}
		return super.api_add();
	}

框架自带的批量添加接口api_batch也是类似调用。

@see callSvc
@see callSvcSafe
*/
	final public Object callSvc(String tbl, String ac, JsObject param, JsObject postParam) throws Exception
	{
		// 已初始化过，创建新对象调用接口，避免污染当前环境。
		if (this.ac != null && this.table != null) {
			AccessControl acObj = (AccessControl) this.env.onNewInstance(this.getClass()); // .newInstance();
			acObj.env = this.env;
			return acObj.callSvc(tbl != null ? tbl: this.table, ac, param, postParam);
		}
		if (param != null || postParam != null) {
			return env.tmpEnv(param, postParam, () -> {
				return this.callSvc(tbl, ac, null, null);
			});
		}

		this.init(tbl, ac);

		String fn = "api_" + ac;
		Method m = null;
		try {
			m = this.getClass().getMethod(fn);
		}
		catch (NoSuchMethodException ex) {
			throw new MyException(E_PARAM, String.format("Bad request - unknown `%s` method: `%s`", tbl, ac), "接口不支持");
		}
		this.before();
		Object ret = m.invoke(this);
		this.after(ret);
		return ret;
	}

	// for get/query
	protected void initQuery() throws Exception
	{
		String gres = (String)param("gres", null, null, false);
		String res = (String)param("res", null, null, false);

		sqlConf = new SqlConf();
		sqlConf.res = asList();
		sqlConf.resExt = asList();
		sqlConf.gres = gres;
		sqlConf.gcond = getCondParam("gcond");
		sqlConf.cond = asList(getCondParam("cond"));
		sqlConf.join = asList();
		sqlConf.orderby = (String)param("orderby", null, null, false);
		sqlConf.subobj = new HashMap<String, SubobjDef>();
		sqlConf.union = (String)param("union", null, null, false);
		sqlConf.distinct = (boolean)param("distinct/b", false);
		this.isAggregationQuery = sqlConf.gres != null;

		this.initVColMap();

		// support internal param res2/join/cond2, 内部使用, 必须用dbExpr()包装一下.
		Object v;
		if ((v = param("res2")) != null) {
			if (! (v instanceof DbExpr))
				throw new MyException(E_SERVER, "res2 should be DbExpr");
			this.filterRes(((DbExpr)v).val);
		}
		if ((v = param("join")) != null) {
			if (! (v instanceof DbExpr))
				throw new MyException(E_SERVER, "join should be DbExpr");
			this.addJoin(((DbExpr)v).val);
		}
		if ((v = param("cond2", null, null, false)) != null) {
			if (! (v instanceof DbExpr))
				throw new MyException(E_SERVER, "cond2 should be DbExpr");
			this.addCond(((DbExpr)v).val);
		}

		this.onQuery();
		if (this.delField != null) {
			this.addCond(this.delField + "=0");
			this.hiddenFields.add(this.delField);
		}

		boolean addDefaultCol = false;
		// 确保res/gres参数符合安全限定
		if (gres != null) {
			this.filterRes(gres, true);
		}
		else {
			if (res == null)
				res = this.defaultRes;
			if (res.charAt(0) == '*')
				addDefaultCol = true;
		}

		if (res != null) {
			this.filterRes(res);
		}
		// 设置gres时，不使用default vcols/subobj
		if (addDefaultCol) {
			this.addDefaultVCols();
			if (this.sqlConf.subobj.size() == 0 && this.subobj != null) {
				for (Map.Entry<String, SubobjDef> kv : this.subobj.entrySet()) {
					String col = kv.getKey();
					SubobjDef def = kv.getValue();
					if (def.isDefault)
						this.addSubobj(col, def);
				}
			}
		}
		if (ac.equals("query"))
		{
			this.supportEasyui();
			if (this.sqlConf.orderby != null && this.sqlConf.union == null)
				this.sqlConf.orderby = this.filterOrderby(this.sqlConf.orderby);
		}

		// fixUserQuery
		String cond = this.sqlConf.cond.get(0);
		if (cond != null)
			this.sqlConf.cond.set(0, fixUserQuery(cond));
		if (this.sqlConf.gcond != null)
			this.sqlConf.gcond = fixUserQuery(this.sqlConf.gcond);
	}

	// for add/set
	protected void validate() throws Exception
	{
		// TODO: check fields in metadata
		// foreach ($_POST as ($field, $val))

		if (this.readonlyFields != null)
		{
			for (Object field : this.readonlyFields)
			{
				if (env._POST.containsKey(field) && !(ac.equals("add") && this.requiredFields.contains(field)))
				{
					logit(String.format("!!! warn: attempt to chang readonly field `%s`", field));
					env._POST.remove(field);
				}
			}
		}
		if (ac.equals("set")) {
			if (this.readonlyFields2 != null)
			{
				for (Object field : this.readonlyFields2)
				{
					if (env._POST.containsKey(field))
					{
						logit(String.format("!!! warn: attempt to change readonly field `%s`", field));
						env._POST.remove(field);
					}
				}
			}
		}
		if (ac.equals("add")) {
			if (this.requiredFields != null)
			{
				for (Object field : this.requiredFields)
				{
					// 					if (! issetval(field, _POST))
					// 						throw new MyException(E_PARAM, "missing field `{field}`", "参数`{field}`未填写");
					mparam((String)field, "P"); // validate field and type; refer to field/type format for mparam.
				}
			}
		}
		else { // for set, the fields can not be set null
			List<String> arr = new ArrayList<>();
			if (this.requiredFields != null)
				arr.addAll(this.requiredFields);
			if (this.requiredFields2 != null)
				arr.addAll(this.requiredFields2);
			for (Object field : arr) {
				/* 
				if (is_array(field)) // TODO
					continue;
				*/
				Object v = env._POST.get(field);
				if (v != null && (v.equals("null") || v.equals("") || v.equals("") )) {
					throw new MyException(E_PARAM, String.format("%s.set: cannot set field `field` to null.", field));
				}
			}
		}
		this.onValidate();
	}
	
	public void before() throws Exception
	{
		if (this.allowedAc != null && stdAc.contains(ac) && !this.allowedAc.contains(ac))
			throw new MyException(E_FORBIDDEN, String.format("Operation `%s` is not allowed on object `%s`", ac, table));
	}
	
	protected AccessControl enumFields(String key, EnumFieldFn fn)
	{
		if (this.enumFields == null)
			this.enumFields = asMap();
		this.enumFields.put(key, fn);
		return this;
	}
	protected AccessControl enumFields(String key, Object obj)
	{
		if (this.enumFields == null)
			this.enumFields = asMap();
		this.enumFields.put(key, obj);
		return this;
	}

	private void handleEnumFields(JsObject rowData) throws Exception
	{
		if (this.enumFields != null) {
			// 处理enum/enumList字段返回
			forEach(this.enumFields, (field, e) -> {
				if (this.aliasMap.containsKey(field)) {
					field = this.aliasMap.get(field);
				}
				if (! rowData.containsKey(field))
					return;
				Object v = rowData.get(field);
				if (e instanceof EnumFieldFn) {
					EnumFieldFn fn = (EnumFieldFn)e;
					v = fn.call(v, rowData);
				}
				else if (e instanceof Map) {
					Map map = (Map)e;
					String SEP = ",";
					String k = v.toString();
					if (map.containsKey(k)) {
						v = map.get(k);
					}
					else if (k.contains(SEP)) {
						StringBuffer v1 = new StringBuffer();
						for (String ve: k.split(SEP)) {
							if (v1.length() > 0)
								v1.append(SEP);
							if (map.containsKey(ve))
								v1.append(map.get(ve));
							else
								v1.append(ve);
						}
						v = v1.toString();
					}
				}
				rowData.put(field, v);
			});
		}
	}

	// 用于onHandleRow或enumFields中，从结果中取指定列数据，避免直接用$row[$col]，因为字段有可能用的是别名。
	final protected Object getAliasVal(Map row, String col) {
		String alias = this.aliasMap.get(col);
		return row.get(alias != null? alias: col);
	}
	final protected Object setAliasVal(Map row, String col, Object val) {
		String alias = this.aliasMap.get(col);
		return row.put(alias != null? alias: col, val);
	}

	private void handleRow(JsObject rowData, int idx, int rowCnt) throws Exception
	{
		// TODO: flag_handleResult(rowData);
		this.onHandleRow(rowData);

		this.handleEnumFields(rowData);

		if (idx == 0) {
			this.fixHiddenFields();
		}
		for (Object field : this.hiddenFields)
		{
			rowData.remove(field);
		}
	}

/*
合并hiddenFields0到hiddenFields, 仅当字段符合下列条件：

- 在请求的res参数中未指定该字段
- 若res参数中包含"t0.*", 且该字段不是主表字段(t0.xxx)
- 若res参数中包含"*"（未指定res参数也缺省是"*"），且该字段不是主表字段(t0.xxx)，且不是缺省的虚拟字段或虚拟表(vcolDefs或subobj的default属性为false)
*/
	final protected void fixHiddenFields()
	{
		this.hiddenFields.add("pwd");

		String hiddenFields = (String)param("hiddenFields");
		if (hiddenFields != null) {
			// 当请求时指定参数"hiddenFields=0"时，忽略自动隐藏
			if (Objects.equals(hiddenFields, "0"))
				return;
			for (String e: hiddenFields.split("\\s*,\\s*")) {
				this.hiddenFields.add(e);
			}
		}

		for (String col: this.hiddenFields0) {
			if (this.userRes.contains(col) || this.hiddenFields.contains(col))
				continue;

			if (this.userRes.contains("*") || this.userRes.contains("t0.*")) {
				Vcol vcol = this.vcolMap.get(col);
				if (vcol != null) { // isVCol
					int idx = vcol.vcolDefIdx;
					boolean isDefault = this.vcolDefs.get(idx).isDefault;
					if (isDefault && this.userRes.contains("*"))
						continue;
				}
				else if (this.subobj != null && this.subobj.containsKey(col)) { // isSubobj
					boolean isDefault = this.subobj.get(col).isDefault;
					if (isDefault && this.userRes.contains("*"))
						continue;
				}
				else { // isMainobj
					continue;
				}
			}
			this.hiddenFields.add(col);
		}
	}

	// for query. "field1"=>"t0.field1"
	private String fixUserQuery(String q)
	{
		this.initVColMap();
		// group(0)匹配：禁止各类函数（以后面跟括号来识别）和select子句）
		Matcher m = regexMatch(q, "(?ix)\\b \\w+ (?=\\s*\\() | \\b select \\b");
		while (m.find()) {
			String key = m.group(0);
			if (! contains_ignoreCase(new String[] {"AND", "OR", "IN"}, key))
				throw new MyException(E_FORBIDDEN, String.format("forbidden `%s` in param cond", key));
		}
		
		// "aa = 100 and t1.bb>30 and cc IS null" . "t0.aa = 100 and t1.bb>30 and t0.cc IS null"
		m = regexMatch(q, "(?iU)[\\w.]+(?=\\s*[=><]|\\s+(IS|LIKE|BETWEEN|IN|NOT)\\s)");
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			// 't0.0' for col, or 'voldef' for vcol
			String col = m.group();
			if (col.matches("\\d+") || col.equalsIgnoreCase("NOT") || col.equalsIgnoreCase("IS")) {
				m.appendReplacement(sb, col);
				continue;
			}
			if (col.contains(".")) {
				m.appendReplacement(sb, col);
				continue;
			}
			if (this.vcolMap.containsKey(col)) {
				this.addVCol(col, false, "-");
				m.appendReplacement(sb, this.vcolMap.get(col).def);
				continue;
			}
			m.appendReplacement(sb, "t0." + col);
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private void supportEasyui()
	{
		if (param("rows") != null) {
			env._GET.put("pagesz", param("rows"));
		}

		// support easyui: sort/order
		String sort = (String)param("sort");
		if (sort != null)
		{
			String orderby = sort;
			String order = (String)param("order");
			if (order != null)
				orderby += " " + order;
			this.sqlConf.orderby = orderby;
		}
		// 兼容旧代码: 支持 _pagesz等参数，新代码应使用pagesz
		String[] arr = new String[] {"_pagesz", "_pagekey", "_fmt"};
		for (int i=0; i<arr.length; ++i) {
			String key = arr[i];
			if (param(key) != null) {
				env._GET.put(key.substring(1), param(key));
			}
		}
	}
	static String removeQuote(String k) {
		return regexReplace(k, "^\"(.*)\"$", "$1");
	}
	
	// 因为java不能对alias参数传址修改，只能返回它
	protected String handleAlias(String col, String alias)
	{
		// support enum
		String[] a = alias.split("=", 2);
		if (a.length != 2)
			return alias;

		alias = a[0].length() == 0? null: a[0];
		String k = alias==null?col:alias;
		if (k.charAt(0) == '"') // remove ""
			k = k.substring(1, k.length()-1);
		this.enumFields(k, parseKvList(a[1], ";", ":"));
		if (alias != null)
			this.aliasMap.put(removeQuote(col), removeQuote(alias));
		return alias;
	}

	private void addSubobj(String col, SubobjDef def) {
		if (def.cond != null) {
			def.cond = regexReplace(def.cond, "(?U)\\{(\\w+)\\}", (ms) -> {
				def.relatedKey = ms.group(1);
				return "%d";
			});
		}
		if (def.sql != null) {
			def.sql = regexReplace(def.sql, "(?U)\\{(\\w+)\\}", (ms) -> {
				def.relatedKey = ms.group(1);
				return "%d";
			});
		}
		this.sqlConf.subobj.put(col, def);
		if (def.relatedKey != null) {
			String col1 = def.relatedKey;
			if (col1.matches("(?U)\\W")) {
				throw new MyException(E_PARAM, "bad subobj.relatedKey=`" + col1 + "`. MUST be a column or virtual column.", "子对象定义错误");
			}
			this.addVCol(col1, VCOL_ADD_RES, null, true);
		}
	}

	private void filterRes(String res) {
		filterRes(res, false);
	}
	
	// 和fixUserQuery处理外部cond类似(安全版的addCond), filterRes处理外部传入的res (安全版的addRes)
	// gres?=false
	// return: new field list
	private void filterRes(String res, boolean gres)
	{
		List<String> cols = new ArrayList<String>();
		boolean isAll = false;
		for (String col0 : res.split(",")) 
		{
			String col = col0.trim();
			String alias = null;
			String fn = null;
			if (col.equals("*") || col.equals("t0.*")) 
			{
				this.addRes("t0.*", false);
				this.userRes.add(col);
				isAll = true;
				continue;
			}
			Matcher m;
			// 适用于res/gres, 支持格式："col" / "col col1" / "col as col1", alias可以为中文，如"col 某列"
			// 如果alias中有特殊字符（逗号不支持），则应加引号，如"amount \"金额(元)\"", "v \"速率 m/s\""等。
			if (! (m=regexMatch(col, "^(?iU)(\\w+)(?:\\s+(?:AS\\s+)?([^,]+))?$")).find())
			{
				// 对于res, 还支持部分函数: "fn(col) as col1", 目前支持函数: count/sum，如"count(distinct ac) cnt", "sum(qty*price) docTotal"
				if (!gres && (m=regexMatch(col, "^(?iU)(\\w+)\\(([a-z0-9_.\'* ,+-\\/]*)\\)\\s+(?:AS\\s+)?([^,]+)$")).find())
				{
					fn = m.group(1).toUpperCase();
					if (!fn.equals("COUNT") && !fn.equals("SUM") && !fn.equals("AVG") && !fn.equals("MAX") && !fn.equals("MIN"))
						throw new MyException(E_FORBIDDEN, String.format("function not allowed: `%s`", fn));
					String expr = m.group(2);
					alias = m.group(3);
					// 支持对虚拟字段的聚合函数 (addVCol)
					expr = regexReplace(expr, "(?Ui)\\b\\w+\\b", ms -> {
						String col1 = ms.group();
						if (col1.compareToIgnoreCase("distinct") == 0 || col1.matches("^\\d+$"))
							return col1;
						// isVCol
						if (this.addVCol(col1, true, "-"))
							return this.vcolMap.get(col1).def;
						return "t0." + col1;
					});
					col = String.format("%s(%s) %s", fn, expr, alias);
					this.isAggregationQuery = true;
				}
				else 
					throw new MyException(E_PARAM, String.format("bad property `%s`", col));
			}
			else
			{
				if (m.group(2) != null) {
					col = m.group(1);
					alias = m.group(2);
				}
			}
			if (fn != null) 
			{
				this.addRes(col);
				continue;
			}

			if (alias!=null)
				alias = handleAlias(col, alias);
			this.userRes.add(alias==null? col: alias);

// 			if (! ctype_alnum(col))
// 				throw new MyException(E_PARAM, "bad property `col`");
			if (this.addVCol(col, true, alias) == false)
			{
				if (!gres && this.subobj != null && this.subobj.containsKey(col))
				{
					String key = removeQuote(alias != null ? alias : col);
					this.addSubobj(key, this.subobj.get(col));
				}
				else
				{
					if (isAll)
						throw new MyException(E_PARAM, "`" + col + "` MUST be virtual column when `res` has `*`", "虚拟字段未定义: " + col);

					col = "t0." + col;
					String col1 = col;
					if (alias != null)
					{
						col1 += " " + alias;
					}
					this.addRes(col1);
				}
			}
			// mysql可在group-by中直接用alias, 而mssql要用原始定义
			if (env.dbStrategy.acceptAliasInOrderBy())
				cols.add(alias != null ? alias : col);
			else
				cols.add(col);
		}
		if (gres)
			this.sqlConf.gres = String.join(",", cols);
	}

	// 注意：mysql中order by/group by可以使用alias, 但mssql中不可以，需要换成alias的原始定义
	// 而在where条件中，alias都需要换成原始定义，见 fixUserQuery
	private String filterOrderby(String orderby)
	{
		List<String> colArr = new ArrayList<String>();
		for (String col0 : orderby.split(",")) {
			String col = col0.trim();
			Matcher m = regexMatch(col, "^(?iU)(\\w+\\.)?(\\S+)(\\s+(asc|desc))?$");
			if (! m.find())
				throw new MyException(E_PARAM, String.format("bad property `%s`", col));
			if (m.group(1) != null) // e.g. "t0.id desc"
			{
				colArr.add(col);
				continue;
			}
			col = regexReplace(col, "(?U)^(\\w+)", ms -> {
				String col1 = ms.group(1);
				// 注意：与cond不同，orderby使用了虚拟字段，应在res中添加。而cond中是直接展开了虚拟字段。因为where条件不支持虚拟字段。
				// 故不用：$this->addVCol($col1, true, '-'); 但应在处理完后删除辅助字段，避免多余字段影响导出文件等场景。
				if (this.addVCol(col1, true, null, true) != false) {
					// mysql可在order-by中直接用alias, 而mssql要用原始定义
					if (! env.dbStrategy.acceptAliasInOrderBy())
						col1 = this.vcolMap.get(col1).def;
					return col1;
				}
				return "t0." + col1;
			});
			colArr.add(col);
		}
		return String.join(",", colArr);
	}

	private boolean afterIsCalled = false;
	public void after(Object ret) throws Exception
	{
		// 确保只调用一次
		if (afterIsCalled)
			return;
		afterIsCalled = true;

		this.onAfter(ret);
		if (this.onAfterActions != null) {
			//this.onAfterActions.forEach(e -> e.exec(ret));
			for (OnAfterAction e: this.onAfterActions) {
				e.exec(ret);
			}
		}
	}

	public Object api_add() throws Exception
	{
		this.validate();
		this.id = this.onGenId();
		if (this.id != 0)
			env._POST.put("id", this.id);
		else if (env._POST.containsKey("id")) {
			env._POST.remove("id");
		}
		this.handleSubObjForAddSet();

		this.id = dbInsert(this.table, env._POST);

		String res = (String)param("res");
		Object ret = null;
		if (res != null) {
			ret = this.callSvc(null, "get", new JsObject("id", this.id, "res", res), null);
		}
		else {
			ret = this.id;
		}
		return ret;
	}

	public Object api_set() throws Exception
	{
		this.validateId();
		this.validate();
		this.handleSubObjForAddSet();

		@SuppressWarnings("unused")
		int cnt = dbUpdate(this.table, env._POST, this.id);
		return "OK";
	}

	private void handleSubObjForAddSet() throws Exception
	{
		if (this.subobj == null)
			return;
		List<OnAfterAction> onAfterActions = asList();
		forEach (this.subobj, (k, v) -> {
			Object subobjList = env._POST.get(k);
			if (subobjList != null && subobjList instanceof List && v.obj != null) {
				onAfterActions.add(ret -> {
					String relatedKey = null;
					Matcher m;
					if ((m=regexMatch(v.cond, "(?u)(\\w+)=%d")).find()) {
						relatedKey = m.group(1);
					}
					if (relatedKey == null) {
						throw new MyException(E_SERVER, "bad cond: cannot get relatedKey", "子表配置错误");
					}

					String objName = v.obj;
					AccessControl acObj = this.createAC(objName, null, v.AC);
					for (Map<String, Object> subobj: (List<Map>)subobjList) {
						Object subid = subobj.get("id");
						if (subid != null) {
							// set/del接口支持cond.
							String cond = relatedKey + "=" + this.id;
							if (subobj.get("_delete") == null) {
								acObj.callSvc(objName, "set", new JsObject("id", subid, "cond", cond), new JsObject(subobj));
							}
							else {
								acObj.callSvc(objName, "del", new JsObject("id", subid, "cond", cond), null);
							}
						}
						else {
							subobj.put(relatedKey, this.id);
							acObj.callSvc(objName, "add", null, new JsObject(subobj));
						}
					}
				});
				env._POST.remove(k);
			}
		});
		if (onAfterActions.size() > 0) {
			this.onAfterActions.addAll(0, onAfterActions);
		}
	}

	public Object api_del() throws Exception
	{
		this.validateId();

		String sql = this.delField == null
			? String.format("DELETE FROM %s WHERE id=%s", table, id)
			: String.format("UPDATE %s SET %s=1 WHERE id=%s", table, delField, id);
		int cnt = execOne(sql);
		if (cnt != 1)
			throw new MyException(E_PARAM, String.format("not found id=%s", id));
		return "OK";
	}

/**<pre>
@fn AccessControl::checkSetFields(allowedFields)

"set"/"setIf"接口中，限定可设置的字段。
不可设置的字段用readonlyFields/readonlyFields2来设置。

e.g.
	void onValidate()
	{
		if (this.ac.equals("set"))
			checkSetFields(asList("status", "cmt"));
	}
*/
	protected void checkSetFields(List<String> allowedFields)
	{
		for (String k: env._POST.keySet()) {
			if (! allowedFields.contains(k))
				throw new MyException(E_FORBIDDEN, String.format("forbidden to set field `%s`", k));
		}
	}

	protected class CondSql
	{
		String tblSql;
		String condSql;
	}
	// return {tblSql, condSql}
	protected CondSql genCondSql()
	{
		String cond = getCondParam("cond");
		if (cond == null)
			throw new MyException(E_PARAM, "requires param `cond`");

		initVColMap();
		addCond(fixUserQuery(cond));

		CondSql ret = new CondSql();
		ret.condSql = getCondStr(sqlConf.cond);

		ret.tblSql = this.table + " t0";
		if (sqlConf != null && sqlConf.join != null && sqlConf.join.size() > 0)
			ret.tblSql += "\n" + String.join("\n", sqlConf.join);
		return ret;
	}
/**<pre>
@fn AccessControl::api_setIf()

批量更新。

setIf接口会检测readonlyFields及readonlyFields2中定义的字段不可更新。
也可以直接用checkSetFields指定哪些字段允许更新。
返回更新记录数。
示例：

	class AC2_Ordr extends AccessControl {
		@Override
		public Object api_setIf() throws Exception {
			checkAuth(App.PERM_MGR);
			this.checkSetFields(asList("dscr", "amount"));
			Object empId = getSession("empId");
			addCond("t0.empId=" + empId);
			// addJoin("...");
			return super.api_setIf();
		}
	}

 */
	public Object api_setIf() throws Exception
	{
		for (List<String> roFields: asList(this.readonlyFields, this.readonlyFields2)) {
			if (roFields == null)
				continue;
			for (String field: roFields) {
				if (env._POST.containsKey(field))
					throw new MyException(E_FORBIDDEN, String.format("forbidden to set field `%s`", field));
			}
		}
		
		CondSql cond = genCondSql();
		JsObject kv = env._POST;
		// 有join时，防止字段重名。统一加"t0."
		if (sqlConf != null && sqlConf.join != null && sqlConf.join.size() > 0) {
			kv = new JsObject();
			for (Map.Entry<String,Object> pair: env._POST.entrySet()) {
				kv.put("t0." + pair.getKey(), pair.getValue());
			}
		}
		int cnt = dbUpdate(cond.tblSql, kv, cond.condSql);
		return cnt;
	}
	
/**<pre>
@fn AccessControl::api_delIf()

批量删除。返回删除记录数。
示例：

	class AC2_Ordr extends AccessControl {
		@Override
		public Object api_delIf() throws Exception {
			checkAuth(App.PERM_MGR);
			return super.api_delIf();
		}
	}
 */
	public Object api_delIf() throws Exception
	{
		CondSql cond = genCondSql();
		String sql = this.delField == null
			? String.format("DELETE t0 FROM %s WHERE %s", cond.tblSql, cond.condSql)
			: String.format("UPDATE %s SET t0.%s=1 WHERE %s AND t0.%s=0", cond.tblSql, delField, cond.condSql, delField);
		int cnt = execOne(sql);
		return cnt;
	}
	
	// 没有cond则返回null
	static String getCondStr(List<String> condArr)
	{
		StringBuffer condBuilder = new StringBuffer();
		for (String cond : condArr) {
			if (cond == null || cond.length() == 0)
				continue;
			if (condBuilder.length() > 0)
				condBuilder.append(" AND ");
			if (cond.charAt(0) != '(' && regexMatch(cond, "(?i) (and|or) ").find())
				condBuilder.append("(").append(cond).append(")");
			else 
				condBuilder.append(cond);
		}
		if (condBuilder.length() == 0)
			return null;
		return condBuilder.toString();
	}
	
/**<pre>
@fn AccessControl.getCondParam(paramName)

由于cond参数的特殊性，不宜用param("cond")来取，可以使用：

	String cond = getCondParam("cond");

支持GET/POST中各有一个cond/gcond条件。而且支持其中含有">","<"等特殊字符。
没有cond则返回null
*/
	protected String getCondParam(String paramName) {
		List<String> condArr = asList();
		Object[] conds = new Object[] { env._GET.get(paramName), env._POST.get(paramName) };
		for (Object cond: conds) {
			if (cond == null)
				continue;
			if (cond instanceof List)
				condArr.addAll((List)cond);
			else
				condArr.add(cond.toString());
		}
		return getCondStr(condArr);
	}

	// return [stringbuffer, tblSql, condSql]
	protected Object[] genQuerySql()
	{
		String tblSql, condSql;
		String resSql = String.join(",", sqlConf.res);
		if (resSql.equals("")) {
			resSql = "t0.id";
		}
		if (sqlConf.distinct) {
			resSql = "DISTINCT " + resSql;
		}

		tblSql = table + " t0";
		if (sqlConf.join.size() > 0)
			tblSql += "\n" + String.join("\n", sqlConf.join);

		condSql = getCondStr(sqlConf.cond);
		StringBuffer sql = new StringBuffer();
		sql.append(String.format("SELECT %s FROM %s", resSql, tblSql));
		if (condSql != null && condSql.length() > 0)
		{
			// TODO: flag_handleCond(condSql);
			sql.append("\nWHERE ").append(condSql);
		}
		return new Object[] { sql, tblSql, condSql };
	}

	private JsObject queryAllCache = new JsObject();
	protected JsArray queryAll(String sql, boolean assoc, boolean tryCache) throws Exception
	{
		JsArray ret = null;
		if (tryCache && queryAllCache != null)
		{
			ret = (JsArray)queryAllCache.get(sql);
		}
		if (ret == null)
		{
			ret = queryAll(sql, assoc);
			if (tryCache)
			{
				if (queryAllCache == null)
					queryAllCache = new JsObject();
				queryAllCache.put(sql, ret);
			}
		}
		return ret;
	}
	// k: subobj name
	private JsArray querySubObj(String k, SubobjDef opt, Map<String, Object> opt1) throws Exception
	{
		if (opt.obj == null)
			throw new MyException(E_PARAM, "missing subobj.obj", "子表定义错误");

		JsObject param = new JsObject("cond", opt.cond, "res", opt.res);
		Map param1 = castOrNull(param("param_" + k), Map.class);
		if (param1 != null) {
			Object cond = param1.get("cond");
			if (cond != null) {
				param.put("cond2", dbExpr(cond.toString()));
			}
			param.putAll(param1);
			if (param1.containsKey("wantOne")) {
				opt.wantOne = (boolean)param("wantOne/b", null, param1, false);
			}
		}
		Object res = param("res_" + k);
		if (res != null) {
			param.put("res", res);
		}

		// 设置默认参数，可被覆盖
		param.put("fmt", "list");
		if (this.ac.equals("query") && !Objects.equals(param("disableSubobjOptimize/b"), true)) {
			if (param.containsKey("pagesz"))
				throw new MyException(E_PARAM, "pagesz not allowed", "子查询query接口不可指定pagesz参数，请使用get接口或加disableSubobjOptimize=1参数");
			// 由于query操作对子查询做了查询优化，不支持指定pagesz, 必须查全部子对象数据。
			param.put("pagesz", -1);
		}
		else if (! param.containsKey("pagesz")) {
			param.put("pagesz", opt.wantOne? 1: -1);
		}

		param.putAll(opt1);

		String objName = opt.obj;
		AccessControl acObj = this.createAC(objName, null, opt.AC);
		Object rv = acObj.callSvc(objName, "query", param, null);
		return (JsArray)getJsValue(rv, "list");
	}

	private void handleSubObj(int id, JsObject mainObj) throws Exception
	{
		Map<String, SubobjDef> subobj = this.sqlConf.subobj;
		if (subobj != null) 
		{
			// opt: {sql, wantOne=false}
			forEach(subobj, (k, opt) -> {
				JsArray ret1;
				Object id1 = opt.relatedKey != null? this.getAliasVal(mainObj, opt.relatedKey) : id; // %d指定的关联字段会事先添加
				if (opt.sql == null) {
					if (id1 != null) {
						String cond = opt.cond != null? String.format(opt.cond, id1): null;
						ret1 = querySubObj(k, opt, asMap("cond", cond));
					}
					else {
						ret1 = new JsArray();
					}
				}
				else {
					String sql1 = String.format(opt.sql, id1); // e.g. "select * from OrderItem where orderId=%d"
					boolean tryCache = sql1.equals(opt.sql);
					ret1 = queryAll(sql1, true, tryCache);
				}
				if (opt.wantOne) 
				{
					if (ret1.size() > 0)
						mainObj.put(k, ret1.get(0));
					else
						mainObj.put(k, null);
				}
				else {
					mainObj.put(k, ret1);
				}
			});
		}
	}

	// 优化的子表查询. 对列表使用一次`IN (id,...)`查询出子表, 然后使用程序自行join
	// 临时添加了"id_"作为辅助字段.
	protected void handleSubObjForList(JsArray objArr) throws Exception
	{
		Map<String, SubobjDef> subobj = this.sqlConf.subobj;
		if (subobj == null || subobj.size() == 0 || objArr.size() == 0)
			return;

		for (Map.Entry<String, SubobjDef> kv: subobj.entrySet()) {
			String k = kv.getKey();
			// opt: {sql, wantOne=false}
			SubobjDef opt = kv.getValue();

			String idField = opt.relatedKey != null? opt.relatedKey: "id"; // 主表关联字段，默认为id，也可由"%d"选项指定。
			String[] joinField = {null};
			List<Object> idArr = asList();
			forEach(objArr, row -> {
				Object val = this.getAliasVal((JsObject)row, idField);
				if (val != null)
					idArr.add(val);
			});
			JsArray ret1 = new JsArray();
			if (idArr.size() > 0) {
				String idList = join(",", idArr);
				String sql = opt.cond != null? opt.cond: opt.sql;
				if (sql != null) {
					sql = regexReplace(sql, "(\\S+)=%d", ms -> {
						joinField[0] = ms.group(1);
						return joinField[0] + " IN (" + idList + ")";
					});
				}
				if (opt.sql == null) {
					Map<String, Object> param = asMap("cond", sql);
					if (joinField[0] != null) {
						// NOTE: GROUP BY也要做调整
						if (opt.get("gres") != null) {
							opt.put("gres", "id_," + opt.get("gres"));
						}
						param.put("res2", dbExpr(joinField[0] + " id_"));
					}
					ret1 = this.querySubObj(k, opt, param);
				}
				else {
					if (joinField[0] != null) {
						//TODO: 只替换第一个
						sql = regexReplace(sql, "(?i) from", String.format(", %s id_$0", joinField[0]), 1);
						sql = regexReplace(sql, "(?i)group by", String.format("$0 id_, ", sql), 1);
					}
					ret1 = queryAll(sql, true);
				}
			}

			if (joinField[0] == null) {
				Object ret1_o = ret1;
				if (opt.wantOne) {
					if (ret1.size() == 0)
						ret1_o = null;
					else
						ret1_o = ret1.get(0);
				}
				for (Object e: objArr) {
					JsObject row = (JsObject)e;
					row.put(k, ret1_o);
				}
				continue;
			}

			Map<Object, List<JsObject>>subMap = new HashMap<>(); // {id_=>[subobj_row]}
			for (Object e: ret1) {
				JsObject e1 = (JsObject)e;
				Object key = e1.get("id_");
				e1.remove("id_");
				if (! subMap.containsKey(key)) {
					subMap.put(key, asList(e1));
				}
				else {
					subMap.get(key).add(e1);
				}
			}
			// 关联主表
			for (Object e: objArr) {
				JsObject row = (JsObject)e;
				Object key = this.getAliasVal(row, idField);
				List<JsObject> val = subMap.get(key);
				Object val_o = val;
				if (opt.wantOne) {
					if (val != null)
						val_o = val.get(0);
				}
				else {
					if (val == null)
						val_o = asList();
				}
				row.put(k, val_o);
			}
		}
	}
	
	protected void validateId() throws Exception
	{
		this.onValidateId();
		if (this.id == 0)
			this.id = (int)mparam("id");
	
		// TODO: checkCond (refer to jdcloud-php)
	}

	// return: JsObject
	public Object api_get() throws Exception
	{
		this.validateId();
		this.initQuery();

		JsObject ret;
		this.addCond("t0.id=" + this.id, true);
		boolean hasFields = this.sqlConf.res.size() > 0;
		if (hasFields) {
			Object[] rv = genQuerySql();
			StringBuffer sql = (StringBuffer)rv[0];
			Object rv1 = queryOne(sql.toString(), true);
			if (rv1.equals(false))
				throw new MyException(E_PARAM, String.format("not found `%s.id`=`%s`", table, id));
			ret = (JsObject)rv1;
		}
		else {
			// 如果get接口用res字段指定只取子对象，则不必多次查询。e.g. callSvr('Ordr.get', {res: orderLog});
			ret = new JsObject("id", this.id);
		}

		this.handleSubObj(this.id, ret);
		this.handleRow(ret, 0, 1);
		return ret;
	}

	static DecimalFormat numberFormat = new DecimalFormat("#.######");
	void outputCsvLine(JsArray row, String enc)
	{
		boolean firstCol = true;
		for (Object e : row)
		{
			if (firstCol)
				firstCol = false;
			else
				echo(',');
			String s = null;
			if (e == null) {
				s = "";
			}
			else if (e instanceof Number) {
				s = numberFormat.format(e);
			}
			else if (e instanceof java.sql.Timestamp) {
				java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				s = df.format(e);
			}
			else {
				s = e.toString().replace("\"", "\"\"");
				if (enc != null)
				{
					try {
						byte[] bs = s.getBytes(enc);
						s = new String(bs, enc);
					} catch (UnsupportedEncodingException e1) {
					}
				}
			}
			// Excel使用本地编码(gb18030)
			// 大数字，避免excel用科学计数法显示（从11位手机号开始）。
			// 5位-10位数字时，Excel会根据列宽显示科学计数法或完整数字，11位以上数字总显示科学计数法。
			if (s.matches("^\\d{11,}$"))
				s += "\t";
			if (s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf(',') >= 0)
				echo('"', s, '"');
			else
				echo(s);
		}
		echo("\n");
	}

	void table2csv(JsObject tbl, String enc)
	{
		outputCsvLine((JsArray)tbl.get("h"), enc);
		for (Object row : (JsArray)tbl.get("d")) 
		{
			outputCsvLine((JsArray)row, enc);
		}
	}

	void table2txt(JsObject tbl)
	{
		String hdr = join("\t", (JsArray)tbl.get("h"));
		echo(hdr, "\n");
		for (Object row : (JsArray)tbl.get("d")) 
		{
			echo(join("\t", (JsArray)row), "\n");
		}
	}
	
	void table2html(JsObject tbl) throws Exception
	{
		table2html(tbl, false);
	}

	String table2html(JsObject tbl, boolean retStr) throws Exception
	{
		StringBuilder rv = new StringBuilder();
		rv.append("<table border=1 cellspacing=0>");
		if (tbl.containsKey("h")) {
			rv.append("<tr><th>").append(join("</th><th>", (JsArray)tbl.get("h"))).append("</th></tr>\n");
		}
		forEach ((JsArray)tbl.get("d"), row -> {
			rv.append("<tr><td>").append(join("</td><td>", (JsArray)row)).append("</td></tr>\n");
		});
		rv.append("</table>");
		if (retStr)
			return rv.toString();
		echo(rv.toString());
		return null;
	}

	void handleExportFormat(String fmt, JsObject ret, String fname) throws Exception
	{
		boolean handled = false;
		fname = java.net.URLEncoder.encode(fname, "UTF-8");
		if (fmt.equals("csv")) 
		{
			header("Content-Type", "application/csv; charset=UTF-8");
			header("Content-Disposition", "attachment;filename=" + fname + ".csv");
			table2csv(ret, null);
			handled = true;
		}
		else if (fmt.equals("excel") || fmt.equals("excelcsv")) 
		{
			header("Content-Type", "application/csv; charset=gb18030");
			header("Content-Disposition", "attachment;filename=" + fname + ".csv");
			table2csv(ret, "gb18030");
			handled = true;
		}
		else if (fmt.equals("txt")) 
		{
			header("Content-Type", "text/plain; charset=UTF-8");
			header("Content-Disposition", "attachment;filename=" + fname + ".txt");
			table2txt(ret);
			handled = true;
		}
		else if (fmt.equals("html")) {
			header("Content-Type", "text/html; charset=UTF-8");
			header("Content-Disposition", "filename=" + fname + ".html");
			table2html(ret);
			handled = true;
		}

		if (handled)
			exit();
	}

	public int getMaxPageSz()
	{
		return this.maxPageSz <0? PAGE_SZ_LIMIT: Math.min(this.maxPageSz, PAGE_SZ_LIMIT);
	}

	public Object api_query() throws Exception
	{
		this.initQuery();

		Integer pagesz = (Integer)param("pagesz/i");
		Integer pagekey = (Integer)param("pagekey/i");
		boolean enableTotalCnt = false;
		boolean enablePartialQuery = true;

		if (pagekey == null) {
			pagekey = (Integer)param("page/i");
			if (pagekey != null)
			{
				enableTotalCnt = true;
				enablePartialQuery = false;
			}
		}
		String fmt = (String)param("fmt");
		if (Objects.equals(fmt, "one") || Objects.equals(fmt, "one?"))
			pagesz = 1;
		else if (Objects.equals(fmt, "array") && pagesz == null)
			pagesz = -1;
		else if (pagesz == null || pagesz == 0)
			pagesz = 20;

		int maxPageSz = getMaxPageSz();
		if (pagesz != null && (pagesz < 0 || pagesz > maxPageSz))
			pagesz = maxPageSz;

		if (this.isAggregationQuery) {
			enablePartialQuery = false;
		}

		String orderSql = sqlConf.orderby;

		// setup cond for partialQuery
		if (orderSql == null && !this.isAggregationQuery)
			orderSql = this.filterOrderby(defaultSort);

		if (enableTotalCnt == false && pagekey != null && pagekey.intValue() == 0)
		{
			enableTotalCnt = true;
		}

		// 如果未指定orderby或只用了id(以后可放宽到唯一性字段), 则可以用partialQuery机制(性能更好更精准), pagekey表示该字段的最后值；否则pagekey表示下一页页码。
		String partialQueryCond;
		if (enablePartialQuery) {
			if (regexMatch(orderSql, "^(t0\\.)?id\\b").find()) {
				if (pagekey != null && pagekey != 0) {
					if (regexMatch(orderSql, "(?i)\\bid DESC").find()) {
						partialQueryCond = "t0.id<" + pagekey;
					}
					else {
						partialQueryCond = "t0.id>" + pagekey;
					}
					// setup res for partialQuery
					if (partialQueryCond != null) {
// 							if (sqlConf["res"][0] != null && !Regex.IsMatch('/\bid\b/',sqlConf["res"][0])) {
// 								array_unshift(sqlConf["res"], "t0.id");
// 							}
						sqlConf.cond.add(0, partialQueryCond);
					}
				}
			}
			else {
				enablePartialQuery = false;
			}
		}

		String tblSql, condSql;
		Object[] rv = genQuerySql();
		StringBuffer sql = (StringBuffer)rv[0];
		tblSql = (String)rv[1];
		condSql = (String)rv[2];
		boolean complexCntSql = false;
		if (sqlConf.union != null) {
			sql.append("\nUNION\n").append(sqlConf.union);
			complexCntSql = true;
		}
		if (sqlConf.gres != null) {
			sql.append(String.format("\nGROUP BY %s", sqlConf.gres));
			if (sqlConf.gcond != null)
				sql.append(String.format("\nHAVING %s", sqlConf.gcond));
			complexCntSql = true;
		}

		Object totalCnt = null;

		if (enableTotalCnt) {
			String cntSql;
			if (! complexCntSql) {
				cntSql = "SELECT COUNT(*) FROM " + tblSql;
				if (condSql != null && condSql.length() > 0)
					cntSql += "\nWHERE " + condSql;
			}
			else {
				cntSql = "SELECT COUNT(*) FROM (" + sql + ") t0";
			}
			totalCnt = queryOne(cntSql);
		}

		if (orderSql != null)
			sql.append(String.format("\nORDER BY %s", orderSql));

		if (enablePartialQuery) {
			sql.append(String.format("\nLIMIT %s", pagesz));
		}
		else {
			if (pagekey == null || pagekey == 0) {
				pagekey = 1;
				sql.append(String.format("\nLIMIT %s", pagesz));
			}
			else {
				sql.append(String.format("\nLIMIT %s,%s", (pagekey-1)*pagesz, pagesz));
			}
		}

		String sql1 = env.fixPaging(sql.toString());
		JsArray objArr = queryAll(sql1, true);

		// Note: colCnt may be changed in after().
		int fixedColCnt = objArr.size()==0? 0: ((JsObject)objArr.get(0)).size();
		
		boolean SUBOBJ_OPTIMIZE = true; // !!!
		int rowCnt = objArr.size();
		if (SUBOBJ_OPTIMIZE) {
			handleSubObjForList(objArr); // 优化: 总共只用一次查询, 替代每个主表查询一次
			int i = 0;
			for (Object rowData: objArr) {
				JsObject row = (JsObject)rowData;
				this.handleRow(row, i++, rowCnt);
			}
		}
		else {
			int i = 0;
			for (Object rowData : objArr) {
				JsObject row = (JsObject)rowData;
				Object id1 = this.getAliasVal(row, "id");
				if (id1 != null) {
					handleSubObj((int)id1, row);
				}
				this.handleRow(row, i++, rowCnt);
			}
		}
		Object reto = objArr;
		this.after(reto);

		String pivot = (String)param("pivot");
		if (pivot != null) {
			int[] v = new int[] {0};
			objArr = pivot(objArr, pivot, v);
			fixedColCnt = v[0];
		}

		Object nextkey = null;
		if (pagesz == objArr.size()) { // 还有下一页数据, 添加nextkey
			// TODO: res参数中没有指定id时?
			if (enablePartialQuery) {
				nextkey = ((JsObject)(objArr.get(objArr.size()-1))).get("id");
			}
			else {
				nextkey = pagekey + 1;
			}
		}
		return queryRet(objArr, nextkey, totalCnt, fixedColCnt);
	}
	
/**<pre>
@fn AccessControl.queryRet(objArr, nextkey?, totalCnt?, fixedColCnt?=0)

处理objArr，按照fmt参数指定的格式返回，与query接口返回相同。例如，默认的`h-d`表格式, `list`格式，`excel`等。
 */
	protected Object queryRet(JsArray objArr) throws Exception
	{
		return queryRet(objArr, null, null, 0);
	}
	protected Object queryRet(JsArray objArr, Object nextkey, Object totalCnt, int fixedColCnt) throws Exception
	{
		String fmt = (String)param("fmt");
		if (Objects.equals(fmt, "array"))
			return objArr;

		JsObject ret = null;
		Matcher m = null;
		if (Objects.equals(fmt, "list")) {
			ret = new JsObject("list", objArr);
		}
		else if (Objects.equals(fmt, "one")) {
			if (objArr.size() == 0)
				throw new MyException(E_PARAM, "no data", "查询不到数据");
			return objArr.get(0);
		}
		else if (Objects.equals(fmt, "one?")) {
			if (objArr.size() == 0)
				return false;
			JsObject row1 = (JsObject)objArr.get(0);
			if (row1.size() == 1)
				return row1.values().iterator().next();
			return objArr.get(0);
		}
		// hash
		// hash:keyField
		// hash:keyField,valueField
		// multihash
		// multihash:keyField
		// multihash:keyField,valueField
		else if (fmt != null && (m=regexMatch(fmt, "(?xU)^(multi)?hash (: (\\w+)? (,(\\w+))? )?$")).find()) {
			String isMulti = m.group(1);
			String keyField = m.group(3);
			String valueField = m.group(5);
			JsObject ret1 = new JsObject();
			forEach(objArr, row0 -> {
				Map row = cast(row0);
				String k = keyField != null? row.get(keyField).toString(): row.values().iterator().next().toString();
				Object v = valueField != null? row.get(valueField): row;
				if (isMulti != null) {
					if (ret1.containsKey(k)) {
						JsArray arr = cast(ret1.get(k));
						arr.add(v);
					}
					else {
						ret1.put(k, new JsArray(v));
					}
				}
				else {
					ret1.put(k, v);
				}
			});
			return ret1;
		}
		else {
			ret = objarr2table(objArr, fixedColCnt);
		}
		if (nextkey != null) {
			ret.put("nextkey", nextkey);
		}
		if (totalCnt != null) {
			ret.put("total", totalCnt);
		}
		if (fmt != null && !fmt.equals("list"))
			handleExportFormat(fmt, ret, (String)param("fname", this.table));
		return ret;
	}

/**<pre>
@fn AccessControl.qsearch(fields, q)

模糊查询

示例接口：

	Obj.query(q) -> 同query接口返回

查询匹配参数q的内容（比如查询name, label等字段）。
参数q是一个字符串，或多个以空格分隔的字符串。例如"aa bb"表示字段包含"aa"且包含"bb"。
每个字符串中可以用通配符"*"，如"a*"表示以a开头，"*a"表示以a结尾，而"*a*"和"a"是效果相同的。

实现：

	protected function onQuery() {
		this.qsearch(asList("name", "label", "content"), param("q"));
	}

*/
	protected void qsearch(List<String> fields, Object q)
	{
		if (q == null)
			return;

		StringBuilder cond = new StringBuilder();
		for (String q1: q.toString().trim().split("\\s+")) {
			if (q1.length() == 0)
				continue;
			String qstr;
			if (q1.indexOf("*") >= 0) {
				qstr = Q(q1.replace('*', '%'));
			}
			else {
				qstr = Q("%" + q1 + "%");
			}
			StringBuilder cond1 = new StringBuilder();
			for (String f: fields) {
				addToStr(cond1, f + " LIKE " + qstr, " OR ");
			}
			addToStr(cond, "(" + cond1 + ")", " AND ");
		}
		addCond(cond.toString());
	}

	public boolean addRes(String res) {
		return this.addRes(res, true, false);
	}
	public boolean addRes(String res, boolean analyzeCol) {
		return this.addRes(res, analyzeCol, false);
	}
	// analyzeCol?=true, isExt?=false
	public boolean addRes(String res, boolean analyzeCol, boolean isExt) {
		if (isExt) {
			return this.addResInt(this.sqlConf.resExt, res);
		}
		boolean rv = this.addResInt(this.sqlConf.res, res);
		if (analyzeCol)
			this.setColFromRes(res, true, -1);
		return rv;
	}

	// 内部被addRes调用。避免重复添加字段到res。
	// 返回true/false: 是否添加到输出列表
	private boolean addResInt(List<String>resArr, String col) {
		boolean ignoreT0 = resArr.contains("t0.*");
		// 如果有"t0.*"，则忽略主表字段如"t0.id"，但应避免别名字段如"t0.id orderId"被去掉
		if (ignoreT0 && col.startsWith("t0.") && !col.contains(" "))
			return false;
		boolean found = resArr.contains(col);
		if (!found) {
			resArr.add(col);
			return true;
		}
		return false;
	}

/**<pre>
@fn AccessControl::addCond(cond, prepend=false)

@param prepend 为true时将条件排到前面。

调用多次addCond时，多个条件会依次用"AND"连接起来。

添加查询条件。
示例：假如设计有接口：

	Ordr.query(q?) . tbl(..., payTm?)
	参数：
	q:: 查询条件，值为"paid"时，查询10天内已付款的订单。且结果会多返回payTm/付款时间字段。

实现时，在onQuery中检查参数"q"并定制查询条件：

	protected void onQuery()
	{
		// 限制只能看用户自己的订单
		uid = _SESSION["uid"];
		this.addCond("t0.userId=uid");

		q = param("q");
		if (isset(q) && q.equals("paid")) {
			validDate = date("Y-m-d", strtotime("-9 day"));
			this.addRes("olpay.tm payTm");
			this.addJoin("INNER JOIN OrderLog olpay ON olpay.orderId=t0.id");
			this.addCond("olpay.action='PA' AND olpay.tm>'validDate'");
		}
	}

@see AccessControl::addRes
@see AccessControl::addJoin
 */
	public void addCond(String cond) {
		this.addCond(cond, false);
	}
	public void addCond(String cond, boolean prepend) {
		this.addCond(cond, prepend, true);
	}
	// prepend?=false, fixUserQuery=true
	public void addCond(String cond, boolean prepend, boolean doFixUserQuery)
	{
		if (doFixUserQuery)
			cond = fixUserQuery(cond);
			
		if (sqlConf == null)
			sqlConf = new SqlConf();
		if (sqlConf.cond == null)
			sqlConf.cond = asList();
		if (prepend)
			this.sqlConf.cond.add(0, cond);
		else
			this.sqlConf.cond.add(cond);
	}

	/**
@fn AccessControl::addJoin(joinCond)

添加Join条件.

@see AccessControl::addCond 其中有示例
	 */
	public void addJoin(String join)
	{
		if (sqlConf == null)
			sqlConf = new SqlConf();
		if (sqlConf.join == null)
			sqlConf.join = asList();
		this.sqlConf.join.add(join);
	}

	// vcolDefIdx?=-1
	private void setColFromRes(String res, boolean added, int vcolDefIdx)
	{
		Matcher m = null;
		String colName, def;
		if ( (m=regexMatch(res, "(?U)^(\\w+)\\.(\\w+)$")).find()) {
			if (m.group(1).equals("t0"))
				return;
			colName = m.group(2);
			def = res;
		}
		else if ( (m = regexMatch(res, "(?isU)^(.*?)\\s+(?:as\\s+)?\"?(\\S+?)\"?$")).find()) {
			colName = m.group(2);
			def = m.group(1);
		}
		else
			throw new MyException(E_PARAM, String.format("bad res definition: `%s`", res));

		colName = removeQuote(colName);
		if (this.vcolMap.containsKey(colName)) {
			if (!added)
				throw new MyException(E_SERVER, String.format("redefine vcol `%s.%s`", this.table, colName), "虚拟字段定义重复");
			this.vcolMap.get(colName).added = true;
		}
		else {
			Vcol vcol = new Vcol();
			vcol.def = def;
			vcol.def0 = res;
			vcol.added = added;
			vcol.vcolDefIdx = vcolDefIdx;
			this.vcolMap.put(colName, vcol);
		}
	}


	// 外部虚拟字段：如果未设置isExt，且无join条件，将自动识别和处理外部虚拟字段（以便之后优化查询）。
	// 示例 "res" => ["(select count(*) from ApiLog t1 where t1.ses=t0.ses) sesCnt"] 将设置 isExt=true, require="t0.ses"
	// 注意框架自动分析res得到isExt和require属性，如果分析不正确，则可手工设置。require属性支持逗号分隔的多字段。
	private void autoHandleExtVCol(VcolDef vcolDef) {
		/* TODO
		if (isset($vcolDef["isExt"]) || isset($vcolDef["join"]) || isset($this->sqlConf['gres']))
			return;

		// 只有res数组定义时：不允许既有外部字段又有内部字段；如果全是外部字段，尝试自动分析得到require字段。
		$isExt = null;
		$reqColSet = []; // [col => true]
		foreach ($vcolDef["res"] as $res) {
			$isExt1 = preg_match('/\(.*select.*where(.*)\)/ui', $res, $ms)? true: false;
			if ($isExt === null)
				$isExt = $isExt1;
			if ($isExt !== $isExt1) {
				throw new MyException(E_SERVER, "bad res: '$res'", "字段定义错误：外部虚拟字段与普通虚拟字段不可定义在一起，请分拆成多组，或明确定义`isExt`。");
			}
			if (preg_match_all('/\bt0\.(\w+)\b/u', $ms[1], $ms1)) {
				foreach ($ms1[1] as $e) {
					$reqColSet[$e] = true;
				}
			}
		}
		$vcolDef["isExt"] = $isExt;
		if (count($reqColSet) > 0)
			$vcolDef["require"] = join(',', array_keys($reqColSet));
		*/
	}

	private void initVColMap()
	{
		if (this.vcolMap != null)
			return;

		this.vcolMap = new HashMap<String,Vcol>();
		if (this.vcolDefs == null)
			return;

		int idx = 0;
		for (VcolDef vcolDef : this.vcolDefs) {
			for (String e : vcolDef.res) {
				this.setColFromRes(e, false, idx);
			}
			++ idx;

			this.autoHandleExtVCol(vcolDef);
		}
	}

 	final int VCOL_ADD_RES = 0x2;
 	final int VCOL_ADD_SUBOBJ = 0x4;

/**<pre>
@fn AccessControl::addVCol(col, ignoreError=false, alias=null)

根据列名找到vcolMap中的一项，添加到最终查询语句中.
vcolMap是分析vcolDef后的结果，每一列都对应一项；而在一项vcolDef中可以包含多列。

@param col 必须是一个英文词, 不允许"col as col1"形式; 该列必须在 vcolDefs 中已定义.
@param alias 列的别名。可以中文. 特殊字符"-"表示不加到最终res中(只添加join/cond等定义), 由addVColDef内部调用时使用.
@return Boolean T/F

用于AccessControl子类添加已在vcolDefs中定义的vcol. 一般应先考虑调用addRes(col)函数.

@see AccessControl::addRes
 */
	// ignoreError?=false, alias?=null
	protected boolean addVCol(String col, Object ignoreError /*= false*/, String alias /*= null*/, boolean isHiddenField)
	{
		if (! this.vcolMap.containsKey(col)) {
			boolean rv = false;
			if (ignoreError instanceof Integer && ((Integer)ignoreError & VCOL_ADD_SUBOBJ) != 0 && this.subobj.containsKey(col)) {
				this.addSubobj(col, this.subobj.get(col));
				rv = true;
			}
			else if (Objects.equals(ignoreError, false)) {
				throw new MyException(E_SERVER, String.format("unknown vcol `%s`", col));
			}
			else if (ignoreError instanceof Integer && ((Integer)ignoreError & VCOL_ADD_RES) != 0) {
				rv = this.addRes("t0." + col);
			}
			if (isHiddenField && rv == true)
				this.hiddenFields0.add(col);
			return rv;
		}
		if (this.vcolMap.get(col).added)
			return true;

		VcolDef vcolDef = this.addVColDef(this.vcolMap.get(col).vcolDefIdx);
		if (vcolDef == null)
			throw new MyException(E_SERVER, "bad vcol " + col);
		if (Objects.equals(alias, "-"))
			return true;

		Vcol vcol = this.vcolMap.get(col);
		vcol.added = true;
		if (alias != null) {
			this.addRes(vcol.def + " " + alias, false, vcolDef.isExt);
			this.vcolMap.put(alias, vcol); // vcol及其alias同时加入vcolMap并标记已添加"added"
		}
		else {
			this.addRes(vcol.def0, false, vcolDef.isExt);
		}
		if (isHiddenField) {
			this.hiddenFields0.add(alias!=null? alias: col);
		}
		return true;
	}
	protected boolean addVCol(String col, Object ignoreError, String alias)
	{
		return this.addVCol(col, ignoreError, alias, false);
	}

	private void addDefaultVCols()
	{
		if (this.vcolDefs == null)
			return;
		int idx = 0;
		for (VcolDef vcolDef : this.vcolDefs) {
			if (vcolDef.isDefault) {
				this.addVColDef(idx);
				for (String e : vcolDef.res) {
					this.addRes(e, true, vcolDef.isExt);
				}
			}
			++ idx;
		}
	}

	/*
	根据index找到vcolDef中的一项，添加join/cond到最终查询语句(但不包含res)。
	 */
	private Set<Integer> m_vcolDefIndex = new HashSet<Integer>();
	private VcolDef addVColDef(int idx)
	{
		if (idx < 0)
			return null;

		VcolDef vcolDef = this.vcolDefs.get(idx);
		if (m_vcolDefIndex.contains(idx))
			return vcolDef;
		m_vcolDefIndex.add(idx);

		boolean isExt = vcolDef.isExt;
		// require支持一个或多个字段(虚拟字段, 表字段, 子表字段均可), 多个字段以逗号分隔
		if (vcolDef.require != null) {
			this.addRequireCol(vcolDef.require, isExt);
		}
		if (isExt)
			return vcolDef;

		if (vcolDef.join != null)
			this.addJoin(vcolDef.join);
		if (vcolDef.cond != null)
			this.addCond(vcolDef.cond);
		return vcolDef;
	}

	private void addRequireCol(String col, boolean isExt) {
		if (col.contains(",")) {
			String[] colArr = col.split(",");
			for (String col1: colArr) {
				this.addRequireCol(col1.trim(), isExt);
			}
			return;
		}
		if (col.contains("."))
			throw new MyException(E_PARAM, "`require` cannot use table name: " + col, "字段依赖设置错误");
		this.addVCol(col, VCOL_ADD_RES | VCOL_ADD_SUBOBJ, null, true);
	}

/**<pre>
@fn AccessControl::isFileExport()

返回是否为导出文件请求。
*/
	protected boolean isFileExport()
	{
		if (! Objects.equals(this.ac, "query"))
			return false;
		Object fmt = param("fmt");
		return fmt != null && !Objects.equals(fmt, "list") && !Objects.equals(fmt, "one") && !Objects.equals(fmt, "one?");
	}
	
/**<pre>
@fn AccessControl::api_batchAdd()

批量添加（导入）。返回导入记录数cnt及编号列表idList

	Obj.batchAdd(title?)(...) -> {cnt, @idList}

在一个事务中执行，一行出错后立即失败返回，该行前面已导入的内容也会被取消（回滚）。

- title: List(fieldName). 指定标题行(即字段列表). 如果有该参数, 则忽略POST内容或文件中的标题行.
 如"title=name,-,addr"表示导入第一列name和第三列addr, 其中"-"表示忽略该列，不导入。
 字段列表以逗号或空白分隔, 如"title=name - addr"与"title=name, -, addr"都可以.

支持三种方式上传：

1. 直接在HTTP POST中传输内容，数据格式为：首行为标题行(即字段名列表)，之后为实际数据行。
行使用"\n"分隔, 列使用"\t"分隔.
接口为：

	{Obj}.batchAdd(title?)(标题行，数据行)
	(Content-Type=text/plain)

前端JS调用示例：

	var data = "name\taddr\n" + "门店1\t地址1\n门店2\t地址2\n";
	callSvr("Store.batchAdd", function (ret) {
		app_alert("成功导入" + ret.cnt + "条数据！");
	}, data, {contentType:"text/plain"});

或指定title参数:

	var data = "门店名\t地址\n" + "门店1\t地址1\n门店2\t地址2\n";
	callSvr("Store.batchAdd", {title: "name,addr"}, function (ret) {
		app_alert("成功导入" + ret.cnt + "条数据！");
	}, data, {contentType:"text/plain"});

示例: 在chrome console中导入数据

	callSvr("Vendor.batchAdd", {title: "-,name, tel, idCard, addr, email, legalAddr, weixin, qq, area, picId"}, $.noop, `编号	姓名	手机号码	身份证号	通讯地址	邮箱	户籍地址	微信号	QQ号	负责安装的区域	身份证图
	112	郭志强	15384813214	150221199211215000	内蒙古呼和浩特赛罕区丰州路法院小区二号楼	815060695@qq.com	内蒙古包头市	15384813214	815060695	内蒙古	532
	111	高长平	18375998418	500226198312065000	重庆市南岸区丁香路同景国际W组	1119780700@qq.com	荣昌	18375998418	1119780700	重庆	534
	`, {contentType:"text/plain"});
		
2. 标准csv/txt文件上传：

上传的文件首行当作标题列，如果这一行不是后台要求的标题名称，可通过URL参数title重新定义。
一般使用excel csv文件（编码一般为gbk），或txt文件（以"\t"分隔列）。
接口为：

	{Obj}.batchAdd(title?)(csv/txt文件)
	(Content-Type=multipart/form-data, 即html form默认传文件的格式)

后端处理时, 将自动判断文本编码(utf-8或gbk).

前端HTML:

	<input type="file" name="f" accept=".csv,.txt">

前端JS示例：

	var fd = new FormData();
	fd.append("file", frm.f.files[0]);
	callSvr("Store.batchAdd", {title: "name,addr"}, function (ret) {
		app_alert("成功导入" + ret.cnt + "条数据！");
	}, fd);

或者使用curl等工具导入：
从excel中将数据全选复制到1.txt中(包含标题行，也可另存为csv格式文件)，然后导入。
下面示例用curl工具调用VendorA.batchAdd导入：

	#/bin/sh
	baseUrl=http://localhost/p/anzhuang/api.php
	param=title=name,phone,idCard,addr,email,legalAddr,weixin,qq,area
	curl -v -F "file=@1.txt" "$baseUrl/VendorA.batchAdd?$param"

如果要调试(php/xdebug)，可加URL参数`XDEBUG_SESSION_START=1`或Cookie中加`XDEBUG_SESSION=1`

3. 传入对象数组
格式为 {list: [...]}

	var data = {
		list: [
			{name: "郭志强", tel: "15384813214"},
			{name: "高长平", tel: "18375998418"}
		]
	};
	callSvr("Store.batchAdd", function (ret) {
		app_alert("成功导入" + ret.cnt + "条数据！");
	}, data, {contentType:"application/json"});

*/
	protected BatchAddLogic batchAddLogic;
	public Object api_batchAdd() throws Exception
	{
		BatchAddStrategy st = BatchAddStrategy.create(this.batchAddLogic, this);
		int n = 0;
		List titleRow = null;
		JsArray idList = new JsArray();
		Object row = null; // 可以是List或Map类型。当isTable=true时返回List是值数组形式。
		int cnt = 0;
		while ((row = st.getRow()) != null) {
			++ n;
			if (st.isTable() && n == 1) {
				titleRow = (List)row;
				continue;
			}
			List row1 = null;
			Map row2 = null;
			if (row instanceof List) {
				row1 = (List)row;
				cnt = row1.size();
			}
			else if (row instanceof Map) {
				row2 = (Map)row;
				cnt = row2.size();
			}
			if (cnt == 0)
				continue;

			JsObject postParam = new JsObject();
			if (row1 != null) { // list
				int i = 0;
				for (Object e0: titleRow) {
					String e = (String)e0;
					if (i >= cnt)
						break;
					if (e.equals("-")) {
						++ i;
						continue;
					}
					Object val = row1.get(i);
					++ i;
					if (Objects.equals(val, ""))
						val = null;
					postParam.put(e, val);
				}
			}
			else {
				postParam.putAll(row2);
			}
			Object id = null;
			try {
				st.beforeAdd(postParam, row);
				id = this.callSvc(null, "add", env._GET, postParam);
			}
			catch (DirectReturn ex) {
				id = ex.retVal;
//				throw new MyException(E_SERVER, "bad DirectReturn", String.format("第%d行出错(\"%s\"): 批量添加不支持返回DirectReturn", n, row));
			}
			catch (Exception ex) {
				Throwable e1 = ex;
				if (e1 instanceof InvocationTargetException) {
					do {
						e1 = e1.getCause();
					}
					while (e1 != null && e1 instanceof InvocationTargetException);
				}
				String msg = e1.getMessage();
				if (e1 instanceof DirectReturn) {
					id = ((DirectReturn)e1).retVal;
				}
				else {
					e1.printStackTrace();
					throw new MyException(E_PARAM, e1.toString(), String.format("第%d行出错(\"%s\"): %s", n, row, msg));
				}
			}
			idList.add(id);
		}
		return asMap(
			"cnt", idList.size(),
			"idList", idList
		);
	}

/**
@class BatchAddLogic

用于定制批量导入行为。
示例，实现接口：

	Task.batchAdd(orderId, task1)(city, brand, vendorName, storeName)

其中vendorName和storeName字段需要通过查阅修正为vendorId和storeId字段。

	class TaskBatchAddLogic extends BatchAddLogic
	{
		protected $vendorCache = [];
		function __construct () {
			// 每个对象添加时都会用的字段，加在$this->params数组中
			$this->params["orderId"] = mparam("orderId", "G"); // mparam要求必须指定该字段
			$this->params["task1"] = param("task1", null, "G");
		}
		// $params为待添加数据，可在此修改，如用`$params["k1"]=val1`添加或更新字段，用unset($params["k1"])删除字段。
		// $row为原始行数据数组。
		function beforeAdd(&$params, $row) {
			// vendorName -> vendorId
			// 如果会大量重复查询vendorName,可以将结果加入cache来优化性能
			if (! $this->vendorCache)
				$this->vendorCache = new SimpleCache(); // name=>vendorId
			$vendorId = $this->vendorCache->get($params["vendorName"], function () use ($params) {
				$id = queryOne("SELECT id FROM Vendor", false, ["name" => $params["vendorName"]] );
				if (!$id) {
					// throw new MyException(E_PARAM, "请添加供应商", "供应商未注册: " . $params["vendorName"]);
					// 自动添加
					$id = callSvcInt("Vendor.add", null, [
						"name" => $params["vendorName"],
						"tel" => $params["vendorPhone"]
					]);
				}
				return $id;
			});
			$params["vendorId"] = $vendorId;
			unset($params["vendorName"]);
			unset($params["vendorPhone"]);

			// storeName -> storeId 类似处理 ...
		}
		// 处理原始标题行数据, $row1是通过title参数传入的标题数组，可能为空
		function onGetTitleRow($row, $row1) {
		}
	}

	class AC2_Task extends AC0_Task
	{
		function api_batchAdd() {
			$this->batchAddLogic = new TaskBatchAddLogic();
			return parent::api_batchAdd();
		}
	}

@see api_batchAdd
*/
	public static class BatchAddLogic
	{
		public JsObject params = new JsObject();

		// postParam为待添加的数据，row为原始行数据数组。
		public void beforeAdd(JsObject postParam, Object row) {
		}
		// 处理原始标题行数据, row1是通过title参数传入的标题数组，可能为空
		public void onGetTitleRow(Object row, List row1) {
		}
	}

/*
分析符合下列格式的HTTP POST内容：

- 以"\n"为行分隔，以"\t"为列分隔的文本数据表。
- 第1行: 标题(如果有URL参数title，则忽略该行)，第2行开始为数据

若需要定制其它导入方式，可继承和改写该类，如CsvBatchAddStrategy，改写以下方法

	onInit
	onGetRow

通过BatchAddLogic::create来创建合适的类。
*/
	public static class BatchAddStrategy
	{
		protected int rowIdx = 0;
		protected BatchAddLogic logic;
		private List<List<String>> rows;

		protected JDEnvBase env;
		protected AccessControl api;

		public static BatchAddStrategy create(BatchAddLogic logic, AccessControl api) {
			BatchAddStrategy st = null;
			if (api.env._POST != null && api.env._POST.containsKey("list")) {
				st = new JsonBatchAddStrategy();
			}
			/* 统一用BatchAddLogic处理，支持有引号、换行等特殊csv文件，支持自动检测以逗号或tab分隔。
			else if (api.env._FILES == null) {
				st = new CsvBatchAddStrategy();
			}
			*/
			else {
				st = new BatchAddStrategy();
			}
			if (logic == null)
				st.logic = new BatchAddLogic();
			else
				st.logic = logic;
			st.api = api;
			st.env = api.env;
			return st;
		}

		public void beforeAdd(JsObject postParam, Object row) {
			postParam.putAll(this.logic.params);
			this.logic.beforeAdd(postParam, row);
		}

		// true: h,d分离的格式, false: objarr格式
		public boolean isTable() {
			return true;
		}

		protected void onInit() throws Exception {
			String content = null;
			if (env._FILES == null) {
				content = this.api.env.getHttpInput();
				if (content == null || content.length() == 0)
					throw new MyException(E_PARAM, "no file", "上传内容为空");
				backupFile(content, null);
			}
			else {
				if (env._FILES.size() == 0)
					throw new MyException(E_PARAM, "no file", "没有文件上传");
				FileItem f = env._FILES.get(0);
				if (f.getSize() <= 0)
					throw new MyException(E_PARAM, "error file", "文件数据出错");
				File bakF = backupFile(null, f);
				content = readFile(bakF);
			}
			int pos = content.indexOf('\n');
			String line1 = pos<0? content: content.substring(0, pos);
			String sep = ",";
			if (line1.contains("\t"))
				sep = "\\t";
			this.rows = parseCsv(content, sep);
		}
		protected Object onGetRow() {
			if (this.rowIdx >= this.rows.size())
				return null;
			return rows.get(this.rowIdx);
		}

		public Object getRow() throws Exception {
			if (this.rowIdx == 0) {
				this.onInit();
			}
			Object row = this.onGetRow();
			if (row == null)
				return null;
			if (++ this.rowIdx == 1) {
				String title = (String)api.param("title", null, "G");
				List row1 = null;
				if (title != null) {
					row1 = Arrays.asList(title.split("[\\s,]+"));
				}
				this.logic.onGetTitleRow(row, row1);
				if (row1 != null)
					row = row1;
			}
			return row;
		}

		// 保存http请求的内容.
		File backupFile(String content, FileItem fi) throws Exception {
			File dir = new File(env.baseDir + "/upload/import");
			if (!dir.isDirectory()) {
				if (! dir.mkdirs()) // TODO: file mode 777
					throw new MyException(E_SERVER, "fail to create folder: dir");
			}
			String fname = dir.getPath() + "/" + date("yyyyMMdd_HHmmss", null);
			String orgName = fi != null? fi.getName(): "(content)";
			String ext = extname(orgName);
			if (ext.length() == 0)
				ext = "txt";
			int n = 0;
			File bakF = null;
			do {
				if (n == 0)
					bakF = new File(String.format("%s.%s", fname, ext));
				else
					bakF = new File(String.format("%s_%d.%s", fname, n, ext));
				++ n;
			} while (bakF.exists());

			if (content != null) {
				writeFile(content, bakF);
			}
			else {
				fi.write(bakF);
			}

			String title = (String)api.param("title", null, "G");
			if (title == null) {
				title = "(line 1)";
			}
			api.logit(String.format("import file: %s, backup: %s, title: %s", orgName, bakF.getPath(), title));
			return bakF;
		}
	}

	/*
	支持csv, txt两种文件，分别以","和"\t"分隔。
	标题栏为数据第一行，也可通过title参数来覆盖。
	public static class CsvBatchAddStrategy extends BatchAddStrategy
	{
		protected void onInit() {
		}
	}
	*/

	public static class JsonBatchAddStrategy extends BatchAddStrategy
	{
		private List rows;
		protected void onInit() {
			this.rows = cast(env._POST.get("list"));
		}
		protected Object onGetRow() {
			if (this.rowIdx < 0 || this.rowIdx >= this.rows.size())
				return null;
			return this.rows.get(this.rowIdx);
		}
		public boolean isTable() {
			return false;
		}
	}
}
