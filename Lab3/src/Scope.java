import java.util.Map;

public interface Scope {
	public String getName();

	public void setName(String name);

	public Scope getEnclosingScope(); // 得到父作用域

	public Map<String, Symbol> getSymbols();

	public void define(Symbol symbol); // 在作用域中定义符号

	public Symbol resolve(String name); // 根据名称查找
}
