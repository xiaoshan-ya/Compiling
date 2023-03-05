import java.util.Objects;

public class BaseSymbol implements Symbol{
	public String name;
	public Type type;
	public boolean init = false; // 变量是否初始化
	public boolean isRename = false;
	public String rename;

	public BaseSymbol(String name, Type type) {
		this.name = name;
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public void setInit(boolean init) {
		this.init = init;
	}
}
