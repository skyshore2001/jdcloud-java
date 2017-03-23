package com.jdcloud;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
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
	}
	public class SubobjDef
	{
		public String sql;
		public boolean wantOne;
		public boolean isDefault;
	}

	class SqlConf
	{
		public List<String> cond;
		public List<String> res;
		public List<String> join;
		public String orderby;
		public String gres;
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

	public static final JsArray stdAc = new JsArray("add", "get", "set", "del", "query");
	protected JsArray allowedAc;
	protected String ac;
	protected String table;

	// 在add后自动设置; 在get/set/del操作调用onValidateId后设置。
	protected int id;

	// for add/set
	protected JsArray readonlyFields;
	// for set
	protected JsArray readonlyFields2;
	// for add/set
	protected JsArray requiredFields;
	// for set
	protected JsArray requiredFields2;
	// for get/query
	protected JsArray hiddenFields;
	// for query
	protected String defaultRes; // 缺省为 "t0.*" 加  default=true的虚拟字段
	protected String defaultSort = "t0.id";
	// for query
	protected int maxPageSz = 100;

	// for get/query
	// virtual columns definition
	protected List<VcolDef> vcolDefs; // elem: {res, join, default?=false}
	protected HashMap<String, SubobjDef> subobj; // elem: { name => {sql, wantOne, isDefault}}

/* TODO
	// 回调函数集。在after中执行（在onAfter回调之后）。
	public delegate void OnAfterActions();
	protected OnAfterActions onAfterActions;
*/

	// for get/query
	// 注意：sqlConf.res/.cond[0]分别是传入的res/cond参数, sqlConf.orderby是传入的orderby参数, 为空均表示未传值。
	private SqlConf sqlConf; // {@cond, @res, @join, orderby, @subobj, @gres}

	// virtual columns
	private HashMap<String, Vcol> vcolMap; // elem: vcol => {def, def0, added?, vcolDefIdx?=-1}

	public void init(String table, String ac)
	{
		this.table = table;
		this.ac = ac;
		this.onInit();
	}

	protected void onInit()
	{
	}
	protected void onValidate()
	{
	}
	protected void onValidateId()
	{
	}
	protected void onHandleRow(JsObject rowData)
	{
	}
	protected void onAfter(Object ret)
	{
	}
	protected void onQuery()
	{
	}
	protected int onGenId()
	{
		return 0;
	}

	public void before()
	{
		if (this.allowedAc != null && stdAc.contains(ac) && !this.allowedAc.contains(ac))
			throw new MyException(E_FORBIDDEN, String.format("Operation `%s` is not allowed on object `%s`", ac, table));

		if (ac.equals("get") || ac.equals("set") || ac.equals("del")) {
			this.onValidateId();
			this.id = (int)mparam("id");
		}

		// TODO: check fields in metadata
		// for ($_POST as ($field, $val))

		if (ac.equals("add") || ac.equals("set")) 
		{
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
				JsArray arr = new JsArray();
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
		else if (ac.equals("get") || ac.equals("query")) {
			String gres = (String)param("gres", null, null, false);
			String res = (String)param("res", null, this.defaultRes, false);
			String res1 = res;
			sqlConf = new SqlConf() {{
				res = new ArrayList<String>() {{ add(res1); }};
				gres = gres;
				cond = new ArrayList<String>() {{ add((String)param("cond", null, null, false)); }};
				join = new ArrayList<String>();
				orderby = (String)param("orderby", null, null, false);
				subobj = new HashMap<String, SubobjDef>();
				union = (String)param("union", null, null, false);
				distinct = (boolean)param("distinct/b", false);
			}};

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
			this.fixUserQuery();
			this.onQuery();

			// 确保res/gres参数符合安全限定
			if (gres != null) {
				this.filterRes(gres, true);
			}
			if (res != null) {
				this.filterRes(res);
			}
			else {
				/* TODO
				this.addDefaultVCols();
				if (this.sqlConf.subobj.size() == 0 && this.subobj != null) {
					for (var kv : this.subobj) {
						var col = kv.Key;
						var def = kv.Value;
						if (def.isDefault)
							this.sqlConf.subobj[col] = def;
					}
				}
				*/
			}
			if (ac.equals("query"))
			{
				this.supportEasyuiSort();
				if (this.sqlConf.orderby != null && this.sqlConf.union == null)
					this.sqlConf.orderby = this.filterOrderby(this.sqlConf.orderby);
			}
		}
	}

	private void handleRow(JsObject rowData)
	{
		if (this.hiddenFields != null)
		{
			for (Object field : this.hiddenFields)
			{
				rowData.remove(field);
			}
		}
		if (rowData.containsKey("pwd"))
			rowData.put("pwd", "****");
		// TODO: flag_handleResult(rowData);
		this.onHandleRow(rowData);
	}

	// for query. "field1"=>"t0.field1"
	private void fixUserQuery()
	{
		/* TODO
		if (this.sqlConf.cond[0] != null) {
			if (this.sqlConf.cond[0].indexOf("select", StringComparison.OrdinalIgnoreCase) >= 0) {
				throw new MyException(E_FORBIDDEN, "forbidden SELECT in param cond");
			}
			// "aa = 100 and t1.bb>30 and cc IS null" . "t0.aa = 100 and t1.bb>30 and t0.cc IS null" 
			this.sqlConf.cond[0] = Regex.replace(this.sqlConf.cond[0], "[\\w|.]+(?=(\\s*[=><]|(\\s+(IS|LIKE))))", m => {
				// 't0.0' for col, or 'voldef' for vcol
				var col = m.Value;
				if (col.Contains('.'))
					return col;
				if (this.vcolMap.ContainsKey(col)) {
					this.addVCol(col, false, "-");
					return this.vcolMap[col].def;
				}
				return "t0." + col;
			}, RegexOptions.IgnoreCase);
		}
		*/
	}
	private void supportEasyuiSort()
	{
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
	}
	
	private void filterRes(String res) {
		filterRes(res, false);
	}
	
	// gres?=false
	// return: new field list
	private void filterRes(String res, boolean gres)
	{
		String firstCol = "";
		List<String> cols = new ArrayList<String>();
		for (String col0 : res.split(",")) 
		{
			String col = col0.trim();
			String alias = null;
			String fn = null;
			if (col.equals("*") || col.equals("t0.*")) 
			{
				firstCol = "t0.*";
				continue;
			}
			Matcher m;
			// 适用于res/gres, 支持格式："col" / "col col1" / "col as col1"
			if (! (m=regexMatch(col, "^(?i)(\\w+)(?:\\s+(?:AS\\s+)?(\\S+))?$")).find())
			{
				// 对于res, 还支持部分函数: "fn(col) as col1", 目前支持函数: count/sum
				if (!gres && (m=regexMatch(col, "^(?i)(\\w+)\\([a-z0-9_.\'*]+\\)\\s+(?:AS\\s+)?(\\S+)$")).find())
				{
					fn = m.group(1).toUpperCase();
					if (!fn.equals("COUNT") && !fn.equals("SUM"))
						throw new MyException(E_FORBIDDEN, String.format("SQL function not allowed: `%s`", fn));
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
					this.addRes(col1, false);
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
		else
			this.sqlConf.res.set(0, firstCol);
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
	public void after(Object ret)
	{
		// 确保只调用一次
		if (afterIsCalled)
			return;
		afterIsCalled = true;

		if (ac.equals("get")) {
			JsObject ret1 = (JsObject)ret;
			this.handleRow(ret1);
		}
		else if (ac.equals("query")) {
			for (Object rowData : (JsArray)ret) {
				JsObject row = (JsObject)rowData;
				this.handleRow(row);
			};
		}

		this.onAfter(ret);

		/* TODO
		if (this.onAfterActions != null)
			this.onAfterActions();
			*/
	}

	public Object api_add() throws Throwable
	{
		StringBuffer keys = new StringBuffer();
		StringBuffer values = new StringBuffer();

		for (String k : env._POST.keySet())
		{
			if (k.equals("id"))
				continue;
			String val = env._POST.get(k).toString();
			if (val.length() == 0)
				continue;
			if (!k.matches("^\\w+$"))
				throw new MyException(E_PARAM, String.format("bad property `%s`" + k));
			if (keys.length() > 0)
			{
				keys.append(", ");
				values.append(", ");
			}
			keys.append(k);
			val = htmlEscape(val);
			values.append(Q(val));
		}
		
		if (keys.length() == 0)
			throw new MyException(E_PARAM, "no field found to be added");

		String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table, keys, values);
		this.id = execOne(sql, true);

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

	public void api_set() throws SQLException
	{
		StringBuffer kv = new StringBuffer();
		for (String k : env._POST.keySet())
		{
			if (k.equals("id"))
				continue;
			// ignore non-field param
			//if (substr($k,0,2) == "p_")
				//continue;
			// TODO: check meta
			if (!k.matches("^\\w+$"))
				throw new MyException(E_PARAM, String.format("bad property `%s`" + k));

			if (kv.length() > 0)
				kv.append(", ");
			// 空串或null置空；empty设置空字符串
			Object val = env._POST.get(k);
			if (val.equals("") || val.equals("null"))
				kv.append(k + "=null");
			else if (val.equals("empty"))
				kv.append(k + "=''");
			else
				kv.append(k + "=" + Q(htmlEscape(val.toString())));
		}
		if (kv.length() == 0) 
		{
			addLog("no field found to be set");
		}
		else {
			String sql = String.format("UPDATE %s SET %s WHERE id=%s", table, kv, id);
			int cnt = execOne(sql);
		}
	}

	public void api_del() throws SQLException
	{
		String sql = String.format("DELETE FROM %s WHERE id=%s", table, id);
		int cnt = execOne(sql);
		if (cnt != 1)
			throw new MyException(E_PARAM, String.format("not found id=%s", id));
	}

	// return [stringbuffer, tblSql, condSql]
	protected Object[] genQuerySql()
	{
		if (sqlConf.res.get(0) == null)
			sqlConf.res.set(0, "t0.*");
		else if (sqlConf.res.get(0).length() == 0)
			sqlConf.res.remove(0);

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

		StringBuffer condBuilder = new StringBuffer();
		for (String cond : sqlConf.cond) {
			if (cond == null)
				continue;
			if (condBuilder.length() > 0)
				condBuilder.append(" AND ");
			if (cond.matches("(?i) (and|or) "))
				condBuilder.append("(").append(cond).append(")");
			else 
				condBuilder.append(cond);
		}
		condSql = condBuilder.toString();
		StringBuffer sql = new StringBuffer();
		sql.append(String.format("SELECT %s FROM %s", resSql, tblSql));
		if (condBuilder.length() > 0)
		{
			// TODO: flag_handleCond(condSql);
			sql.append("\nWHERE ").append(condBuilder);
		}
		return new Object[] { sql, tblSql, condSql };
	}

	/* TODO
	private JsObject queryAllCache = new JsObject();
	protected JsArray queryAll(String sql, boolean assoc, boolean tryCache)
	{
		JsArray ret = null;
		if (tryCache && queryAllCache != null)
		{
			Object value;
			queryAllCache.TryGetValue(sql, out value);
			ret = value as JsArray;
		}
		if (ret == null)
		{
			ret = queryAll(sql, assoc);
			if (tryCache)
			{
				if (queryAllCache == null)
					queryAllCache = new JsObject();
				queryAllCache[sql] = ret;
			}
		}
		return ret;
	}
	private void handleSubObj(int id, JsObject mainObj)
	{
		var subobj = this.sqlConf.subobj;
		if (subobj != null) 
		{
			// opt: {sql, wantOne=false}
			for (var kv : subobj) {
				String k = kv.Key;
				var opt = kv.Value;
				if (opt.sql == null)
					continue;
				String sql1 = String.format(opt.sql, id); // e.g. "select * from OrderItem where orderId=%d"
				boolean tryCache = sql1 == opt.sql;
				JsArray ret1 = queryAll(sql1, true, true);
				if (opt.wantOne) 
				{
					if (ret1.Count > 0)
						mainObj[k] = ret1[0];
					else
						mainObj[k] = null;
				}
				else {
					mainObj[k] = ret1;
				}
			}
		}
	}
	*/

	// return: JsObject
	public Object api_get() throws Exception
	{
		this.addCond("t0.id=" + this.id, true);
		Object[] rv = genQuerySql();
		StringBuffer sql = (StringBuffer)rv[0];
		Object ret = queryOne(sql.toString(), true);
		if (ret.equals(false))
			throw new MyException(E_PARAM, String.format("not found `%s.id`=`%s`", table, id));
		/* TODO
		JsObject ret1 = ret as JsObject;
		this.handleSubObj(this.id, ret1);
*/
		return ret;
	}

	void outputCsvLine(JsArray row, String enc)
	{
		boolean firstCol = true;
		for (Object e : row)
		{
			if (firstCol)
				firstCol = false;
			else
				echo(',');
			String s = e.toString().replace("\"", "\"\"");
			if (enc != null)
			{
				try {
					byte[] bs = s.getBytes(enc);
					s = new String(bs, enc);
				} catch (UnsupportedEncodingException e1) {
				}
				echo('"', s, '"');
			}
			else
			{
				echo('"', s, '"');
			}
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

	void handleExportFormat(String fmt, JsObject ret, String fname)
	{
		boolean handled = false;
		if (fmt.equals("csv")) 
		{
			header("Content-Type", "application/csv; charset=UTF-8");
			header("Content-Disposition", "attachment;filename=" + fname + ".csv");
			table2csv(ret, null);
			handled = true;
		}
		else if (fmt.equals("excel")) 
		{
			header("Content-Type", "application/csv; charset=gb2312");
			header("Content-Disposition", "attachment;filename=" + fname + ".csv");
			table2csv(ret, "gb2312");
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
			throw new DirectReturn();
	}

	public Object api_query() throws Exception
	{
		Integer pagesz = (Integer)param("_pagesz/i");
		Integer pagekey = (Integer)param("_pagekey/i");
		boolean enableTotalCnt = false;
		boolean enablePartialQuery = false;

		// support jquery-easyui
		if (pagesz == null && pagekey == null) {
			pagesz = (Integer)param("rows/i");
			pagekey = (Integer)param("page/i");
			if (pagekey != null)
			{
				enableTotalCnt = true;
				enablePartialQuery = false;
			}
		}
		int maxPageSz = Math.min(this.maxPageSz, PAGE_SZ_LIMIT);
		if (pagesz != null && (pagesz < 0 || pagesz > maxPageSz))
			pagesz = maxPageSz;
		else if (pagesz == null || pagesz == 0)
			pagesz = 20;

		if (sqlConf.gres != null) {
			enablePartialQuery = false;
		}

		String orderSql = sqlConf.orderby;

		// setup cond for partialQuery
		if (orderSql == null)
			orderSql = defaultSort;

		if (enableTotalCnt == false && pagekey != null && pagekey.intValue() == 0)
		{
			enableTotalCnt = true;
		}

		// 如果未指定orderby或只用了id(以后可放宽到唯一性字段), 则可以用partialQuery机制(性能更好更精准), _pagekey表示该字段的最后值；否则_pagekey表示下一页页码。
		String partialQueryCond;
		if (! enablePartialQuery) {
			if (orderSql.matches("^(t0\\.)?id\b")) {
				enablePartialQuery = true;
				if (pagekey != null && pagekey != 0) {
					if (orderSql.matches("(?i)\bid DESC")) {
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
		}

		String tblSql, condSql;
		Object[] rv = genQuerySql();
		StringBuffer sql = (StringBuffer)rv[0];
		tblSql = (String)rv[1];
		condSql = (String)rv[2];
		if (sqlConf.union != null) {
			sql.append("\nUNION\n").append(sqlConf.union);
		}
		if (sqlConf.gres != null) {
			sql.append(String.format("\nGROUP BY %s", sqlConf.gres));
		}

		Object totalCnt = null;
		if (orderSql != null) {
			sql.append(String.format("\nORDER BY %s", orderSql));

			if (enableTotalCnt) {
				String cntSql = "SELECT COUNT(*) FROM " + tblSql;
				if (condSql.length() > 0)
					cntSql += "\nWHERE " + condSql;
				totalCnt = queryOne(cntSql);
			}

			if (enablePartialQuery) {
				sql.append(String.format("\nLIMIT %s", pagesz));
			}
			else {
				if (pagekey == null || pagekey == 0)
					pagekey = 1;
				sql.append(String.format("\nLIMIT %s,%s", (pagekey-1)*pagesz, pagesz));
			}
		}

		String sql1 = env.fixPaging(sql.toString());
		JsArray objArr = queryAll(sql1, true);

		// Note: colCnt may be changed in after().
		int fixedColCnt = objArr.size()==0? 0: ((JsObject)objArr.get(0)).size();
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
		for (Object mainObj0 : objArr) {
			JsObject mainObj = (JsObject)mainObj0;
			Object id = mainObj.get("id");
			if (id != null)
			{
				/* TODO
				handleSubObj((int)id, (JsObject)mainObj);
				*/
			}
		}
		String fmt = (String)param("_fmt");
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
			handleExportFormat(fmt, ret, this.table);
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
