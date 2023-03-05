import org.bytedeco.llvm.LLVM.LLVMValueRef;

public class Function extends Type{
	public String kind = "function"; // 类型为函数
	public Type returnType; // 返回值类型
	LLVMValueRef pointer; // 返回值的值

	public Function(){
		super.kind="function";
	}

	public void setPointer(LLVMValueRef pointer) {
		this.pointer = pointer;
		super.pointer = pointer;
	}

	public Type getReturnType() {
		return returnType;
	}
}
