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
// WITHOUT WARRANTIES OEnvironment CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package wyil.transform;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import wybs.lang.CompilationUnit;
import wybs.lang.SyntacticItem;
import wybs.lang.SyntaxError.InternalFailure;
import wybs.util.AbstractCompilationUnit.Tuple;
import static wyil.lang.WyilFile.*;
import wyil.lang.WyilFile;
import wyil.lang.WyilFile.Decl;
import wyil.lang.WyilFile.Expr;
import wyil.lang.WyilFile.LVal;
import wyil.lang.WyilFile.Stmt;
import wyil.lang.WyilFile.Type;
import wyil.lang.WyilFile.Decl.Variable;
import wyil.lang.WyilFile.Stmt.Block;
import wyil.util.AbstractFunction;
import wyil.util.AbstractProducerConsumer;

/**
 * <p>
 * Responsible for versioning variables in a manner suitable for verification
 * condition generation. The transformation is similar, in some ways, with
 * static single assignment form. Roughly speaking, whenever a variable is
 * declared it has an initial version of <code>0</code>. Then, when that
 * variable is assigned, its version is increased. The following illustrates a
 * simple example, where variable versions are denoted with tick notation.
 * </p>
 *
 * <pre>
 * function f(int x'0) -> (int r'0):
 *    x'1 = x'0 + 1
 *    return x'1
 * </pre>
 *
 * <p>
 * What we see is that, as a result of the assignment, the version of variable
 * <code>x</code> is increased and subsequent uses of that variable report the
 * updated version. The first challenge faced in versioning is that of
 * conditional control-flow. The following illustrates an example:
 * </p>
 *
 * <pre>
 * function abs(int x'0) -> int:
 *    if x'0 < 0:
 *       x'1 = -x'0
 *    //
 *    return x'2
 * </pre>
 * <p>
 * Here, we see the version of <code>x</code> is increased by the assignment and
 * we must then <em>merge</em> versions together after the conditional in a
 * conservative fashion. Since neither <code>x'0</code> nor <code>x'1</code> are
 * suitable versions to represent its value after the condition, we pick a new
 * version for this purpose.
 * </p>
 *
 * <p>
 * Loops are another challenge faced in versioning because the life of a
 * variable which is <em>modified</em> in a loop is split into three cases: that
 * <em>before</em> the loop, that <em>within</em> the loop and that
 * <em>after</em> the loop. The following illustrates:
 * </p>
 *
 * <pre>
 * function count(int n'0) -> int:
 *    int i'0 = 0
 *    //
 *    while i'1 < n'0:
 *        i'2 = i'1 + 1
 *    //
 *    return i'3
 * </pre>
 * <p>
 * Here, version <code>i'0</code> represents the life of variable <code>i</code>
 * before the loop; versions <code>i'1</code> and version <code>i'2</code>
 * represent it within the loop; and, finally, version <code>i'3</code>
 * represents it after the loop. Finally, we note that method invocations also
 * affect the version of reference variables. The following illustrates:
 * </p>
 *
 * <pre>
 * method swap(&int x, &int y):
 *    ...
 *
 * method main():
 *    &int x'0 = new 0
 *    &int y'0 = new 1
 *    swap(x'0,y'0)
 *    *x'1 = 2
 * </pre>
 * <p>
 * Here, we see that the version of <code>x</code> has been updated by the call
 * to <code>swap</code> to reflect the fact that its referent may have been
 * changed by the <code>swap</code> call.
 * </p>
 *
 * @author David J. Pearce
 *
 */
public class VariableVersioningAnalysis extends AbstractProducerConsumer<VariableVersioningAnalysis.Environment> {

	public void apply(WyilFile module) {
		visitModule(module, null);
	}

	@Override
	public Environment visitType(Decl.Type type, Environment environment) {
		// NOTE: there is nothing to do for type declarations
		return environment;
	}

	@Override
	public Environment visitStaticVariable(Decl.StaticVariable type, Environment environment) {
		// NOTE: there is nothing to do for static variable declarations
		return environment;
	}

	@Override
	public Environment visitFunctionOrMethod(Decl.FunctionOrMethod fm, Environment environment) {
		Tuple<Variable> parameters = fm.getParameters();
		Tuple<Variable> returns = fm.getReturns();
		// Create initial environment
		environment = new Environment(parameters, returns);
		// Pass through all statements in block
		Block b = fm.getBody();
		for (int i = 0; i != b.size(); ++i) {
			environment = visitStatement(b.get(i), environment);
		}
		//
		return environment;
	}

