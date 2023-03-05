import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.*;

public class Type {
	public String kind = "int";
	//申请一块能存放int型的内存,用于存放每个变量的值
	LLVMValueRef pointer;

	public Type(String kind) {
		this.kind = kind;
	}

	public Type() {
	}
}
