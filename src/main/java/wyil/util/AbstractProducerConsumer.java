// Copyright 2011 The Whiley Project Developers
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OP CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package wyil.util;

import static wyil.lang.WyilFile.*;

import wybs.util.AbstractCompilationUnit.Tuple;
import wyil.lang.WyilFile;
import wyil.lang.WyilFile.Decl;
import wyil.lang.WyilFile.SemanticType;
import wyil.lang.WyilFile.Type;

/**
 * A simple visitor over all declarations, statements, expressions and types in
 * a given WhileyFile which consumes one data parameter and returns one value.
 * The intention is that this is extended as necessary to provide custom
 * functionality.
 *
 * @author David J. Pearce
 *
 */
public abstract class AbstractProducerConsumer<P> {

	public P visitModule(WyilFile wf, P data) {
		for (Decl decl : wf.getModule().getUnits()) {
			visitDeclaration(decl, data);
		}
		return data;
	}

	public P visitDeclaration(Decl decl, P data) {
		switch (decl.getOpcode()) {
		case DECL_unit:
			return visitUnit((Decl.Unit) decl, data);
		case DECL_importfrom:
		case DECL_import:
			return visitImport((Decl.Import) decl, data);
		case DECL_staticvar:
			return visitStaticVariable((Decl.StaticVariable) decl, data);
		case DECL_type:
		case DECL_rectype:
			return visitType((Decl.Type) decl, data);
		case DECL_function:
		case DECL_method:
		case DECL_property:
			return visitCallable((Decl.Callable) decl, data);
		default:
			throw new IllegalArgumentException("unknown declaration encountered (" + decl.getClass().getName() + ")");
		}
	}

	public P visitUnit(Decl.Unit unit, P data) {
		for (Decl decl : unit.getDeclarations()) {
			data = visitDeclaration(decl, data);
		}
		return data;
	}
	public P visitImport(Decl.Import decl, P data) {
		return data;
	}


	public P visitLambda(Decl.Lambda decl, P data) {
		data = visitVariables(decl.getParameters(), data);
		data = visitExpression(decl.getBody(), data);
		return data;
	}

	public P visitVariables(Tuple<Decl.Variable> vars, P data) {
		for(int i=0;i!=vars.size();++i) {
			Decl.Variable var = vars.get(i);
			data = visitVariable(var, data);
		}
		return data;
	}

	public P visitVariable(Decl.Variable decl, P data) {
		data = visitType(decl.getType(), data);
		if(decl.hasInitialiser()) {
			data = visitExpression(decl.getInitialiser(), data);
		}
		return data;
	}

	public P visitStaticVariable(Decl.StaticVariable decl, P data) {
		data = visitType(decl.getType(), data);
		if (decl.hasInitialiser()) {
			data = visitExpression(decl.getInitialiser(), data);
		}
		return data;
	}

	public P visitType(Decl.Type decl, P data) {
		data = visitVariable(decl.getVariableDeclaration(), data);
		data = visitExpressions(decl.getInvariant(), data);
		return data;
	}

	public P visitCallable(Decl.Callable decl, P data) {
		switch (decl.getOpcode()) {
		case DECL_function:
		case DECL_method:
			return visitFunctionOrMethod((Decl.FunctionOrMethod) decl, data);
		case DECL_property:
			return visitProperty((Decl.Property) decl, data);
		default:
			throw new IllegalArgumentException("unknown declaration encountered (" + decl.getClass().getName() + ")");
		}
	}

	public P visitFunctionOrMethod(Decl.FunctionOrMethod decl, P data) {
		switch (decl.getOpcode()) {
		case DECL_function:
			return visitFunction((Decl.Function) decl, data);
		case DECL_method:
			return visitMethod((Decl.Method) decl, data);
		default:
			throw new IllegalArgumentException("unknown declaration encountered (" + decl.getClass().getName() + ")");
		}
	}

	public P visitProperty(Decl.Property decl, P data) {
		data = visitVariables(decl.getParameters(), data);
		data = visitVariables(decl.getReturns(), data);
		data = visitExpressions(decl.getInvariant(), data);
		return data;
	}

