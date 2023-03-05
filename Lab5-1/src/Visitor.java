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

	public GlobalScope globalScope = null; // 指向根节点的全局作用域
	public BaseScope currentScope = null; // 指向当前遍历到的作用域
	public int localScopeCounter = 0; // 为了区分函数名和局部作用域，添加到局部作用域的名字上
	public Map<String, SysYParser.ReturnStmtContext> returnStmtCTX = new HashMap<>();

	@Override
	// 在进入Prog节点时开启一个全局作用域
	public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
		globalScope = new GlobalScope(null);
		currentScope = globalScope;
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
		funcScope.type = type;
		globalScope.define(funcScope);

		// 为函数返回值申请内存
		funcScope.type.pointer = LLVMBuildAlloca(builder, i32Type, funcName);

		super.visitFuncDef(ctx);
		currentScope = funcScope.enclosingScope;
		return null;
	}

	@Override
	public LLVMValueRef visitFuncFParam(SysYParser.FuncFParamContext ctx) {
		String paramName = ctx.IDENT().getText();
		Type type = new Type("int");
		type.pointer = LLVMBuildAlloca(builder, i32Type, paramName);
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
		returnStmtCTX.put(currentScope.name, ctx);
		//函数返回指令
		//创建一个常量,这里是常数0
		//TODO 表达式中有变量时
		int number = getNumber(ctx.exp());
		//int a = Integer.parseInt(ctx.exp().getText());

		LLVMValueRef result = LLVMConstInt(i32Type, number, /* signExtend */ 0);

		//TODO 如果返回值是函数参数，该如何传递
		//LLVMBuildStore(builder, result, ((FuncSymbol)currentScope).pointer); // 返回值保存到函数作用域中

		LLVMBuildRet(builder, /*result:LLVMValueRef*/result); // 语句保存到module中
		//super.visitReturnStmt(ctx);
		return result;
	}

	@Override
	public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
		String leftVarName = ctx.IDENT().getText();
		Type leftVarType; // 左值
		int number = 0;

		List<TerminalNode> dimension = ctx.R_BRACKT();
		if (dimension.size() == 0) { // 左侧为变量
			leftVarType = new Type("int");
			leftVarType.pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/leftVarName);

			//TODO 右侧分为数字，变量，数组，函数四种
			number = getNumber(ctx.initVal().exp());
			LLVMValueRef zero = LLVMConstInt(i32Type, number, /* signExtend */ 0);

			//将数值存入该内存
			LLVMBuildStore(builder, zero, leftVarType.pointer);
		}
		else { // 左侧为数组
			leftVarType = new Array();
			((Array) leftVarType).elementType = "int";
			((Array) leftVarType).dimension = dimension.size();
			((Array) leftVarType).length = Integer.parseInt(ctx.constExp(0).exp().getText());

			//TODO 对每个数组元素进行初始化
			List<SysYParser.InitValContext> element = ctx.initVal().initVal();
			for (int i = 0; i < element.size(); i++) {
				number = getNumber(element.get(i).exp());
				LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/leftVarName+i);
				LLVMValueRef zero = LLVMConstInt(i32Type, number, /* signExtend */ 0);
				LLVMBuildStore(builder, zero, leftVarType.pointer);
				((Array) leftVarType).arrayList.add(pointer);
			}

			// 将剩下未定义的补全为0
			for (int i = element.size(); i < ((Array) leftVarType).length; i++) {
				number = 0;
				LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/leftVarName+i);
				LLVMValueRef zero = LLVMConstInt(i32Type, number, /* signExtend */ 0);
				LLVMBuildStore(builder, zero, leftVarType.pointer);
				((Array) leftVarType).arrayList.add(pointer);
			}

		}

		BaseSymbol symbol = new BaseSymbol(leftVarName, leftVarType);
		currentScope.define(symbol);

		return super.visitVarDef(ctx);
	}

	@Override
	// 加法表达式
	public LLVMValueRef visitPlusExp(SysYParser.PlusExpContext ctx) {
		int number = 0;

		// 左半部分
		number += getNumber(ctx.exp(0));

		// 右半部分
		if (ctx.PLUS() != null) { // 加法
			number += getNumber(ctx.exp(1));
		}
		else if (ctx.MINUS() != null) { // 减法
			number -= getNumber(ctx.exp(1));
		}

		LLVMValueRef result = LLVMConstInt(i32Type, number, /* signExtend */ 0);
//		super.visitPlusExp(ctx);
		return result;
	}

	@Override
	//乘法表达式
	public LLVMValueRef visitMulExp(SysYParser.MulExpContext ctx) {
		int number = 0;
		int number2;

		// 左半部分
		number += getNumber(ctx.exp(0));

		// 右半部分
		if (ctx.MUL() != null) {
			number2 =getNumber(ctx.exp(1));
			number *= number2;
		}
		else if (ctx.DIV() != null) {
			number2 = getNumber(ctx.exp(1));
			number = number / number2;
		}
		else if (ctx.MOD() != null) {
			number2 =getNumber(ctx.exp(1));
			number %= getNumber(ctx.exp(1));
		}

		LLVMValueRef result = LLVMConstInt(i32Type, number, /* signExtend */ 0);
//		super.visitMulExp(ctx);
		return result;
	}

	@Override
	// 单目表达式
	public LLVMValueRef visitUnaryOpExp(SysYParser.UnaryOpExpContext ctx) {
		int number = 0;
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
		}
		else if (ctx.exp() instanceof SysYParser.ExpParenthesisContext) {
			if (ctx.unaryOp().getText().equals("-")) {
				number = (int) -LLVMConstIntGetSExtValue(visitExpParenthesis((SysYParser.ExpParenthesisContext) ctx.exp()));
			}
			else if (ctx.unaryOp().getText().equals("+")) {
				number = (int) LLVMConstIntGetSExtValue(visitExpParenthesis((SysYParser.ExpParenthesisContext) ctx.exp()));
			}
			else if (ctx.unaryOp().getText().equals("!")) {
				if (LLVMConstIntGetSExtValue(visitExpParenthesis((SysYParser.ExpParenthesisContext) ctx.exp())) == 0) {
					number = 1;
				}
			}
		}
		else if (ctx.exp() instanceof SysYParser.UnaryOpExpContext) {
			if (ctx.unaryOp().getText().equals("-")) {
				number = (int) -LLVMConstIntGetSExtValue(visitUnaryOpExp((SysYParser.UnaryOpExpContext) ctx.exp()));
			}
			else if (ctx.unaryOp().getText().equals("+")) {
				number = (int) LLVMConstIntGetSExtValue(visitUnaryOpExp((SysYParser.UnaryOpExpContext) ctx.exp()));
			}
			else if (ctx.unaryOp().getText().equals("!")) {
				if ((int)LLVMConstIntGetSExtValue(visitUnaryOpExp((SysYParser.UnaryOpExpContext) ctx.exp())) == 0) {
					number = 1;
				}
			}
		}

		LLVMValueRef result = LLVMConstInt(i32Type, number, /* signExtend */ 0);
