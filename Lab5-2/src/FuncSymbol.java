import org.bytedeco.llvm.LLVM.LLVMValueRef;

public class FuncSymbol extends BaseScope implements Symbol {

	public FuncSymbol(String name, BaseScope enclosingScope) {
		super(name, enclosingScope);
	}

	@Override
	public String getName() {
		return this.name;
	}

	public Type getType() {
		return new Function();
	}
}