	public P visitFunction(Decl.Function decl, P data) {
		data = visitVariables(decl.getParameters(), data);
		data = visitVariables(decl.getReturns(), data);
		data = visitExpressions(decl.getRequires(), data);
		data = visitExpressions(decl.getEnsures(), data);
		data = visitStatement(decl.getBody(), data);
		return data;
	}

	public P visitMethod(Decl.Method decl, P data) {
		data = visitVariables(decl.getParameters(), data);
		data = visitVariables(decl.getReturns(), data);
		data = visitExpressions(decl.getRequires(), data);
		data = visitExpressions(decl.getEnsures(), data);
		data = visitStatement(decl.getBody(), data);
		return data;
	}

	public P visitStatement(Stmt stmt, P data) {
		switch (stmt.getOpcode()) {
		case DECL_variable:
		case DECL_variableinitialiser:
			return visitVariable((Decl.Variable) stmt, data);
		case STMT_assert:
			return visitAssert((Stmt.Assert) stmt, data);
		case STMT_assign:
			return visitAssign((Stmt.Assign) stmt, data);
		case STMT_assume:
			return visitAssume((Stmt.Assume) stmt, data);
		case STMT_block:
			return visitBlock((Stmt.Block) stmt, data);
		case STMT_break:
			return visitBreak((Stmt.Break) stmt, data);
		case STMT_continue:
			return visitContinue((Stmt.Continue) stmt, data);
		case STMT_debug:
			return visitDebug((Stmt.Debug) stmt, data);
		case STMT_dowhile:
			return visitDoWhile((Stmt.DoWhile) stmt, data);
		case STMT_fail:
			return visitFail((Stmt.Fail) stmt, data);
		case STMT_if:
		case STMT_ifelse:
			return visitIfElse((Stmt.IfElse) stmt, data);
		case EXPR_invoke:
			return visitInvoke((Expr.Invoke) stmt, data);
		case EXPR_indirectinvoke:
			return visitIndirectInvoke((Expr.IndirectInvoke) stmt, data);
		case STMT_namedblock:
			return visitNamedBlock((Stmt.NamedBlock) stmt, data);
		case STMT_return:
			return visitReturn((Stmt.Return) stmt, data);
		case STMT_skip:
			return visitSkip((Stmt.Skip) stmt, data);
		case STMT_switch:
			return visitSwitch((Stmt.Switch) stmt, data);
		case STMT_while:
			return visitWhile((Stmt.While) stmt, data);
		default:
			throw new IllegalArgumentException("unknown statement encountered (" + stmt.getClass().getName() + ")");
		}
	}

	public P visitAssert(Stmt.Assert stmt, P data) {
		return visitExpression(stmt.getCondition(), data);
	}


	public P visitAssign(Stmt.Assign stmt, P data) {
		data = visitLVals(stmt.getLeftHandSide(), data);
		data = visitExpressions(stmt.getRightHandSide(), data);
		return data;
	}

	public P visitLVals(Tuple<LVal> lvals, P data) {
		for(int i=0;i!=lvals.size();++i) {
			data = visitExpression(lvals.get(i), data);
		}
		return data;
	}

	public P visitAssume(Stmt.Assume stmt, P data) {
		return visitExpression(stmt.getCondition(), data);
	}

	public P visitBlock(Stmt.Block stmt, P data) {
		for(int i=0;i!=stmt.size();++i) {
			data = visitStatement(stmt.get(i), data);
		}
		return data;
	}

	public P visitBreak(Stmt.Break stmt, P data) {
		return data;
	}

	public P visitContinue(Stmt.Continue stmt, P data) {
		return data;
	}

	public P visitDebug(Stmt.Debug stmt, P data) {
		return visitExpression(stmt.getOperand(), data);
	}

	public P visitDoWhile(Stmt.DoWhile stmt, P data) {
		data = visitStatement(stmt.getBody(), data);
		data = visitExpression(stmt.getCondition(), data);
		data = visitExpressions(stmt.getInvariant(), data);
		return data;
	}

	public P visitFail(Stmt.Fail stmt, P data) {
		return data;
	}

