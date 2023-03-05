import com.sun.source.tree.Scope;

public class LocalScope extends BaseScope{
	public LocalScope(BaseScope enclosingScope) {
		super("LocalScope", enclosingScope);
	}

}
