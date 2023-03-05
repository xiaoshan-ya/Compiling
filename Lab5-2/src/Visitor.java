import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.LLVM.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bytedeco.llvm.global.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.LLVMConstIntGetSExtValue;

public class Visitor extends SysYParserBaseVisitor<LLVMValueRef>{
	public Visitor() { // 构造函数
		//初始化LLVM
		LLVMInitializeCore(LLVMGetGlobalPassRegistry());
		LLVMLinkInMCJIT();
		LLVMInitializeNativeAsmPrinter();
		LLVMInitializeNativeAsmParser();
		LLVMInitializeNativeTarget();
	}
	//创建module
	LLVMModuleRef module = LLVMModuleCreateWithName("moudle");

	//初始化IRBuilder，后续将使用这个builder去生成LLVM IR
	LLVMBuilderRef builder = LLVMCreateBuilder();

	//考虑到我们的语言中仅存在int一个基本类型，可以通过下面的语句为LLVM的int型重命名方便以后使用
	LLVMTypeRef i32Type = LLVMInt32Type();

	//创建可存放200个int的vector类型
	LLVMTypeRef vectorType = LLVMVectorType(i32Type, 200);

	public GlobalScope globalScope = new GlobalScope(null); // 指向根节点的全局作用域
	public BaseScope currentScope = globalScope; // 指向当前遍历到的作用域
	public int localScopeCounter = 0; // 为了区分函数名和局部作用域，添加到局部作用域的名字上
	public Map<String, SysYParser.ReturnStmtContext> returnStmtCTX = new HashMap<>();