	public P visitIfElse(Stmt.IfElse stmt, P data) {
		data = visitExpression(stmt.getCondition(), data);
		data = visitStatement(stmt.getTrueBranch(), data);
		if(stmt.hasFalseBranch()) {
			data = visitStatement(stmt.getFalseBranch(), data);
		}
		return data;
	}

	public P visitNamedBlock(Stmt.NamedBlock stmt, P data) {
		return visitStatement(stmt.getBlock(), data);
	}

	public P visitReturn(Stmt.Return stmt, P data) {
		return visitExpressions(stmt.getReturns(), data);
	}

	public P visitSkip(Stmt.Skip stmt, P data) {
		return data;
	}

	public P visitSwitch(Stmt.Switch stmt, P data) {
		data = visitExpression(stmt.getCondition(), data);
		Tuple<Stmt.Case> cases = stmt.getCases();
		for(int i=0;i!=cases.size();++i) {
			data = visitCase(cases.get(i), data);
		}
		return data;
	}

	public P visitCase(Stmt.Case stmt, P data) {
		data = visitExpressions(stmt.getConditions(), data);
		data = visitStatement(stmt.getBlock(), data);
		return data;
	}

	public P visitWhile(Stmt.While stmt, P data) {
		data = visitExpression(stmt.getCondition(), data);
		data = visitExpressions(stmt.getInvariant(), data);
		data = visitStatement(stmt.getBody(), data);
		return data;
	}

	public P visitExpressions(Tuple<Expr> exprs, P data) {
		for (int i = 0; i != exprs.size(); ++i) {
			data = visitExpression(exprs.get(i), data);
		}
		return data;
	}

	public P visitExpression(Expr expr, P data) {
		switch (expr.getOpcode()) {
		// Terminals
		case EXPR_constant:
			return visitConstant((Expr.Constant) expr, data);
		case EXPR_indirectinvoke:
			return visitIndirectInvoke((Expr.IndirectInvoke) expr, data);
		case EXPR_lambdaaccess:
			return visitLambdaAccess((Expr.LambdaAccess) expr, data);
		case DECL_lambda:
			return visitLambda((Decl.Lambda) expr, data);
		case EXPR_staticvariable:
			return visitStaticVariableAccess((Expr.StaticVariableAccess) expr, data);
		case EXPR_variablecopy:
		case EXPR_variablemove:
			return visitVariableAccess((Expr.VariableAccess) expr, data);
		// Unary Operators
		case EXPR_cast:
		case EXPR_integernegation:
		case EXPR_is:
		case EXPR_logicalnot:
		case EXPR_logicalexistential:
		case EXPR_logicaluniversal:
		case EXPR_bitwisenot:
		case EXPR_dereference:
		case EXPR_staticnew:
		case EXPR_new:
		case EXPR_recordaccess:
		case EXPR_recordborrow:
		case EXPR_arraylength:
			return visitUnaryOperator((Expr.UnaryOperator) expr, data);
		// Binary Operators
		case EXPR_logiaclimplication:
		case EXPR_logicaliff:
		case EXPR_equal:
		case EXPR_notequal:
		case EXPR_integerlessthan:
		case EXPR_integerlessequal:
		case EXPR_integergreaterthan:
		case EXPR_integergreaterequal:
		case EXPR_integeraddition:
		case EXPR_integersubtraction:
		case EXPR_integermultiplication:
		case EXPR_integerdivision:
		case EXPR_integerremainder:
		case EXPR_bitwiseshl:
		case EXPR_bitwiseshr:
		case EXPR_arrayaccess:
		case EXPR_arrayborrow:
		case EXPR_arrayrange:
		case EXPR_recordupdate:
		case EXPR_arraygenerator:
			return visitBinaryOperator((Expr.BinaryOperator) expr, data);
		// Nary Operators
		case EXPR_logicaland:
		case EXPR_logicalor:
		case EXPR_invoke:
		case EXPR_bitwiseand:
		case EXPR_bitwiseor:
		case EXPR_bitwisexor:
		case EXPR_arrayinitialiser:
		case EXPR_recordinitialiser:
			return visitNaryOperator((Expr.NaryOperator) expr, data);
		// Ternary Operators
		case EXPR_arrayupdate:
			return visitTernaryOperator((Expr.TernaryOperator) expr, data);
		default:
			throw new IllegalArgumentException("unknown expression encountered (" + expr.getClass().getName() + ")");
		}
	}

