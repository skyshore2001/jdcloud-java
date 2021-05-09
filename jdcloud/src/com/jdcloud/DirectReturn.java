package com.jdcloud;

/**<pre>
%class DirectReturn

直接返回，跳过后续处理。建议统一使用jdRet。
输出`[0, null]`并返回

	throw new DirectReturn();
	(等价于 jdRet(0); )

输出`[0, "hello"]`并返回

	throw new DirectReturn(0, "hello");
	(等价于 jdRet(0, "hello"); )

如果不需要标准格式输出，可调用echo + exit:

	echo("{'id':100, 'name':'jdcloud'}");
	exit(); // 等价于jdRet()

上例中，不会记录输出到ApiLog中，如果想在ApiLog中记录输出，可以用echo+DirectReturn(code, val, true)：

	int retCode = 0;
	String retVal = "{'id':100, 'name':'jdcloud'}";
	echo(retVal);
	throw new DirectReturn(retCode, retVal, true);

%see jdRet
%see exit
*/
@SuppressWarnings("serial")
public class DirectReturn extends RuntimeException {
	public int retCode = 0;
	public Object retVal;
	public boolean output = true;

	public DirectReturn() {}
	public DirectReturn(Object retVal) {
		this.retVal = retVal;
	}
	public DirectReturn(int retCode, Object retVal, boolean output) {
		this.retCode = retCode;
		this.retVal = retVal;
		this.output = output;
	}
}
