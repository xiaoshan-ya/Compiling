import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.*;

public class Type {
	public String kind = "int";
	public String name;
	//存放函数ref
	LLVMValueRef pointer;

	public Type(String kind) {
		this.kind = kind;
	}

	public Type() {
	}
}