	public P visitUnaryOperator(Expr.UnaryOperator expr, P data) {
		switch (expr.getOpcode()) {
		// Unary Operators
		case EXPR_cast:
			return visitCast((Expr.Cast) expr, data);
		case EXPR_integernegation:
			return visitIntegerNegation((Expr.IntegerNegation) expr, data);
		case EXPR_is:
			return visitIs((Expr.Is) expr, data);
		case EXPR_logicalnot:
			return visitLogicalNot((Expr.LogicalNot) expr, data);
		case EXPR_logicalexistential:
			return visitExistentialQuantifier((Expr.ExistentialQuantifier) expr, data);
		case EXPR_logicaluniversal:
			return visitUniversalQuantifier((Expr.UniversalQuantifier) expr, data);
		case EXPR_bitwisenot:
			return visitBitwiseComplement((Expr.BitwiseComplement) expr, data);
		case EXPR_dereference:
			return visitDereference((Expr.Dereference) expr, data);
		case EXPR_staticnew:
		case EXPR_new:
			return visitNew((Expr.New) expr, data);
		case EXPR_recordaccess:
		case EXPR_recordborrow:
			return visitRecordAccess((Expr.RecordAccess) expr, data);
		case EXPR_arraylength:
			return visitArrayLength((Expr.ArrayLength) expr, data);
		default:
			throw new IllegalArgumentException("unknown expression encountered (" + expr.getClass().getName() + ")");
		}
	}

	public P visitBinaryOperator(Expr.BinaryOperator expr, P data) {
		switch (expr.getOpcode()) {
		// Binary Operators
		case EXPR_equal:
			return visitEqual((Expr.Equal) expr, data);
		case EXPR_notequal:
			return visitNotEqual((Expr.NotEqual) expr, data);
		case EXPR_logiaclimplication:
			return visitLogicalImplication((Expr.LogicalImplication) expr, data);
		case EXPR_logicaliff:
			return visitLogicalIff((Expr.LogicalIff) expr, data);
		case EXPR_integerlessthan:
			return visitIntegerLessThan((Expr.IntegerLessThan) expr, data);
		case EXPR_integerlessequal:
			return visitIntegerLessThanOrEqual((Expr.IntegerLessThanOrEqual) expr, data);
		case EXPR_integergreaterthan:
			return visitIntegerGreaterThan((Expr.IntegerGreaterThan) expr, data);
		case EXPR_integergreaterequal:
			return visitIntegerGreaterThanOrEqual((Expr.IntegerGreaterThanOrEqual) expr, data);
		case EXPR_integeraddition:
			return visitIntegerAddition((Expr.IntegerAddition) expr, data);
		case EXPR_integersubtraction:
			return visitIntegerSubtraction((Expr.IntegerSubtraction) expr, data);
		case EXPR_integermultiplication:
			return visitIntegerMultiplication((Expr.IntegerMultiplication) expr, data);
		case EXPR_integerdivision:
			return visitIntegerDivision((Expr.IntegerDivision) expr, data);
		case EXPR_integerremainder:
			return visitIntegerRemainder((Expr.IntegerRemainder) expr, data);
		case EXPR_bitwiseshl:
			return visitBitwiseShiftLeft((Expr.BitwiseShiftLeft) expr, data);
		case EXPR_bitwiseshr:
			return visitBitwiseShiftRight((Expr.BitwiseShiftRight) expr, data);
		case EXPR_arraygenerator:
			return visitArrayGenerator((Expr.ArrayGenerator) expr, data);
		case EXPR_arrayaccess:
		case EXPR_arrayborrow:
			return visitArrayAccess((Expr.ArrayAccess) expr, data);
		case EXPR_arrayrange:
			return visitArrayRange((Expr.ArrayRange) expr, data);
		case EXPR_recordupdate:
			return visitRecordUpdate((Expr.RecordUpdate) expr, data);
		default:
			throw new IllegalArgumentException("unknown expression encountered (" + expr.getClass().getName() + ")");
		}
	}