	@Override
	public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
		//int a = Integer.parseInt(ctx.getText());
		return super.visitProgram(ctx);
	}

	@Override
	// 函数定义，函数无参数，且仅包含return语句
	public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
		//生成返回值类型
		LLVMTypeRef returnType = i32Type;

		int paramNum = 0;
		if (ctx.funcFParams() != null) {
			paramNum = ctx.funcFParams().funcFParam().size();
		}
		//生成函数参数类型
		PointerPointer<Pointer> argumentTypes = new PointerPointer<>(paramNum);
		for (int i = 0; i < paramNum; i++) {
			argumentTypes.put(i, i32Type);
		}

		//生成函数类型,多个参数
		LLVMTypeRef ft = LLVMFunctionType(returnType, argumentTypes, /* argumentCount */ paramNum, /* isVariadic */ 0);
		//生成函数，即向之前创建的module中添加函数
		LLVMValueRef function = LLVMAddFunction(module, /*functionName:String*/ctx.IDENT().getText(), ft);

		//通过如下语句在函数中加入基本块，一个函数可以加入多个基本块
		LLVMBasicBlockRef block1 = LLVMAppendBasicBlock(function, /*blockName:String*/"mainEntry");
		//选择要在哪个基本块后追加指令
		LLVMPositionBuilderAtEnd(builder, block1);//后续生成的指令将追加在block1的后面

		currentScope = globalScope;
		String funcName = ctx.IDENT().getText(); // 得到函数名
		FuncSymbol funcScope = new FuncSymbol(funcName, currentScope); // 建立函数作用域

		String funReturnType = ctx.funcType().getText(); // 得到函数的返回值类型,只有int和void，没有数组函数
		Function type = new Function();
		if (funReturnType.equals("void")) {
			type.returnType = new Type("void");
		}
		else {
			type.returnType = new Type("int");
		}

		type.setPointer(function);
		funcScope.type = type;
		globalScope.define(funcScope);

		currentScope = funcScope;
		super.visitFuncDef(ctx);
		currentScope = funcScope.enclosingScope;
		return null;
	}

	@Override
	public LLVMValueRef visitFuncFParam(SysYParser.FuncFParamContext ctx) {
		String paramName = ctx.IDENT().getText();
		Type type = new Type("param");
		LLVMValueRef param = LLVMBuildAlloca(builder, i32Type, paramName);

		LLVMBuildStore(builder, LLVMGetParam(currentScope.type.pointer, currentScope.symbols.size()), param);
		type.pointer = param;
		BaseSymbol paramSymbol = new BaseSymbol(paramName, type);

		currentScope.define(paramSymbol);

		return super.visitFuncFParam(ctx);
	}

	@Override
	public LLVMValueRef visitBlock(SysYParser.BlockContext ctx) {
		LocalScope localScope = new LocalScope(currentScope);
		localScope.name = "localScope" + localScopeCounter;

		localScopeCounter++;
		currentScope = localScope;

		super.visitBlock(ctx);
		currentScope = localScope.enclosingScope;
		return null;
	}

	@Override
	// 返回语句
	public LLVMValueRef visitReturnStmt(SysYParser.ReturnStmtContext ctx) {
		String x = ctx.getText();
		returnStmtCTX.put(currentScope.enclosingScope.name, ctx);

		LLVMValueRef result = getNumber(ctx.exp());

		//currentScope.enclosingScope.type.pointer = result;
		LLVMBuildStore(builder, result, currentScope.enclosingScope.type.pointer);

		LLVMBuildRet(builder, result); // 语句保存到module中
		return null;
	}

	@Override
	public LLVMValueRef visitConstDef(SysYParser.ConstDefContext ctx) {
		String leftVarName = ctx.IDENT().getText();
		Type leftVarType; // 左值
		int number;

		List<TerminalNode> dimension = ctx.R_BRACKT();
		if (dimension.size() == 0) { // 左侧为变量
			leftVarType = new Type("int");

			// 赋值
			if (ctx.ASSIGN() != null) {
				LLVMValueRef rightVal = getNumber(ctx.constInitVal().constExp().exp());
				//leftVarType.pointer = rightVal;
				LLVMBuildStore(builder, rightVal, leftVarType.pointer);
			}
		}
		else { // 左侧为数组
			leftVarType = new Array();
			((Array) leftVarType).elementType = "int";
			((Array) leftVarType).dimension = dimension.size();
			((Array) leftVarType).length = Integer.parseInt(ctx.constExp(0).exp().getText());

			//TODO 对每个数组元素进行初始化
			List<SysYParser.ConstInitValContext> element = new ArrayList<>();
			if (ctx.ASSIGN() != null) {
				element = ctx.constInitVal().constInitVal();
			}
			for (int i = 0; i < element.size(); i++) {
				LLVMValueRef zero = getNumber(element.get(i).constExp().exp());
				((Array) leftVarType).arrayList.add(zero);
			}

			for (int i = element.size(); i < ((Array) leftVarType).length; i++) {
				number = 0;
				LLVMValueRef zero = LLVMConstInt(i32Type, number, /* signExtend */ 0);
				((Array) leftVarType).arrayList.add(zero);
			}

		}

		BaseSymbol symbol = new BaseSymbol(leftVarName, leftVarType);

		currentScope.define(symbol);

		return null;
	}

	@Override
	public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
		String leftVarName = ctx.IDENT().getText();
		Type leftVarType; // 左值
		int number;

		List<TerminalNode> dimension = ctx.R_BRACKT();
		if (dimension.size() == 0) { // 左侧为变量
			leftVarType = new Type("int");
			leftVarType.pointer = LLVMBuildAlloca(builder, i32Type, leftVarName);

			// 对变量进行赋值
			if (ctx.ASSIGN() != null) {
				LLVMValueRef rightVal = getNumber(ctx.initVal().exp());
				//leftVarType.pointer = rightVal;
				LLVMBuildStore(builder, rightVal, leftVarType.pointer);
			}
		}
		else { // 左侧为数组
			leftVarType = new Array();
			((Array) leftVarType).elementType = "int";
			((Array) leftVarType).dimension = dimension.size();
			((Array) leftVarType).length = Integer.parseInt(ctx.constExp(0).exp().getText());

			//TODO 对每个数组元素进行初始化
			List<SysYParser.InitValContext> element = new ArrayList<>();
			if (ctx.ASSIGN() != null) {
				element = ctx.initVal().initVal();
			}
			for (int i = 0; i < element.size(); i++) {
				LLVMValueRef zero = getNumber(element.get(i).exp());
				LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/"pointer");
				LLVMBuildStore(builder, zero, pointer);
				((Array) leftVarType).arrayList.add(pointer);
			}

			// 将剩下未定义的补全为0
			for (int i = element.size(); i < ((Array) leftVarType).length; i++) {
				number = 0;
				LLVMValueRef zero = LLVMConstInt(i32Type, number,0);
				LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/"pointer");
				LLVMBuildStore(builder, zero, pointer);
				((Array) leftVarType).arrayList.add(pointer);
			}

		}

		BaseSymbol symbol = new BaseSymbol(leftVarName, leftVarType);

		currentScope.define(symbol);

		return null;
	}

	@Override
	public LLVMValueRef visitAssignStmt(SysYParser.AssignStmtContext ctx) {
		String text = ctx.lVal().getText();
		String leftVarName = "";
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '(' || text.charAt(i) == '[') break;
			leftVarName = leftVarName + text.charAt(i);
		}
		Symbol leftSymbol = currentScope.resolve(leftVarName);

		if (leftSymbol.getType().kind.equals("int") || leftSymbol.getType().kind.equals("param")) { // 对变量进行赋值
			LLVMValueRef rightVal = getNumber(ctx.exp());
			//leftSymbol.getType().pointer = rightVal;
			LLVMBuildStore(builder, rightVal, leftSymbol.getType().pointer);
		}
		else { // 对数组进行赋值
			int begin = text.indexOf("[") + 1;
			int end = text.indexOf("]");
			int cnt = Integer.parseInt(text.substring(begin, end)); // 数组下标

			((Array) leftSymbol.getType()).arrayList.set(cnt, getNumber(ctx.exp()));
		}

		return super.visitAssignStmt(ctx);
	}

	@Override
	// 加法表达式
	public LLVMValueRef visitPlusExp(SysYParser.PlusExpContext ctx) {
		LLVMValueRef result, left, right;
		// 左半部分
		left = getNumber(ctx.exp(0));

		// 右半部分
		right = getNumber(ctx.exp(1));
		if (ctx.PLUS() != null) { // 加法
			result = LLVMBuildAdd(builder, left, right, "add");
		}
		else { // 减法
			result = LLVMBuildSub(builder, left, right, "sub");
		}

		return result;
	}

	@Override
	//乘法表达式
	public LLVMValueRef visitMulExp(SysYParser.MulExpContext ctx) {
		LLVMValueRef result, left, right;

		// 左半部分
		left = getNumber(ctx.exp(0));
		int x = (int) LLVMConstIntGetSExtValue(left);

		// 右半部分
		right = getNumber(ctx.exp(1));
		int y = (int) LLVMConstIntGetSExtValue(right);
		if (ctx.MUL() != null) {
			result = LLVMBuildMul(builder, left, right, "mul");
		}
		else if (ctx.DIV() != null) {
			result = LLVMBuildSDiv(builder, left, right, "div");
		}
		else {
			result = LLVMBuildSRem(builder, left, right, "mod");
		}

		x = (int) LLVMConstIntGetSExtValue(result);
		return result;
	}

	@Override
	// 单目表达式
	public LLVMValueRef visitUnaryOpExp(SysYParser.UnaryOpExpContext ctx) {
		int number = 0;
		LLVMValueRef result = null;
		String x = ctx.getText();

		if (ctx.exp() instanceof SysYParser.NumberExpContext) {
			String numStr = ctx.exp().getText();
			numStr = getTenNumber(numStr);

			if (ctx.unaryOp().getText().equals("-")) {
				number = -Integer.parseInt(numStr);
			}
			else if (ctx.unaryOp().getText().equals("+")) {
				number = Integer.parseInt(numStr);
			}
			else if (ctx.unaryOp().getText().equals("!")) {
				if (Integer.parseInt(numStr) == 0) {
					number = 1;
				}
			}
			result = LLVMConstInt(i32Type, number, /* signExtend */ 0);

		}
		else {
			if (ctx.unaryOp().getText().equals("-")) {
				LLVMValueRef zero = LLVMConstInt(i32Type, 0, /* signExtend */ 0);
				result = LLVMBuildSub(builder, zero, getNumber(ctx.exp()), "sub");
			}
			else if (ctx.unaryOp().getText().equals("+")) {
				result = getNumber(ctx.exp());
			}
			else if (ctx.unaryOp().getText().equals("!")) {
				result = getNumber(ctx.exp());
				result = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(i32Type, 0, 0), result, "tmp_");
				result = LLVMBuildXor(builder, result, LLVMConstInt(LLVMInt1Type(), 1, 0), "tmp_");
				result = LLVMBuildZExt(builder, result, i32Type, "tmp_");
			}
		}

		return result;
	}

	@Override
	// 括号表达式
	public LLVMValueRef visitExpParenthesis(SysYParser.ExpParenthesisContext ctx) {
		LLVMValueRef result = getNumber(ctx.exp());
		return result;
	}

	@Override
	public LLVMValueRef visitCallFuncExp(SysYParser.CallFuncExpContext ctx) {
		LLVMValueRef result;
		String funName = ctx.IDENT().getText();
		FuncSymbol funcScope = (FuncSymbol) globalScope.resolve(funName);// 得到函数作用域
		if (((Function) funcScope.type).getReturnType().kind.equals("void")) {
			return null;
		}

		// 函数调用参数列表
		List<SysYParser.ParamContext> callParam = new ArrayList<>();
		if (ctx.funcRParams() != null) {
			callParam = ctx.funcRParams().param();
		}

		// 实参列表,存放地址
		LLVMValueRef[] actualParam = new LLVMValueRef[callParam.size()];
		for (int i = 0; i < callParam.size(); i++) {
			String text = callParam.get(i).exp().getText();
			Symbol paramSymbol = currentScope.resolve(callParam.get(i).exp().getText()); // 实参

			if (paramSymbol.getType().kind.equals("array")) { // 参数类型为数组
				int begin = text.indexOf("[") + 1;
				int end = text.indexOf("]");
				int cnt = Integer.parseInt(text.substring(begin, end)); // 数组下标
				actualParam[i] = LLVMBuildLoad(builder, ((Array) paramSymbol.getType()).arrayList.get(cnt), "value");
			}
			else {// 参数类型为Int
				actualParam[i] = LLVMBuildLoad(builder, paramSymbol.getType().pointer, "value");
			}
		}

		// (builder, 函数ref, 实参数组, 个数, "")
		result = LLVMBuildCall(builder, funcScope.type.pointer, new PointerPointer<>(actualParam), actualParam.length, funName);

		return result;
	}

	//TODO 增加变量、函数调用
	public LLVMValueRef getNumber (SysYParser.ExpContext exp) {
		int number = 0;
		LLVMValueRef result = null;
		if (exp instanceof SysYParser.NumberExpContext) { // 数字表达式
			String numStr = exp.getText();
			numStr = getTenNumber(numStr);
			number += Integer.parseInt(numStr);

			result = LLVMConstInt(i32Type, number,0);
		}
		else if (exp instanceof SysYParser.UnaryOpExpContext) { // 单目运算
			result = visitUnaryOpExp((SysYParser.UnaryOpExpContext) exp);

		}
		else if (exp instanceof SysYParser.ExpParenthesisContext) { // 括号运算
			result = visitExpParenthesis((SysYParser.ExpParenthesisContext) exp);

		}
		else if (exp instanceof SysYParser.MulExpContext) { // 乘法运算
			result = visitMulExp((SysYParser.MulExpContext) exp);

		}
		else if (exp instanceof SysYParser.PlusExpContext) { // 加法运算
			result = visitPlusExp((SysYParser.PlusExpContext) exp);

		}
		else if (exp instanceof SysYParser.CallFuncExpContext) { // 函数调用
			result = visitCallFuncExp((SysYParser.CallFuncExpContext) exp);
		}

		else if (exp instanceof SysYParser.LvalExpContext) { // 变量调用
			String valName = ((SysYParser.LvalExpContext) exp).lVal().IDENT().getText();
			Symbol valSymbol = currentScope.resolve(valName);

			if (valSymbol.getType().kind.equals("int")) { // int型变量
				result = LLVMBuildLoad(builder, valSymbol.getType().pointer, "value");
			}
			else if (valSymbol.getType().kind.equals("param")) { // 函数形参
				result = LLVMBuildLoad(builder, valSymbol.getType().pointer, "value");
			}
			else { // 数组类型
				String text = ((SysYParser.LvalExpContext) exp).lVal().getText();
				int begin = text.indexOf("[") + 1;
				int end = text.indexOf("]");
				int cnt = Integer.parseInt(text.substring(begin, end)); // 数组下标

				result = LLVMBuildLoad(builder, ((Array) valSymbol.getType()).arrayList.get(cnt),"value");
			}
		}
		return result;
	}
	public String getTenNumber(String str) {
		int n = 0;
		boolean judge = false;
		if (str.length() != 1 && str.charAt(0) == '0' && str.charAt(1) != 'x' && str.charAt(1) != 'X') { // 八进制转十进制
			str = str.substring(1);
			n = Integer.parseInt(str, 8);
			judge = true;
		}
		else if (str.length() != 1 && (str.charAt(1) == 'x' || str.charAt(1) == 'X')) {// 十六进制转十进制
			str = str.substring(2);
			n = Integer.parseInt(str, 16);
			judge = true;
		}

		if (judge) {
			str = n + "";
		}
		return str;
	}
}
