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
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package wyc.check;

import java.util.ArrayList;
import java.util.Arrays;

import wybs.lang.CompilationUnit;
import wybs.lang.NameResolver;
import wybs.lang.NameResolver.ResolutionError;
import wybs.lang.SyntacticItem;
import wybs.lang.SyntaxError;
import wybs.util.AbstractCompilationUnit.Tuple;
import wyc.lang.WhileyFile;
import wyc.lang.WhileyFile.Decl;
import wyc.lang.WhileyFile.Expr;
import wyc.lang.WhileyFile.Type;
import wyc.task.CompileTask;
import wyc.util.AbstractTypedVisitor;
import wyc.util.AbstractTypedVisitor.Environment;
import wyil.type.subtyping.StrictTypeEmptinessTest;
import wyil.type.subtyping.SubtypeOperator;
import wyil.type.subtyping.EmptinessTest.LifetimeRelation;

/**
 * <p>
 * Responsible for checking that implicit coercions are coherent following
 * RFC#19. The challenge is that, whilst union types in Whiley are powerful,
 * they also open up real challenges for efficient compilation. Specifically,
 * for determining appropriate runtime type tags. The following illustrates:
 * </p>
 *
 * <pre>
 * type msg is {int kind, int payload}|{int kind, int[] payload}
 *
 * function msg(int kind, int payload) -> (msg r):
 *	 return {kind: kind, payload: payload}
 * </pre>
 * <p>
 * At the point of the return, an implicit coercion is inserted to transform a
 * value of type <code>{int kind, int payload}</code> to a value of type
 * <code>msg</code>. The latter requires a single tag bit to distinguish the two
 * cases. In this case, the code generator can automatically determine that the
 * coercion targets the first case from the available type information.
 * </p>
 * <p>
 * Whilst the above was relatively straightforward, this is not always the case.
 * Consider this variation on the above:
 * </p>
 *
 * <pre>
 * type msg is {int kind, int payload}|{int kind, int|null payload}
 *
 * function msg(int kind, int payload) -> (msg r):
 *	 return {kind: kind, payload: payload}
 * </pre>
 * <p>
 * The intuition here might be that the first case is preferred since it
 * occupies less space, but the second is provided for general usage. In the
 * above, it is unclear what the appropriate tag should be. This is because
 * either case is a valid supertype of <code>{int kind, int payload}</code>.
 * However, we should note that <code>{int kind, int payload}</code> is more
 * precise than (i.e. is a subtype of)
 * <code>{int kind, int|null payload}</code>. The key to resolving this case is
 * that the most precise type is always preferred, provided it is unique.
 * </p>
 *
 * @author David J. Pearce
 *
 */
public class AmbiguousCoercionCheck extends AbstractTypedVisitor {

	public AmbiguousCoercionCheck(CompileTask builder) {
		super(builder.getNameResolver(),
				new SubtypeOperator(builder.getNameResolver(), new StrictTypeEmptinessTest(builder.getNameResolver())));
	}

	public void check(WhileyFile file) {
		visitWhileyFile(file);
	}

	@Override
	public void visitExpression(Expr expr, Type target, Environment environment) {
		checkCoercion(expr, target, environment);
		// Finally, recursively continue exploring this expression
		super.visitExpression(expr, target, environment);
	}


	@Override
	public void visitMultiExpression(Expr expr, Tuple<Type> targets, Environment environment) {
		checkCoercion(expr, targets, environment);
		// Finally, recursively continue exploring this expression
		super.visitMultiExpression(expr, targets, environment);
	}

	private void checkCoercion(Expr expr, Type target, Environment environment) {
		Type source = expr.getType();
		if (!checkCoercion(target, source, environment)) {
			syntaxError("ambiguous coercion required (" + source + " to " + target + ")", expr);
		}
	}

	private void checkCoercion(Expr expr, Tuple<Type> targets, Environment environment) {
		Tuple<Type> types = expr.getTypes();
		for(int j=0;j!=targets.size();++j) {
			Type target = targets.get(j);
			Type source = types.get(j);
			if (!checkCoercion(target, source, environment)) {
				syntaxError("ambiguous coercion required (" + source + " to " + target + ")", expr);
			}
		}
	}

	private boolean checkCoercion(Type target, Type source, Environment environment) {
		// FIXME: need to add coinduction here!!
		try {
			if (target instanceof Type.Atom) {
				// Have reached an atom, and we need to decompose this more.
				return checkCoercion((Type.Atom) target, source, environment);
			} else if (target instanceof Type.Nominal) {
				// Have reached a nominal so we just expand this as is.
				return checkCoercion((Type.Nominal) target, source, environment);
			} else {
				// Have reached a decision point. Therefore, need to try and make the decision.
				return checkCoercion((Type.Union) target, source, environment);
			}
		}catch (ResolutionError e) {
			throw new IllegalArgumentException("invalid coercion " + source + " to " + target);
		}
	}

