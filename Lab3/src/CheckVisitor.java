import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CheckVisitor extends SysYParserBaseVisitor<Void>{

	public GlobalScope globalScope = null; // 指向根节点的全局作用域
	public BaseScope currentScope = null; // 指向当前遍历到的作用域
	public int localScopeCounter = 0; // 为了区分函数名和局部作用域，添加到局部作用域的名字上
	public boolean canDefine = true;
	int lineNo;
	int columnNo;
	String rename;
	public String renameScopeName;
	Visitor visitor = new Visitor();
	boolean judgeError = true;

	/**
	 * (1) When/How to start/enter a new scope?
	 * 什么时候开启一个新的作用域
	 * 什么时候进入一个新的作用域：将currentScope设置为当前作用域
	 */
	@Override
	// 在进入Prog节点时开启一个全局作用域
	public Void visitProgram(SysYParser.ProgramContext ctx) {
		globalScope = new GlobalScope(null);
		currentScope = globalScope;
		visitor.globalScope = globalScope;
		return super.visitProgram(ctx);
	}

	@Override
	// 进入函数作用域
	public Void visitFuncDef(SysYParser.FuncDefContext ctx) {
		currentScope = globalScope;
		String funcName = ctx.IDENT().getText(); // 得到函数名

		// 检查函数是否重名
		if (!globalScope.judgeFuncSame(funcName)) { // 函数名有重复,直接结束子节点的遍历
			System.err.println("Error type 4 at Line " + (ctx.start.getLine()) + ": your function already exist");
			judgeError = false;
			judgeError = false;
			return null;
		}

			// 建立函数作用域
			FunctionSymbol funcScope = new FunctionSymbol(funcName, currentScope);
			Function type = new Function();

			String returnType = ctx.funcType().getText(); // 得到函数的返回值类型,只有int和void，没有数组函数
			if(returnType.equals("void")) {
				funcScope.setReturnType(new Type("void"));
				type.returnType = new Type("void");
			}
			else if(returnType.equals("int")) {
				funcScope.setReturnType(new Type("int"));
				type.returnType = new Type("int");
			}
			funcScope.setType(type);
			// 函数同时也是一个符号，需要放入符号表中
			globalScope.define(funcScope);
			currentScope = funcScope;

			visitor.funcScopeList.add(funcScope);

			// 设置重命名
			if (ctx.start.getLine() == this.lineNo && ctx.start.getCharPositionInLine() == this.columnNo) {
				funcScope.isRename = true;
				funcScope.rename = this.rename;
				visitor.originalName = funcName;
				renameScopeName = currentScope.name;
			}

		super.visitFuncDef(ctx);
		currentScope = funcScope.enclosingScope;
		return null;
	}

	@Override
	public Void visitFuncFParam(SysYParser.FuncFParamContext ctx) {
		String paramName = ""; // 参数名称
		String paramType = ctx.bType().getText(); // 参数类型
		List<TerminalNode> dimension = ctx.R_BRACKT();

		for (int i = 0; i <ctx.IDENT().getText().length(); i++) {
			if (ctx.IDENT().getText().charAt(i) == '(' || ctx.IDENT().getText().charAt(i) == '[') break;
			paramName = paramName + ctx.IDENT().getText().charAt(i);
		}
		/* error 3
		*  变量重复声明
		* */
		if (currentScope.symbols.get(paramName) != null){ // 当前参数命名已经存在
			System.err.println("Error type 3 at Line " + ctx.start.getLine() + ": Redefined variable");
			judgeError = false;
			judgeError = false;
			return super.visitFuncFParam(ctx);
		}

		Type type;
		if (dimension.size() == 0) { // int类型参数
			type = new Type(paramType);
		}
		else { // array类型参数
			type = new Array();
			((Array)type).dimension = dimension.size();
			((Array)type).elementType = paramType;
		}

		BaseSymbol symbol = new BaseSymbol(paramName, type);
		currentScope.define(symbol);

		int x = ctx.start.getLine();
		int y = ctx.start.getCharPositionInLine();
		if (ctx.start.getLine() == this.lineNo && ctx.start.getCharPositionInLine()+4 == this.columnNo) {
			symbol.isRename = true;
			symbol.rename = this.rename;
			currentScope.rename = this.rename;
			renameScopeName = currentScope.name;
		}

		return super.visitFuncFParam(ctx);
	}

	@Override
	// 进入局部作用域
	public Void visitBlock(SysYParser.BlockContext ctx) {
		LocalScope localScope = new LocalScope(currentScope);
		String localScopeName = localScope.getName() + localScopeCounter;
		localScope.setName(localScopeName);
		localScopeCounter++;

		currentScope = localScope;

		visitor.localScopeList.add(localScope);

		super.visitBlock(ctx);
		currentScope = localScope.enclosingScope;
		return null;
	}

	@Override
	public Void visitConstDef(SysYParser.ConstDefContext ctx) {
		String leftVarName = ctx.IDENT().getText();
		Type leftVarType; // 左值
		canDefine = true;

		/* error 3
		 *  变量重复声明
		 * */
		if (!currentScope.judgeValSame(leftVarName, globalScope)) {
			System.err.println("Error type 3 at Line " + ctx.start.getLine() + ": your val is already exist");
			judgeError = false;
			canDefine = false;
		}

		List<TerminalNode> dimension = ctx.R_BRACKT();
		if(dimension.size() == 0) {
			leftVarType = new Type("int");
		}
		else {
			leftVarType = new Array();
			((Array)leftVarType).elementType = "int";
			((Array)leftVarType).dimension = dimension.size();
		}

		BaseSymbol symbol = new BaseSymbol(leftVarName, leftVarType);
		if (ctx.ASSIGN() != null) {
			String exp = ctx.constInitVal().constExp().getText();
			//TODO 得到表达式
			List<String> valList = splitExp(new ArrayList<String>(), ctx.constInitVal().constExp().exp());
			judgeError(valList, ctx.start.getLine(), leftVarName, leftVarType);

		}
		currentScope.define(symbol);

		if (ctx.start.getLine() == this.lineNo && ctx.start.getCharPositionInLine() == this.columnNo) {
			visitor.originalName = ctx.IDENT().getText();
			currentScope.rename = this.rename;
			renameScopeName = currentScope.name;
		}
		return super.visitConstDef(ctx);
	}

	@Override
	// 因为变量定义只有Int,所以可以在下一个子节点进行
	// error 3
	public Void visitVarDef(SysYParser.VarDefContext ctx) {
		String leftVarName = ctx.IDENT().getText();
		Type leftVarType; // 左值
		canDefine = true;

		/* error 3
		*  变量重复声明
		* */
		if (!currentScope.judgeValSame(leftVarName, globalScope)) {
			System.err.println("Error type 3 at Line " + ctx.start.getLine() + ": your val is already exist");
			judgeError = false;
			canDefine = false;
		}

		List<TerminalNode> dimension = ctx.R_BRACKT();
		if(dimension.size() == 0) {
			leftVarType = new Type("int");
		}
		else {
			leftVarType = new Array();
			((Array)leftVarType).elementType = "int";
			((Array)leftVarType).dimension = dimension.size();
		}

		BaseSymbol symbol = new BaseSymbol(leftVarName, leftVarType);
		if (ctx.ASSIGN() != null) {
			SysYParser.ExpContext exp = ctx.initVal().exp();
			//TODO 得到表达式
			List<String> valList = splitExp(new ArrayList<String>(), exp);
			judgeError(valList, ctx.start.getLine(), leftVarName, leftVarType);

		}

		currentScope.define(symbol);

		int x = ctx.start.getLine();
		int y = ctx.start.getCharPositionInLine();

		if (ctx.start.getLine() == this.lineNo && this.columnNo == ctx.start.getCharPositionInLine()) { // 判断左值是否匹配
			visitor.originalName = ctx.IDENT().getText();
			currentScope.rename = this.rename;
			renameScopeName = currentScope.name;
		}
		return super.visitVarDef(ctx);
	}

	// error 1、2、5、8、9、10
	public void judgeError(List<String> valList, int line, String leftVarName, Type leftVarType) {
		for (String val: valList) {
			Symbol rightValSymbol = currentScope.resolve(val); // 右值
			if(Character.isDigit(val.charAt(0))) { // 运算因子为数字
				continue;
			}
			/* error 1
			 *  变量未声明
			 *  error 2
			 *  函数未定义
			 * */
			if (rightValSymbol == null && val.contains("(")) { // 函数未定义
				System.err.println("Error type 2 at Line " + line + ": your function is undeclared");
				judgeError = false;
				continue;
			}
			if (rightValSymbol == null && !val.contains("(")) { // 运算因子未声明变量
				System.err.println("Error type 1 at Line " + line + ": your val is undeclared");
				judgeError = false;
				continue;
			}

			if (rightValSymbol != null) { // 右边有表达式时
				// error 9 数组取下标越界判断,并且直接返回，因为实际上rightValSymbol为空
				if (rightValSymbol.getType().kind.equals("array") && getDimension(val) > ((Array)rightValSymbol.getType()).dimension) {
					System.err.println("Error type 9 at Line " + line + ": Not an array");
					judgeError = false;
					continue;
				}

				/* error 5
				 *  判断赋值号两侧类型
				 * */
				if (leftVarType != null) {
					if (!rightValSymbol.getType().kind.equals("function") && !rightValSymbol.getType().kind.equals("array") && !rightValSymbol.getType().kind.equals(leftVarType.kind)) { //当两侧类型不匹配
						System.err.println("Error type 5 at Line " + line + ": your val type is error");
						judgeError = false;
					}
					if (rightValSymbol.getType().kind.equals("function") && !((FunctionSymbol)rightValSymbol).returnType.kind.equals(leftVarType.kind)) {
						System.err.println("Error type 5 at Line " + line + ": your val type is error");
						judgeError = false;
					}
					if (rightValSymbol.getType().kind.equals("array") && leftVarType.kind.equals("array") && ((Array)leftVarType).dimension != ((Array)rightValSymbol.getType()).dimension) { // 两侧类型都匹配且为数组，则需要检查数组维度是否相等
						System.err.println("Error type 5 at Line " + line + ": your val type is error");
						judgeError = false;
					}
					if (rightValSymbol.getType().kind.equals("array") && !leftVarType.kind.equals("array") && getDimension(leftVarName) != ((Array)rightValSymbol.getType()).dimension) { // 两侧类型都匹配且为数组，则需要检查数组维度是否相等
						System.err.println("Error type 5 at Line " + line + ": your val type is error");
						judgeError = false;
					}
				}


				/* error 8
				 *  函数参数不适用，函数参数的数量或类型与函数声明的参数数量或类型不一致
				 * */
				if (val.contains("(")) { // 建立在函数已经定义的前提上
					String funName = val.substring(0, val.indexOf("("));
					String[] paramList = val.substring(val.indexOf("(")+1, val.indexOf(")")).split(",");

					// 在全局作用域中寻找函数
					if (!globalScope.judgeError8(currentScope.getParamType(paramList), funName)) {
						System.err.println("Error type 8 at Line " + line + ": function param error");
					}

				}

				/* error 9
				 *  对非数组使用下标运算符,对数组下标超出数组界限
				 * */
				// 在不是数组的前提下有[]则报错
				if (leftVarType != null && !leftVarType.kind.equals("array")) {
					if(leftVarName.charAt(leftVarName.length()-1) == ']') {
						System.err.println("Error type 9 at Line " + line + ": Not an array");
						judgeError = false;
					}
				}
				if (!rightValSymbol.getType().kind.equals("array") && !rightValSymbol.getName().equals("array")) {
					// 特殊情况:数组取到最内层时resolve返回的是int，但是name为array
					if(val.charAt(val.length()-1) == ']') {
						System.err.println("Error type 9 at Line " + line + ": Not an array");
						judgeError = false;
					}
				}

				/* error 10
				 *  对变量使用函数调用
				 * */
				if (leftVarType != null && !leftVarType.kind.equals("function")) {
					if(leftVarName.charAt(leftVarName.length()-1) == ')') {
						System.err.println("Error type 10 at Line " + line + ": Not an function");
						judgeError = false;
					}
				}
				if (!rightValSymbol.getType().kind.equals("function")) {
					if(val.charAt(val.length()-1) == ')') {
						System.err.println("Error type 10 at Line " + line + ": Not an function");
						judgeError = false;
					}
				}
			}

		}
	}

	public List<String> splitExp(List<String> valList, SysYParser.ExpContext exp) {
		// 递归结束条件
		if (exp instanceof SysYParser.NumberExpContext) { // 数字
			String val = exp.getText();
			valList.add(val);
			return valList;
		}
		if (exp instanceof SysYParser.CallFuncExpContext) { // 函数调用
			String val = exp.getText();
			valList.add(val);
			return valList;
		}
		if (exp instanceof SysYParser.LvalExpContext) { // 变量或数组
			String val = exp.getText();
			valList.add(val);
			return valList;
		}

		// 继续递归
		if (exp instanceof SysYParser.ExpParenthesisContext) { // （exp）递归,返回内层exp
			return splitExp(valList, ((SysYParser.ExpParenthesisContext) exp).exp());
		}
		if (exp instanceof SysYParser.PlusExpContext) { // 加减法,返回左右两侧的表达式
			SysYParser.ExpContext leftExp = ((SysYParser.PlusExpContext) exp).exp(0);
			List<String> list = splitExp(valList, leftExp);
			SysYParser.ExpContext rightExp =  ((SysYParser.PlusExpContext) exp).exp(1);
			return splitExp(list, rightExp);
		}
		if (exp instanceof SysYParser.UnaryOpExpContext) { // 单元运算符,返回下一层exp即可
			return splitExp(valList, ((SysYParser.UnaryOpExpContext) exp).exp());
		}
		if (exp instanceof SysYParser.MulExpContext) { // 乘除法,返回左右两侧的表达式
			SysYParser.ExpContext leftExp = ((SysYParser.MulExpContext) exp).exp(0);
			List<String> list = splitExp(valList, leftExp);
			SysYParser.ExpContext rightExp =  ((SysYParser.MulExpContext) exp).exp(1);
			return splitExp(list, rightExp);
		}

		return new ArrayList<>();
	}

	@Override
	/* error 7
	*  返回值类型不匹配：返回值类型与函数声明的返回值类型不同
	*  当前作用域为函数作用域，父作用域为函数参数作用域，返回值类型在父作用域中取得
	*  error 1、2、8
	* */
	public Void visitReturnStmt(SysYParser.ReturnStmtContext ctx) {
		String returnType = currentScope.enclosingScope.returnType.kind;

		if (ctx.exp() != null) { // 如果有返回表达式
			String exp =ctx.exp().getText();
			//TODO 得到表达式的元素
			String valName = "";
			for (int i = 0; i < exp.length(); i++) {
				if (exp.charAt(i) == '(' || exp.charAt(i) == '[') break;
				valName = valName + exp.charAt(i);
			}
			Symbol valSymbol = currentScope.resolve(valName);
			if (valSymbol == null) { // 返回表达式未定义
				// error 2
				if (exp.contains("(")) {
					System.err.println("Error type 2 at Line " + ctx.start.getLine() + ": Undefined function");
					judgeError = false;
				}
				// error 1
				if (!exp.contains("(")) {
					System.err.println("Error type 1 at Line " + ctx.start.getLine() + ": Undefined var");
					judgeError = false;
				}

			}
			else {
				// error 8
				if (exp.contains("(")) { // 建立在函数已经定义的前提上
					String funName = exp.substring(0, exp.indexOf("("));
					String[] paramList = exp.substring(exp.indexOf("(")+1, exp.indexOf(")")).split(",");

					// 在全局作用域中寻找函数
					if (!globalScope.judgeError8(currentScope.getParamType(paramList), funName)) {
						System.err.println("Error type 8 at Line " + ctx.start.getLine() + ": function param error");
						judgeError = false;
					}
				}

				// error 7
				if (!valSymbol.getType().kind.equals("function") && !returnType.equals(valSymbol.getType().kind)) {
					System.err.println("Error type 7 at Line " + ctx.start.getLine() + ": Type mismatched for return.");
					judgeError = false;
				}
				else if (valSymbol.getType().kind.equals("function") && exp.contains("(") && !returnType.equals(((FunctionSymbol)valSymbol).returnType.kind)) {
					System.err.println("Error type 7 at Line " + ctx.start.getLine() + ": Type mismatched for return.");
					judgeError = false;
				}
				else if (valSymbol.getType().kind.equals("function") && !exp.contains("(") && !returnType.equals("function")) {
					System.err.println("Error type 7 at Line " + ctx.start.getLine() + ": Type mismatched for return.");
					judgeError = false;
				}
			}

		}
		else { // 如果没有返回表达式
			if (!returnType.equals("void")) {
				System.err.println("Error type 7 at Line " + ctx.start.getLine() + ": Type mismatched for return.");
				judgeError = false;
			}
		}


		return super.visitReturnStmt(ctx);
	}

	@Override
	// 判断加减法表达式的运算因子是否合理
	public Void visitPlusExp(SysYParser.PlusExpContext ctx) {
		//TODO 得到表达式
		String leftExp = ctx.exp(0).getText();

		List<String> valList = splitExp(new ArrayList<String>(), ctx.exp(0));
		if (hasOperator(ctx.exp(0).getText()) == 0) {
			judgeError6(valList, ctx.start.getLine());
		}
		valList = splitExp(new ArrayList<String>(), ctx.exp(1));
		judgeError6(valList, ctx.start.getLine());

		return super.visitPlusExp(ctx);
	}

	@Override
	public Void visitMulExp(SysYParser.MulExpContext ctx) {
		//TODO 得到表达式
		List<String> valList = splitExp(new ArrayList<String>(), ctx.exp(0));
		if (hasOperator(ctx.exp(0).getText()) == 0) {
			judgeError6(valList, ctx.start.getLine());
		}
		valList = splitExp(new ArrayList<String>(), ctx.exp(1));
		judgeError6(valList, ctx.start.getLine());
		return super.visitMulExp(ctx);
	}

	// 判断运算符个数
	public int hasOperator (String exp) {
		int cnt = 0;
		for (int i = 0; i < exp.length(); i++) {
			if (exp.charAt(i) == '+' || exp.charAt(i) == '-' || exp.charAt(i) == '*' || exp.charAt(i) == '/' || exp.charAt(i) == '%') {
				cnt++;
			}
		}
		return cnt;
	}

	@Override
	public Void visitUnaryOpExp(SysYParser.UnaryOpExpContext ctx) {
		//TODO 得到表达式
		List<String> valList = splitExp(new ArrayList<String>(), ctx.exp());
		judgeError6(valList, ctx.start.getLine());
		return super.visitUnaryOpExp(ctx);
	}

	/* error 6
	 *  运算符需求类型与提供类型不匹配：运算符需要的类型为int却提供array或function等
	 * */
	public void judgeError6 (List<String> valList, int line) {
		for (String valName: valList) {
			Symbol valSymbol = currentScope.resolve(valName);
			// 不需要判断error 1，因为在judgeError中已经判断完了
			if (valSymbol == null) continue;

			if (valSymbol.getType().kind.equals("array")) { // 数组参与运算报错
				System.err.println("Error type 6 at Line " + line + ": Type mismatched for operands.");
				judgeError = false;
			}
			else if (valSymbol.getType().kind.equals("function") && valName.contains("(") && !((FunctionSymbol)valSymbol).returnType.kind.equals("int")) { // 函数返回值不为int报错
				System.err.println("Error type 6 at Line " + line + ": Type mismatched for operands.");
				judgeError = false;
			}
			else if (valSymbol.getType().kind.equals("function") && !valName.contains("(")) {
				System.err.println("Error type 6 at Line " + line + ": Type mismatched for operands.");
				judgeError = false;
			}
		}
	}

	// 得到数组维度
	public int getDimension (String array) {
		int dimension = 0;
		for (int i = 0; i < array.length(); i++) {
			if (array.charAt(i) == '[') {
				dimension++;
			}
		}
		return dimension;
	}

	@Override
	// 赋值语句
	public Void visitAssignStmt(SysYParser.AssignStmtContext ctx) {
		String lvalText = ctx.lVal().getText();
		String leftVarName = "";
		for (int i = 0; i < lvalText.length(); i++) {
			if (lvalText.charAt(i) == '(' || lvalText.charAt(i) == '[') break;
			leftVarName = leftVarName + lvalText.charAt(i);
		}
		/* error 1
		 *  变量未声明
		 * */
		Symbol valSymbol = currentScope.resolve(leftVarName);
		if (valSymbol == null) {
			System.err.println("Error type 1 at Line " + ctx.start.getLine() + ": Undefined variable");
			judgeError = false;
			//TODO 继续判断
			valSymbol = new BaseSymbol("error1", new Type("int"));
		}

		Type leftVarType = valSymbol.getType();

		/* error 11
		*  赋值号左侧非变量或数组元素：对函数进行赋值操作
		* */
		if (leftVarType.kind.equals("function")) {
			System.err.println("Error type 11 at Line " + ctx.start.getLine() + ": left var is function.");
			judgeError = false;
		}
		if (leftVarType.kind.equals("array")) {
			((Array)leftVarType).dimension =  ((Array)leftVarType).dimension - getDimension(lvalText);
		}

		int line = ctx.start.getLine();
		List<String> valList = splitExp(new ArrayList<String>(), ctx.exp());
		judgeError(valList, line, leftVarName, leftVarType);

		if (leftVarType.kind.equals("array")) {
			((Array)leftVarType).dimension =  ((Array)leftVarType).dimension + getDimension(lvalText);
		}

		int x = ctx.start.getLine();
		int y = ctx.start.getCharPositionInLine();
		String z = ctx.getText();
		if (ctx.start.getLine() == this.lineNo && ctx.start.getCharPositionInLine() == this.columnNo) { // 判断等号左边是否匹配
			visitor.originalName = ctx.lVal().IDENT().getText();
			currentScope.rename = this.rename;
			renameScopeName = currentScope.name;
		}

		return super.visitAssignStmt(ctx);
	}

	@Override
	public Void visitExpStmt(SysYParser.ExpStmtContext ctx) {
		int line = ctx.start.getLine();
		List<String> valList = splitExp(new ArrayList<String>(), ctx.exp());
		judgeError(valList, line, "", null);

		return super.visitExpStmt(ctx);
	}

	@Override
	// error 6
	public Void visitConditionStmt(SysYParser.ConditionStmtContext ctx) {
		List<String> condList = splitCond(new ArrayList<String>(), ctx.cond());
		judgeCondError6(condList, ctx.start.getLine());
		return super.visitConditionStmt(ctx);
	}

	public List<String> splitCond (List<String> condList, SysYParser.CondContext cond) {
		// 递归结束条件
		if (cond instanceof SysYParser.ExpCondContext) {
			String condStr = splitExp(new ArrayList<String>() ,((SysYParser.ExpCondContext) cond).exp()).get(0);
			condList.add(condStr);
			return condList;
		}

		//继续递归
		if (cond instanceof SysYParser.LtCondContext) {
			SysYParser.CondContext leftCond = ((SysYParser.LtCondContext) cond).cond(0);
			List<String> list = splitCond(condList, leftCond);
			SysYParser.CondContext rightCond = ((SysYParser.LtCondContext) cond).cond(1);
			return splitCond(list, rightCond);
		}
		if (cond instanceof SysYParser.EqCondContext) {
			SysYParser.CondContext leftCond = ((SysYParser.EqCondContext) cond).cond(0);
			List<String> list = splitCond(condList, leftCond);
			SysYParser.CondContext rightCond = ((SysYParser.EqCondContext) cond).cond(1);
			return splitCond(list, rightCond);
		}
		if (cond instanceof SysYParser.OrCondContext) {
			SysYParser.CondContext leftCond = ((SysYParser.OrCondContext) cond).cond(0);
			List<String> list = splitCond(condList, leftCond);
			SysYParser.CondContext rightCond = ((SysYParser.OrCondContext) cond).cond(1);
			return splitCond(list, rightCond);
		}

		return new ArrayList<>();
	}

	public void judgeCondError6 (List<String> valList, int line) {
		for (String valName: valList) {
			Symbol valSymbol = currentScope.resolve(valName);
			// 不需要判断error 1，因为在judgeError中已经判断完了
			if (valSymbol == null) {
				if (valName.contains("(")){
					System.err.println("Error type 2 at Line " + line + ": Undefined function");
					judgeError = false;
				}
				else {
					System.err.println("Error type 1 at Line " + line + ": Undefined val");
					judgeError = false;
				}
				break;
			}

			if (valSymbol.getType().kind.equals("array")) { // 数组参与运算报错
				System.err.println("Error type 6 at Line " + line + ": Type mismatched for operands.");
				judgeError = false;
				break;
			}
			else if (valSymbol.getType().kind.equals("function") && valName.contains("(") && !((FunctionSymbol)valSymbol).returnType.kind.equals("int")) { // 函数返回值不为int报错
				System.err.println("Error type 6 at Line " + line + ": Type mismatched for operands.");
				judgeError = false;
				break;
			}
			else if (valSymbol.getType().kind.equals("function") && !valName.contains("(")) {
				System.err.println("Error type 6 at Line " + line + ": Type mismatched for operands.");
				judgeError = false;
				break;
			}
		}
	}

	@Override
	//TODO 其实不需要，在block中已经实现，但是需要检查作用域是否合理
	public Void visitBlockStmt(SysYParser.BlockStmtContext ctx) {
		return super.visitBlockStmt(ctx);
	}

	public void findVal (int lineNo, int column, String name) {
		this.lineNo = lineNo;
		this.columnNo = column;
		this.rename = name;
	}

	@Override
	// 等号右边的值
	public Void visitLvalExp(SysYParser.LvalExpContext ctx) {
		int x = ctx.start.getLine();
		int y = ctx.start.getCharPositionInLine();
		String z = ctx.getText();
		if (ctx.start.getLine() == this.lineNo && ctx.start.getCharPositionInLine() == this.columnNo) {
			visitor.originalName = ctx.lVal().IDENT().getText();
			currentScope.rename = this.rename;
			renameScopeName = currentScope.name;
		}
		return super.visitLvalExp(ctx);
	}

	public void visitor (ParseTree tree) {
		visitor.rename = this.rename;
		visitor.renameScopeName = this.renameScopeName;
		visitor.visit(tree);
	}
}
