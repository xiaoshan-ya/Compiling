import com.sun.source.tree.Scope;

public class FunctionSymbol extends BaseScope implements Symbol{
	public FunctionSymbol(String name, BaseScope enclosingScope) {
		super(name, enclosingScope);
	}

	@Override
	public Type getType() {
		return new Function();
	}

	@Override
	public void setInit(boolean init) {

	}

}