	@Override
	public Environment visitProperty(Decl.Property p, Environment environment) {
		// NOTE: there is nothing to do for property declarations
		return environment;
	}

	@Override
	public Environment visitVariable(Decl.Variable e, Environment environment) {
		if (e.hasInitialiser()) {
			environment = visitExpression(e.getInitialiser(), environment);
		}
		return environment.declare(e);
	}

	@Override
	public Environment visitAssign(Stmt.Assign stmt, Environment environment) {
		visitExpressions(stmt.getRightHandSide(), environment);
		return visitLVals(stmt.getLeftHandSide(), environment);
	}

	@Override
	public Environment visitLVals(Tuple<LVal> lvals, Environment environment) {
		for (int i = 0; i != lvals.size(); ++i) {
			// TODO: is this correct for multi-way assignments to same variable?
			environment = visitLVal(lvals.get(i), environment);
		}
		return environment;
	}

	public Environment visitLVal(LVal lval, Environment environment) {
		switch (lval.getOpcode()) {
		case EXPR_staticvariable: {
			return visitLVal((Expr.StaticVariableAccess) lval, environment);
		}
		case EXPR_variablecopy:
		case EXPR_variablemove: {
			return visitLVal((Expr.VariableAccess) lval, environment);
		}
		case EXPR_arrayaccess: {
			return visitLVal((Expr.ArrayAccess) lval, environment);
		}
		case EXPR_recordaccess: {
			return visitLVal((Expr.RecordAccess) lval, environment);
		}
		case EXPR_dereference: {
			return visitLVal((Expr.Dereference) lval, environment);
		}
		default:
			return internalFailure("unknown lval encountered", lval);
		}
	}

	public Environment visitLVal(Expr.StaticVariableAccess lval, Environment environment) {
		Decl.StaticVariable var = lval.getDeclaration();
		environment = environment.havoc(var);
		lval.setVersion(environment.getVersion(var));
		return environment;
	}

	public Environment visitLVal(Expr.VariableAccess lval, Environment environment) {
		Decl.Variable var = lval.getVariableDeclaration();
		environment = environment.havoc(var);
		lval.setVersion(environment.getVersion(var));
		return environment;
	}

	public Environment visitLVal(Expr.ArrayAccess lval, Environment environment) {
		visitExpression(lval.getSecondOperand(), environment);
		//
		return visitLVal((LVal) lval.getFirstOperand(), environment);
	}

	public Environment visitLVal(Expr.RecordAccess lval, Environment environment) {
		return visitLVal((LVal) lval.getOperand(), environment);
	}

	public Environment visitLVal(Expr.Dereference lval, Environment environment) {
		return visitLVal((LVal) lval.getOperand(), environment);
	}

	@Override
	public Environment visitAssume(Stmt.Assume stmt, Environment environment) {
		visitExpression(stmt.getCondition(), environment);
		//
		return environment;
	}

	@Override
	public Environment visitBlock(Stmt.Block stmt, Environment environment) {
		for (int i = 0; i != stmt.size(); ++i) {
			environment = visitStatement(stmt.get(i), environment);
		}
		return environment;
	}

	@Override
	public Environment visitBreak(Stmt.Break stmt, Environment environment) {
		return null;
	}

	@Override
	public Environment visitContinue(Stmt.Continue stmt, Environment environment) {
		return null;
	}

	@Override
	public Environment visitDebug(Stmt.Debug stmt, Environment environment) {
		visitExpression(stmt.getOperand(), environment);
		return environment;
	}

	@Override
	public Environment visitDoWhile(Stmt.DoWhile stmt, Environment environment) {
		// Havoc modified variables within loop
		Environment body = environment.havoc(stmt.getModified());
		body = visitStatement(stmt.getBody(), environment);
		visitExpression(stmt.getCondition(), body);
		visitExpressions(stmt.getInvariant(), body);
		//
		return environment.havoc(stmt.getModified());
	}

	@Override
	public Environment visitFail(Stmt.Fail stmt, Environment environment) {
		return null;
	}

	@Override
	public Environment visitIfElse(Stmt.IfElse stmt, Environment environment) {
		visitExpression(stmt.getCondition(), environment);
		//
		Environment ttenv = visitStatement(stmt.getTrueBranch(), environment);
		Environment ffenv = environment;
		if (stmt.hasFalseBranch()) {
			ffenv = visitStatement(stmt.getFalseBranch(), environment);
		}
		return join(ttenv, ffenv);
	}

	@Override
	public Environment visitNamedBlock(Stmt.NamedBlock stmt, Environment environment) {
		return visitStatement(stmt.getBlock(), environment);
	}

