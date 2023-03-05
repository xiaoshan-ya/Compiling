import com.sun.source.tree.Scope;

public class GlobalScope extends BaseScope{
	public GlobalScope(BaseScope enclosingScope) {
		super("GlobalScope", enclosingScope);
	}
}