	private boolean checkCoercion(Type.Atom target, Type source, Environment environment) throws ResolutionError {
		if (target instanceof Type.Primitive) {
			return true;
		} else if(source instanceof Type.Nominal) {
			Type.Nominal s = (Type.Nominal) source;
			Decl.Type decl = resolver.resolveExactly(s.getName(), Decl.Type.class);
			return checkCoercion(target,decl.getType(),environment);
		} else if(source instanceof Type.Union) {
			Type.Union s = (Type.Union) source;
			for (int i = 0; i != s.size(); ++i) {
				if (!checkCoercion(target, s.get(i), environment)) {
					return false;
				}
			}
			return true;
		} else if (target instanceof Type.Array) {
			return checkCoercion((Type.Array) target, (Type.Array) source, environment);
		} else if (target instanceof Type.Reference) {
			return checkCoercion((Type.Reference) target, (Type.Reference) source, environment);
		} else if (target instanceof Type.Record) {
			return checkCoercion((Type.Record) target, (Type.Record) source, environment);
		} else if (target instanceof Type.Callable) {
			return checkCoercion((Type.Callable) target, (Type.Callable) source, environment);
		} else {
			throw new IllegalArgumentException("unknown type encountered: " + target);
		}
	}

	private boolean checkCoercion(Type.Array target, Type.Array source, Environment environment) throws ResolutionError {
		return checkCoercion(target.getElement(),source.getElement(),environment);
	}

	private boolean checkCoercion(Type.Reference target, Type.Reference source, Environment environment) throws ResolutionError {
		return checkCoercion(target.getElement(),source.getElement(),environment);
	}

	private boolean checkCoercion(Type.Record target, Type.Record source, Environment environment)
			throws ResolutionError {
		Tuple<Type.Field> fields = target.getFields();
		for (int i = 0; i != fields.size(); ++i) {
			Type.Field field = fields.get(i);
			Type type = source.getField(field.getName());
			if (!checkCoercion(field.getType(), type, environment)) {
				return false;
			}
		}
		return true;
	}

	private boolean checkCoercion(Type.Callable target, Type.Callable source, Environment environment) throws ResolutionError {
		// FIXME: this is considered safe at this point only because the subtype
		// operator currently does not allow any kind of subtyping between parameters.
		return true;
	}

	private boolean checkCoercion(Type.Nominal target, Type source, Environment environment) throws ResolutionError {
		Type.Nominal t = (Type.Nominal) target;
		Decl.Type decl = resolver.resolveExactly(t.getName(), Decl.Type.class);
		return checkCoercion(decl.getType(), source, environment);
	}

	private boolean checkCoercion(Type.Union target, Type source, Environment environment) throws ResolutionError {
		Type.Union ut = (Type.Union) target;
		Type candidate = selectCoercionCandidate(ut.getAll(), source, environment);
		if (candidate != null) {
			// Indicates decision made easily enough. Continue traversal down the type.
			return checkCoercion(candidate, source, environment);
		} else if (source instanceof Type.Nominal) {
			// Proceed by expanding source
			Type.Nominal s = (Type.Nominal) source;
			Decl.Type decl = resolver.resolveExactly(s.getName(), Decl.Type.class);
			return checkCoercion(target,decl.getType(),environment);
		} else if (source instanceof Type.Union) {
			// Proceed by expanding source
			Type.Union su = (Type.Union) source;
			for (int i = 0; i != su.size(); ++i) {
				if (!checkCoercion(target, su.get(i), environment)) {
					return false;
				}
			}
			return true;
		} else {
			// Cannot proceed
			return false;
		}
	}

	private Type selectCoercionCandidate(Type[] candidates, Type type, Environment environment) {
		// FIXME: this is temporary
		return super.selectCandidate(candidates, type, environment);
	}

	private Type.Atom[] expand(Type type) {
		ArrayList<Type.Atom> atoms = new ArrayList<>();
		expand(type,atoms);
		return atoms.toArray(new Type.Atom[atoms.size()]);
	}

	private ArrayList<Type.Atom> expand(Type type, ArrayList<Type.Atom> atoms) {
		if (type instanceof Type.Atom) {
			atoms.add((Type.Atom) type);
		} else if (type instanceof Type.Union) {
			Type.Union t = (Type.Union) type;
			for(int i=0;i!=t.size();++i) {
				expand(t.get(i),atoms);
			}
		} else {
			try {
				Type.Nominal t = (Type.Nominal) type;
				Decl.Type decl = resolver.resolveExactly(t.getName(), Decl.Type.class);
				expand(decl.getType(),atoms);
			} catch (NameResolver.ResolutionError e) {
				throw new IllegalArgumentException("invalid type: " + type);
			}
		}
		return atoms;
	}

	private <T> T syntaxError(String msg, SyntacticItem e) {
		// FIXME: this is a kludge
		CompilationUnit cu = (CompilationUnit) e.getHeap();
		throw new SyntaxError(msg, cu.getEntry(), e);
	}
}
