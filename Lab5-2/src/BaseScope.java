import java.util.LinkedHashMap;
import java.util.Map;

public class BaseScope {
	public BaseScope enclosingScope;// 该作用域的父节点
	public Map<String, Symbol> symbols = new LinkedHashMap<>();// 每一个作用域里都要有一个符号表，前面是符号名，后面是符号本身
	public String name; // 作用域的名字
	public Type type;

	// 构造函数
	public BaseScope(String name, BaseScope enclosingScope) {
		this.name = name;
		this.enclosingScope = enclosingScope;
	}

	// 在作用域中定义符号
	public void define(Symbol symbol) {
		symbols.put(symbol.getName(), symbol);
	}

	public Symbol resolve(String name1) {
		if (isNumber(name1)) { // 传入参数为数字直接返回
			BaseSymbol newSymbol = new BaseSymbol("number", new Type("int"));
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
			return symbol;
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
}