	public P visitTernaryOperator(Expr.TernaryOperator expr, P data) {
		switch (expr.getOpcode()) {
		// Ternary Operators
		case EXPR_arrayupdate:
			return visitArrayUpdate((Expr.ArrayUpdate) expr, data);

		default:
			throw new IllegalArgumentException("unknown expression encountered (" + expr.getClass().getName() + ")");
		}
	}

	public P visitNaryOperator(Expr.NaryOperator expr, P data) {
		switch (expr.getOpcode()) {
		// Nary Operators
		case EXPR_arrayinitialiser:
			return visitArrayInitialiser((Expr.ArrayInitialiser) expr, data);
		case EXPR_bitwiseand:
			return visitBitwiseAnd((Expr.BitwiseAnd) expr, data);
		case EXPR_bitwiseor:
			return visitBitwiseOr((Expr.BitwiseOr) expr, data);
		case EXPR_bitwisexor:
			return visitBitwiseXor((Expr.BitwiseXor) expr, data);
		case EXPR_invoke:
			return visitInvoke((Expr.Invoke) expr, data);
		case EXPR_logicaland:
			return visitLogicalAnd((Expr.LogicalAnd) expr, data);
		case EXPR_logicalor:
			return visitLogicalOr((Expr.LogicalOr) expr, data);
		case EXPR_recordinitialiser:
			return visitRecordInitialiser((Expr.RecordInitialiser) expr, data);
		default:
			throw new IllegalArgumentException("unknown expression encountered (" + expr.getClass().getName() + ")");
		}
	}

