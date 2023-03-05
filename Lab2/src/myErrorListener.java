import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class myErrorListener extends BaseErrorListener {
	private boolean flag = true;

	public boolean getFlag(){
		return flag;
	}

	@Override
	public void syntaxError(Recognizer<?,?> recognizer,
							Object offendingSymbol, int line,
							int charPositionInLine,
							String msg,
							RecognitionException e){
		if(flag){
			flag=false;
		}

		System.err.println("Error type B at Line " + line + ":" + msg);
	}
}
