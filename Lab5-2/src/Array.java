import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.ArrayList;
import java.util.List;

public class Array extends Type{
	public String kind = "array";
	public String elementType; // 数组内存放的类型
	public int dimension = 1; // 数组的维度
	// 记录数组中的每一个元素
	public List<LLVMValueRef> arrayList = new ArrayList<>(); // 存放的是pointer
	public int length;

	public Array(){
		super.kind="array";
	}
}
