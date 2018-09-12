package com.jdcloud;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.*;

public class AccessControl extends JDApiBase {

	public class VcolDef
	{
		public List<String> res;
		public String join;
		public String cond;
		public boolean isDefault;
		// 依赖另一列
		public String require;
		
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
	}
	public class SubobjDef
	{
		public String sql;
		public boolean wantOne;
		public boolean isDefault;
		public boolean force;
		
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
		public SubobjDef force(boolean val) {
			this.force = val;
			return this;
		}
	}

	class SqlConf
	{
		public List<String> cond;
		public List<String> res;
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
	protected List<String> hiddenFields;

/**<pre>

@var AccessControl.enumFields 枚举支持及自定义字段处理

(版本v1.1)

格式为{field => map/fn(val) }

作为比onHandleRow/onAfterActions等更易用的工具，enumFields可对返回字段做修正。例如，想要对返回的status字段做修正，如"CR"显示为"Created"，可设置：

	Map<String, Object> map = asMap("CR","Created", "CA","Cancelled");
	this.enumFields = asMap(
		"status", map
	);

(TODO:暂不支持)也可以设置为自定义函数，如：

	map = asMap(...);
	this.enumFields.asMap("status", v -> {
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

	protected Map<String, Object> enumFields; // elem: {field => {key=>val}} 或 {field => fn(val)}，与onHandleRow类似地去修改数据。TODO: 目前只支持map，不支持

	// for query
	protected String defaultRes = "t0.*"; // 缺省为 "t0.*" 加  default=true的虚拟字段
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

	// for get/query
	// 注意：sqlConf.res/.cond[0]分别是传入的res/cond参数, sqlConf.orderby是传入的orderby参数, 为空均表示未传值。
	private SqlConf sqlConf; // {@cond, @res, @join, orderby, @subobj, @gres}
	private boolean isAggregationQuery; // 是聚合查询，如带group by或res中有聚合函数

	// virtual columns
	private HashMap<String, Vcol> vcolMap; // elem: vcol => {def, def0, added?, vcolDefIdx?=-1}

	public void init(String table, String ac) throws Exception
	{
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

	// for get/query
	protected void initQuery() throws Exception
	{
		String gres = (String)param("gres", null, null, false);
		String res = (String)param("res", null, null, false);

		sqlConf = new SqlConf();
		sqlConf.res = asList();
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

		/* TODO
		// support internal param res2/join/cond2
		if ((res2 = param("res2")) != null) {
			if (! is_array(res2))
				throw new MyException(E_SERVER, "res2 should be an array: `res2`");
			for (res2 as e)
				this.addRes(e);
		}
		if ((join=param("join")) != null) {
			this.addJoin(join);
		}
		if ((cond2 = param("cond2")) != null) {
			if (! is_array(cond2))
				throw new MyException(E_SERVER, "cond2 should be an array: `cond2`");
			for (cond2 as e)
				this.addCond(e);
		}
		if ((subobj = param("subobj")) != null) {
			if (! is_array(subobj))
				throw new MyException(E_SERVER, "subobj should be an array");
			this.sqlConf["subobj"] = subobj;
		}
		*/
		this.onQuery();