	@Override
	public Environment visitReturn(Stmt.Return stmt, Environment environment) {
		visitExpressions(stmt.getReturns(), environment);
		return null;
	}

	@Override
	public Environment visitSkip(Stmt.Skip stmt, Environment environment) {
		return environment;
	}

	@Override
	public Environment visitSwitch(Stmt.Switch stmt, Environment environment) {
		visitExpression(stmt.getCondition(), environment);
		//
		Tuple<Stmt.Case> cases = stmt.getCases();
		Environment[] nenvs = new Environment[cases.size()];
		boolean hasDefault = false;
		for (int i = 0; i != cases.size(); ++i) {
			Stmt.Case c = cases.get(i);
			hasDefault |= c.isDefault();
			nenvs[i] = visitCase(cases.get(i), environment);
		}
		// If a default case was detected then the resulting environment is constructed
		// from the cases. Otherwise, control can pass throug the switch statement
		// without executing any of the cases and, hence, the original environment may
		// be in play.
		if (hasDefault) {
			return join(nenvs);
		} else {
			return join(environment, join(nenvs));
		}
	}

	@Override
	public Environment visitCase(Stmt.Case stmt, Environment environment) {
		visitExpressions(stmt.getConditions(), environment);
		//
		return visitStatement(stmt.getBlock(), environment);
	}

	@Override
	public Environment visitWhile(Stmt.While stmt, Environment environment) {
		// Havoc modified variables within loop
		Environment body = environment.havoc(stmt.getModified());
		//
		visitExpression(stmt.getCondition(), body);
		visitExpressions(stmt.getInvariant(), body);
		visitStatement(stmt.getBody(), body);
		// Havoc modified variables after loop
		return environment.havoc(stmt.getModified());
	}

	@Override
	public Environment visitInvoke(Expr.Invoke stmt, Environment environment) {
		Tuple<Expr> args = stmt.getOperands();
		environment = super.visitInvoke(stmt, environment);
		//
		if(stmt.getDeclaration() instanceof Decl.Method) {
			// Conservatively handle reference types
			for (int i = 0; i != args.size(); ++i) {
				environment = havocReferences(args.get(i), environment);
			}
		}
		//
		return environment;
	}

	@Override
	public Environment visitIndirectInvoke(Expr.IndirectInvoke stmt, Environment environment) {
		Tuple<Expr> args = stmt.getArguments();
		environment = super.visitIndirectInvoke(stmt, environment);
		// FIXME: could update this to havoc in the presence of methods only.
		// Conservatively handle reference types
		for (int i = 0; i != args.size(); ++i) {
			environment = havocReferences(args.get(i), environment);
		}
		//
		return environment;
	}

	@Override
	public Environment visitVariableAccess(Expr.VariableAccess e, Environment environment) {
		Decl.Variable var = e.getVariableDeclaration();
		e.setVersion(environment.getVersion(var));
		return environment;
	}

	private static Environment join(Environment lhs, Environment rhs) {
		if (lhs == null) {
			return rhs;
		} else if (rhs == null) {
			return lhs;
		} else {
			return lhs.join(rhs);
		}
	}

	private static Environment join(Environment... envs) {
		// TODO: this could be made more efficient
		Environment e = envs[0];
		for (int i = 1; i != envs.length; ++i) {
			e = join(e, envs[i]);
		}
		return e;
	}

	private boolean containsReference(Type type, BitSet visited) {
		//
		if(type instanceof Type.Nominal) {
			// Sanity check for recursive types
			if(visited.get(type.getIndex())) {
				// Have seen this type before already, therefore can assume it doesn't contain a
				// reference.
				return false;
			} else {
				// Have not yet seen this type before, therefore record this encase we see it
				// again.
				visited.set(type.getIndex());
			}
		}
		//
		switch(type.getOpcode()) {
		case TYPE_bool:
		case TYPE_byte:
		case TYPE_int:
		case TYPE_null:
		case TYPE_void:
		case TYPE_property:
		case TYPE_function:
		case TYPE_unknown:
			// NOTE: the argument for methods is simple. Assume the method contains a
			// reference type. Then, this will need to be supplied using an additional
			// parameter which must itself be a reference.
		case TYPE_method:
			return false;
		case TYPE_staticreference:
		case TYPE_reference:
			return true;
		case TYPE_array: {
			Type.Array t = (Type.Array) type;
			return containsReference(t.getElement(), visited);
		}
		case TYPE_nominal:  {
			Type.Nominal t = (Type.Nominal) type;
			return containsReference(t.getDeclaration().getType(), visited);
		}
		case TYPE_record: {
			Type.Record t = (Type.Record) type;
			Tuple<Type.Field> fields = t.getFields();
			for (int i = 0; i != fields.size(); ++i) {
				if(containsReference(fields.get(i).getType(),visited)) {
					return true;
				}
			}
			// TODO: following is conservative and always rejects open records. This is
			// because the type of an open record is "hidden" but can be recovered using a
			// type test. Therefore, it could contain a hidden reference which is then
			// modified after the type test. There is no easy to way to get around this.
			return !t.isOpen();
		}
		case TYPE_union: {
			Type.Union t = (Type.Union) type;
			for (int i = 0; i != t.size(); ++i) {
				if (containsReference(t.get(i), visited)) {
					return true;
				}
			}
			return false;
		}
		default:
			return internalFailure("unknown type encountered",type);
		}
	}

