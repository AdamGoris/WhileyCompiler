package wycs.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import wybs.lang.Attribute;
import wybs.lang.SyntacticElement;
import wybs.util.Pair;

public abstract class Stmt extends SyntacticElement.Impl implements SyntacticElement {
	
	public Stmt(Attribute... attributes) {
		super(attributes);
	}
	
	public Stmt(Collection<Attribute> attributes) {
		super(attributes);
	}

	// ==================================================================
	// Constructors
	// ==================================================================

	public static Assert Assert(String message, Expr expr, Attribute... attributes) {
		return new Assert(message,expr,attributes);
	}
	
	public static Assert Assert(String message, Expr expr, Collection<Attribute> attributes) {
		return new Assert(message,expr,attributes);
	}
	
	public static Predicate Define(String name, Collection<String> generics,
			Collection<Pair<Type, String>> arguments, Expr expr,
			Attribute... attributes) {
		return new Predicate(name, generics, arguments, expr, attributes);
	}
	
	// ==================================================================
	// Classes
	// ==================================================================

	public static class Assert extends Stmt {
		public final String message;
		public final Expr expr;
		
		private Assert(String message, Expr expr, Attribute... attributes) {
			super(attributes);
			this.message = message;
			this.expr = expr;
		}
		
		private Assert(String message, Expr expr, Collection<Attribute> attributes) {
			super(attributes);
			this.message = message;
			this.expr = expr;
		}
		
		public String toString() {
			if(message == null) {
				return "assert " + expr;	
			} else {
				return "assert " + expr + ", \"" + message + "\"";
			}
			
		}
	}	
	
	public static class Function extends Stmt {
		public final String name;
		public final ArrayList<String> generics;
		public final List<Pair<Type, String>> arguments;
		public final Type ret;
		public final Expr condition;

		public Function(String name, Collection<String> generics, Collection<Pair<Type, String>> arguments, Type ret,
				Expr condition, Attribute... attributes) {
			super(attributes);
			this.name = name;
			this.generics = new ArrayList<String>(generics);
			this.arguments = new ArrayList<Pair<Type, String>>(arguments);
			this.ret = ret;
			this.condition = condition;
		}
		
		public String toString() {
			String gens = "";
			if(generics.size() > 0) {
				gens += "<";
				for(int i=0;i!=arguments.size();++i) {					
					if(i != 0) {
						gens = gens + ", ";
					}
					gens = gens + generics.get(i);
				}	
				gens += ">";
			}
			
			String params = "";
			for(int i=0;i!=arguments.size();++i) {
				Pair<Type,String> argument = arguments.get(i);
				if(i != 0) {
					params = params + ", ";
				}
				params = params + argument.first() + " " + argument.second();
			}
			String kind = "function ";
			if(this instanceof Predicate) {
				kind = "predicate ";
			}
			return kind + name + gens + "(" + params + ") where " + condition;			
		}
	}
	
	public static class Predicate extends Function {
		public Predicate(String name, Collection<String> generics, Collection<Pair<Type, String>> arguments, 
				Expr condition, Attribute... attributes) {
			super(name,generics,arguments,Type.Bool,condition,attributes);
		}
	}
}