		boolean addDefaultCol = false;
		// 确保res/gres参数符合安全限定
		if (gres != null) {
			this.filterRes(gres, true);
		}
		else if (res == null) {
			res = this.defaultRes;
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
						this.sqlConf.subobj.put(col, def);
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
	
	private void handleEnumFields(JsObject rowData)
	{
		if (this.enumFields != null) {
			// 处理enum/enumList字段返回
			Iterator<Map.Entry<String, Object>> it = this.enumFields.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, Object> e = it.next();
				String k = e.getKey();
				if (! rowData.containsKey(k))
					return;
				Object v = rowData.get(k).toString();
				@SuppressWarnings("rawtypes")
				Map map = (Map)e.getValue();
				String SEP = ",";
				if (map instanceof Map) {
					if (map.containsKey(v)) {
						v = map.get(v);
					}
					else if (v.toString().contains(SEP)) {
						StringBuffer v1 = new StringBuffer();
						for (String ve: v.toString().split(SEP)) {
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
				rowData.put(k, v);
			}
		}
	}

	private void handleRow(JsObject rowData) throws Exception
	{
		if (this.hiddenFields != null)
		{
			for (Object field : this.hiddenFields)
			{
				rowData.remove(field);
			}
		}
		rowData.remove("pwd");
		
		// TODO: flag_handleResult(rowData);
		this.onHandleRow(rowData);

		this.handleEnumFields(rowData);
	}

	// for query. "field1"=>"t0.field1"
	private String fixUserQuery(String q)
	{
		if (regexMatch(q, "(?i)select").find()) {
			throw new MyException(E_FORBIDDEN, "forbidden SELECT in param cond");
		}
		// "aa = 100 and t1.bb>30 and cc IS null" . "t0.aa = 100 and t1.bb>30 and t0.cc IS null"
		Matcher m = regexMatch(q, "(?i)[\\w.\\u4E00-\\u9FA5]+(?=(\\s*[=><]|(\\s+(IS|LIKE))))");
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			// 't0.0' for col, or 'voldef' for vcol
			String col = m.group();
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
	
	// 因为java不能对alias参数传址修改，只能返回它
	protected String handleAlias(String col, String alias)
	{
		// support enum
		String[] a = alias.split("=", 2);
		if (a.length != 2)
			return alias;

		alias = a[0].length() == 0? null: a[0];
		if (this.enumFields == null)
			this.enumFields = asMap();
		String k = alias==null?col:alias;
		if (k.charAt(0) == '"') // remove ""
			k = k.substring(1, k.length()-1);
		this.enumFields.put(k, parseKvList(a[1], ";", ":"));
		return alias;
	}

	private void filterRes(String res) {
		filterRes(res, false);
	}
	
	// gres?=false
	// return: new field list
	private void filterRes(String res, boolean gres)
	{
		List<String> cols = new ArrayList<String>();
		for (String col0 : res.split(",")) 
		{
			String col = col0.trim();
			String alias = null;
			String fn = null;
			if (col.equals("*") || col.equals("t0.*")) 
			{
				this.addRes("t0.*", false);
				continue;
			}
			Matcher m;
			// 适用于res/gres, 支持格式："col" / "col col1" / "col as col1", alias可以为中文，如"col 某列"
			// 如果alias中有特殊字符（逗号不支持），则应加引号，如"amount \"金额(元)\"", "v \"速率 m/s\""等。
			if (! (m=regexMatch(col, "^(?i)(\\w+)(?:\\s+(?:AS\\s+)?([^,]+))?$")).find())
			{
				// 对于res, 还支持部分函数: "fn(col) as col1", 目前支持函数: count/sum，如"count(distinct ac) cnt", "sum(qty*price) docTotal"
				if (!gres && (m=regexMatch(col, "^(?i)(\\w+)\\([a-z0-9_.\'* ,+\\/]+\\)\\s+(?:AS\\s+)?([^,]+)$")).find())
				{
					fn = m.group(1).toUpperCase();
					if (!fn.equals("COUNT") && !fn.equals("SUM"))
						throw new MyException(E_FORBIDDEN, String.format("SQL function not allowed: `%s`", fn));
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

// 			if (! ctype_alnum(col))
// 				throw new MyException(E_PARAM, "bad property `col`");
			if (this.addVCol(col, true, alias) == false)
			{
				if (!gres && this.subobj != null && this.subobj.containsKey(col))
				{
					String key = alias != null ? alias : col;
					this.sqlConf.subobj.put(key, this.subobj.get(col));
				}
				else
				{
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
			Matcher m = regexMatch(col, "^(?i)(\\w+\\.)?(\\S+)(\\s+(asc|desc))?$");
			if (! m.find())
				throw new MyException(E_PARAM, String.format("bad property `%s`", col));
			if (m.group(1) != null) // e.g. "t0.id desc"
			{
				colArr.add(col);
				continue;
			}
			if (col.indexOf(".") < 0)
			{
				Matcher m1 = regexMatch(col, "^(\\S+)");
				StringBuffer sb = new StringBuffer();
				while (m1.find()) {
					String rep;
					String col1 = m1.group(1);
					col1 = col1.replace("\"", "");
					if (this.addVCol(col1, true, "-") != false)
					{
						// mysql可在order-by中直接用alias, 而mssql要用原始定义
						if (! env.dbStrategy.acceptAliasInOrderBy())
							rep = this.vcolMap.get(col1).def;
						else
							rep = col1;
					}
					else
						rep = "t0." + col1;
					m1.appendReplacement(sb, rep);
				}
				m1.appendTail(sb);
				col = sb.toString();
			}
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

		this.id = dbInsert(this.table, env._POST);

		String res = (String)param("res");
		Object ret = null;
		if (res != null)
		{
			env._GET.put("id", this.id);
			ret = env.callSvc(this.table + ".get");
		}
		else
			ret = this.id;
		return ret;
	}

	public void api_set() throws Exception
	{
		this.onValidateId();
		this.id = (int)mparam("id");
		this.validate();

		@SuppressWarnings("unused")
		int cnt = dbUpdate(this.table, env._POST, this.id);
	}

	public void api_del() throws Exception
	{
		this.onValidateId();
		this.id = (int)mparam("id");

		String sql = String.format("DELETE FROM %s WHERE id=%s", table, id);
		int cnt = execOne(sql);
		if (cnt != 1)
			throw new MyException(E_PARAM, String.format("not found id=%s", id));
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
	@SuppressWarnings("unchecked")
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
		String sql = String.format("DELETE t0 FROM %s WHERE %s", cond.tblSql, cond.condSql);
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
	
	// 没有cond则返回null, 支持GET/POST中各有一个cond/gcond条件。
	private String getCondParam(String paramName) {
		String[] condArr = env.request.getParameterValues(paramName);
		if (condArr == null)
			return null;
		return getCondStr(Arrays.asList(condArr));
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
	private void handleSubObj(int id, JsObject mainObj) throws Exception
	{
		HashMap<String, SubobjDef> subobj = this.sqlConf.subobj;
		if (subobj != null) 
		{
			// opt: {sql, wantOne=false}
			for (Map.Entry<String, SubobjDef> kv : subobj.entrySet()) {
				String k = kv.getKey();
				SubobjDef opt = kv.getValue();
				if (opt.sql == null)
					continue;
				String sql1 = String.format(opt.sql, id); // e.g. "select * from OrderItem where orderId=%d"
				boolean tryCache = sql1.equals(opt.sql);
				JsArray ret1 = queryAll(sql1, true, tryCache);
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
			}
		}
	}

	// 优化的子表查询. 对列表使用一次`IN (id,...)`查询出子表, 然后使用程序自行join
	// 临时添加了"id_"作为辅助字段.
	protected void handleSubObjForList(JsArray objArr) throws Exception
	{
		Map<String, SubobjDef> subobj = this.sqlConf.subobj;
		if (subobj == null || subobj.size() == 0)
			return;

		List<String> idArr = asList();
		for (Object e: objArr) {
			JsObject row = (JsObject)e;
			Object key = row.getOrDefault("id", row.getOrDefault("编号", null)); // TODO: use id
			if (key == null)
				continue;
			idArr.add(key.toString());
		}
		if (idArr.size() == 0)
			return;
		String idList = String.join(",", idArr);

		for (Map.Entry<String, SubobjDef> kv: subobj.entrySet()) {
			String k = kv.getKey();
			// opt: {sql, wantOne=false}
			SubobjDef opt = kv.getValue();

			if (opt.sql == null)
				continue;
			String[] joinField = {null};

			// e.g. "select * from OrderItem where orderId=%d" => (添加主表关联字段id_) "select *, orderId id_ from OrderItem where orderId=%d"
			String sql = regexReplace(opt.sql, "(\\S+)=%d", ms -> {
				joinField[0] = ms.group(1);
				return joinField[0] + " IN (" + idList + ")";
			});

			if (joinField[0] == null) {
				if (! opt.force)
					throw new MyException(E_SERVER, "bad subobj def: `" + opt.sql + "'. require `field=%d`");

				JsArray ret1 = queryAll(sql, true);
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

			sql = regexReplace(sql, "(?i) from", String.format(", %s id_$0", joinField[0]));

			JsArray ret1 = queryAll(sql, true);
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
				Object key = row.getOrDefault("id", row.getOrDefault("编号", null)); // TODO: use id
				List<JsObject> val = subMap.getOrDefault(key, null);
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

	// return: JsObject
	public Object api_get() throws Exception
	{
		this.onValidateId();
		this.id = (int)mparam("id");
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
		this.handleRow(ret);
		return ret;
	}

	static DecimalFormat numberFormat = new DecimalFormat("#");
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
			if (s.indexOf('"') >= 0 || s.indexOf('\n') >= 0)
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

	void handleExportFormat(String fmt, JsObject ret, String fname) throws UnsupportedEncodingException
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
		else if (fmt.equals("excel")) 
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
		int maxPageSz = getMaxPageSz();
		if (pagesz != null && (pagesz < 0 || pagesz > maxPageSz))
			pagesz = maxPageSz;
		else if (pagesz == null || pagesz == 0)
			pagesz = 20;

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
		
		boolean SUBOBJ_OPTIMIZE = true;
		if (SUBOBJ_OPTIMIZE) {
			handleSubObjForList(objArr); // 优化: 总共只用一次查询, 替代每个主表查询一次
			for (Object rowData: objArr) {
				JsObject row = (JsObject)rowData;
				this.handleRow(row);
			}
		}
		else {
			for (Object rowData : objArr) {
				JsObject row = (JsObject)rowData;
				Object id1 = row.get("id");
				if (id1 != null) {
					handleSubObj((int)id1, row);
				}
				this.handleRow(row);
			}
		}
		Object reto = objArr;
		this.after(reto);

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
		String fmt = (String)param("fmt");
		JsObject ret = null;
		if (fmt != null && fmt.equals("list")) {
			ret = new JsObject("list", objArr);
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

	public void addRes(String res) {
		this.addRes(res, true);
	}
	// analyzeCol?=true
	public void addRes(String res, boolean analyzeCol) {
		this.sqlConf.res.add(res);
		if (analyzeCol)
			this.setColFromRes(res, true, -1);
	}

/**
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
	// prepend?=false
	public void addCond(String cond, boolean prepend)
	{
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
		if ( (m=regexMatch(res, "^(\\w+)\\.(\\w+)$")).find()) {
			colName = m.group(2);
			def = res;
		}
		else if ( (m = regexMatch(res, "(?is)^(.*?)\\s+(?:as\\s+)?\"?(\\S+?)\"?$")).find()) {
			colName = m.group(2);
			def = m.group(1);
		}
		else
			throw new MyException(E_PARAM, String.format("bad res definition: `%s`", res));

		if (this.vcolMap.containsKey(colName)) {
			if (added && this.vcolMap.get(colName).added)
				throw new MyException(E_SERVER, String.format("res for col `%s` has added: `%s`", colName, res));
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

	private void initVColMap()
	{
		if (this.vcolMap == null)
			this.vcolMap = new HashMap<String,Vcol>();
		if (this.vcolDefs == null)
			return;

		int idx = 0;
		for (VcolDef vcolDef : this.vcolDefs) {
			for (String e : vcolDef.res) {
				this.setColFromRes(e, false, idx);
			}
			++ idx;
		}
	}

/**
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
	protected boolean addVCol(String col, boolean ignoreError /*= false*/, String alias /*= null*/)
	{
		if (! this.vcolMap.containsKey(col)) {
			if (!ignoreError)
				throw new MyException(E_SERVER, String.format("unknown vcol `%s`", col));
			return false;
		}
		if (this.vcolMap.get(col).added)
			return true;
		this.addVColDef(this.vcolMap.get(col).vcolDefIdx);
		if (alias != null) {
			if (alias != "-")
				this.addRes(this.vcolMap.get(col).def + " " + alias, false);
		}
		else {
			this.addRes(this.vcolMap.get(col).def0, false);
		}
		return true;
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
					this.addRes(e);
				}
			}
			++ idx;
		}
	}

	/*
	根据index找到vcolDef中的一项，添加join/cond到最终查询语句(但不包含res)。
	 */
	private Set<Integer> m_vcolDefIndex = new HashSet<Integer>();
	private void addVColDef(int idx)
	{
		if (idx < 0 || m_vcolDefIndex.contains(idx))
			return;

		VcolDef vcolDef = this.vcolDefs.get(idx);
		m_vcolDefIndex.add(idx);
		if (vcolDef.require != null)
		{
			String requireCol = vcolDef.require;
			this.addVCol(requireCol, false, "-");
		}
		if (vcolDef.join != null)
			this.addJoin(vcolDef.join);
		if (vcolDef.cond != null)
			this.addCond(vcolDef.cond);
	}
}