//		super.visitUnaryOpExp(ctx);
		return result;
	}

	@Override
	// 括号表达式
	public LLVMValueRef visitExpParenthesis(SysYParser.ExpParenthesisContext ctx) {
		int number = getNumber(ctx.exp());
		LLVMValueRef result = LLVMConstInt(i32Type, number, /* signExtend */ 0);
//		super.visitExpParenthesis(ctx);
		return result;
	}

	//TODO 增加变量、函数调用
	public int getNumber (SysYParser.ExpContext exp) {
		int number = 0;
		if (exp instanceof SysYParser.NumberExpContext) { // 数字表达式
			String numStr = exp.getText();
			numStr = getTenNumber(numStr);
			number += Integer.parseInt(numStr);
		}
		else if (exp instanceof SysYParser.UnaryOpExpContext) { // 单目运算
			number += LLVMConstIntGetSExtValue(visitUnaryOpExp((SysYParser.UnaryOpExpContext) exp));
		}
		else if (exp instanceof SysYParser.ExpParenthesisContext) { // 括号运算
			number += LLVMConstIntGetSExtValue(visitExpParenthesis((SysYParser.ExpParenthesisContext) exp));
		}
		else if (exp instanceof SysYParser.MulExpContext) { // 乘法运算
			number += LLVMConstIntGetSExtValue(visitMulExp((SysYParser.MulExpContext) exp));
		}
		else if (exp instanceof SysYParser.PlusExpContext) { // 加法运算
			number += LLVMConstIntGetSExtValue(visitPlusExp((SysYParser.PlusExpContext) exp));
		}
		else if (exp instanceof SysYParser.CallFuncExpContext) { // 函数调用
			String funName = ((SysYParser.CallFuncExpContext) exp).IDENT().getText();
			FuncSymbol funcScope = (FuncSymbol) globalScope.resolve(funName);// 得到函数作用域
			// 形参列表
			List<SysYParser.ParamContext> formalParam = ((SysYParser.CallFuncExpContext) exp).funcRParams().param();
			// 实参列表
			List<LLVMValueRef> actualParam = new ArrayList<>();
			for (int i = 0; i < formalParam.size(); i++) {
				String text = formalParam.get(i).exp().getText();
				Symbol paramSymbol = currentScope.resolve(formalParam.get(i).exp().getText());

				if (paramSymbol.getType().kind.equals("array")) { // 参数类型为数组
					int begin = text.indexOf("[") + 1;
					int end = text.indexOf("]");
					int cnt = Integer.parseInt(text.substring(begin, end)); // 数组下标
					LLVMValueRef value = LLVMBuildLoad(builder, ((Array) paramSymbol).arrayList.get(cnt), /*varName:String*/text);
					actualParam.add(value);
				}
				else {// 参数类型为Int
					LLVMValueRef value = LLVMBuildLoad(builder, paramSymbol.getType().pointer, /*varName:String*/text);
					actualParam.add(value);
				}
			}

			//TODO 得到函数返回值
			/* 步骤
			*  1. 将实参中的值传递给函数作用域中的形参
			*  2. visitReturn，得到函数的返回值
			* */
			int i = 0;
			for (Symbol symbol: funcScope.symbols.values()) {
				LLVMBuildStore(builder, actualParam.get(i), symbol.getType().pointer);
				i++;
			}

			LLVMValueRef returnValue = visitReturnStmt(returnStmtCTX.get(funcScope.name));
			number += LLVMConstIntGetSExtValue(returnValue);
		}
		else if (exp instanceof SysYParser.LvalExpContext) { // 变量调用
			String valName = ((SysYParser.LvalExpContext) exp).lVal().IDENT().getText();
			Symbol valSymbol = currentScope.resolve(valName);
			if (valSymbol.getType().kind.equals("int")) { // int型变量
				LLVMValueRef value = LLVMBuildLoad(builder, valSymbol.getType().pointer, /*varName:String*/valName);
				number += LLVMConstIntGetSExtValue(value);
			}
			else { // 数组类型
				String text = ((SysYParser.LvalExpContext) exp).lVal().getText();
				int begin = text.indexOf("[") + 1;
				int end = text.indexOf("]");
				int cnt = Integer.parseInt(text.substring(begin, end)); // 数组下标
				LLVMValueRef value = LLVMBuildLoad(builder, ((Array) valSymbol).arrayList.get(cnt), /*varName:String*/valName);
				number += LLVMConstIntGetSExtValue(value);
			}
		}
		return number;
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
