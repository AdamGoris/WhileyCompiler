package wyjc.lang;

import java.util.*;

import wyil.lang.*;
import wyil.util.Pair;

public interface Expr extends SyntacticElement {

	public interface LVal extends Expr {}
	
	public static class Variable extends SyntacticElement.Impl implements Expr, LVal {
		public final String var;

		public Variable(String var, Attribute... attributes) {
			super(attributes);
			this.var = var;
		}

		public String toString() {
			return var;
		}
	}
	
	public static class NamedConstant extends Variable {
		public final ModuleID mid;

		public NamedConstant(String var, ModuleID mid, Attribute... attributes) {
			super(var, attributes);
			this.mid = mid;
		}
	}

	public static class Constant extends SyntacticElement.Impl implements Expr {
		public final Value val;

		public Constant(Value val, Attribute... attributes) {
			super(attributes);
			this.val = val;
		}
	}

	public static class TypeConst extends SyntacticElement.Impl implements Expr {
		public final UnresolvedType type;

		public TypeConst(UnresolvedType val, Attribute... attributes) {
			super(attributes);
			this.type = val;
		}
	}
	
	public enum BOp { 
		AND,
		OR,
		ADD,
		SUB,
		MUL,
		DIV,		
		UNION,
		INTERSECTION,
		EQ,
		NEQ,
		LT,
		LTEQ,
		GT,
		GTEQ,
		SUBSET,
		SUBSETEQ,
		ELEMENTOF,
		LISTACCESS,
		LISTRANGE,
		TYPEEQ,
		TYPEIMPLIES
	};
		
	public static class BinOp extends SyntacticElement.Impl implements Expr {
		public final BOp op;
		public final Expr lhs;
		public final Expr rhs;
		
		public BinOp(BOp op, Expr lhs, Expr rhs, Attribute... attributes) {
			super(attributes);
			this.op = op;
			this.lhs = lhs;
			this.rhs = rhs;
		}
	}


	public enum UOp {
		NOT,
		NEG,
		LENGTHOF,
		PROCESSACCESS,
		PROCESSSPAWN
	}
	
	public static class UnOp extends SyntacticElement.Impl implements Expr {
		public final UOp op;
		public final Expr mhs;		
		
		public UnOp(UOp op, Expr mhs, Attribute... attributes) {
			super(attributes);
			this.op = op;
			this.mhs = mhs;			
		}
	}
	
	public static class NaryOp extends SyntacticElement.Impl implements Expr {
		public final NOp nop;
		public final ArrayList<Expr> arguments;
		public NaryOp(NOp nop, Collection<Expr> arguments, Attribute... attributes) {
			super(attributes);
			this.nop = nop;
			this.arguments = new ArrayList<Expr>(arguments);
		}
		public NaryOp(NOp nop, Attribute attribute, Expr... arguments) {
			super(attribute);
			this.nop = nop;
			this.arguments = new ArrayList<Expr>();
			for(Expr a : arguments) {
				this.arguments.add(a);
			}
		}
	}
	
	public enum NOp {
		SETGEN,
		LISTGEN,
		SUBLIST					
	}
	
	public static class Comprehension extends SyntacticElement.Impl implements Expr {
		public final COp cop;
		public final Expr value;
		public final ArrayList<Pair<String,Expr>> sources;
		public final Expr condition;
		
		public Comprehension(COp cop, Expr value,
				Collection<Pair<String, Expr>> sources, Expr condition,
				Attribute... attributes) {
			super(attributes);
			this.cop = cop;
			this.value = value;
			this.condition = condition;
			this.sources = new ArrayList<Pair<String, Expr>>(sources);
		}
	}
	
	public enum COp {
		SETCOMP,
		LISTCOMP,
		NONE, // implies value == null					
		SOME, // implies value == null
	}
	
	public static class TupleAccess extends SyntacticElement.Impl implements
			Expr {
		public final Expr lhs;
		public final String name;

		public TupleAccess(Expr lhs, String name, Attribute... attributes) {
			super(attributes);
			this.lhs = lhs;
			this.name = name;
		}
	}		

	public static class TupleGen extends SyntacticElement.Impl implements Expr {
		public final HashMap<String,Expr> fields;		
		
		public TupleGen(Map<String, Expr> fields, Attribute... attributes) {
			super(attributes);
			this.fields = new HashMap<String, Expr>(fields);
		}
	}
	
	public static class Invoke extends SyntacticElement.Impl implements Expr,Stmt {
		public final String name;
		public final Expr receiver;
		public final List<Expr> arguments;
		
		public Invoke(String name, Expr receiver, List<Expr> arguments,
				Attribute... attributes) {
			super(attributes);
			this.name = name;
			this.receiver = receiver;
			this.arguments = arguments;
		}
	}
	
	public static class Spawn extends UnOp implements Stmt {		
		public Spawn(Expr mhs, Attribute... attributes) {
			super(UOp.PROCESSSPAWN,mhs,attributes);							
		}
	}
}
