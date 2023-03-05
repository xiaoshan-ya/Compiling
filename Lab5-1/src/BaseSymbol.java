import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.*;

public class BaseSymbol implements Symbol{
	public String name; // 变量名
	public Type type; // 变量类型

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
}
