import com.sun.source.tree.Scope;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

public class BaseScope implements Scope {
	public BaseScope enclosingScope;// 该作用域的父节点
	public Map<String, Symbol> symbols = new LinkedHashMap<>();// 每一个作用域里都要有一个符号表，前面是符号名，后面是符号本身
	public String name; // 作用域的名字
	public Type type;
	public Type returnType;
	public boolean isRename = false;
	public String rename;

	// 最终的scope是一棵树
	public BaseScope(String name, BaseScope enclosingScope) {
		this.name = name;
		this.enclosingScope = enclosingScope;
	}

	// 得到作用域的名字
	public String getName() {
		return this.name;
	}

	// 设置作用域的名字
	public void setName(String name) {
		this.name = name;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public void setReturnType(Type type) {
		this.returnType = type;
	}

	@Override
	// 得到当前作用域的父作用域
	public Scope getEnclosingScope() {
		return this.enclosingScope;
	}

	@Override
	//返回包含此作用域位置的最内层类型元素
	public TypeElement getEnclosingClass() {
		return null;
	}

	@Override
	// 返回包含此作用域位置的最内层可执行元素。
	public ExecutableElement getEnclosingMethod() {
		return null;
	}

	@Override
	// 返回直接包含在此作用域中的元素
	public Iterable<? extends Element> getLocalElements() {
		return null;
	}

	// 得到符号表
	public Map<String, Symbol> getSymbols() {
		return this.symbols;
	}

	// 在作用域中定义符号
	public void define(Symbol symbol) {
		symbols.put(symbol.getName(), symbol);
	}

	// 在当前作用域中按照name:类型名解析符号，在符号表中检查
	// 返回值类型为int,void,function,array，其中如果array下标为最内层返回int,如果传入变量是数字，则直接返回int
	public Symbol resolve(String name1) {
		//TODO 增加对于数组的读取，对数字的特判
		if (isNumber(name1)) { // 传入参数为数字直接返回
			Symbol newSymbol = new BaseSymbol("number", new Type("int"));
			return newSymbol;
		}

		int dimension = 0;
		String name = "";
		for (int i = 0; i < name1.length(); i++) {
			if(name1.charAt(i) == '[' || name1.charAt(i) == '(') {
				break;
			}
			name = name + name1.charAt(i);
		}
		for (int i = 0; i < name1.length(); i++) {
			if(name1.charAt(i) == '[') {
				dimension++;
			}
		}

		Symbol symbol = symbols.get(name);
		if (symbol != null) {

			//TODO 对函数类型特殊处理
			if (symbol.getType().kind.equals("array") && dimension == ((Array)symbol.getType()).dimension) { // 数组类型并且下标取到最内层
				Symbol newSymbol = new BaseSymbol("array", new Type("int"));
				return newSymbol;
			}
			else {
				return symbol;
			}
		}

		if (enclosingScope != null) { // 如果没有在当前作用域中找到符号，则到父作用域中查找，并且顺序是找离当前作用域最近的一个
			return enclosingScope.resolve(name);
		}

		// 如果都没有，则表示当前符号未被定义
		return null;
	}

	public boolean isNumber (String str) {
		boolean judge = true;
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isDigit(str.charAt(i))) {
				judge = false;
				break;
			}
		}
		return judge;
	}

	/* error 8
	 *  函数参数不适用，函数参数的数量或类型与函数声明的参数数量或类型不一致,基于函数存在进行判断
	 * */
	public boolean judgeError8 (String[] paramTypeList, String funName) {

		if(symbols.get(funName) == null || !symbols.get(funName).getType().kind.equals("function")) { //如果函数未定义或者对全局变量进行函数调用，则交给其他判断处理，直接返回true
			return true;
		}
		FunctionSymbol function = (FunctionSymbol) symbols.get(funName);

		if (paramTypeList.length != function.symbols.size()) {
			return false;
		}

		int i = 0;
		for (Symbol funParam : function.symbols.values()) {
			if (!funParam.getType().kind.equals(paramTypeList[i])) {
				return false;
			}
			i++;
		}
		return true;
	}

	public String[] getParamType (String[] paramList) {
		//TODO 处理a+1，只需要取到第一个元素，其他的交给exp判断
		String x = paramList[0];
		if (paramList[0].equals("")) {
			return new String[0];
		}
		String[] TypeList = new String[paramList.length];
		for (int i = 0; i < TypeList.length; i++) {
			Symbol symbol = resolve(getParamName(paramList[i]));
			TypeList[i] = symbol.getType().kind;
		}
		return TypeList;
	}

	public String getParamName (String param) {
		if (param.contains("+")) {
			return param.substring(0, param.indexOf("+"));
		}
		if (param.contains("-")) {
			return param.substring(0, param.indexOf("-"));
		}
		if (param.contains("*")) {
			return param.substring(0, param.indexOf("*"));
		}
		if (param.contains("/")) {
			return param.substring(0, param.indexOf("/"));
		}
		if (param.contains("%")) {
			return param.substring(0, param.indexOf("%"));
		}

		return param;
	}

	/* error 4
	 * 函数重复定义,在全局作用域GlobalScope中检查
	 * true表示函数名合法，false表示函数有重复
	 * */
	public boolean judgeFuncSame (String funcName) {
		Symbol symbol = symbols.get(funcName);
		if (symbol != null) {
			return false;
		}
		return true;
	}

	/* error 3
	* 变量重复声明
	* true表示局部变量名合法
	* */
	public boolean judgeValSame (String valName, GlobalScope globalScope) {
		Symbol symbol = symbols.get(valName);
		if (symbol != null || globalScope.symbols.get(valName) != null || (enclosingScope!=null&&enclosingScope.symbols.get(valName) != null)) {
			return false;
		}
		return true;
	}
}