	public P visitArrayAccess(Expr.ArrayAccess expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitArrayLength(Expr.ArrayLength expr, P data) {
		return visitExpression(expr.getOperand(), data);
	}

	public P visitArrayGenerator(Expr.ArrayGenerator expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitArrayInitialiser(Expr.ArrayInitialiser expr, P data) {
		return visitExpressions(expr.getOperands(), data);
	}

	public P visitArrayRange(Expr.ArrayRange expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitArrayUpdate(Expr.ArrayUpdate expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		data = visitExpression(expr.getThirdOperand(), data);
		return data;
	}

	public P visitBitwiseComplement(Expr.BitwiseComplement expr, P data) {
		return visitExpression(expr.getOperand(), data);
	}

	public P visitBitwiseAnd(Expr.BitwiseAnd expr, P data) {
		return visitExpressions(expr.getOperands(), data);
	}

	public P visitBitwiseOr(Expr.BitwiseOr expr, P data) {
		return visitExpressions(expr.getOperands(), data);
	}

	public P visitBitwiseXor(Expr.BitwiseXor expr, P data) {
		return visitExpressions(expr.getOperands(), data);
	}

	public P visitBitwiseShiftLeft(Expr.BitwiseShiftLeft expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitBitwiseShiftRight(Expr.BitwiseShiftRight expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitCast(Expr.Cast expr, P data) {
		data = visitType(expr.getType(), data);
		data = visitExpression(expr.getOperand(), data);
		return data;
	}

	public P visitConstant(Expr.Constant expr, P data) {
		return data;
	}

	public P visitDereference(Expr.Dereference expr, P data) {
		data = visitExpression(expr.getOperand(), data);
		return data;
	}

	public P visitEqual(Expr.Equal expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitIntegerLessThan(Expr.IntegerLessThan expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitIntegerLessThanOrEqual(Expr.IntegerLessThanOrEqual expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitIntegerGreaterThan(Expr.IntegerGreaterThan expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitIntegerGreaterThanOrEqual(Expr.IntegerGreaterThanOrEqual expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitIntegerNegation(Expr.IntegerNegation expr, P data) {
		data = visitExpression(expr.getOperand(), data);
		return data;
	}

	public P visitIntegerAddition(Expr.IntegerAddition expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitIntegerSubtraction(Expr.IntegerSubtraction expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitIntegerMultiplication(Expr.IntegerMultiplication expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitIntegerDivision(Expr.IntegerDivision expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitIntegerRemainder(Expr.IntegerRemainder expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitIs(Expr.Is expr, P data) {
		data = visitExpression(expr.getOperand(), data);
		data = visitType(expr.getTestType(), data);
		return data;
	}

	public P visitLogicalAnd(Expr.LogicalAnd expr, P data) {
		data = visitExpressions(expr.getOperands(), data);
		return data;
	}

	public P visitLogicalImplication(Expr.LogicalImplication expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitLogicalIff(Expr.LogicalIff expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitLogicalNot(Expr.LogicalNot expr, P data) {
		data = visitExpression(expr.getOperand(), data);
		return data;
	}

	public P visitLogicalOr(Expr.LogicalOr expr, P data) {
		data = visitExpressions(expr.getOperands(), data);
		return data;
	}

	public P visitExistentialQuantifier(Expr.ExistentialQuantifier expr, P data) {
		data = visitVariables(expr.getParameters(), data);
		data = visitExpression(expr.getOperand(), data);
		return data;
	}

	public P visitUniversalQuantifier(Expr.UniversalQuantifier expr, P data) {
		data = visitVariables(expr.getParameters(), data);
		data = visitExpression(expr.getOperand(), data);
		return data;
	}

	public P visitInvoke(Expr.Invoke expr, P data) {
		data = visitExpressions(expr.getOperands(), data);
		return data;
	}

	public P visitIndirectInvoke(Expr.IndirectInvoke expr, P data) {
		data = visitExpression(expr.getSource(), data);
		data = visitExpressions(expr.getArguments(), data);
		return data;
	}

	public P visitLambdaAccess(Expr.LambdaAccess expr, P data) {
		return data;
	}

	public P visitNew(Expr.New expr, P data) {
		data = visitExpression(expr.getOperand(), data);
		return data;
	}

	public P visitNotEqual(Expr.NotEqual expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitRecordAccess(Expr.RecordAccess expr, P data) {
		data = visitExpression(expr.getOperand(), data);
		return data;
	}

	public P visitRecordInitialiser(Expr.RecordInitialiser expr, P data) {
		data = visitExpressions(expr.getOperands(), data);
		return data;
	}

	public P visitRecordUpdate(Expr.RecordUpdate expr, P data) {
		data = visitExpression(expr.getFirstOperand(), data);
		data = visitExpression(expr.getSecondOperand(), data);
		return data;
	}

	public P visitStaticVariableAccess(Expr.StaticVariableAccess expr, P data) {
		return data;
	}

	public P visitVariableAccess(Expr.VariableAccess expr, P data) {
		return data;
	}

	public P visitTypes(Tuple<Type> type, P data) {
		for (int i = 0; i != type.size(); ++i) {
			data = visitType(type.get(i), data);
		}
		return data;
	}

	public P visitType(Type type, P data) {
		switch (type.getOpcode()) {
		case TYPE_array:
			return visitTypeArray((Type.Array) type, data);
		case TYPE_bool:
			return visitTypeBool((Type.Bool) type, data);
		case TYPE_byte:
			return visitTypeByte((Type.Byte) type, data);
		case TYPE_int:
			return visitTypeInt((Type.Int) type, data);
		case TYPE_nominal:
			return visitTypeNominal((Type.Nominal) type, data);
		case TYPE_null:
			return visitTypeNull((Type.Null) type, data);
		case TYPE_record:
			return visitTypeRecord((Type.Record) type, data);
		case TYPE_reference:
			return visitTypeReference((Type.Reference) type, data);
		case TYPE_function:
		case TYPE_method:
		case TYPE_property:
			return visitTypeCallable((Type.Callable) type, data);
		case TYPE_union:
			return visitTypeUnion((Type.Union) type, data);
		case TYPE_unknown:
			return visitTypeUnresolved((Type.Unknown) type, data);
		case TYPE_void:
			return visitTypeVoid((Type.Void) type, data);
		default:
			throw new IllegalArgumentException("unknown type encountered (" + type.getClass().getName() + ")");
		}
	}

	public P visitTypeCallable(Type.Callable type, P data) {
		switch (type.getOpcode()) {
		case TYPE_function:
			return visitTypeFunction((Type.Function) type, data);
		case TYPE_method:
			return visitTypeMethod((Type.Method) type, data);
		case TYPE_property:
			return visitTypeProperty((Type.Property) type, data);
		default:
			throw new IllegalArgumentException("unknown type encountered (" + type.getClass().getName() + ")");
		}
	}

	public P visitTypeArray(Type.Array type, P data) {
		data = visitType(type.getElement(), data);
		return data;
	}

	public P visitTypeBool(Type.Bool type, P data) {
		return data;
	}

	public P visitTypeByte(Type.Byte type, P data) {
		return data;
	}

	public P visitTypeFunction(Type.Function type, P data) {
		data = visitTypes(type.getParameters(), data);
		data = visitTypes(type.getReturns(), data);
		return data;
	}

	public P visitTypeInt(Type.Int type, P data) {
		return data;
	}

	public P visitTypeMethod(Type.Method type, P data) {
		data = visitTypes(type.getParameters(), data);
		data = visitTypes(type.getReturns(), data);
		return data;
	}

	public P visitTypeNominal(Type.Nominal type, P data) {
		return data;
	}

	public P visitTypeNull(Type.Null type, P data) {
		return data;
	}

	public P visitTypeProperty(Type.Property type, P data) {
		data = visitTypes(type.getParameters(), data);
		data = visitTypes(type.getReturns(), data);
		return data;
	}

	public P visitTypeRecord(Type.Record type, P data) {
		data = visitFields(type.getFields(), data);
		return data;
	}

	public P visitFields(Tuple<Type.Field> fields, P data) {
		for(int i=0;i!=fields.size();++i) {
			data = visitField(fields.get(i), data);
		}
		return data;
	}

	public P visitField(Type.Field field, P data) {
		data = visitType(field.getType(), data);
		return data;
	}

	public P visitTypeReference(Type.Reference type, P data) {
		data = visitType(type.getElement(), data);
		return data;
	}

	public P visitTypeUnion(Type.Union type, P data) {
		for(int i=0;i!=type.size();++i) {
			data = visitType(type.get(i), data);
		}
		return data;
	}

	public P visitTypeUnresolved(Type.Unknown type, P data) {
		return data;
	}

	public P visitTypeVoid(Type.Void type, P data) {
		return data;
	}


	public P visitSemanticType(SemanticType type, P data) {
		switch (type.getOpcode()) {
		case SEMTYPE_array:
			return visitSemanticTypeArray((SemanticType.Array) type, data);
		case SEMTYPE_record:
			return visitSemanticTypeRecord((SemanticType.Record) type, data);
		case SEMTYPE_staticreference:
		case SEMTYPE_reference:
			return visitSemanticTypeReference((SemanticType.Reference) type, data);
		case SEMTYPE_union:
			return visitSemanticTypeUnion((SemanticType.Union) type, data);
		case SEMTYPE_intersection:
			return visitSemanticTypeIntersection((SemanticType.Intersection) type, data);
		case SEMTYPE_difference:
			return visitSemanticTypeDifference((SemanticType.Difference) type, data);
		default:
			// Handle leaf cases
			return visitType((Type) type, data);
		}
	}

	public P visitSemanticTypeArray(SemanticType.Array type, P data) {
		data = visitSemanticType(type.getElement(), data);
		return data;
	}

	public P visitSemanticTypeRecord(SemanticType.Record type, P data) {
		for(SemanticType.Field f : type.getFields()) {
			data = visitSemanticType(f.getType(), data);
		}
		return data;
	}

	public P visitSemanticTypeReference(SemanticType.Reference type, P data) {
		data = visitSemanticType(type.getElement(), data);
		return data;
	}

	public P visitSemanticTypeUnion(SemanticType.Union type, P data) {
		for(SemanticType t : type.getAll()) {
			data = visitSemanticType(t, data);
		}
		return data;
	}

	public P visitSemanticTypeIntersection(SemanticType.Intersection type, P data) {
		for(SemanticType t : type.getAll()) {
			data = visitSemanticType(t, data);
		}
		return data;
	}

	public P visitSemanticTypeDifference(SemanticType.Difference type, P data) {
		data = visitSemanticType(type.getLeftHandSide(), data);
		data = visitSemanticType(type.getRightHandSide(), data);
		return data;
	}

}