	private Environment havocReferences(Expr expr, Environment data) {
		AbstractProducerConsumer<Environment> visitor = new AbstractProducerConsumer<Environment>() {
			@Override
			public Environment visitExpression(Expr e, Environment environment) {
				if (containsReference(e.getType(), new BitSet())) {
					return super.visitExpression(e, environment);
				} else {
					// Expression does not return a reference type, therefore no need to proceed
					// further.
					return environment;
				}
			}
			@Override
			public Environment visitVariableAccess(Expr.VariableAccess e, Environment environment) {
				// Found variable which could be invalidated.
				return environment.havoc(e.getVariableDeclaration());
			}
		};
		return visitor.visitExpression(expr, data);
	}

	/**
	 * The environment maps local variables to their versioned
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Environment {
		/**
		 * Responsible for holding the global versioning map for a given function or
		 * method declaration.
		 */
		private final HashMap<Decl.Variable, Integer> versions;

		/**
		 * The mapping for this particular environment.
		 */
		private final HashMap<Decl.Variable, Integer> mapping;

		public Environment(Tuple<Variable> parameters, Tuple<Variable> returns) {
			this.versions = new HashMap<>();
			this.mapping = new HashMap<>();
			// Allocate parameters
			initialise(parameters);
			initialise(returns);
		}

		private Environment(Environment other) {
			this.versions = other.versions;
			this.mapping = new HashMap<>(other.mapping);
		}

		private Environment(HashMap<Decl.Variable, Integer> versions) {
			this.versions = versions;
			this.mapping = new HashMap<>();
		}

		public int getVersion(Decl.Variable v) {
			Integer i = mapping.get(v);
			if (i == null) {
				return 0;
			} else {
				return (int) i;
			}
		}

		Environment declare(Decl.Variable v) {
			Environment n = new Environment(this);
			n.versions.put(v, 0);
			n.mapping.put(v, 0);
			return n;
		}

		Environment havoc(Tuple<Decl.Variable> vars) {
			Environment n = new Environment(this);
			for (int i = 0; i != vars.size(); ++i) {
				Decl.Variable var = vars.get(i);
				// Allocate new version
				int version = alloc(var);
				// Update local mapping
				n.mapping.put(var, version);
			}
			return n;
		}

		Environment havoc(Decl.Variable v) {
			Environment n = new Environment(this);
			// Allocate new version
			int version = alloc(v);
			// Update local mapping
			n.mapping.put(v, version);
			// Done
			return n;
		}

		public Environment join(Environment environment) {
			if (environment == this) {
				return this;
			} else {
				Environment n = new Environment(versions);
				//
				for (Map.Entry<Decl.Variable, Integer> e : mapping.entrySet()) {
					Decl.Variable var = e.getKey();
					int my_version = e.getValue();
					Integer other_version = environment.mapping.get(var);
					if (other_version != null) {
						int version = join(var, my_version, other_version);
						n.mapping.put(var, version);
					}
				}
				return n;
			}
		}

		private int join(Decl.Variable var, int v1, int v2) {
			if (v1 == v2) {
				return v1;
			} else {
				return alloc(var);
			}
		}

		private int alloc(Decl.Variable var) {
			int version = versions.get(var) + 1;
			versions.put(var, version);
			return version;
		}

		private void initialise(Tuple<Decl.Variable> vars) {
			for (int i = 0; i != vars.size(); ++i) {
				Variable v = vars.get(i);
				this.versions.put(v, 0);
				this.mapping.put(v, 0);
			}
		}
	}

	private <T> T internalFailure(String msg, SyntacticItem e) {
		// FIXME: this is a kludge
		CompilationUnit cu = (CompilationUnit) e.getHeap();
		throw new InternalFailure(msg, cu.getEntry(), e);
	}
}
