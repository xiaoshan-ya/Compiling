import java.util.ArrayList;
import java.util.List;

public class Function extends Type {
	public String kind = "function"; // 类型为函数
	public List<Type> paramsList = new ArrayList<>(); // 参数列表
	public Type returnType; // 返回值类型

	public Function(){
		super.kind="function";
	}

	public Type getReturnType() {
		return returnType;
	}
}
