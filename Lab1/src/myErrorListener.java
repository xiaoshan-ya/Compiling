import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class myErrorListener extends BaseErrorListener {
	private boolean flag = false;

	public boolean getFlag(){
		return flag;
	}
	@Override
	public void syntaxError(Recognizer<?,?> recognizer,
							 Object offendingSymbol, int line,
							 int charPositionInLine,
							 String msg,
							 RecognitionException e){
		if(!flag)
			flag=true;
		System.err.println("Error type A at Line " + line + ":" + msg);
	}
}
