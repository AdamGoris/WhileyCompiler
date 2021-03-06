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
package wyil.lang;

import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.*;

import wybs.lang.CompilationUnit;
import wybs.lang.SyntacticHeap;
import wybs.lang.SyntacticItem;
import wybs.lang.SyntacticItem.Data;
import wybs.lang.SyntacticItem.Operands;
import wybs.lang.SyntacticItem.Schema;
import wybs.util.AbstractCompilationUnit;
import wybs.util.AbstractSyntacticItem;
import wybs.util.AbstractCompilationUnit.Identifier;
import wybs.util.AbstractCompilationUnit.Name;
import wybs.util.AbstractCompilationUnit.Ref;
import wybs.util.AbstractCompilationUnit.Tuple;
import wyc.util.ErrorMessages;
import wycc.util.ArrayUtils;
import wyfs.lang.Content;
import wyfs.lang.Path;
import wyfs.lang.Path.Entry;
import wyfs.util.Trie;
import wyil.io.WyilFilePrinter;
import wyil.io.WyilFileReader;
import wyil.io.WyilFileWriter;
import wyil.lang.WyilFile.Decl;
import wyil.lang.WyilFile.SemanticType;
import wyil.lang.WyilFile.Template;
import wyil.lang.WyilFile.Type;
import wyil.util.AbstractConsumer;

/**
 * <p>
 * Provides the in-memory representation of a Whiley source file (a.k.a. an
 * "Abstract Syntax Tree"). This is implemented as a "heap" of syntactic items.
 * For example, consider the following simple Whiley source file:
 * </p>
 *
 * <pre>
 * function id(int x) -> (int y):
 *     return x
 * </pre>
 *
 * <p>
 * This is represented internally using a heap of syntactic items which might
 * look something like this:
 * </p>
 *
 * <pre>
 * [00] DECL_function(#0,#2,#6,#8)
 * [01] ITEM_utf8("id")
 * [02] ITEM_tuple(#3)
 * [03] DECL_variable(#4,#5)
 * [04] ITEM_utf8("x")
 * [05] TYPE_int
 * [06] ITEM_tuple(#7)
 * [07] DECL_variable(#8,#9)
 * [08] ITEM_utf8("y")
 * [09] TYPE_int
 * [10] STMT_block(#11)
 * [11] STMT_return(#12)
 * [12] EXPR_variable(#03)
 * </pre>
 *
 * <p>
 * Each of these syntactic items will additionally be associated with one or
 * more attributes (e.g. encoding line number information, etc).
 * </p>
 *
 * @author David J. Pearce
 *
 */
public class WyilFile extends AbstractCompilationUnit<WyilFile> {

	// =========================================================================
	// Binary Content Type
	// =========================================================================

	public static final Content.Type<WyilFile> ContentType = new Content.Printable<WyilFile>() {

		/**
		 * This method simply parses a whiley file into an abstract syntax tree. It
		 * makes little effort to check whether or not the file is syntactically
		 * correct. In particular, it does not determine the correct type of all
		 * declarations, expressions, etc.
		 *
		 * @param file
		 * @return
		 * @throws IOException
		 */
		@Override
		public WyilFile read(Path.Entry<WyilFile> e, InputStream input) throws IOException {
			WyilFile wf = new WyilFileReader(e).read();
			// new SyntacticHeapPrinter(new PrintWriter(System.out)).print(wf);
			return wf;
		}

		@Override
		public void write(OutputStream output, WyilFile value) throws IOException {
			new WyilFileWriter(output).write(value);
		}

		@Override
		public void print(PrintStream output, WyilFile content) throws IOException {
			new WyilFilePrinter(output).apply(content);
		}

		@Override
		public String toString() {
			return "Content-Type: wyil";
		}

		@Override
		public String getSuffix() {
			return "wyil";
		}
	};

	// DECLARATIONS:
	public static final int DECL_mask = 0b00010000;
	public static final int DECL_unknown = DECL_mask + 0;
	public static final int DECL_module = DECL_mask + 1;
	public static final int DECL_unit = DECL_mask + 2;
	public static final int DECL_import = DECL_mask + 3;
	public static final int DECL_importfrom = DECL_mask + 4;
	public static final int DECL_staticvar = DECL_mask + 5;
	public static final int DECL_type = DECL_mask + 6;
	public static final int DECL_rectype = DECL_mask + 7;
	public static final int DECL_function = DECL_mask + 8;
	public static final int DECL_method = DECL_mask + 9;
	public static final int DECL_property = DECL_mask + 10;
	public static final int DECL_lambda = DECL_mask + 11;
	public static final int DECL_variable = DECL_mask + 12;
	public static final int DECL_variableinitialiser = DECL_mask + 13;
	public static final int DECL_link = DECL_mask + 14;
	public static final int DECL_binding = DECL_mask + 15;
	// MODIFIERS
	public static final int MOD_mask = DECL_mask + 32;
	public static final int MOD_native = MOD_mask + 0;
	public static final int MOD_export = MOD_mask + 1;
	public static final int MOD_final = MOD_mask + 2;
	public static final int MOD_protected = MOD_mask + 3;
	public static final int MOD_private = MOD_mask + 4;
	public static final int MOD_public = MOD_mask + 5;
	// TEMPLATES
	public static final int TEMPLATE_mask = MOD_mask + 8;
	public static final int TEMPLATE_type = TEMPLATE_mask + 0;
	public static final int TEMPLATE_lifetime = TEMPLATE_mask + 1;
	// ATTRIBUTES
	public static final int ATTR_mask = MOD_mask + 16;
	public static final int ATTR_warning = ATTR_mask + 0;
	public static final int ATTR_error = ATTR_mask + 1;
	public static final int ATTR_verificationcondition = ATTR_mask + 2;
	// TYPES:
	public static final int TYPE_mask = MOD_mask + 32;
	public static final int TYPE_unknown = TYPE_mask + 0;
	public static final int TYPE_void = TYPE_mask + 1;
	public static final int TYPE_any = TYPE_mask + 2;
	public static final int TYPE_null = TYPE_mask + 3;
	public static final int TYPE_bool = TYPE_mask + 4;
	public static final int TYPE_int = TYPE_mask + 5;
	public static final int TYPE_nominal = TYPE_mask + 6;
	public static final int TYPE_reference = TYPE_mask + 7;
	public static final int TYPE_staticreference = TYPE_mask + 8;
	public static final int TYPE_array = TYPE_mask + 9;
	public static final int TYPE_record = TYPE_mask + 10;
	public static final int TYPE_field = TYPE_mask + 11;
	public static final int TYPE_function = TYPE_mask + 12;
	public static final int TYPE_method = TYPE_mask + 13;
	public static final int TYPE_property = TYPE_mask + 14;
	public static final int TYPE_invariant = TYPE_mask + 15;
	public static final int TYPE_union = TYPE_mask + 16;
	public static final int TYPE_byte = TYPE_mask + 17;
	public static final int SEMTYPE_reference = TYPE_mask + 18;
	public static final int SEMTYPE_staticreference = TYPE_mask + 19;
	public static final int SEMTYPE_array = TYPE_mask + 20;
	public static final int SEMTYPE_record = TYPE_mask + 21;
	public static final int SEMTYPE_field = TYPE_mask + 22;
	public static final int SEMTYPE_union = TYPE_mask + 23;
	public static final int SEMTYPE_intersection = TYPE_mask + 24;
	public static final int SEMTYPE_difference = TYPE_mask + 25;
	public static final int TYPE_recursive = TYPE_mask + 26;
	public static final int TYPE_variable = TYPE_mask + 27;
	// STATEMENTS:
	public static final int STMT_mask = TYPE_mask + 64;
	public static final int STMT_block = STMT_mask + 0;
	public static final int STMT_namedblock = STMT_mask + 1;
	public static final int STMT_caseblock = STMT_mask + 2;
	public static final int STMT_assert = STMT_mask + 3;
	public static final int STMT_assign = STMT_mask + 4;
	public static final int STMT_assume = STMT_mask + 5;
	public static final int STMT_debug = STMT_mask + 6;
	public static final int STMT_skip = STMT_mask + 7;
	public static final int STMT_break = STMT_mask + 8;
	public static final int STMT_continue = STMT_mask + 9;
	public static final int STMT_dowhile = STMT_mask + 10;
	public static final int STMT_fail = STMT_mask + 11;
	public static final int STMT_for = STMT_mask + 12;
	public static final int STMT_foreach = STMT_mask + 13;
	public static final int STMT_if = STMT_mask + 14;
	public static final int STMT_ifelse = STMT_mask + 15;
	public static final int STMT_return = STMT_mask + 16;
	public static final int STMT_switch = STMT_mask + 17;
	public static final int STMT_while = STMT_mask + 18;
	// EXPRESSIONS:
	public static final int EXPR_mask = STMT_mask + 32;
	public static final int EXPR_variablecopy = EXPR_mask + 0;
	public static final int EXPR_variablemove = EXPR_mask + 1;
	public static final int EXPR_staticvariable = EXPR_mask + 3;
	public static final int EXPR_constant = EXPR_mask + 4;
	public static final int EXPR_cast = EXPR_mask + 5;
	public static final int EXPR_invoke = EXPR_mask + 7;
	public static final int EXPR_indirectinvoke = EXPR_mask + 8;
	// LOGICAL
	public static final int EXPR_logicalnot = EXPR_mask + 9;
	public static final int EXPR_logicaland = EXPR_mask + 10;
	public static final int EXPR_logicalor = EXPR_mask + 11;
	public static final int EXPR_logiaclimplication = EXPR_mask + 12;
	public static final int EXPR_logicaliff = EXPR_mask + 13;
	public static final int EXPR_logicalexistential = EXPR_mask + 14;
	public static final int EXPR_logicaluniversal = EXPR_mask + 15;
	// COMPARATORS
	public static final int EXPR_equal = EXPR_mask + 16;
	public static final int EXPR_notequal = EXPR_mask + 17;
	public static final int EXPR_integerlessthan = EXPR_mask + 18;
	public static final int EXPR_integerlessequal = EXPR_mask + 19;
	public static final int EXPR_integergreaterthan = EXPR_mask + 20;
	public static final int EXPR_integergreaterequal = EXPR_mask + 21;
	public static final int EXPR_is = EXPR_mask + 22;
	// ARITHMETIC
	public static final int EXPR_integernegation = EXPR_mask + 24;
	public static final int EXPR_integeraddition = EXPR_mask + 25;
	public static final int EXPR_integersubtraction = EXPR_mask + 26;
	public static final int EXPR_integermultiplication = EXPR_mask + 27;
	public static final int EXPR_integerdivision = EXPR_mask + 28;
	public static final int EXPR_integerremainder = EXPR_mask + 29;
	// BITWISE
	public static final int EXPR_bitwisenot = EXPR_mask + 32;
	public static final int EXPR_bitwiseand = EXPR_mask + 33;
	public static final int EXPR_bitwiseor = EXPR_mask + 34;
	public static final int EXPR_bitwisexor = EXPR_mask + 35;
	public static final int EXPR_bitwiseshl = EXPR_mask + 36;
	public static final int EXPR_bitwiseshr = EXPR_mask + 37;
	// REFERENCES
	public static final int EXPR_dereference = EXPR_mask + 40;
	public static final int EXPR_new = EXPR_mask + 41;
	public static final int EXPR_staticnew = EXPR_mask + 42;
	public static final int EXPR_lambdaaccess = EXPR_mask + 43;
	// RECORDS
	public static final int EXPR_recordaccess = EXPR_mask + 48;
	public static final int EXPR_recordborrow = EXPR_mask + 49;
	public static final int EXPR_recordupdate = EXPR_mask + 50;
	public static final int EXPR_recordinitialiser = EXPR_mask + 51;
	// ARRAYS
	public static final int EXPR_arrayaccess = EXPR_mask + 56;
	public static final int EXPR_arrayborrow = EXPR_mask + 57;
	public static final int EXPR_arrayupdate = EXPR_mask + 58;
	public static final int EXPR_arraylength = EXPR_mask + 59;
	public static final int EXPR_arraygenerator = EXPR_mask + 60;
	public static final int EXPR_arrayinitialiser = EXPR_mask + 61;
	public static final int EXPR_arrayrange = EXPR_mask + 62;

	// =========================================================================
	// Constructors
	// =========================================================================

	public WyilFile(Path.Entry<WyilFile> entry) {
		super(entry);
	}

	public WyilFile(Entry<WyilFile> entry, int root, SyntacticItem[] items) {
		super(entry);
		// Allocate every item into this heap
		for (int i = 0; i != items.length; ++i) {
			syntacticItems.add(items[i]);
			items[i].allocate(this, i);
		}
		// Set the distinguished root item
		setRootItem(getSyntacticItem(root));
	}

	// =========================================================================
	// Accessors
	// =========================================================================

	public Decl.Module getModule() {
		return (Decl.Module) getRootItem();
	}

	public Decl.Unit getUnit() {
		// The first node is always the declaration root.
		List<Decl.Unit> modules = getSyntacticItems(Decl.Unit.class);
		if (modules.size() != 1) {
			throw new RuntimeException("expecting one module, found " + modules.size());
		}
		return modules.get(0);
	}

	public <S extends Decl.Named> S getDeclaration(Identifier name, Type signature, Class<S> kind) {
		List<S> matches = super.getSyntacticItems(kind);
		for (int i = 0; i != matches.size(); ++i) {
			S match = matches.get(i);
			if (match.getName().equals(name)) {
				if (signature != null && signature.equals(match.getType())) {
					return match;
				} else if (signature == null) {
					return match;
				}
			}
		}
		throw new IllegalArgumentException("unknown declarataion (" + name + "," + signature + ")");
	}

	/**
	 * A qualified name represents a <i>fully-qualified</i> name within a
	 * compilation unit. That is, a full-qualified unit identifier and corresponding
	 * name.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class QualifiedName {
		private final Name unit;
		private final Identifier name;

		public QualifiedName(Tuple<Identifier> path, Identifier name) {
			this(path.toArray(Identifier.class), name);
		}

		public QualifiedName(Identifier[] path, Identifier name) {
			this(new Name(path), name);
		}

		public QualifiedName(Name unit, Identifier name) {
			this.unit = unit;
			this.name = name;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof QualifiedName) {
				QualifiedName n = (QualifiedName) o;
				return unit.equals(n.unit) && name.equals(n.name);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return unit.hashCode() ^ name.hashCode();
		}

		public Name getUnit() {
			return unit;
		}

		public Identifier getName() {
			return name;
		}

		/**
		 * Provide a simple conversion from a qualified name to a generic name
		 *
		 * @return
		 */
		public Name toName() {
			return new Name(ArrayUtils.append(unit.getAll(), name));
		}

		@Override
		public String toString() {
			return unit + "::" + name;
		}
	}

	// ============================================================
	// Declarations
	// ============================================================
	/**
	 * <p>
	 * Represents a declaration within a Whiley source file. This includes <i>import
	 * declarations</i>, <i>function or method declarations</i>, <i>type
	 * declarations</i>, <i>variable declarations</i> and more.
	 * </p>
	 * <p>
	 * In general, a declaration is often a top-level entity within a module.
	 * However, this is not always the case. For example, variable declarations are
	 * used to represent local variables, function or method parameters, etc.
	 * </p>
	 *
	 * @author David J. Pearce
	 *
	 */
	public static interface Decl extends CompilationUnit.Declaration {

		public static class Unknown extends AbstractSyntacticItem implements Decl {
			public Unknown() {
				super(DECL_unknown);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Decl.Unknown();
			}
		}

		/**
		 * A WyilFile contains exactly one active module which represents the root of
		 * all items in the module.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Module extends AbstractSyntacticItem {

			public Module(Name name, Tuple<Decl.Unit> modules, Tuple<Decl.Unit> externs,
					Tuple<SyntacticItem.Marker> attributes) {
				super(DECL_module, name, modules, externs, attributes);
			}

			public Name getName() {
				return (Name) get(0);
			}

			public Tuple<Decl.Unit> getUnits() {
				return (Tuple<Decl.Unit>) get(1);
			}

			public Tuple<Decl.Unit> getExterns() {
				return (Tuple<Decl.Unit>) get(2);
			}

			public Tuple<SyntacticItem.Marker> getAttributes() {
				return (Tuple<SyntacticItem.Marker>) get(3);
			}

			public void putUnit(Decl.Unit unit) {
				Tuple<Decl.Unit> units = getUnits();
				// Check whether replacing unit or adding new
				for (int i = 0; i != units.size(); ++i) {
					Decl.Unit ith = units.get(i);
					if (ith.getName().equals(unit.getName())) {
						// We're replacing an existing unit
						units.setOperand(i, unit);
						return;
					}
				}
				// We're adding a new unit
				setOperand(1, getHeap().allocate(units.append(unit)));
			}

			public void putExtern(Decl.Unit unit) {
				Tuple<Decl.Unit> externs = getExterns();
				// Check whether replacing unit or adding new
				for (int i = 0; i != externs.size(); ++i) {
					Decl.Unit ith = externs.get(i);
					if (ith.getName().equals(unit.getName())) {
						// We're replacing an existing unit
						externs.setOperand(i, unit);
						return;
					}
				}
				// We're adding a new unit
				setOperand(2, getHeap().allocate(externs.append(unit)));
			}

			public void addAttribute(SyntacticItem.Marker attribute) {
				setOperand(3, getHeap().allocate(getAttributes().append(attribute)));
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Module((Name) operands[0], (Tuple<Decl.Unit>) operands[1], (Tuple<Decl.Unit>) operands[2],
						(Tuple<SyntacticItem.Marker>) operands[3]);
			}
		}

		/**
		 * Represents the top-level entity in a Whiley source file. All other
		 * declartions are contained within this.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Unit extends AbstractSyntacticItem implements Decl {

			public Unit(Name name, Tuple<Decl> declarations) {
				super(DECL_unit, name, declarations);
			}

			public Name getName() {
				return (Name) get(0);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Decl> getDeclarations() {
				return (Tuple<Decl>) get(1);
			}

			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Unit((Name) operands[0], (Tuple<Decl>) operands[1]);
			}
		}

		/**
		 * <p>
		 * Represents an import declaration in a Whiley source file. For example, the
		 * following illustrates a simple import statement:
		 * </p>
		 *
		 * <pre>
		 * import println from std::io
		 * </pre>
		 *
		 * <p>
		 * Here, the module is <code>std::io</code> and the symbol imported is
		 * <code>println</code>.
		 * </p>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Import extends AbstractSyntacticItem implements Decl {
			public Import(Tuple<Identifier> path) {
				super(DECL_import, path);
			}

			public Import(Tuple<Identifier> path, Identifier from) {
				super(DECL_importfrom, path, from);
			}

			/**
			 * Get the filter path associated with this import declaration. This is
			 * <code>std::math</code> in <code>import max from std::math</code>.
			 *
			 * @return
			 */
			@SuppressWarnings("unchecked")
			public Tuple<Identifier> getPath() {
				return (Tuple<Identifier>) super.get(0);
			}

			/**
			 * Check whether from name is associated with this import declaration. This
			 * would <code>max</code> in <code>import max from std::math</code>, but is not
			 * present in <code>import std::math</code>.
			 *
			 * @return
			 */
			public boolean hasFrom() {
				return opcode == DECL_importfrom;
			}

			/**
			 * Get the from name associated with this import declaration. This is
			 * <code>max</code> in <code>import max from std::math</code>.
			 *
			 * @return
			 */
			public Identifier getFrom() {
				return (Identifier) super.get(1);
			}

			@SuppressWarnings("unchecked")
			@Override
			public Import clone(SyntacticItem[] operands) {
				if (operands.length == 1) {
					return new Import((Tuple<Identifier>) operands[0]);
				} else {
					return new Import((Tuple<Identifier>) operands[0], (Identifier) operands[1]);
				}
			}

			@Override
			public String toString() {
				String r = "import ";
				if (hasFrom()) {
					r += getFrom();
					r += " from ";
				}
				Tuple<Identifier> path = getPath();
				for (int i = 0; i != path.size(); ++i) {
					if (i != 0) {
						r += ".";
					}
					Identifier component = path.get(i);
					if (component == null) {
						r += "*";
					} else {
						r += component.get();
					}
				}
				return r;
			}
		}

		/**
		 * A named declaration has an additional symbol name associated with it
		 *
		 * @author David J. Pearce
		 *
		 */
		public static abstract class Named<T extends WyilFile.Type> extends AbstractSyntacticItem implements Decl {

			public Named(int opcode, Tuple<Modifier> modifiers, Identifier name, SyntacticItem... rest) {
				super(opcode, ArrayUtils.append(new SyntacticItem[] { modifiers, name }, rest));
			}

			@SuppressWarnings("unchecked")
			public Tuple<Modifier> getModifiers() {
				return (Tuple<Modifier>) super.get(0);
			}

			public Identifier getName() {
				return (Identifier) super.get(1);
			}

			public QualifiedName getQualifiedName() {
				// FIXME: this is completely broken.
				Unit module = getAncestor(Decl.Unit.class);
				return new QualifiedName(module.getName(), getName());
			}

			public Tuple<Template.Variable> getTemplate() {
				throw new UnsupportedOperationException();
			}

			public abstract T getType();
		}

		/**
		 * Represents a <i>function</i>, <i>method</i> or <i>property</i> declaration in
		 * a Whiley source file.
		 */
		public static abstract class Callable extends Named<WyilFile.Type.Callable> {

			public Callable(int opcode, Tuple<Modifier> modifiers, Identifier name, Tuple<Template.Variable> template,
					Tuple<Decl.Variable> parameters, Tuple<Decl.Variable> returns, SyntacticItem... rest) {
				super(opcode, modifiers, name,
						ArrayUtils.append(new SyntacticItem[] { template, parameters, returns }, rest));
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Template.Variable> getTemplate() {
				return (Tuple<Template.Variable>) get(2);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Decl.Variable> getParameters() {
				return (Tuple<Decl.Variable>) get(3);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Decl.Variable> getReturns() {
				return (Tuple<Decl.Variable>) get(4);
			}

			@Override
			public abstract WyilFile.Type.Callable getType();
		}

		/**
		 * <p>
		 * Represents a <i>function</i>, <i>method</i> or <i>property</i> declaration in
		 * a Whiley source file. The following function declaration provides a small
		 * example to illustrate:
		 * </p>
		 *
		 * <pre>
		 * function max(int[] xs) -> (int z)
		 * // array xs cannot be empty
		 * requires |xs| > 0
		 * // return must be greater than all elements in xs
		 * ensures all { i in 0..|xs| | xs[i] <= z }
		 * // return must equal one of the elements in xs
		 * ensures some { i in 0..|xs| | xs[i] == z }
		 *     ...
		 * </pre>
		 *
		 * <p>
		 * Here, we see the specification for the well-known <code>max()</code> function
		 * which returns the largest value of an array. This employs both
		 * <i>requires</i> and <i>ensures</i> clauses:
		 * <ul>
		 * <li><b>Requires clause</b>. This defines a constraint on the permissible
		 * values of the parameters on entry to the function or method, and is often
		 * referred to as the "precondition". This expression may refer to any variables
		 * declared within the parameter type pattern. Multiple clauses may be given,
		 * and these are taken together as a conjunction. Furthermore, the convention is
		 * to specify the requires clause(s) before any ensure(s) clauses.</li>
		 * <li><b>Ensures clause</b>. This defines a constraint on the permissible
		 * values of the the function or method's return value, and is often referred to
		 * as the "postcondition". This expression may refer to any variables declared
		 * within either the parameter or return type pattern. Multiple clauses may be
		 * given, and these are taken together as a conjunction. Furthermore, the
		 * convention is to specify the requires clause(s) after the others.</li>
		 * </ul>
		 * </p>
		 *
		 * @see Callable
		 */
		public static abstract class FunctionOrMethod extends Callable {

			public FunctionOrMethod(int opcode, Tuple<Modifier> modifiers, Identifier name,
					Tuple<Template.Variable> template, Tuple<Decl.Variable> parameters, Tuple<Decl.Variable> returns,
					Tuple<Expr> requires, Tuple<Expr> ensures, Stmt.Block body, SyntacticItem... rest) {
				super(opcode, modifiers, name, template, parameters, returns,
						ArrayUtils.append(new SyntacticItem[] { requires, ensures, body }, rest));
			}

			@SuppressWarnings("unchecked")
			public Tuple<Expr> getRequires() {
				return (Tuple<Expr>) get(5);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Expr> getEnsures() {
				return (Tuple<Expr>) get(6);
			}

			public Stmt.Block getBody() {
				return (Stmt.Block) get(7);
			}
		}

		/**
		 * <p>
		 * Represents a function declaration in a Whiley source file. For example:
		 * </p>
		 *
		 * <pre>
		 * function f(int x) -> (int y)
		 * // Parameter must be positive
		 * requires x > 0
		 * // Return must be negative
		 * ensures y < 0:
		 *    // body
		 *    return -x
		 * </pre>
		 *
		 * <p>
		 * Here, a function <code>f</code> is defined which accepts only positive
		 * integers and returns only negative integers. The variable <code>y</code> is
		 * used to refer to the return value. Functions in Whiley may not have
		 * side-effects (i.e. they are <code>pure functions</code>).
		 * </p>
		 *
		 * <p>
		 * Function declarations may also have modifiers, such as <code>public</code>
		 * and <code>private</code>.
		 * </p>
		 *
		 * @see FunctionOrMethod
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Function extends FunctionOrMethod {

			public Function(Tuple<Modifier> modifiers, Identifier name, Tuple<Template.Variable> template,
					Tuple<Decl.Variable> parameters, Tuple<Decl.Variable> returns, Tuple<Expr> requires,
					Tuple<Expr> ensures, Stmt.Block body) {
				super(DECL_function, modifiers, name, template, parameters, returns, requires, ensures, body);
			}

			@Override
			public WyilFile.Type.Function getType() {
				// FIXME: a better solution would be to have an actual signature
				// object
				Tuple<WyilFile.Type> projectedParameters = getParameters()
						.map((WyilFile.Decl.Variable d) -> d.getType());
				Tuple<WyilFile.Type> projectedReturns = getReturns().map((WyilFile.Decl.Variable d) -> d.getType());
				return new WyilFile.Type.Function(projectedParameters, projectedReturns);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Function clone(SyntacticItem[] operands) {
				return new Function((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Tuple<Decl.Variable>) operands[3],
						(Tuple<Decl.Variable>) operands[4], (Tuple<Expr>) operands[5], (Tuple<Expr>) operands[6],
						(Stmt.Block) operands[7]);
			}

			@Override
			public String toString() {
				return "function " + getName() + " : " + getType();
			}

		}

		/**
		 * <p>
		 * Represents a method declaration in a Whiley source file. For example:
		 * </p>
		 *
		 * <pre>
		 * method m(int x) -> (int y)
		 * // Parameter must be positive
		 * requires x > 0
		 * // Return must be negative
		 * ensures $ < 0:
		 *    // body
		 *    return -x
		 * </pre>
		 *
		 * <p>
		 * Here, a method <code>m</code> is defined which accepts only positive integers
		 * and returns only negative integers. The variable <code>y</code> is used to
		 * refer to the return value. Unlike functions, methods in Whiley may have
		 * side-effects.
		 * </p>
		 *
		 * <p>
		 * Method declarations may also have modifiers, such as <code>public</code> and
		 * <code>private</code>.
		 * </p>
		 *
		 * @see FunctionOrMethod
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Method extends FunctionOrMethod {

			public Method(Tuple<Modifier> modifiers, Identifier name, Tuple<Template.Variable> template,
					Tuple<Decl.Variable> parameters, Tuple<Decl.Variable> returns, Tuple<Expr> requires,
					Tuple<Expr> ensures, Stmt.Block body) {
				super(DECL_method, modifiers, name, template, parameters, returns, requires, ensures, body);
			}

			public Identifier[] getLifetimes() {
				Tuple<Template.Variable> template = getTemplate();
				// Count how many lifetimes
				int count = 0;
				for(int i=0;i!=template.size();++i) {
					if(template.get(i) instanceof Template.Lifetime) {
						count = count + 1;
					}
				}
				//
				Identifier[] lifetimes = new Identifier[count];
				// copy over the lifetimes
				for(int i=0,j=0;i!=template.size();++i) {
					Template.Variable tvar = template.get(i);
					if(tvar instanceof Template.Lifetime) {
						lifetimes[j++] = tvar.getName();
					}
				}
				// Done
				return lifetimes;
			}

			@Override
			public WyilFile.Type.Method getType() {
				Tuple<WyilFile.Type> projectedParameters = getParameters()
						.map((WyilFile.Decl.Variable d) -> d.getType());
				Tuple<WyilFile.Type> projectedReturns = getReturns().map((WyilFile.Decl.Variable d) -> d.getType());
				// FIXME: This just feels wrong as we are throwing away other template
				// variables. The issue is that callable types do not declare template variables
				// as they are compiled away.
				Tuple<Identifier> lifetimes = new Tuple<>(getLifetimes());
				return new WyilFile.Type.Method(projectedParameters, projectedReturns, new Tuple<>(), lifetimes);
			}

			@SuppressWarnings("unchecked")
			@Override
			public Method clone(SyntacticItem[] operands) {
				return new Method((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Tuple<Decl.Variable>) operands[3],
						(Tuple<Decl.Variable>) operands[4], (Tuple<Expr>) operands[5], (Tuple<Expr>) operands[6],
						(Stmt.Block) operands[7]);
			}

			@Override
			public String toString() {
				return "method " + getName() + " : " + getType();
			}
		}

		/**
		 * <p>
		 * Represents a property declaration in a Whiley source file. For example:
		 * </p>
		 *
		 * <pre>
		 * property contains(int[] xs, int x)
		 * where some { i in 0..|xs| | xs[i] == x}
		 * </pre>
		 *
		 * <p>
		 * Here, a property <code>contains</code> is defined which captures the concept
		 * of an element being contained in an array.
		 * </p>
		 *
		 * <p>
		 * Property declarations may also have modifiers, such as <code>public</code>
		 * and <code>private</code>.
		 * </p>
		 *
		 * @See FunctionOrMethod
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Property extends Callable {

			public Property(Tuple<Modifier> modifiers, Identifier name, Tuple<Template.Variable> template,
					Tuple<Decl.Variable> parameters, Tuple<Expr> invariant) {
				super(DECL_property, modifiers, name, template, parameters, new Tuple<Decl.Variable>(), invariant);
			}

			public Property(Tuple<Modifier> modifiers, Identifier name, Tuple<Template.Variable> template,
					Tuple<Decl.Variable> parameters, Tuple<Decl.Variable> returns, Tuple<Expr> invariant) {
				super(DECL_property, modifiers, name, template, parameters, returns, invariant);
			}

			@Override
			public WyilFile.Type.Property getType() {
				// FIXME: a better solution would be to have an actual signature
				// object
				Tuple<WyilFile.Type> projectedParameters = getParameters()
						.map((WyilFile.Decl.Variable d) -> d.getType());
				Tuple<WyilFile.Type> projectedReturns = new Tuple<>(WyilFile.Type.Bool);
				return new WyilFile.Type.Property(projectedParameters, projectedReturns);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Expr> getInvariant() {
				return (Tuple<Expr>) get(5);
			}

			@SuppressWarnings("unchecked")
			@Override
			public Property clone(SyntacticItem[] operands) {
				return new Property((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Tuple<Decl.Variable>) operands[3],
						(Tuple<Decl.Variable>) operands[4], (Tuple<Expr>) operands[5]);
			}
		}

		/**
		 * <p>
		 * Represents a lambda declaration within a Whiley source file. Sometimes also
		 * known as closures, these are anonymous function or method declarations
		 * declared within an expression. The following illustrates:
		 * </p>
		 *
		 * <pre>
		 * type func is function(int)->int
		*
		* function g() -> func:
		*    return &(int x -> x + 1)
		 * </pre>
		 * <p>
		 * This defines a lambda which accepts one parameter <code>x</code> and returns
		 * its increment.
		 * </p>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Lambda extends Callable implements Expr {

			public Lambda(Tuple<Modifier> modifiers, Identifier name, Tuple<Decl.Variable> parameters,
					Tuple<Identifier> captures, Tuple<Identifier> lifetimes, Expr body,
					WyilFile.Type.Callable signature) {
				this(modifiers, name, parameters, new Tuple<Decl.Variable>(), captures, lifetimes, body, signature);
			}

			public Lambda(Tuple<Modifier> modifiers, Identifier name, Tuple<Decl.Variable> parameters,
					Tuple<Decl.Variable> returns, Tuple<Identifier> captures, Tuple<Identifier> lifetimes, Expr body,
					WyilFile.Type.Callable signature) {
				this(modifiers, name, new Tuple<>(), parameters, returns, captures, lifetimes, body, signature);
			}

			public Lambda(Tuple<Modifier> modifiers, Identifier name, Tuple<Template.Variable> template,
					Tuple<Decl.Variable> parameters, Tuple<Decl.Variable> returns, Tuple<Identifier> captures,
					Tuple<Identifier> lifetimes, Expr body, WyilFile.Type.Callable signature) {
				super(DECL_lambda, modifiers, name, template, parameters, returns, captures, lifetimes, body,
						signature);
			}

			public Set<Decl.Variable> getCapturedVariables() {
				HashSet<Decl.Variable> captured = new HashSet<>();
				usedVariableExtractor.visitExpression(getBody(), captured);
				Tuple<Decl.Variable> parameters = getParameters();
				for (int i = 0; i != parameters.size(); ++i) {
					captured.remove(parameters.get(i));
				}
				return captured;
			}

			@SuppressWarnings("unchecked")
			public Tuple<Identifier> getCapturedLifetimes() {
				return (Tuple<Identifier>) get(5);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Identifier> getLifetimes() {
				return (Tuple<Identifier>) get(6);
			}

			public Expr getBody() {
				return (Expr) get(7);
			}

			@Override
			public WyilFile.Type.Callable getType() {
				return (WyilFile.Type.Callable) super.get(8);
			}

			@Override
			public void setType(WyilFile.Type type) {
				if (type instanceof WyilFile.Type.Callable) {
					operands[8] = type;
				} else {
					throw new IllegalArgumentException();
				}
			}

			@Override
			public Tuple<WyilFile.Type> getTypes() {
				return null;
			}

			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Lambda((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Tuple<Decl.Variable>) operands[3],
						(Tuple<Decl.Variable>) operands[4], (Tuple<Identifier>) operands[5],
						(Tuple<Identifier>) operands[6], (Expr) operands[7], (WyilFile.Type.Callable) operands[8]);
			}
		}

		/**
		 * <p>
		 * Represents a type declaration in a Whiley source file. A simple example to
		 * illustrate is:
		 * </p>
		 *
		 * <pre>
		 * type nat is (int x) where x >= 0
		 * </pre>
		 *
		 * <p>
		 * This defines a <i>constrained type</i> called <code>nat</code> which
		 * represents the set of natural numbers (i.e the non-negative integers). The
		 * "where" clause is optional and is often referred to as the type's
		 * "constraint".
		 * </p>
		 *
		 * <p>
		 * Type declarations may also have modifiers, such as <code>public</code> and
		 * <code>private</code>.
		 * </p>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Type extends Named<WyilFile.Type> {

			public Type(Tuple<Modifier> modifiers, Identifier name, Tuple<Template.Variable> template,
					Decl.Variable vardecl, Tuple<Expr> invariant) {
				super(DECL_type, modifiers, name, template, vardecl, invariant);
			}

			@Override
			public Tuple<Template.Variable> getTemplate() {
				return (Tuple<Template.Variable>) get(2);
			}

			public Decl.Variable getVariableDeclaration() {
				return (Decl.Variable) get(3);
			}

			public boolean isRecursive() {
				return opcode == DECL_rectype;
			}

			public void setRecursive() {
				this.opcode = DECL_rectype;
			}

			@SuppressWarnings("unchecked")
			public Tuple<Expr> getInvariant() {
				return (Tuple<Expr>) get(4);
			}

			@Override
			public WyilFile.Type getType() {
				return getVariableDeclaration().getType();
			}

			@SuppressWarnings("unchecked")
			@Override
			public Decl.Type clone(SyntacticItem[] operands) {
				return new Decl.Type((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Decl.Variable) operands[3], (Tuple<Expr>) operands[4]);
			}
		}

		// ============================================================
		// Variable Declaration
		// ============================================================

		/**
		 * <p>
		 * Represents a variable declaration which has an optional expression assignment
		 * referred to as an <i>initialiser</i>. If an initialiser is given, then this
		 * will be evaluated and assigned to the variable when the declaration is
		 * executed. Some example declarations:
		 * </p>
		 *
		 * <pre>
		 * int x
		 * int y = 1
		 * int z = x + y
		 * </pre>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Variable extends Named<WyilFile.Type> implements Stmt {
			public Variable(Tuple<Modifier> modifiers, Identifier name, WyilFile.Type type) {
				super(DECL_variable, modifiers, name, type);
			}

			public Variable(Tuple<Modifier> modifiers, Identifier name, WyilFile.Type type, Expr initialiser) {
				super(DECL_variableinitialiser, modifiers, name, type, initialiser);
			}

			protected Variable(int opcode, Tuple<Modifier> modifiers, Identifier name, WyilFile.Type type,
					Expr initialiser) {
				super(opcode, modifiers, name, type, initialiser);
			}

			public boolean hasInitialiser() {
				return getOpcode() == DECL_variableinitialiser;
			}

			@Override
			public WyilFile.Type getType() {
				return (WyilFile.Type) get(2);
			}

			public Expr getInitialiser() {
				return (Expr) get(3);
			}

			@SuppressWarnings("unchecked")
			@Override
			public Decl.Variable clone(SyntacticItem[] operands) {
				if (operands.length == 3) {
					return new Decl.Variable((Tuple<Modifier>) operands[0], (Identifier) operands[1],
							(WyilFile.Type) operands[2]);
				} else {
					return new Decl.Variable((Tuple<Modifier>) operands[0], (Identifier) operands[1],
							(WyilFile.Type) operands[2], (Expr) operands[3]);
				}
			}

			@Override
			public String toString() {
				String r = getType().toString();
				r += " " + getName().toString();
				if (hasInitialiser()) {
					r += " = " + getInitialiser().toString();
				}
				return r;
			}
		}

		/**
		 * Represents a constant declaration in a Whiley source file, which has the
		 * form:
		 *
		 * <pre>
		 * ConstantDeclaration ::= "constant" Identifier "is" Expression
		 * </pre>
		 *
		 * A simple example to illustrate is:
		 *
		 * <pre>
		 * constant PI is 3.141592654
		 * </pre>
		 *
		 * Here, we are defining a constant called <code>PI</code> which represents the
		 * decimal value "3.141592654". Constant declarations may also have modifiers,
		 * such as <code>public</code> and <code>private</code>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public class StaticVariable extends Variable {
			public StaticVariable(Tuple<Modifier> modifiers, Identifier name, WyilFile.Type type, Expr initialiser) {
				super(DECL_staticvar, modifiers, name, type, initialiser);
			}

			@Override
			public boolean hasInitialiser() {
				return true;
			}

			@SuppressWarnings("unchecked")
			@Override
			public StaticVariable clone(SyntacticItem[] operands) {
				return new StaticVariable((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(WyilFile.Type) operands[2], (Expr) operands[3]);
			}
		}

		/**
		 * Represents a link to a given syntactic item which is "resolvable". That is,
		 * it is given as a name which is subsequently resolved during some compilation
		 * stage.
		 *
		 * @author David J. Pearce
		 *
		 * @param <T>
		 */
		public static class Link<T extends SyntacticItem> extends AbstractSyntacticItem {
			public Link(Name name) {
				super(DECL_link,name);
			}

			private Link(int opcode, SyntacticItem... operands) {
				super(opcode, operands);
			}

			public boolean isResolved() {
				return operands.length == 2;
			}

			public boolean isPartiallyResolved() {
				return operands.length >= 2;
			}

			public Name getName() {
				return (Name) operands[0];
			}

			public T getTarget() {
				if(isResolved()) {
					return ((Ref<T>) operands[1]).get();
				} else {
					throw new IllegalArgumentException("link unresolved");
				}
			}

			public List<T> getCandidates() {
				ArrayList<T> candidates = new ArrayList<>();
				for (int i = 1; i != operands.length; ++i) {
					Ref<T> candidate = (Ref<T>) operands[i];
					candidates.add(candidate.get());
				}
				return candidates;
			}

			@SuppressWarnings("unchecked")
			public void resolve(T... items) {
				SyntacticHeap heap = getHeap();
				SyntacticItem first = operands[0];
				this.operands = Arrays.copyOf(operands, items.length + 1);
				this.operands[0] = first;
				for(int i=1;i!=operands.length;++i) {
					operands[i] = heap.allocate(new Ref<>(items[i - 1]));
				}
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Link<T>(DECL_link, operands);
			}
		}

		/**
		 * Represents a binding between a Linkable item and the corresponding
		 * declaration.
		 *
		 * @author David J. Pearce
		 *
		 * @param <T>
		 */
		public static class Binding<S extends WyilFile.Type, T extends Decl.Named<S>> extends AbstractSyntacticItem {
			private S concreteType;

			public Binding(Link<T> link, Tuple<? extends SyntacticItem> arguments) {
				super(DECL_binding, link, arguments);
			}

			@SuppressWarnings("unchecked")
			public Link<T> getLink() {
				return (Link<T>) get(0);
			}

			public S getConcreteType() {
				if(concreteType == null) {
					T decl = getLink().getTarget();
					S type = decl.getType();
					// Substitute type parameters & lifetimes
					if(type instanceof WyilFile.Type.Callable) {
						concreteType = (S) WyilFile.substitute((WyilFile.Type.Callable) type, decl.getTemplate(),
								getArguments());
					} else {
						concreteType = (S) type.substitute(bindingFunction(decl.getTemplate(), getArguments()));
					}
				}
				return concreteType;
			}

			public T getDeclaration() {
				return getLink().getTarget();
			}

			/**
			 * Get the provided lifetime arguments.
			 *
			 * @return
			 */
			public Tuple<SyntacticItem> getArguments() {
				return (Tuple<SyntacticItem>) get(1);
			}

			public void setArguments(Tuple<SyntacticItem> arguments) {
				operands[1] = arguments;
				concreteType = null;
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Binding((Link) operands[0], (Tuple<SyntacticItem>) operands[1]);
			}

			@Override
			public String toString() {
				String arguments = getArguments().toBareString();
				return "<" + arguments + ">";
			}
		}
	}
	// ============================================================
	// Template
	// ============================================================
	public interface Template {
		public static abstract class Variable extends AbstractSyntacticItem {
			public Variable(int opcode, Identifier name) {
				super(opcode,name);
			}
			public Identifier getName() {
				return (Identifier) get(0);
			}
		}
		public static class Type extends Variable {
			public Type(Identifier name) {
				super(TEMPLATE_type,name);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Type((Identifier) operands[0]);
			}

			@Override
			public String toString() {
				return getName().get();
			}
		}

		public static class Lifetime extends Variable {
			public Lifetime(Identifier name) {
				super(TEMPLATE_lifetime,name);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Lifetime((Identifier) operands[0]);
			}

			@Override
			public String toString() {
				return "&" + getName().get();
			}
		}
	}
	// ============================================================
	// Stmt
	// ============================================================

	/**
	 * Provides classes for representing statements in Whiley's source language.
	 * Examples include <i>assignments</i>, <i>for-loops</i>, <i>conditions</i>,
	 * etc. Each class is an instance of <code>SyntacticItem</code> and, hence, can
	 * be adorned with certain information (such as source location, etc).
	 *
	 * @author David J. Pearce
	 *
	 */
	public interface Stmt extends SyntacticItem {

		public interface Loop extends Stmt {
			Expr getCondition();

			Tuple<Expr> getInvariant();

			Tuple<Decl.Variable> getModified();

			Stmt getBody();
		}

		/**
		 * <p>
		 * A statement block represents a sequence of zero or more consecutive
		 * statements at the same indentation level. The following illustrates:
		 * </p>
		 *
		 * <pre>
		 * function abs(int x) -> (int r):
		 * // ---------------------------+
		 *    if x > 0:               // |
		 *       // ----------------+    |
		 *       return x        // |    |
		 *       // ----------------+    |
		 *    else:                   // |
		 *       // ----------------+    |
		 *       return -x       // |    |
		 *       // ----------------+    |
		 * // ---------------------------+
		 * </pre>
		 * <p>
		 * This example contains three statement blocks. The outermost block defines the
		 * body of the function and contains exactly one statement (i.e. the
		 * <code>if</code> statement). Two inner blocks are used to represent the true
		 * and false branches of the conditional.
		 * </p>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Block extends AbstractSyntacticItem implements Stmt {
			public Block(Stmt... stmts) {
				super(STMT_block, stmts);
			}

			@Override
			public Stmt get(int i) {
				return (Stmt) super.get(i);
			}

			@Override
			public Block clone(SyntacticItem[] operands) {
				return new Block(ArrayUtils.toArray(Stmt.class, operands));
			}
		}

		/**
		 * Represents a named block, which has the form:
		 *
		 * <pre>
		 * NamedBlcok ::= LifetimeIdentifier ':' NewLine Block
		 * </pre>
		 *
		 * As an example:
		 *
		 * <pre>
		 * function sum():
		 *   &this:int x = new:this x
		 *   myblock:
		 *     &myblock:int y = new:myblock y
		 * </pre>
		 */
		public static class NamedBlock extends AbstractSyntacticItem implements Stmt {
			public NamedBlock(Identifier name, Stmt.Block block) {
				super(STMT_namedblock, name, block);
			}

			public Identifier getName() {
				return (Identifier) super.get(0);
			}

			public Block getBlock() {
				return (Block) super.get(1);
			}

			@Override
			public NamedBlock clone(SyntacticItem[] operands) {
				return new NamedBlock((Identifier) operands[0], (Block) operands[1]);
			}
		}

		/**
		 * <p>
		 * Represents a assert statement of the form "<code>assert e</code>", where
		 * <code>e</code> is a boolean expression. The following illustrates:
		 * </p>
		 *
		 * <pre>
		 * function abs(int x) -> int:
		 *     if x < 0:
		 *         x = -x
		 *     assert x >= 0
		 *     return x
		 * </pre>
		 *
		 * <p>
		 * Assertions are either statically checked by the verifier, or turned into
		 * runtime checks.
		 * </p>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Assert extends AbstractSyntacticItem implements Stmt {
			public Assert(Expr condition) {
				super(STMT_assert, condition);
			}

			public Expr getCondition() {
				return (Expr) super.get(0);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Assert((Expr) operands[0]);
			}
		}

		/**
		 * <p>
		 * Represents an assignment statement of the form "<code>lhs = rhs</code>".
		 * Here, the <code>rhs</code> is any expression, whilst the <code>lhs</code>
		 * must be an <code>LVal</code> --- that is, an expression permitted on the
		 * left-side of an assignment. The following illustrates different possible
		 * assignment statements:
		 * </p>
		 *
		 * <pre>
		 * x = y       // variable assignment
		 * x.f = y     // field assignment
		 * x[i] = y    // list assignment
		 * x[i].f = y  // compound assignment
		 * </pre>
		 *
		 * <p>
		 * The last assignment here illustrates that the left-hand side of an assignment
		 * can be arbitrarily complex, involving nested assignments into lists and
		 * records.
		 * </p>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Assign extends AbstractSyntacticItem implements Stmt {
			public Assign(Tuple<LVal> lvals, Tuple<Expr> rvals) {
				super(STMT_assign, lvals, rvals);
			}

			@SuppressWarnings("unchecked")
			public Tuple<LVal> getLeftHandSide() {
				return (Tuple<LVal>) super.get(0);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Expr> getRightHandSide() {
				return (Tuple<Expr>) super.get(1);
			}

			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Assign((Tuple<LVal>) operands[0], (Tuple<Expr>) operands[1]);
			}
		}

		/**
		 * <p>
		 * Represents an assume statement of the form "<code>assume e</code>", where
		 * <code>e</code> is a boolean expression. The following illustrates:
		 * </p>
		 *
		 * <pre>
		 * function abs(int x) -> int:
		 *     if x < 0:
		 *         x = -x
		 *     assume x >= 0
		 *     return x
		 * </pre>
		 *
		 * <p>
		 * Assumptions are assumed by the verifier and, since this may be unsound,
		 * always turned into runtime checks.
		 * </p>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Assume extends AbstractSyntacticItem implements Stmt {
			public Assume(Expr condition) {
				super(STMT_assume, condition);
			}

			public Expr getCondition() {
				return (Expr) super.get(0);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Assume((Expr) operands[0]);
			}
		}

		/**
		 * Represents a debug statement of the form "<code>debug e</code>" where
		 * <code>e</code> is a string expression. Debug statements are effectively print
		 * statements in debug mode, and no-operations otherwise.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Debug extends AbstractSyntacticItem implements Stmt {
			public Debug(Expr condition) {
				super(STMT_debug, condition);
			}

			public Expr getOperand() {
				return (Expr) super.get(0);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Debug((Expr) operands[0]);
			}
		}

		/**
		 * Represents a classical skip statement of the form "<code>skip</code>". A skip
		 * statement is simply a no-operation.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Skip extends AbstractSyntacticItem implements Stmt {
			public Skip() {
				super(STMT_skip);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Skip();
			}
		}

		/**
		 * Represents a classical break statement of the form "<code>break</code>" which
		 * can be used to force the termination of a loop or switch statement.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Break extends AbstractSyntacticItem implements Stmt {
			public Break() {
				super(STMT_break);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Break();
			}
		}

		/**
		 * Represents a classical continue statement of the form "<code>continue</code>"
		 * which can be used to proceed to the next iteration of a loop or the next case
		 * of a switch statement.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Continue extends AbstractSyntacticItem implements Stmt {
			public Continue() {
				super(STMT_continue);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Continue();
			}
		}

		/**
		 * <p>
		 * Represents a do-while statement whose body is made up from a block of
		 * statements separated by indentation. As an example:
		 * </p>
		 *
		 * <pre>
		 * function sum([int] xs) -> int
		 * requires |xs| > 0:
		 *   int r = 0
		 *   int i = 0
		 *   do:
		 *     r = r + xs[i]
		 *     i = i + 1
		 *   while i < |xs| where i >= 0
		 *   return r
		 * </pre>
		 *
		 * <p>
		 * The <code>where</code> clause is optional, and commonly referred to as the
		 * <i>loop invariant</i>. When multiple clauses are given, these are combined
		 * using a conjunction. The combined invariant defines a condition which must be
		 * true on every iteration of the loop.
		 * </p>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class DoWhile extends AbstractSyntacticItem implements Loop {
			public DoWhile(Expr condition, Tuple<Expr> invariant, Tuple<Decl.Variable> modified, Stmt.Block body) {
				super(STMT_dowhile, condition, invariant, modified, body);
			}

			@Override
			public Expr getCondition() {
				return (Expr) super.get(0);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Expr> getInvariant() {
				return (Tuple<Expr>) super.get(1);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Decl.Variable> getModified() {
				return (Tuple<Decl.Variable>) super.get(2);
			}

			public void setModified(Tuple<Decl.Variable> modified) {
				operands[2] = modified;
			}

			@Override
			public Stmt.Block getBody() {
				return (Stmt.Block) super.get(3);
			}

			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new DoWhile((Expr) operands[0], (Tuple<Expr>) operands[1], (Tuple<Decl.Variable>) operands[2],
						(Stmt.Block) operands[3]);
			}
		}

		/**
		 * Represents a fail statement for the form "<code>fail</code>". This causes an
		 * abrupt termination of the program and should represent dead-code if present.
		 */
		public static class Fail extends AbstractSyntacticItem implements Stmt {
			public Fail() {
				super(STMT_fail);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Fail();
			}
		}

		/**
		 * <p>
		 * Represents a classical if-else statement consisting of a <i>condition</i>, a
		 * <i>true branch</i> and an optional <i>false branch</i>. The following
		 * illustrates:
		 * </p>
		 *
		 * <pre>
		 * function max(int x, int y) -> int:
		 *   if(x > y):
		 *     return x
		 *   else if(x == y):
		 *   	return 0
		 *   else:
		 *     return y
		 * </pre>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IfElse extends AbstractSyntacticItem implements Stmt {
			public IfElse(Expr condition, Stmt.Block trueBranch) {
				super(STMT_if, condition, trueBranch);
			}

			public IfElse(Expr condition, Stmt.Block trueBranch, Stmt.Block falseBranch) {
				super(STMT_ifelse, condition, trueBranch, falseBranch);
			}

			public boolean hasFalseBranch() {
				return getOpcode() == STMT_ifelse;
			}

			public Expr getCondition() {
				return (Expr) super.get(0);
			}

			public Stmt.Block getTrueBranch() {
				return (Stmt.Block) super.get(1);
			}

			public Stmt.Block getFalseBranch() {
				return (Stmt.Block) super.get(2);
			}

			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				if (operands.length == 2) {
					return new IfElse((Expr) operands[0], (Stmt.Block) operands[1]);
				} else {
					return new IfElse((Expr) operands[0], (Stmt.Block) operands[1], (Stmt.Block) operands[2]);
				}
			}
		}

		/**
		 * <p>
		 * Represents a return statement which has one or more optional return
		 * expressions referred to simply as the "returns". Note that, the returned
		 * expression (if there is one) must begin on the same line as the return
		 * statement itself. The following illustrates:
		 * </p>
		 *
		 * <pre>
		 * function f(int x) -> int:
		 * 	  return x + 1
		 * </pre>
		 *
		 * <p>
		 * Here, we see a simple <code>return</code> statement which returns an
		 * <code>int</code> value.
		 * </p>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Return extends AbstractSyntacticItem implements Stmt {
			public Return(Tuple<Expr> returns) {
				super(STMT_return, returns);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Expr> getReturns() {
				return (Tuple<Expr>) super.get(0);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Return((Tuple<Expr>) operands[0]);
			}
		}

		/**
		 * <p>
		 * Represents a classical switch statement made of up a condition and one or
		 * more case blocks. Each case consists of zero or more constant expressions.
		 * The following illustrates:
		 * </p>
		 *
		 * <pre>
		 * switch x:
		 *   case 1:
		 *     y = -1
		 *   case 2:
		 *     y = -2
		 *   default:
		 *     y = 0
		 * </pre>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Switch extends AbstractSyntacticItem implements Stmt {
			public Switch(Expr condition, Tuple<Case> cases) {
				super(STMT_switch, condition, cases);
			}

			public Expr getCondition() {
				return (Expr) get(0);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Case> getCases() {
				return (Tuple<Case>) get(1);
			}

			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Switch((Expr) operands[0], (Tuple<Case>) operands[1]);
			}
		}

		public static class Case extends AbstractSyntacticItem {

			public Case(Tuple<Expr> conditions, Stmt.Block block) {
				super(STMT_caseblock, conditions, block);
			}

			public boolean isDefault() {
				return getConditions().size() == 0;
			}

			@SuppressWarnings("unchecked")
			public Tuple<Expr> getConditions() {
				return (Tuple<Expr>) get(0);
			}

			public Stmt.Block getBlock() {
				return (Stmt.Block) get(1);
			}

			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Case((Tuple<Expr>) operands[0], (Stmt.Block) operands[1]);
			}
		}

		/**
		 * <p>
		 * Represents a while statement made up a condition and a block of statements
		 * referred to as the <i>body</i>. The following illustrates:
		 * </p>
		 *
		 * <pre>
		 * function sum([int] xs) -> int:
		 *   int r = 0
		 *   int i = 0
		 *   while i < |xs| where i >= 0:
		 *     r = r + xs[i]
		 *     i = i + 1
		 *   return r
		 * </pre>
		 *
		 * <p>
		 * The optional <code>where</code> clause(s) are commonly referred to as the
		 * "loop invariant". When multiple clauses are given, these are combined using a
		 * conjunction. The combined invariant defines a condition which must be true on
		 * every iteration of the loop.
		 * </p>
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class While extends AbstractSyntacticItem implements Loop {
			public While(Expr condition, Tuple<Expr> invariant, Tuple<Decl.Variable> modified, Stmt.Block body) {
				super(STMT_while, condition, invariant, modified, body);
			}

			@Override
			public Expr getCondition() {
				return (Expr) super.get(0);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Expr> getInvariant() {
				return (Tuple<Expr>) super.get(1);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Decl.Variable> getModified() {
				return (Tuple<Decl.Variable>) super.get(2);
			}

			public void setModified(Tuple<Decl.Variable> modified) {
				operands[2] = modified;
			}

			@Override
			public Stmt.Block getBody() {
				return (Stmt.Block) super.get(3);
			}

			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new While((Expr) operands[0], (Tuple<Expr>) operands[1], (Tuple<Decl.Variable>) operands[2],
						(Stmt.Block) operands[3]);
			}
		}
	}

	/**
	 * <p>
	 * Represents an arbitrary expression permissible on the left-hand side of an
	 * assignment statement. For example, consider the following method:
	 * </p>
	 *
	 * <pre>
	 * method f(int[] xs, int x, int y):
	 *   x = y + 1
	 *   xs[i] = x
	 * </pre>
	 * <p>
	 * This contains two assignment statements with the lval's <code>x</code> and
	 * <code>xs[i]</code> respectively. The set of lvals is a subset of the set of
	 * all expressions, since not every expression can be assigned. For example, an
	 * assignment "<code>f() = x</code>" does not make sense.
	 * </p>
	 *
	 * @author David J. Pearce
	 *
	 */
	public interface LVal extends Expr {

	}

	/**
	 * Represents an arbitrary expression within a Whiley source file. Every
	 * expression has a known type and zero or more expression operands alongside
	 * other syntactic information.
	 *
	 * @author David J. Pearce
	 *
	 */
	public interface Expr extends Stmt {

		/**
		 * Get the type to which this expression is guaranteed to evaluate. That is, the
		 * result type of this expression.
		 *
		 * @return
		 */
		public Type getType();

		/**
		 * Get the set of types which this expression returns. This makes sense only in
		 * the case of an expression which can return multiple types (e.g. an
		 * invocation). In all situations where it doesn't make sense, then
		 * <code>null</code> is returned.
		 *
		 * @return
		 */
		public Tuple<Type> getTypes();

		/**
		 * Set the inferred return type for this expression. Observe that some
		 * expressions do not support this operation.
		 *
		 * @param type
		 */
		public void setType(Type type);

		// =========================================================================
		// General Expressions
		// =========================================================================

		/**
		 * Represents an abstract operator expression over exactly one <i>operand
		 * expression</i>. For example, <code>!x</code> is a unary operator expression.
		 *
		 * @author David J. Pearce
		 *
		 */
		public interface UnaryOperator extends Expr {
			public Expr getOperand();
		}

		/**
		 * Represents an abstract operator expression over exactly two <i>operand
		 * expressions</i>. For example, <code>x &lt;&lt; 1</code> is a binary operator
		 * expression.
		 *
		 * @author David J. Pearce
		 *
		 */
		public interface BinaryOperator extends Expr {
			public Expr getFirstOperand();

			public Expr getSecondOperand();
		}

		/**
		 * Represents an abstract operator expression over exactly three <i>operand
		 * expressions</i>. For example, <code>xs[i:=1]</code> is a ternary operator
		 * expression.
		 *
		 * @author David J. Pearce
		 *
		 */
		public interface TernaryOperator extends Expr {
			public Expr getFirstOperand();

			public Expr getSecondOperand();

			public Expr getThirdOperand();
		}

		/**
		 * Represents an abstract operator expression over one or more <i>operand
		 * expressions</i>. For example. in <code>arr[i+1]</code> the expression
		 * <code>i+1</code> would be an nary operator expression.
		 *
		 * @author David J. Pearce
		 *
		 */
		public interface NaryOperator extends Expr {
			public Tuple<Expr> getOperands();
		}

		public abstract static class AbstractExpr extends AbstractSyntacticItem implements Expr {
			public AbstractExpr(int opcode, Type type, SyntacticItem... items) {
				super(opcode, ArrayUtils.append(type, items));
			}

			@Override
			public Type getType() {
				return (Type) super.get(0);
			}

			@Override
			public void setType(Type type) {
				operands[0] = type;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}
		}

		/**
		 * Represents a cast expression of the form "<code>(T) e</code>" where
		 * <code>T</code> is the <i>cast type</i> and <code>e</code> the <i>casted
		 * expression</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Cast extends AbstractExpr implements Expr, UnaryOperator {
			public Cast(Type type, Expr rhs) {
				super(EXPR_cast, type, rhs);
			}

			@Override
			public Expr getOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Cast clone(SyntacticItem[] operands) {
				return new Cast((Type) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return "(" + getType() + ") " + getOperand();
			}
		}

		/**
		 * Represents the use of a constant within some expression. For example, in
		 * <code>x + 1</code> the expression <code>1</code> is a constant expression.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Constant extends AbstractExpr implements Expr {
			public Constant(Type type, Value value) {
				super(EXPR_constant, type, value);
			}

			public Value getValue() {
				return (Value) get(1);
			}

			@Override
			public Constant clone(SyntacticItem[] operands) {
				return new Constant((Type) operands[0], (Value) operands[1]);
			}

			@Override
			public String toString() {
				return getValue().toString();
			}
		}

		/**
		 * Represents the use of a static variable within an expression. A static
		 * variable is effectively a global variable which may or may not be defined
		 * within the enclosing module.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class StaticVariableAccess extends AbstractExpr implements LVal, Expr, Linkable {
			public StaticVariableAccess(Type type, Decl.Link<Decl.StaticVariable> name) {
				super(EXPR_staticvariable, type, name);
			}

			@Override
			public Decl.Link<Decl.StaticVariable> getLink() {
				return (Decl.Link<Decl.StaticVariable>) get(1);
			}

			@Override
			public StaticVariableAccess clone(SyntacticItem[] operands) {
				return new StaticVariableAccess((Type) operands[0], (Decl.Link<Decl.StaticVariable>) operands[1]);
			}

			@Override
			public String toString() {
				return getLink().toString();
			}
		}

		/**
		 * Represents a <i>type test expression</i> of the form "<code>e is T</code>"
		 * where <code>e</code> is the <i>test expression</i> and <code>T</code> is the
		 * <i>test type</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Is extends AbstractSyntacticItem implements Expr, UnaryOperator {
			public Is(Expr lhs, Type rhs) {
				super(EXPR_is, lhs, rhs);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public Expr getOperand() {
				return (Expr) get(0);
			}

			public Type getTestType() {
				return (Type) get(1);
			}

			@Override
			public Is clone(SyntacticItem[] operands) {
				return new Is((Expr) operands[0], (Type) operands[1]);
			}

			@Override
			public String toString() {
				return getOperand() + " is " + getTestType();
			}
		}

		/**
		 * Represents an invocation of the form "<code>x.y.f(e1,..en)</code>". Here,
		 * <code>x.y.f</code> constitute a <i>partially-</i> or <i>fully-qualified
		 * name</i> and <code>e1</code> ... <code>en</code> are the <i>argument
		 * expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Invoke extends AbstractSyntacticItem implements Expr, NaryOperator, Bindable {

			public Invoke(Decl.Binding<Type.Callable, Decl.Callable> binding, Tuple<Expr> arguments) {
				super(EXPR_invoke, binding, arguments);
			}

			@Override
			public Type getType() {
				Tuple<Type> returns = getBinding().getConcreteType().getReturns();
				// NOTE: if this method is called then it is assumed to be in a position which
				// requires exactly one return type. Anything else is an error which should have
				// been caught earlier in the pipeline.
				if (returns.size() != 1) {
					throw new IllegalArgumentException("invalid number of returns (" + returns.size() + ")");
				} else {
					return returns.get(0);
				}
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Tuple<Type> getTypes() {
				Tuple<Type> types = getLink().getTarget().getType().getReturns();
				if (types.size() > 1) {
					return types;
				} else {
					// FIXME: this is a bit messed up, and exists only to help the particular
					// implementation of WyTP (which should be fixed eventually).
					return null;
				}
			}

			@Override
			public Decl.Link<Decl.Callable> getLink() {
				return getBinding().getLink();
			}

			@Override
			public Decl.Binding<Type.Callable,Decl.Callable> getBinding() {
				return (Decl.Binding<Type.Callable,Decl.Callable>) get(0);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Expr> getOperands() {
				return (Tuple<Expr>) get(1);
			}

			@SuppressWarnings("unchecked")
			@Override
			public Invoke clone(SyntacticItem[] operands) {
				return new Invoke((Decl.Binding<Type.Callable, Decl.Callable>) operands[0], (Tuple<Expr>) operands[1]);
			}

			@Override
			public String toString() {
				return getBinding().toString() + getOperands();
			}
		}

		/**
		 * Represents an indirect invocation of the form "<code>x.y(e1,..en)</code>".
		 * Here, <code>x.y</code> returns a function value and <code>e1</code> ...
		 * <code>en</code> are the <i>argument expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IndirectInvoke extends AbstractSyntacticItem implements Expr {

			public IndirectInvoke(Type type, Expr source, Tuple<Identifier> lifetimes, Tuple<Expr> arguments) {
				super(EXPR_indirectinvoke, type, source, lifetimes, arguments);
			}

			@Override
			public Type getType() {
				return (Type) get(0);
			}

			@Override
			public void setType(Type type) {
				operands[0] = type;
			}

			@Override
			public Tuple<Type> getTypes() {
				// FIXME: this feels wrong.
				return null;
			}

			public Expr getSource() {
				return (Expr) get(1);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Identifier> getLifetimes() {
				return (Tuple<Identifier>) get(2);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Expr> getArguments() {
				return (Tuple<Expr>) get(3);
			}

			@SuppressWarnings("unchecked")
			@Override
			public IndirectInvoke clone(SyntacticItem[] operands) {
				return new IndirectInvoke((Type) operands[0], (Expr) operands[1], (Tuple<Identifier>) operands[2],
						(Tuple<Expr>) operands[3]);
			}

			@Override
			public String toString() {
				String r = getSource().toString();
				r += getArguments();
				return r;
			}
		}

		/**
		 * Represents an abstract quantified expression of the form
		 * "<code>forall(T v1, ... T vn).e</code>" or
		 * "<code>exists(T v1, ... T vn).e</code>" where <code>T1 v1</code> ...
		 * <code>Tn vn</code> are the <i>quantified variable declarations</i> and
		 * <code>e</code> is the body.
		 *
		 * @author David J. Pearce
		 *
		 */
		public abstract static class Quantifier extends AbstractSyntacticItem implements Expr, UnaryOperator {
			public Quantifier(int opcode, Decl.Variable[] parameters, Expr body) {
				super(opcode, new Tuple<>(parameters), body);
			}

			public Quantifier(int opcode, Tuple<Decl.Variable> parameters, Expr body) {
				super(opcode, parameters, body);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@SuppressWarnings("unchecked")
			public Tuple<Decl.Variable> getParameters() {
				return (Tuple<Decl.Variable>) get(0);
			}

			@Override
			public Expr getOperand() {
				return (Expr) get(1);
			}

			@Override
			public abstract Expr clone(SyntacticItem[] operands);
		}

		/**
		 * Represents an unbounded universally quantified expression of the form
		 * "<code>forall(T v1, ... T vn).e</code>" where <code>T1 v1</code> ...
		 * <code>Tn vn</code> are the <i>quantified variable declarations</i> and
		 * <code>e</code> is the body.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class UniversalQuantifier extends Quantifier {
			public UniversalQuantifier(Decl.Variable[] parameters, Expr body) {
				super(EXPR_logicaluniversal, new Tuple<>(parameters), body);
			}

			public UniversalQuantifier(Tuple<Decl.Variable> parameters, Expr body) {
				super(EXPR_logicaluniversal, parameters, body);
			}

			@SuppressWarnings("unchecked")
			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new UniversalQuantifier((Tuple<Decl.Variable>) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				String r = "forall";
				r += getParameters();
				r += ".";
				r += getOperand();
				return r;
			}
		}

		/**
		 * Represents an unbounded existentially quantified expression of the form
		 * "<code>some(T v1, ... T vn).e</code>" where <code>T1 v1</code> ...
		 * <code>Tn vn</code> are the <i>quantified variable declarations</i> and
		 * <code>e</code> is the body.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class ExistentialQuantifier extends Quantifier {
			public ExistentialQuantifier(Decl.Variable[] parameters, Expr body) {
				super(EXPR_logicalexistential, new Tuple<>(parameters), body);
			}

			public ExistentialQuantifier(Tuple<Decl.Variable> parameters, Expr body) {
				super(EXPR_logicalexistential, parameters, body);
			}

			@SuppressWarnings("unchecked")
			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new ExistentialQuantifier((Tuple<Decl.Variable>) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				String r = "exists";
				r += getParameters();
				r += ".";
				r += getOperand();
				return r;
			}
		}

		/**
		 * Represents the use of some variable within an expression. For example, in
		 * <code>x + 1</code> the expression <code>x</code> is a variable access
		 * expression. Every variable access is associated with a <i>variable
		 * declaration</i> that unique identifies which variable is being accessed.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class VariableAccess extends AbstractExpr implements LVal {
			public VariableAccess(Type type, Decl.Variable decl) {
				super(EXPR_variablecopy, type, decl);
			}

			public Decl.Variable getVariableDeclaration() {
				return (Decl.Variable) get(1);
			}

			/**
			 * Mark this variable access as a move or borrow
			 */
			public void setMove() {
				this.opcode = EXPR_variablemove;
			}

			public boolean isMove() {
				return this.opcode == EXPR_variablemove;
			}

			@Override
			public VariableAccess clone(SyntacticItem[] operands) {
				return new VariableAccess((Type) operands[0], (Decl.Variable) operands[1]);
			}

			@Override
			public String toString() {
				return getVariableDeclaration().getName().toString();
			}
		}

		// =========================================================================
		// Logical Expressions
		// =========================================================================
		/**
		 * Represents a <i>logical conjunction</i> of the form
		 * "<code>e1 && .. && en</code>" where <code>e1</code> ... <code>en</code> are
		 * the <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class LogicalAnd extends AbstractSyntacticItem implements NaryOperator {
			public LogicalAnd(Tuple<Expr> operands) {
				super(EXPR_logicaland, operands);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Tuple<Expr> getOperands() {
				return (Tuple<Expr>) super.get(0);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new LogicalAnd((Tuple<Expr>) operands[0]);
			}

			@Override
			public String toString() {
				return " && ";
			}
		}

		/**
		 * Represents a <i>logical disjunction</i> of the form
		 * "<code>e1 || .. || en</code>" where <code>e1</code> ... <code>en</code> are
		 * the <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class LogicalOr extends AbstractSyntacticItem implements NaryOperator {
			public LogicalOr(Tuple<Expr> operands) {
				super(EXPR_logicalor, operands);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Tuple<Expr> getOperands() {
				return (Tuple<Expr>) super.get(0);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new LogicalOr((Tuple<Expr>) operands[0]);
			}

			@Override
			public String toString() {
				return " || ";
			}
		}

		/**
		 * Represents a <i>logical implication</i> of the form "<code>e1 ==> e2</code>"
		 * where <code>e1</code> and <code>e2</code> are the <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class LogicalImplication extends AbstractSyntacticItem implements BinaryOperator {
			public LogicalImplication(Expr lhs, Expr rhs) {
				super(EXPR_logiaclimplication, lhs, rhs);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(0);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new LogicalImplication((Expr) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return " ==> ";
			}
		}

		/**
		 * Represents a <i>logical biconditional</i> of the form
		 * "<code>e1 <==> e2</code>" where <code>e1</code> and <code>e2</code> are the
		 * <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class LogicalIff extends AbstractSyntacticItem implements BinaryOperator {
			public LogicalIff(Expr lhs, Expr rhs) {
				super(EXPR_logicaliff, lhs, rhs);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(0);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new LogicalIff((Expr) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return " <==> ";
			}
		}

		/**
		 * Represents a <i>logical negation</i> of the form "<code>!e</code>" where
		 * <code>e</code> is the <i>operand expression</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class LogicalNot extends AbstractSyntacticItem implements UnaryOperator {
			public LogicalNot(Expr operand) {
				super(EXPR_logicalnot, operand);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Expr getOperand() {
				return (Expr) get(0);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new LogicalNot((Expr) operands[0]);
			}
		}

		// =========================================================================
		// Comparator Expressions
		// =========================================================================

		/**
		 * Represents an equality expression of the form "<code>e1 == e2</code>" where
		 * <code>e1</code> and <code>e2</code> are the <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Equal extends AbstractSyntacticItem implements BinaryOperator {
			public Equal(Expr lhs, Expr rhs) {
				super(EXPR_equal, lhs, rhs);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(0);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new Equal((Expr) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return " == ";
			}
		}

		/**
		 * Represents an unequality expression of the form "<code>e1 != e2</code>" where
		 * <code>e1</code> and <code>e2</code> are the <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class NotEqual extends AbstractSyntacticItem implements BinaryOperator {
			public NotEqual(Expr lhs, Expr rhs) {
				super(EXPR_notequal, lhs, rhs);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(0);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new NotEqual((Expr) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return " != ";
			}
		}

		/**
		 * Represents a strict <i>inequality expression</i> of the form
		 * "<code>e1 < e2</code>" where <code>e1</code> and <code>e2</code> are the
		 * <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IntegerLessThan extends AbstractSyntacticItem implements BinaryOperator {
			public IntegerLessThan(Expr lhs, Expr rhs) {
				super(EXPR_integerlessthan, lhs, rhs);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(0);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new IntegerLessThan((Expr) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return " < ";
			}
		}

		/**
		 * Represents a non-strict <i>inequality expression</i> of the form
		 * "<code>e1 <= e2</code>" where <code>e1</code> and <code>e2</code> are the
		 * <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IntegerLessThanOrEqual extends AbstractSyntacticItem implements BinaryOperator {
			public IntegerLessThanOrEqual(Expr lhs, Expr rhs) {
				super(EXPR_integerlessequal, lhs, rhs);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(0);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new IntegerLessThanOrEqual((Expr) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return " <= ";
			}
		}

		/**
		 * Represents a strict <i>inequality expression</i> of the form
		 * "<code>e1 > e2</code>" where <code>e1</code> and <code>e2</code> are the
		 * <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IntegerGreaterThan extends AbstractSyntacticItem implements BinaryOperator {
			public IntegerGreaterThan(Expr lhs, Expr rhs) {
				super(EXPR_integergreaterthan, lhs, rhs);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(0);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new IntegerGreaterThan((Expr) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return " > ";
			}
		}

		/**
		 * Represents a non-strict <i>inequality expression</i> of the form
		 * "<code>e1 >= e2</code>" where <code>e1</code> and <code>e2</code> are the
		 * <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IntegerGreaterThanOrEqual extends AbstractSyntacticItem implements BinaryOperator {
			public IntegerGreaterThanOrEqual(Expr lhs, Expr rhs) {
				super(EXPR_integergreaterequal, lhs, rhs);
			}

			@Override
			public Type getType() {
				return Type.Bool;
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(0);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new IntegerGreaterThanOrEqual((Expr) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return " >= ";
			}
		}

		// =========================================================================
		// Integer Expressions
		// =========================================================================

		/**
		 * Represents an integer <i>addition expression</i> of the form
		 * "<code>e1 + e2</code>" where <code>e1</code> and <code>e2</code> are the
		 * <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IntegerAddition extends AbstractExpr implements BinaryOperator {
			public IntegerAddition(Type type, Expr lhs, Expr rhs) {
				super(EXPR_integeraddition, type, lhs, rhs);
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(2);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new IntegerAddition((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}

			@Override
			public String toString() {
				return " + ";
			}
		}

		/**
		 * Represents an integer <i>subtraction expression</i> of the form
		 * "<code>e1 - e2</code>" where <code>e1</code> and <code>e2</code> are the
		 * <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IntegerSubtraction extends AbstractExpr implements BinaryOperator {
			public IntegerSubtraction(Type type, Expr lhs, Expr rhs) {
				super(EXPR_integersubtraction, type, lhs, rhs);
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(2);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new IntegerSubtraction((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}

			@Override
			public String toString() {
				return " - ";
			}
		}

		/**
		 * Represents an integer <i>multiplication expression</i> of the form
		 * "<code>e1 * e2</code>" where <code>e1</code> and <code>e2</code> are the
		 * <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IntegerMultiplication extends AbstractExpr implements BinaryOperator {
			public IntegerMultiplication(Type type, Expr lhs, Expr rhs) {
				super(EXPR_integermultiplication, type, lhs, rhs);
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(2);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new IntegerMultiplication((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}

			@Override
			public String toString() {
				return " * ";
			}
		}

		/**
		 * Represents an integer <i>division expression</i> of the form
		 * "<code>e1 / e2</code>" where <code>e1</code> and <code>e2</code> are the
		 * <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IntegerDivision extends AbstractExpr implements BinaryOperator {
			public IntegerDivision(Type type, Expr lhs, Expr rhs) {
				super(EXPR_integerdivision, type, lhs, rhs);
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(2);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new IntegerDivision((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}

			@Override
			public String toString() {
				return " / ";
			}
		}

		/**
		 * Represents an integer <i>remainder expression</i> of the form
		 * "<code>e1 % e2</code>" where <code>e1</code> and <code>e2</code> are the
		 * <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IntegerRemainder extends AbstractExpr implements BinaryOperator {
			public IntegerRemainder(Type type, Expr lhs, Expr rhs) {
				super(EXPR_integerremainder, type, lhs, rhs);
			}

			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(2);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new IntegerRemainder((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}

			@Override
			public String toString() {
				return " % ";
			}
		}

		/**
		 * Represents an integer <i>negation expression</i> of the form
		 * "<code>-e</code>" where <code>e</code> is the <i>operand expression</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IntegerNegation extends AbstractExpr implements UnaryOperator {
			public IntegerNegation(Type type, Expr operand) {
				super(EXPR_integernegation, type, operand);
			}

			@Override
			public Expr getOperand() {
				return (Expr) get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new IntegerNegation((Type) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return "-" + getOperand();
			}
		}

		// =========================================================================
		// Bitwise Expressions
		// =========================================================================

		/**
		 * Represents a <i>bitwise shift left</i> of the form "<code>e << i</code>"
		 * where <code>e</code> is the expression being shifted and <code>i</code> the
		 * amount it is shifted by.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class BitwiseShiftLeft extends AbstractExpr implements BinaryOperator {
			public BitwiseShiftLeft(Type type, Expr lhs, Expr rhs) {
				super(EXPR_bitwiseshl, type, lhs, rhs);
			}

			/**
			 * Get the value operand to be shifted. That is <code>x</code> in
			 * <code>x << i</code>.
			 */
			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(1);
			}

			/**
			 * Get the operand indicating the amount to shift. That is <code>i</code> in
			 * <code>x << i</code>.
			 */
			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(2);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new BitwiseShiftLeft((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}

			@Override
			public String toString() {
				return " << ";
			}
		}

		/**
		 * Represents a <i>bitwise shift right</i> of the form "<code>e >> i</code>"
		 * where <code>e</code> is the expression being shifted and <code>i</code> the
		 * amount it is shifted by.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class BitwiseShiftRight extends AbstractExpr implements BinaryOperator {
			public BitwiseShiftRight(Type type, Expr lhs, Expr rhs) {
				super(EXPR_bitwiseshr, type, lhs, rhs);
			}

			/**
			 * Get the value operand to be shifted. That is <code>x</code> in
			 * <code>x >> i</code>.
			 */
			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(1);
			}

			/**
			 * Get the operand indicating the amount to shift. That is <code>i</code> in
			 * <code>x >> i</code>.
			 */
			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(2);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new BitwiseShiftRight((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}

			@Override
			public String toString() {
				return " >> ";
			}
		}

		/**
		 * Represents a <i>bitwise and</i> of the form "<code>e1 & .. & en</code>" where
		 * <code>e1</code> ... <code>en</code> are the <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class BitwiseAnd extends AbstractExpr implements NaryOperator {
			public BitwiseAnd(Type type, Tuple<Expr> operands) {
				super(EXPR_bitwiseand, type, operands);
			}

			@Override
			public Tuple<Expr> getOperands() {
				return (Tuple<Expr>) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new BitwiseAnd((Type) operands[0], (Tuple<Expr>) operands[1]);
			}

			@Override
			public String toString() {
				return " & ";
			}
		}

		/**
		 * Represents a <i>bitwise or</i> of the form "<code>e1 | .. | en</code>" where
		 * <code>e1</code> ... <code>en</code> are the <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class BitwiseOr extends AbstractExpr implements NaryOperator {
			public BitwiseOr(Type type, Tuple<Expr> operands) {
				super(EXPR_bitwiseor, type, operands);
			}

			@Override
			public Tuple<Expr> getOperands() {
				return (Tuple<Expr>) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new BitwiseOr((Type) operands[0], (Tuple<Expr>) operands[1]);
			}

			@Override
			public String toString() {
				return " | ";
			}
		}

		/**
		 * Represents a <i>bitwise xor</i> of the form "<code>e1 ^ .. ^ en</code>" where
		 * <code>e1</code> ... <code>en</code> are the <i>operand expressions</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class BitwiseXor extends AbstractExpr implements NaryOperator {
			public BitwiseXor(Type type, Tuple<Expr> operands) {
				super(EXPR_bitwisexor, type, operands);
			}

			@Override
			public Tuple<Expr> getOperands() {
				return (Tuple<Expr>) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new BitwiseXor((Type) operands[0], (Tuple<Expr>) operands[1]);
			}

			@Override
			public String toString() {
				return " ^ ";
			}
		}

		/**
		 * Represents a <i>bitwise complement</i> of the form "<code>~e</code>" where
		 * <code>e</code> is the <i>operand expression</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class BitwiseComplement extends AbstractExpr implements UnaryOperator {
			public BitwiseComplement(Type type, Expr operand) {
				super(EXPR_bitwisenot, type, operand);
			}

			/**
			 * Get the operand to be complimented. That is,
			 * <code>e<code> in </code>!e</code>.
			 */
			@Override
			public Expr getOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new BitwiseComplement((Type) operands[0], (Expr) operands[1]);
			}
		}

		// =========================================================================
		// Reference Expressions
		// =========================================================================

		/**
		 * Represents an object dereference expression of the form "<code>*e</code>"
		 * where <code>e</code> is the <i>operand expression</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Dereference extends AbstractExpr implements LVal, UnaryOperator {
			public Dereference(Type type, Expr operand) {
				super(EXPR_dereference, type, operand);
			}

			/**
			 * Get the operand to be dereferenced. That is,
			 * <code>e<code> in </code>*e</code>.
			 */
			@Override
			public Expr getOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new Dereference((Type) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return "*" + getOperand();
			}
		}

		/**
		 * Represents an <i>object allocation</i> expression of the form
		 * <code>new e</code> or <code>l:new e</code> where <code>e</code> is the
		 * operand expression and <code>l</code> the optional lifetime argument.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class New extends AbstractExpr implements LVal, UnaryOperator {
			public New(Type type, Expr operand, Identifier lifetime) {
				super(EXPR_new, type, operand, lifetime);
			}

			public New(Type type, Expr operand) {
				super(EXPR_staticnew, type, operand);
			}

			/**
			 * Get the operand to be evaluated and stored in the heap. That is,
			 * <code>e<code> in </code>new e</code>.
			 */
			@Override
			public Expr getOperand() {
				return (Expr) super.get(1);
			}

			public boolean hasLifetime() {
				return opcode == EXPR_new;
			}

			public Identifier getLifetime() {
				return (Identifier) super.get(2);
			}

			@Override
			public Expr clone(SyntacticItem[] operands) {
				return new New((Type) operands[0], (Expr) operands[1], (Identifier) operands[2]);
			}

			@Override
			public String toString() {
				String r = "new ";
				if (hasLifetime()) {
					r = getLifetime() + ":" + r;
				}
				return r + getOperand();
			}
		}

		public static class LambdaAccess extends AbstractSyntacticItem implements Expr, Bindable {

			public LambdaAccess(Decl.Binding<Type.Callable,Decl.Callable> name, Tuple<Type> parameters) {
				super(EXPR_lambdaaccess, name, parameters);
			}

			@Override
			public Type getType() {
				return getLink().getTarget().getType();
			}

			@Override
			public void setType(Type type) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Tuple<Type> getTypes() {
				return null;
			}

			@Override
			public Decl.Link<Decl.Callable> getLink() {
				return getBinding().getLink();
			}

			@Override
			public Decl.Binding<Type.Callable, Decl.Callable> getBinding() {
				return (Decl.Binding<Type.Callable, Decl.Callable>) get(0);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Type> getParameterTypes() {
				return (Tuple<Type>) get(1);
			}

			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new LambdaAccess((Decl.Binding<Type.Callable, Decl.Callable>) operands[0],
						(Tuple<Type>) operands[1]);
			}
		}

		// =========================================================================
		// Array Expressions
		// =========================================================================

		/**
		 * Represents an <i>array access expression</i> of the form
		 * "<code>arr[e]</code>" where <code>arr</code> is the <i>source array</i> and
		 * <code>e</code> the <i>subscript expression</i>. This returns the value held
		 * in the element determined by <code>e</code>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class ArrayAccess extends AbstractExpr implements LVal, BinaryOperator {
			public ArrayAccess(Type type, Expr src, Expr index) {
				super(EXPR_arrayaccess, type, src, index);
			}

			/**
			 * Get the source array operand for this access. That is <code>xs</code> in
			 * <code>xs[i]</code>.
			 */
			@Override
			public Expr getFirstOperand() {
				return (Expr) get(1);
			}

			/**
			 * Get the index operand for this access. That is <code>i</code> in
			 * <code>xs[i]</code>.
			 */
			@Override
			public Expr getSecondOperand() {
				return (Expr) get(2);
			}

			public void setMove() {
				this.opcode = EXPR_arrayborrow;
			}

			public boolean isMove() {
				return opcode == EXPR_arrayborrow;
			}

			@Override
			public ArrayAccess clone(SyntacticItem[] operands) {
				return new ArrayAccess((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}

			@Override
			public String toString() {
				return getFirstOperand() + "[" + getSecondOperand() + "]";
			}

		}

		/**
		 * Represents an <i>array update expression</i> of the form
		 * "<code>arr[e1:=e2]</code>" where <code>arr</code> is the <i>source array</i>,
		 * <code>e1</code> the <i>subscript expression</i> and <code>e2</code> is the
		 * value expression. This returns a new array which is equivalent to
		 * <code>arr</code> but where the element determined by <code>e1</code> has the
		 * value resulting from <code>e2</code>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class ArrayUpdate extends AbstractExpr implements Expr, TernaryOperator {
			public ArrayUpdate(Type type, Expr src, Expr index, Expr value) {
				super(EXPR_arrayupdate, type, src, index, value);
			}

			/**
			 * Get the source array operand for this update. That is <code>xs</code> in
			 * <code>xs[i:=v]</code>.
			 */
			@Override
			public Expr getFirstOperand() {
				return (Expr) get(1);
			}

			/**
			 * Get the index operand for this update. That is <code>i</code> in
			 * <code>xs[i:=v]</code>.
			 */
			@Override
			public Expr getSecondOperand() {
				return (Expr) get(2);
			}

			/**
			 * Get the value operand of this update. That is <code>v</code> in
			 * <code>xs[i:=v]</code>.
			 */
			@Override
			public Expr getThirdOperand() {
				return (Expr) get(3);
			}

			@Override
			public ArrayUpdate clone(SyntacticItem[] operands) {
				return new ArrayUpdate((Type) operands[0], (Expr) operands[1], (Expr) operands[2], (Expr) operands[3]);
			}

			@Override
			public String toString() {
				return getFirstOperand() + "[" + getSecondOperand() + ":=" + getThirdOperand() + "]";
			}
		}

		/**
		 * Represents an <i>array initialiser expression</i> of the form
		 * "<code>[e1,...,en]</code>" where <code>e1</code> ... <code>en</code> are the
		 * <i>initialiser expressions</i>. Thus returns a new array made up from those
		 * values resulting from the initialiser expressions.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class ArrayInitialiser extends AbstractExpr implements NaryOperator {
			public ArrayInitialiser(Type type, Tuple<Expr> elements) {
				super(EXPR_arrayinitialiser, type, elements);
			}

			@Override
			public Tuple<Expr> getOperands() {
				return (Tuple<Expr>) super.get(1);
			}

			@Override
			public ArrayInitialiser clone(SyntacticItem[] operands) {
				return new ArrayInitialiser((Type) operands[0], (Tuple<Expr>) operands[1]);
			}

			@Override
			public String toString() {
				return Arrays.toString(toArray(SyntacticItem.class));
			}
		}

		/**
		 * Represents an <i>array generator expression</i> of the form
		 * "<code>[e1;e2]</code>" where <code>e1</code> is the <i>element expression</i>
		 * and <code>e2</code> is the <i>length expression</i>. This returns a new array
		 * whose length is determined by <code>e2</code> and where every element has
		 * contains the value determined by <code>e1</code>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class ArrayGenerator extends AbstractExpr implements BinaryOperator {
			public ArrayGenerator(Type type, Expr value, Expr length) {
				super(EXPR_arraygenerator, type, value, length);
			}

			/**
			 * Get the value operand for this generator. That is <code>e</code> in
			 * <code>[e; n]</code>.
			 */
			@Override
			public Expr getFirstOperand() {
				return (Expr) get(1);
			}

			/**
			 * Get the length operand for this generator. That is <code>n</code> in
			 * <code>[e; n]</code>.
			 */
			@Override
			public Expr getSecondOperand() {
				return (Expr) get(2);
			}

			@Override
			public ArrayGenerator clone(SyntacticItem[] operands) {
				return new ArrayGenerator((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		}

		/**
		 * Represents an <i>array range expression</i> of the form
		 * "<code>e1 .. e2</code>" where <code>e1</code> is the start of the range and
		 * <code>e2</code> the end. Thus returns a new array made up from those values
		 * between start and end (but not including the end).
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class ArrayRange extends AbstractExpr implements BinaryOperator {
			public ArrayRange(Type type, Expr start, Expr end) {
				super(EXPR_arrayrange, type, start, end);
			}

			/**
			 * Get the starting operand for this range. That is <code>s</code> in
			 * <code>s..e</code>. This determines the first element of the resulting range.
			 */
			@Override
			public Expr getFirstOperand() {
				return (Expr) super.get(1);
			}

			/**
			 * Get the ending operand for this range. That is <code>e</code> in
			 * <code>s..e</code>. The range extends up to (but not including) this value.
			 */
			@Override
			public Expr getSecondOperand() {
				return (Expr) super.get(2);
			}

			@Override
			public ArrayRange clone(SyntacticItem[] operands) {
				return new ArrayRange((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		}

		/**
		 * Represents an <i>array length expression</i> of the form "<code>|arr|</code>"
		 * where <code>arr</code> is the <i>source array</i>. This simply returns the
		 * length of array <code>arr</code>. <code>e</code>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class ArrayLength extends AbstractExpr implements Expr.UnaryOperator {
			public ArrayLength(Type type, Expr src) {
				super(EXPR_arraylength, type, src);
			}

			@Override
			public Expr getOperand() {
				return (Expr) super.get(1);
			}

			@Override
			public ArrayLength clone(SyntacticItem[] operands) {
				return new ArrayLength((Type) operands[0], (Expr) operands[1]);
			}

			@Override
			public String toString() {
				return "|" + getOperand() + "|";
			}
		}

		// =========================================================================
		// Record Expressions
		// =========================================================================

		/**
		 * Represents a <i>record access expression</i> of the form "<code>rec.f</code>"
		 * where <code>rec</code> is the <i>source record</i> and <code>f</code> is the
		 * <i>field</i>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class RecordAccess extends AbstractExpr implements LVal, UnaryOperator {
			public RecordAccess(Type type, Expr lhs, Identifier rhs) {
				super(EXPR_recordaccess, type, lhs, rhs);
			}

			/**
			 * Get the source operand for this access. That is <code>e</code> in
			 * <code>e.f/code>.
			 */
			@Override
			public Expr getOperand() {
				return (Expr) get(1);
			}

			/**
			 * Get the field name for this access. That is <code>f</code> in
			 * <code>e.f/code>.
			 */
			public Identifier getField() {
				return (Identifier) get(2);
			}

			public void setMove() {
				this.opcode = EXPR_recordborrow;
			}

			public boolean isMove() {
				return opcode == EXPR_recordborrow;
			}

			@Override
			public RecordAccess clone(SyntacticItem[] operands) {
				return new RecordAccess((Type) operands[0], (Expr) operands[1], (Identifier) operands[2]);
			}

			@Override
			public String toString() {
				return getOperand() + "." + getField();
			}
		}

		/**
		 * Represents a <i>record initialiser</i> expression of the form
		 * "<code>{ f1:e1, ..., fn:en }</code>" where <code>f1:e1</code> ...
		 * <code>fn:en</code> are <i>field initialisers</i>. This returns a new record
		 * where each field holds the value resulting from its corresponding expression.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class RecordInitialiser extends AbstractExpr implements Expr, NaryOperator {

			public RecordInitialiser(Type type, Tuple<Identifier> fields, Tuple<Expr> operands) {
				// FIXME: it would be nice for the constructor to require a record type;
				// however, the parser constructs multiple initialisers during parsing (even
				// when only one is present). This causes subsequent problems down the track.
				super(EXPR_recordinitialiser, type, fields, operands);
			}

			@Override
			public Type.Record getType() {
				return (Type.Record) super.getType();
			}

			@Override
			public void setType(Type type) {
				if (type instanceof Type.Record) {
					super.setType(type);
				} else {
					throw new IllegalArgumentException("invalid record initialiser type");
				}
			}

			public Tuple<Identifier> getFields() {
				return (Tuple<Identifier>) super.get(1);
			}

			@Override
			public Tuple<Expr> getOperands() {
				return (Tuple<Expr>) super.get(2);
			}

			@SuppressWarnings("unchecked")
			@Override
			public RecordInitialiser clone(SyntacticItem[] operands) {
				return new RecordInitialiser((Type) operands[0], (Tuple<Identifier>) operands[1],
						(Tuple<Expr>) operands[2]);
			}
		}

		/**
		 * Represents a <i>record update expression</i> of the form
		 * "<code>e[f:=v]</code>" where <code>e</code> is the <i>source operand</i>,
		 * <code>f</code> is the <i>field</i> and <code>v</code> is the <i>value
		 * operand</i>. This returns a new record which is equivalent to <code>e</code>
		 * but where the element in field <code>f</code> has the value resulting from
		 * <code>v</code>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class RecordUpdate extends AbstractExpr implements Expr, BinaryOperator {
			public RecordUpdate(Type type, Expr lhs, Identifier mhs, Expr rhs) {
				super(EXPR_recordupdate, type, lhs, mhs, rhs);
			}

			/**
			 * Get the source operand for this update. That is <code>e</code> in
			 * <code>e[f:=v]/code>.
			 */
			@Override
			public Expr getFirstOperand() {
				return (Expr) get(1);
			}

			/**
			 * Get the field name for this update. That is <code>f</code> in
			 * <code>e[f:=v]/code>.
			 */
			public Identifier getField() {
				return (Identifier) get(2);
			}

			/**
			 * Get the value operand for this update. That is <code>v</code> in
			 * <code>e[f:=v]/code>.
			 */
			@Override
			public Expr getSecondOperand() {
				return (Expr) get(3);
			}

			@Override
			public RecordUpdate clone(SyntacticItem[] operands) {
				return new RecordUpdate((Type) operands[0], (Expr) operands[1], (Identifier) operands[2],
						(Expr) operands[3]);
			}

			@Override
			public String toString() {
				return getFirstOperand() + "{" + getField() + ":=" + getSecondOperand() + "}";
			}
		}
	}

	// =========================================================================
	// Syntactic Type
	// =========================================================================

	public static interface Type extends SemanticType {

		public static final Any Any = new Any();
		public static final Void Void = new Void();
		public static final Bool Bool = new Bool();
		public static final Byte Byte = new Byte();
		public static final Int Int = new Int();
		public static final Null Null = new Null();

		/**
		 * Substitute for lifetime or type parameters
		 *
		 * @param binding A function which returns
		 * @return
		 */
		public Type substitute(java.util.function.Function<Identifier, SyntacticItem> binding);

		/**
		 * Return a canonical string which embodies this type.
		 *
		 * @return
		 */
		public String toCanonicalString();

		public interface Atom extends Type, SemanticType.Atom {
		}

		public interface Primitive extends Atom {

		}

		static abstract class AbstractType extends AbstractSemanticType {
			AbstractType(int opcode) {
				super(opcode);
			}

			AbstractType(int opcode, SyntacticItem operand) {
				super(opcode, operand);
			}

			AbstractType(int opcode, SyntacticItem... operands) {
				super(opcode, operands);
			}

		}

		/**
		 * An any type represents the type whose variables can hold any possible value.
		 * Such types cannot currently be expressed at the source level, though remain
		 * useful for the purposes of type checking.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Any extends AbstractType implements Primitive {
			public Any() {
				super(TYPE_any);
			}

			@Override
			public Type substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				return this;
			}

			@Override
			public Any clone(SyntacticItem[] operands) {
				return new Any();
			}

			@Override
			public String toString() {
				return "any";
			}

			@Override
			public String toCanonicalString() {
				return "any";
			}
		}

		/**
		 * Represents the set of all functions, methods and properties. These are values
		 * which can be called using an indirect invoke expression. Each function or
		 * method accepts zero or more parameters and will produce zero or more returns.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static interface Callable extends Atom {

			public Tuple<Type> getParameters();

			public Tuple<Type> getReturns();

			@Override
			public Type.Callable substitute(java.util.function.Function<Identifier, SyntacticItem> binding);
		}

		/**
		 * A void type represents the type whose variables cannot exist! That is, they
		 * cannot hold any possible value. Void is used to represent the return type of
		 * a function which does not return anything. However, it is also used to
		 * represent the element type of an empty list of set. <b>NOTE:</b> the void
		 * type is a subtype of everything; that is, it is bottom in the type lattice.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Void extends AbstractType implements Primitive {
			public Void() {
				super(TYPE_void);
			}

			@Override
			public Type substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				return this;
			}

			@Override
			public Void clone(SyntacticItem[] operands) {
				return new Void();
			}

			@Override
			public String toString() {
				return "void";
			}

			@Override
			public String toCanonicalString() {
				return "void";
			}
		}

		/**
		 * The null type is a special type which should be used to show the absence of
		 * something. It is distinct from void, since variables can hold the special
		 * <code>null</code> value (where as there is no special "void" value). With all
		 * of the problems surrounding <code>null</code> and
		 * <code>NullPointerException</code>s in languages like Java and C, it may seem
		 * that this type should be avoided. However, it remains a very useful
		 * abstraction to have around and, in Whiley, it is treated in a completely safe
		 * manner (unlike e.g. Java).
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Null extends AbstractType implements Primitive {
			public Null() {
				super(TYPE_null);
			}

			@Override
			public Type substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				return this;
			}

			@Override
			public Null clone(SyntacticItem[] operands) {
				return new Null();
			}

			@Override
			public String toString() {
				return "null";
			}

			@Override
			public String toCanonicalString() {
				return "null";
			}
		}

		/**
		 * Represents the set of boolean values (i.e. true and false)
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Bool extends AbstractType implements Primitive {
			public Bool() {
				super(TYPE_bool);
			}

			@Override
			public Type substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				return this;
			}

			@Override
			public Bool clone(SyntacticItem[] operands) {
				return new Bool();
			}

			@Override
			public String toString() {
				return "bool";
			}

			@Override
			public String toCanonicalString() {
				return "bool";
			}
		}

		/**
		 * Represents a sequence of 8 bits. Note that, unlike many languages, there is
		 * no representation associated with a byte. For example, to extract an integer
		 * value from a byte, it must be explicitly decoded according to some
		 * representation (e.g. two's compliment) using an auxillary function (e.g.
		 * <code>Byte.toInt()</code>).
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Byte extends AbstractType implements Primitive {
			public Byte() {
				super(TYPE_byte);
			}

			@Override
			public Type substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				return this;
			}

			@Override
			public Byte clone(SyntacticItem[] operands) {
				return new Byte();
			}

			@Override
			public String toString() {
				return "byte";
			}

			@Override
			public String toCanonicalString() {
				return "byte";
			}
		}

		/**
		 * Represents the set of (unbound) integer values. Since integer types in Whiley
		 * are unbounded, there is no equivalent to Java's <code>MIN_VALUE</code> and
		 * <code>MAX_VALUE</code> for <code>int</code> types.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Int extends AbstractType implements Primitive {
			public Int() {
				super(TYPE_int);
			}

			@Override
			public Type substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				return this;
			}

			@Override
			public Int clone(SyntacticItem[] operands) {
				return new Int();
			}

			@Override
			public String toString() {
				return "int";
			}

			@Override
			public String toCanonicalString() {
				return "int";
			}
		}

		/**
		 * Represents an array type, which is of the form:
		 *
		 * <pre>
		 * ArrayType ::= Type '[' ']'
		 * </pre>
		 *
		 * An array type describes array values whose elements are subtypes of the
		 * element type. For example, <code>[1,2,3]</code> is an instance of array type
		 * <code>int[]</code>; however, <code>[false]</code> is not.
		 *
		 * @return
		 */
		public static class Array extends SemanticType.Array implements Atom {
			public Array(Type element) {
				super(TYPE_array, element);
			}

			@Override
			public Type getElement() {
				return (Type) get(0);
			}

			@Override
			public Type.Array substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				Type before = getElement();
				Type after = before.substitute(binding);
				if (before == after) {
					return this;
				} else {
					return new Type.Array(after);
				}
			}

			@Override
			public Type.Array clone(SyntacticItem[] operands) {
				return new Type.Array((Type) operands[0]);
			}

			@Override
			public String toString() {
				return getElement().toString() + "[]";
			}

			@Override
			public String toCanonicalString() {
				return canonicalBraceAsNecessary(getElement()) + "[]";
			}
		}

		/**
		 * Parse a reference type, which is of the form:
		 *
		 * <pre>
		 * ReferenceType ::= '&' Type
		 * </pre>
		 *
		 * Represents a reference to an object in Whiley. For example,
		 * <code>&this:int</code> is the type of a reference to a location allocated in
		 * the enclosing scope which holds an integer value.
		 *
		 * @return
		 */
		public static class Reference extends SemanticType.Reference implements Atom {
			public Reference(Type element) {
				super(TYPE_staticreference, element);
			}

			public Reference(Type element, Identifier lifetime) {
				super(TYPE_reference, element, lifetime);
			}

			@Override
			public boolean hasLifetime() {
				return opcode == TYPE_reference;
			}

			@Override
			public Type getElement() {
				return (Type) get(0);
			}

			@Override
			public Identifier getLifetime() {
				return (Identifier) get(1);
			}

			@Override
			public Type.Reference substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				Type elementBefore = getElement();
				Type elementAfter = elementBefore.substitute(binding);
				if(elementBefore != elementAfter && !hasLifetime()) {
					return new Type.Reference(elementAfter);
				} else if(hasLifetime()){
					SyntacticItem lifetime = binding.apply(getLifetime());
					if(lifetime != null) {
						lifetime = (lifetime instanceof Identifier) ? lifetime : getLifetime();
						return new Type.Reference(elementAfter, (Identifier) lifetime);
					}
				}
				return this;
			}

			@Override
			public Type.Reference clone(SyntacticItem[] operands) {
				if (operands.length == 1) {
					return new Type.Reference((Type) operands[0]);
				} else {
					return new Type.Reference((Type) operands[0], (Identifier) operands[1]);
				}
			}

			@Override
			public String toString() {
				if (hasLifetime()) {
					return "&" + getLifetime() + ":" + braceAsNecessary(getElement());
				} else {
					return "&" + braceAsNecessary(getElement());
				}
			}

			@Override
			public String toCanonicalString() {
				if (hasLifetime()) {
					return "&" + getLifetime() + ":" + canonicalBraceAsNecessary(getElement());
				} else {
					return "&" + canonicalBraceAsNecessary(getElement());
				}
			}
		}

		/**
		 * Represents record type, which is of the form:
		 *
		 * <pre>
		 * RecordType ::= '{' Type Identifier (',' Type Identifier)* [ ',' "..." ] '}'
		 * </pre>
		 *
		 * A record is made up of a number of fields, each of which has a unique name.
		 * Each field has a corresponding type. One can think of a record as a special
		 * kind of "fixed" map (i.e. where we know exactly which entries we have).
		 *
		 * @return
		 */
		public static class Record extends SemanticType.Record implements Atom {
			public Record(boolean isOpen, Tuple<Type.Field> fields) {
				this(new Value.Bool(isOpen), fields);
			}

			public Record(Value.Bool isOpen, Tuple<Type.Field> fields) {
				super(TYPE_record, isOpen, fields);
			}

			@Override
			public boolean isOpen() {
				Value.Bool flag = (Value.Bool) get(0);
				return flag.get();
			}

			@Override
			public Type getField(Identifier identifier) {
				return (Type) super.getField(identifier);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Type.Field> getFields() {
				return (Tuple<Type.Field>) get(1);
			}

			@Override
			public Type.Record substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				Tuple<Type.Field> before = getFields();
				Tuple<Type.Field> after = substitute(before, binding);
				if (before == after) {
					return this;
				} else {
					return new Type.Record(isOpen(), after);
				}
			}

			@Override
			public Type.Record clone(SyntacticItem[] operands) {
				return new Type.Record((Value.Bool) operands[0], (Tuple<Type.Field>) operands[1]);
			}

			/**
			 * Substitute through fields whilst minimising memory allocations
			 *
			 * @param fields
			 * @param binding
			 * @return
			 */
			private static Tuple<Type.Field> substitute(Tuple<Type.Field> fields,
					java.util.function.Function<Identifier, SyntacticItem> binding) {
				//
				for (int i = 0; i != fields.size(); ++i) {
					Type.Field field = fields.get(i);
					Type before = field.getType();
					Type after = before.substitute(binding);
					if (before != after) {
						// Now committed to a change
						Type.Field[] nFields = fields.toArray(Type.Field.class);
						nFields[i] = new Type.Field(field.getName(), after);
						for (int j = i + 1; j < fields.size(); ++j) {
							field = fields.get(j);
							before = field.getType();
							after = before.substitute(binding);
							if (before != after) {
								nFields[j] = new Type.Field(field.getName(), after);
							}
						}
						return new Tuple<>(nFields);
					}
				}
				//
				return fields;
			}

			@Override
			public String toString() {
				Tuple<Type.Field> fields = getFields();
				String r = "";
				//
				for (int i = 0; i != fields.size(); ++i) {
					Type.Field field = fields.get(i);
					if (i != 0) {
						r += ",";
					}
					r += field.toString();
				}
				//
				return "{" + r + "}";
			}

			@Override
			public String toCanonicalString() {
				Tuple<Type.Field> fields = getFields();
				String r = "";
				//
				for (int i = 0; i != fields.size(); ++i) {
					Type.Field field = fields.get(i);
					if (i != 0) {
						r += ",";
					}
					r += field.toCanonicalString();
				}
				//
				return "{" + r + "}";
			}
		}

		public static class Field extends SemanticType.Field {

			public Field(Identifier name, Type type) {
				super(TYPE_field, name, type);
			}

			@Override
			public Identifier getName() {
				return (Identifier) get(0);
			}

			@Override
			public Type getType() {
				return (Type) get(1);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Type.Field((Identifier) operands[0], (Type) operands[1]);
			}

			@Override
			public String toString() {
				return getType().toString() + " " + getName();
			}

			public String toCanonicalString() {
				return getType().toString() + " " + getName();
			}
		}

		/**
		 * Represents a nominal type, which is of the form:
		 *
		 * <pre>
		 * NominalType ::= Identifier ('.' Identifier)*
		 * </pre>
		 *
		 * A nominal type specifies the name of a type defined elsewhere. In some cases,
		 * this type can be expanded (or "inlined"). However, visibility modifiers can
		 * prevent this and, thus, give rise to true nominal types.
		 *
		 * @return
		 */
		public static class Nominal extends AbstractSemanticType implements Type, Linkable {

			public Nominal(Decl.Link<Decl.Type> name, Tuple<Type> parameters) {
				super(TYPE_nominal, name, parameters);
			}

			@Override
			public Decl.Link<Decl.Type> getLink() {
				return (Decl.Link<Decl.Type>) get(0);
			}

			public Tuple<Type> getParameters() {
				return (Tuple<Type>) get(1);
			}

			public Type getConcreteType() {
				Decl.Type decl = getLink().getTarget();
				Tuple<Template.Variable> template = decl.getTemplate();
				Tuple<Type> arguments = getParameters();
				Type type = decl.getType();
				//
				if (template.size() > 0) {
					type = type.substitute(bindingFunction(template,arguments));
				}
				//
				return type;
			}

			@Override
			public Type.Nominal substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				Tuple<Type> o_parameters = getParameters();
				Tuple<Type> n_parameters = WyilFile.substitute(getParameters(), binding);
				if (o_parameters == n_parameters) {
					return this;
				} else {
					return new Type.Nominal(getLink(), n_parameters);
				}
			}

			@Override
			public Nominal clone(SyntacticItem[] operands) {
				return new Nominal((Decl.Link<Decl.Type>) operands[0], ((Tuple<Type>) operands[1]));
			}

			@Override
			public String toString() {
				String s = getLink().getName().toString();
				Tuple<Type> parameters = getParameters();
				if (parameters.size() == 0) {
					return s;
				} else {
					return s + "<" + WyilFile.toString(parameters) + ">";
				}
			}

			@Override
			public String toCanonicalString() {
				return getLink().getTarget().getQualifiedName().toString();
			}
		}

		/**
		 * Represents a recursive link. That is a backlink into the type itself.
		 *
		 * @return
		 */
		public static class Recursive extends AbstractSemanticType implements Type {

			public Recursive(Ref<Type> reference) {
				super(TYPE_recursive, reference);
			}

			public Type getHead() {
				Ref<Type> r = (Ref<Type>) get(0);
				return r.get();
			}

			public void setHead(Ref<Type> ref) {
				operands[0] = ref;
			}

			@Override
			public Recursive substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				return this;
			}

			@Override
			public Recursive clone(SyntacticItem[] operands) {
				return new Recursive((Ref<Type>) operands[0]);
			}

			@Override
			public String toString() {
				Type head = getHead();
				if (head instanceof Type.Atom || head instanceof Type.Nominal) {
					return "?" + head;
				} else if (head.getHeap() != null) {
					return "?" + head.getIndex();
				} else {
					return "?";
				}
			}

			@Override
			public String toCanonicalString() {
				throw new UnsupportedOperationException();
			}
		}

		/**
		 * Represents a union type, which is of the form:
		 *
		 * <pre>
		 * UnionType ::= IntersectionType ('|' IntersectionType)*
		 * </pre>
		 *
		 * Union types are used to compose types together. For example, the type
		 * <code>int|null</code> represents the type which is either an <code>int</code>
		 * or <code>null</code>.
		 *
		 * Represents the union of one or more types together. For example, the union of
		 * <code>int</code> and <code>null</code> is <code>int|null</code>. Any variable
		 * of this type may hold any integer or the null value. Furthermore, the types
		 * <code>int</code> and <code>null</code> are collectively referred to as the
		 * "bounds" of this type.
		 *
		 * @return
		 */
		public static class Union extends SemanticType.Union implements Type {
			public Union(Type... types) {
				super(TYPE_union, types);
			}

			@Override
			public Type get(int i) {
				return (Type) super.get(i);
			}

			@Override
			public Type[] getAll() {
				return (Type[]) super.getAll();
			}

			@Override
			public Type.Union substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				Type[] before = getAll();
				Type[] after = WyilFile.substitute(before, binding);
				if (before == after) {
					return this;
				} else {
					return new Type.Union(after);
				}
			}

			@Override
			public Type.Union clone(SyntacticItem[] operands) {
				return new Type.Union(ArrayUtils.toArray(Type.class, operands));
			}

			@Override
			public String toString() {
				String r = "";
				for (int i = 0; i != size(); ++i) {
					if (i != 0) {
						r += "|";
					}
					r += braceAsNecessary(get(i));
				}
				return r;
			}

			@Override
			public String toCanonicalString() {
				String r = "";
				for (int i = 0; i != size(); ++i) {
					if (i != 0) {
						r += "|";
					}
					r += "(" + get(i).toCanonicalString() + ")";
				}
				return r;
			}
		}

		/**
		 * Represents the set of all function values. These are pure functions,
		 * sometimes also called "mathematical" functions. A function cannot have any
		 * side-effects and must always return the same values given the same inputs. A
		 * function cannot have zero returns, since this would make it a no-operation.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Function extends AbstractType implements Type.Callable {
			public Function(Tuple<Type> parameters, Tuple<Type> returns) {
				super(TYPE_function, parameters, returns);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Type> getParameters() {
				return (Tuple<Type>) get(0);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Type> getReturns() {
				return (Tuple<Type>) get(1);
			}

			@Override
			public Type.Function substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				Tuple<Type> parametersBefore = getParameters();
				Tuple<Type> parametersAfter = WyilFile.substitute(parametersBefore, binding);
				Tuple<Type> returnsBefore = getReturns();
				Tuple<Type> returnsAfter = WyilFile.substitute(returnsBefore, binding);
				if (parametersBefore == parametersAfter && returnsBefore == returnsAfter) {
					return this;
				} else {
					return new Type.Function(parametersAfter, returnsAfter);
				}
			}

			@SuppressWarnings("unchecked")
			@Override
			public Function clone(SyntacticItem[] operands) {
				return new Function((Tuple<Type>) operands[0], (Tuple<Type>) operands[1]);
			}

			@Override
			public String toString() {
				return "function" + getParameters().toString() + "->" + getReturns();
			}

			@Override
			public String toCanonicalString() {
				return "function(" + WyilFile.toCanonicalString(getParameters()) + ")->("
						+ WyilFile.toCanonicalString(getReturns()) + ")";
			}
		}

		/**
		 * Represents the set of all method values. These are impure and may have
		 * side-effects (e.g. performing I/O, updating non-local state, etc). A method
		 * may have zero returns and, in such case, the effect of a method comes through
		 * other side-effects. Methods may also have captured lifetime arguments, and
		 * may themselves declare lifetime arguments.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Method extends AbstractType implements Type.Callable {

			public Method(Tuple<Type> parameters, Tuple<Type> returns, Tuple<Identifier> captures,
					Tuple<Identifier> lifetimes) {
				super(TYPE_method, new SyntacticItem[] { parameters, returns, captures, lifetimes });
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Type> getParameters() {
				return (Tuple<Type>) get(0);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Type> getReturns() {
				return (Tuple<Type>) get(1);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Identifier> getCapturedLifetimes() {
				return (Tuple<Identifier>) get(2);
			}

			@SuppressWarnings("unchecked")
			public Tuple<Identifier> getLifetimeParameters() {
				return (Tuple<Identifier>) get(3);
			}

			@Override
			public Type.Method substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				// Sanity check the binding being used here. Specifically, any binding which is
				// declared in this method cannot be subsitituted.
				Tuple<Identifier> lifetimes = getLifetimeParameters();
				binding = removeFromBinding(binding,lifetimes);
				// Proceed with the potentially updated binding
				Tuple<Type> parametersBefore = getParameters();
				Tuple<Type> parametersAfter = WyilFile.substitute(parametersBefore, binding);
				Tuple<Type> returnsBefore = getReturns();
				Tuple<Type> returnsAfter = WyilFile.substitute(returnsBefore, binding);
				if (parametersBefore == parametersAfter && returnsBefore == returnsAfter) {
					return this;
				} else {
					return new Type.Method(parametersAfter, returnsAfter, getCapturedLifetimes(),
							getLifetimeParameters());
				}
			}

			@Override
			public String toString() {
				Tuple<Identifier> captured = getCapturedLifetimes();
				Tuple<Identifier> lifetimes = getLifetimeParameters();
				String r = "method";
				if (captured.size() != 0) {
					r += "[" + captured.toBareString() + "]";
				}
				if (lifetimes.size() != 0) {
					r += "<" + lifetimes.toBareString() + ">";
				}
				return r + getParameters().toString() + "->" + getReturns();
			}

			@Override
			public String toCanonicalString() {
				Tuple<Identifier> captured = getCapturedLifetimes();
				Tuple<Identifier> lifetimes = getLifetimeParameters();
				String r = "method";
				if (captured.size() != 0) {
					r += "[" + captured.toBareString() + "]";
				}
				if (lifetimes.size() != 0) {
					r += "<" + lifetimes.toBareString() + ">";
				}
				return r + "(" + WyilFile.toCanonicalString(getParameters()) + ")->("
						+ WyilFile.toCanonicalString(getReturns()) + ")";
			}

			@SuppressWarnings("unchecked")
			@Override
			public Method clone(SyntacticItem[] operands) {
				return new Method((Tuple<Type>) operands[0], (Tuple<Type>) operands[1], (Tuple<Identifier>) operands[2],
						(Tuple<Identifier>) operands[3]);
			}
		}

		/**
		 * Represents the set of all proeprty values. These are pure predicates,
		 * sometimes also called "mathematical" functions. A property cannot have any
		 * side-effects and always returns the boolean true.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Property extends AbstractType implements Type.Callable {
			public Property(Tuple<Type> parameters) {
				super(TYPE_property, parameters, new Tuple<>(new Type.Bool()));
			}

			public Property(Tuple<Type> parameters, Tuple<Type> returns) {
				super(TYPE_property, parameters, returns);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Type> getParameters() {
				return (Tuple<Type>) get(0);
			}

			@Override
			@SuppressWarnings("unchecked")
			public Tuple<Type> getReturns() {
				return (Tuple<Type>) get(1);
			}

			@Override
			public Type.Property substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				Tuple<Type> parametersBefore = getParameters();
				Tuple<Type> parametersAfter = WyilFile.substitute(parametersBefore, binding);
				Tuple<Type> returnsBefore = getReturns();
				Tuple<Type> returnsAfter = WyilFile.substitute(returnsBefore, binding);
				if (parametersBefore == parametersAfter && returnsBefore == returnsAfter) {
					return this;
				} else {
					return new Type.Property(parametersAfter, returnsAfter);
				}
			}

			@SuppressWarnings("unchecked")
			@Override
			public Property clone(SyntacticItem[] operands) {
				return new Property((Tuple<Type>) operands[0], (Tuple<Type>) operands[1]);
			}

			@Override
			public String toString() {
				return "property" + getParameters().toString() + "->" + getReturns();
			}

			@Override
			public String toCanonicalString() {
				return "property" + WyilFile.toCanonicalString(getParameters()) + ")->("
						+ WyilFile.toCanonicalString(getReturns()) + ")";
			}
		}

		public static class Unknown extends AbstractType implements Callable {
			public Unknown() {
				super(TYPE_unknown);
			}

			@Override
			public Tuple<Type> getParameters() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Tuple<Type> getReturns() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Type.Unknown substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				throw new UnsupportedOperationException();
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Unknown();
			}

			@Override
			public String toString() {
				return "(???)->(???)";
			}

			@Override
			public String toCanonicalString() {
				throw new UnsupportedOperationException();
			}
		}

		public static class Variable extends AbstractSemanticType implements Atom {
			public Variable(Identifier name) {
				super(TYPE_variable, name);
			}

			public Identifier getOperand() {
				return (Identifier) get(0);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Variable((Identifier) operands[0]);
			}

			@Override
			public Type substitute(java.util.function.Function<Identifier, SyntacticItem> binding) {
				SyntacticItem expanded = binding.apply(getOperand());
				if (expanded instanceof Type) {
					return (Type) expanded;
				} else {
					return this;
				}
			}

			@Override
			public String toCanonicalString() {
				return toString();
			}

			@Override
			public String toString() {
				return getOperand().toString();
			}
		}
	}

	private static Type[] substitute(Type[] types, java.util.function.Function<Identifier, SyntacticItem> binding) {
		Type[] nTypes = types;
		for (int i = 0; i != nTypes.length; ++i) {
			Type before = types[i];
			Type after = before.substitute(binding);
			if (before != after) {
				if (nTypes == types) {
					nTypes = Arrays.copyOf(types, types.length);
				}
				nTypes[i] = after;
			}
		}
		//
		return nTypes;
	}

	public static Tuple<Type> substitute(Tuple<Type> types, java.util.function.Function<Identifier, SyntacticItem> binding) {
		for (int i = 0; i != types.size(); ++i) {
			Type before = types.get(i);
			Type after = before.substitute(binding);
			if (before != after) {
				// Now committed to a change
				Type[] nTypes = types.toArray(Type.class);
				nTypes[i] = after;
				for (int j = i + 1; j < types.size(); ++j) {
					nTypes[j] = types.get(j).substitute(binding);
				}
				return new Tuple<>(nTypes);
			}
		}
		//
		return types;
	}


	/**
	 * Apply an explicit binding to a given function, method or property declaration
	 * via substitution. Observe we cannot just use the existing Type.substitute
	 * method as this accounts for lifetime captures. Therefore, we first build the
	 * binding and then apply it to each of the parameters and returns.
	 *
	 * @param fmp
	 * @param templateArguments
	 * @return
	 */
	public static Type.Callable substitute(Type.Callable fmp, Tuple<Template.Variable> templateParameters,
			Tuple<SyntacticItem> templateArguments) {
		Function<Identifier,SyntacticItem> binding = WyilFile.bindingFunction(templateParameters,templateArguments);
		// Proceed with the potentially updated binding
		Tuple<Type> parameters = WyilFile.substitute(fmp.getParameters(), binding);
		Tuple<Type> returns = WyilFile.substitute(fmp.getReturns(), binding);
		//
		if(fmp instanceof Type.Method) {
			Type.Method m = (Type.Method) fmp;
			// FIXME: this looks wrong!!!
			return new Type.Method(parameters, returns, m.getCapturedLifetimes(), new Tuple<>());
		} else if(fmp instanceof Type.Function) {
			return new Type.Function(parameters, returns);
		} else {
			return new Type.Property(parameters, returns);
		}
	}

	private static String toCanonicalString(Tuple<Type> types) {
		String r = "";
		for (int i = 0; i != types.size(); ++i) {
			if (i != 0) {
				r += ",";
			}
			r += types.get(i).toCanonicalString();
		}
		return r;
	}

	private static String toString(Tuple<? extends SyntacticItem> items) {
		String r = "";
		for (int i = 0; i != items.size(); ++i) {
			if (i != 0) {
				r += ",";
			}
			r += items.get(i).toString();
		}
		return r;
	}

	// ============================================================
	// Semantic Types
	// ============================================================

	public static interface SemanticType extends SyntacticItem {

		public static abstract class AbstractSemanticType extends AbstractSyntacticItem implements SemanticType {

			public AbstractSemanticType(int opcode, SyntacticItem... operands) {
				super(opcode, operands);
			}

		}

		public interface Atom extends SemanticType {
		}

		public static interface Leaf extends SemanticType {
		}

		/**
		 * A semantic type combinator represents either the intersection or union of
		 * semantic types. Thus, it is an operator over one or more component types.
		 *
		 * @author David J. Pearce
		 *
		 */
		public interface Combinator extends SemanticType {
			/**
			 * Get number of component types in this combinator
			 */
			@Override
			public int size();

			/**
			 * Get the ith component type in this combinator
			 */
			@Override
			public SemanticType get(int i);

			/**
			 * Get all component types in this combinator
			 */
			@Override
			public SemanticType[] getAll();
		}

		public static class Reference extends AbstractSemanticType implements SemanticType.Atom {
			public Reference(SemanticType element) {
				super(SEMTYPE_staticreference, element);
			}

			public Reference(SemanticType element, Identifier lifetime) {
				super(SEMTYPE_reference, element, lifetime);
			}

			protected Reference(int opcode, SemanticType element) {
				// NOTE: this is specifically to handle Type.Reference
				super(opcode, element);
				if (opcode != TYPE_reference && opcode != TYPE_staticreference) {
					throw new IllegalArgumentException("invalid opcode");
				}
			}

			protected Reference(int opcode, SemanticType element, Identifier lifetime) {
				// NOTE: this is specifically to handle Type.Reference
				super(opcode, element, lifetime);
				if (opcode != TYPE_reference) {
					throw new IllegalArgumentException("invalid opcode");
				}
			}

			public boolean hasLifetime() {
				return opcode == SEMTYPE_reference;
			}

			public SemanticType getElement() {
				return (SemanticType) get(0);
			}

			public Identifier getLifetime() {
				return (Identifier) get(1);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				if (operands.length == 1) {
					return new Reference((SemanticType) operands[0]);
				} else {
					return new Reference((SemanticType) operands[0], (Identifier) operands[1]);
				}
			}

			@Override
			public String toString() {
				if (hasLifetime()) {
					Identifier lifetime = getLifetime();
					return "&" + lifetime + ":" + braceAsNecessary(getElement());
				} else {
					return "&" + braceAsNecessary(getElement());
				}
			}
		}

		public static class Array extends AbstractSemanticType implements SemanticType.Atom {

			public Array(SemanticType element) {
				super(SEMTYPE_array, element);
			}

			protected Array(int opcode, SemanticType element) {
				// NOTE: this is specifically to handle Type.Array
				super(opcode, element);
				if (opcode != TYPE_array) {
					throw new IllegalArgumentException("invalid opcode");
				}
			}

			public SemanticType getElement() {
				return (SemanticType) get(0);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Array((SemanticType) operands[0]);
			}

			@Override
			public String toString() {
				return braceAsNecessary(getElement()) + "[]";
			}
		}

		public static class Field extends AbstractSyntacticItem {

			public Field(Identifier name, SemanticType type) {
				super(SEMTYPE_field, name, type);
			}

			protected Field(int opcode, Identifier name, SemanticType type) {
				// NOTE: specifically to handle Type.Field
				super(opcode, name, type);
				if (opcode != TYPE_field) {
					throw new IllegalArgumentException("invalid opcode");
				}
			}

			public Identifier getName() {
				return (Identifier) get(0);
			}

			public SemanticType getType() {
				return (SemanticType) get(1);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Field((Identifier) operands[0], (SemanticType) operands[1]);
			}
		}

		public static class Record extends AbstractSemanticType implements SemanticType.Atom {
			public Record(boolean isOpen, Tuple<Field> fields) {
				super(SEMTYPE_record, new Value.Bool(isOpen), fields);
			}

			public Record(Value.Bool isOpen, Tuple<Field> fields) {
				super(SEMTYPE_record, isOpen, fields);
			}

			protected Record(int opcode, Value.Bool isOpen, Tuple<Type.Field> fields) {
				// NOTE: this is specifically to handle Type.Record
				super(opcode, isOpen, fields);
				if (opcode != TYPE_record) {
					throw new IllegalArgumentException("invalid opcode");
				}
			}

			public boolean isOpen() {
				Value.Bool flag = (Value.Bool) get(0);
				return flag.get();
			}

			public Tuple<? extends Field> getFields() {
				return (Tuple<Field>) get(1);
			}

			public SemanticType getField(Identifier fieldName) {
				Tuple<? extends Field> fields = getFields();
				for (int i = 0; i != fields.size(); ++i) {
					Field vd = fields.get(i);
					Identifier declaredFieldName = vd.getName();
					if (declaredFieldName.equals(fieldName)) {
						return vd.getType();
					}
				}
				return null;
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Record(((Value.Bool) operands[0]), (Tuple<Field>) operands[1]);
			}

			@Override
			public String toString() {
				String r = "{";
				Tuple<? extends Field> fields = getFields();
				for (int i = 0; i != fields.size(); ++i) {
					if (i != 0) {
						r += ",";
					}
					Field field = fields.get(i);
					r += field.getType() + " " + field.getName();
				}
				if (isOpen()) {
					if (fields.size() > 0) {
						r += ", ...";
					} else {
						r += "...";
					}
				}
				return r + "}";
			}
		}

		public static class Union extends AbstractSemanticType implements Combinator {
			public Union(SemanticType... types) {
				super(SEMTYPE_union, types);
			}

			protected Union(int opcode, SemanticType... types) {
				// NOTE: this is specifically to handle Type.Union
				super(opcode, types);
				if (opcode != TYPE_union) {
					throw new IllegalArgumentException("invalid opcode");
				}
			}

			@Override
			public SemanticType get(int i) {
				return (SemanticType) super.get(i);
			}

			@Override
			public SemanticType[] getAll() {
				return (SemanticType[]) super.getAll();
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Union(ArrayUtils.toArray(SemanticType.class, operands));
			}

			@Override
			public String toString() {
				String r = "";
				for (int i = 0; i != size(); ++i) {
					if (i != 0) {
						r += "|";
					}
					r += braceAsNecessary(get(i));
				}
				return r;
			}
		}

		public static class Intersection extends AbstractSemanticType implements Combinator {
			public Intersection(SemanticType... types) {
				super(SEMTYPE_intersection, types);
			}

			@Override
			public SemanticType get(int i) {
				return (SemanticType) super.get(i);
			}

			@Override
			public SemanticType[] getAll() {
				return (SemanticType[]) super.getAll();
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Intersection(ArrayUtils.toArray(SemanticType.class, operands));
			}

			@Override
			public String toString() {
				String r = "";
				for (int i = 0; i != size(); ++i) {
					if (i != 0) {
						r += "&";
					}
					r += braceAsNecessary(get(i));
				}
				return r;
			}
		}

		public static class Difference extends AbstractSemanticType {

			public Difference(SemanticType lhs, SemanticType rhs) {
				super(SEMTYPE_difference, lhs, rhs);
			}

			public SemanticType getLeftHandSide() {
				return (SemanticType) get(0);
			}

			public SemanticType getRightHandSide() {
				return (SemanticType) get(1);
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Difference((SemanticType) operands[0], (SemanticType) operands[1]);
			}

			@Override
			public String toString() {
				return braceAsNecessary(getLeftHandSide()) + "-" + braceAsNecessary(getRightHandSide());
			}
		}
	}

	// ============================================================
	// Modifiers
	// ============================================================

	/**
	 * <p>
	 * Represents an arbitrary modifier on a declaration. For example, all
	 * declarations (e.g. functions, types, etc) can be marked as
	 * <code>public</code> or <code>private</code>. The following illustrates:
	 * </p>
	 *
	 * <pre>
	 * public function square(int x, int y) -> int:
	 *    return x * y
	 * </pre>
	 * <p>
	 * The <code>public</code> modifier used above indicates that the function
	 * <code>square</code> can be accessed from outside its enclosing module.
	 * </p>
	 * <p>
	 * The modifiers <code>native</code> and <code>export</code> are used to enable
	 * inter-operation with other languages. By declaring a function or method as
	 * <code>native</code> you are signaling that its implementation is provided
	 * elsewhere (e.g. it's implemented in Java code directly). By marking a
	 * function or method with <code>export</code>, you are declaring that external
	 * code may call it. For example, you have some Java code that needs to call it.
	 * The modifier is required because, by default, all the names of all methods
	 * and functions are <i>mangled</i> to include type information and enable
	 * overloading. Therefore, a method/function marked with <code>export</code>
	 * will generate a function without name mangling.
	 * </p>
	 *
	 * @author David J. Pearce
	 *
	 */
	public interface Modifier extends SyntacticItem {

		public static final class Public extends AbstractSyntacticItem implements Modifier {
			public Public() {
				super(MOD_public);
			}

			@Override
			public String toString() {
				return "public";
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Public();
			}
		}

		public static final class Private extends AbstractSyntacticItem implements Modifier {
			public Private() {
				super(MOD_private);
			}

			@Override
			public String toString() {
				return "private";
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Private();
			}
		}

		public static final class Native extends AbstractSyntacticItem implements Modifier {
			public Native() {
				super(MOD_native);
			}

			@Override
			public String toString() {
				return "native";
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Native();
			}
		}

		public static final class Export extends AbstractSyntacticItem implements Modifier {
			public Export() {
				super(MOD_export);
			}

			@Override
			public String toString() {
				return "export";
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Export();
			}
		}

		public static final class Final extends AbstractSyntacticItem implements Modifier {
			public Final() {
				super(MOD_final);
			}

			@Override
			public String toString() {
				return "final";
			}

			@Override
			public SyntacticItem clone(SyntacticItem[] operands) {
				return new Final();
			}
		}
	}

	public interface Linkable {
		/**
		 * Get the link associated with this linkable expression.
		 * @return
		 */
		Decl.Link<? extends Decl.Named> getLink();
	}

	public interface Bindable extends Linkable, SyntacticItem {
		/**
		 * Get the binding associated with this bindable expression.
		 *
		 * @return
		 */
		Decl.Binding<? extends Type, ? extends Decl.Callable> getBinding();
	}

	/**
	 * Create a simple binding function from two tuples representing the key set and
	 * value set respectively.
	 *
	 * @param variables
	 * @param arguments
	 * @return
	 */
	public static <T extends SyntacticItem> java.util.function.Function<Identifier, SyntacticItem> bindingFunction(
			Tuple<Template.Variable> variables, Tuple<T> arguments) {
		//
		return (Identifier var) -> {
			for (int i = 0; i != variables.size(); ++i) {
				if (var.equals(variables.get(i).getName())) {
					return arguments.get(i);
				}
			}
			return null;
		};
	}

	/**
	 * Construct a binding function from another binding where a given set of
	 * variables are removed. This is necessary in situations where the given
	 * variables are captured.
	 *
	 * @param binding
	 * @param variables
	 * @return
	 */
	public static java.util.function.Function<Identifier, SyntacticItem> removeFromBinding(
			java.util.function.Function<Identifier, SyntacticItem> binding, Tuple<Identifier> variables) {
		return (Identifier var) -> {
			// Sanity check whether this is a variable which is being removed
			for (int i = 0; i != variables.size(); ++i) {
				if (var.equals(variables.get(i))) {
					return null;
				}
			}
			// Not being removed, reuse existing binding
			return binding.apply(var);
		};
	}

	// ============================================================
	// Attributes
	// ============================================================
	public static class SyntaxError extends AbstractSyntacticItem implements SyntacticItem.Marker {

		public SyntaxError(int errcode, SyntacticItem target) {
			super(ATTR_error, BigInteger.valueOf(errcode).toByteArray(), target, new Tuple<>());
		}

		public SyntaxError(int errcode, SyntacticItem target, Tuple<SyntacticItem> context) {
			super(ATTR_error, BigInteger.valueOf(errcode).toByteArray(), target, context);
		}

		@Override
		public SyntacticItem getTarget() {
			return operands[0];
		}

		public Tuple<SyntacticItem> getContext() {
			return (Tuple<SyntacticItem>) operands[1];
		}

		/**
		 * Get the error code associated with this message
		 *
		 * @return
		 */
		public int getErrorCode() {
			return new BigInteger(getData()).intValue();
		}

		@Override
		public SyntacticItem clone(SyntacticItem[] operands) {
			return new SyntaxError(getErrorCode(), operands[0], (Tuple<SyntacticItem>) operands[1]);
		}

		@Override
		public String getMessage() {
			// Done
			return ErrorMessages.getErrorMessage(getErrorCode(), getContext());
		}

		@Override
		public Path.ID getSource() {
			Decl.Unit unit = getTarget().getAncestor(Decl.Unit.class);
			// FIXME: this is realy a temporary hack
			String nameStr = unit.getName().toString().replace("::", "/");
			return Trie.fromString(nameStr);
		}
	}

	// Types
	public static final int SUBTYPE_ERROR = 400;
	public static final int EMPTY_TYPE = 401;
	public static final int EXPECTED_ARRAY = 402;
	public static final int EXPECTED_RECORD = 403;
	public static final int EXPECTED_REFERENCE = 404;
	public static final int EXPECTED_LAMBDA = 405;
	public static final int INVALID_FIELD = 406;
	public static final int RESOLUTION_ERROR = 407;
	public static final int AMBIGUOUS_COERCION = 408;
	public static final int MISSING_TEMPLATE_PARAMETERS = 409;
	public static final int TOOMANY_TEMPLATE_PARAMETERS = 410;
	// Statements
	public static final int MISSING_RETURN_STATEMENT = 500;
	public static final int UNREACHABLE_CODE = 504;
	public static final int BRANCH_ALWAYS_TAKEN = 506;
	public static final int TOO_MANY_RETURNS = 507;
	public static final int INSUFFICIENT_RETURNS = 508;
	public static final int CYCLIC_STATIC_INITIALISER = 509;
	// Expressions
	public static final int VARIABLE_POSSIBLY_UNITIALISED = 601;
	public static final int INCOMPARABLE_OPERANDS = 602;
	public static final int INSUFFICIENT_ARGUMENTS = 603;
	public static final int AMBIGUOUS_CALLABLE = 604;
	public static final int PARAMETER_REASSIGNED = 605;
	public static final int FINAL_VARIABLE_REASSIGNED = 606;
	public static final int ALLOCATION_NOT_PERMITTED = 607;
	public static final int METHODCALL_NOT_PERMITTED = 608;
	public static final int REFERENCE_ACCESS_NOT_PERMITTED = 609;
	public static final int INVALID_LVAL_EXPRESSION = 610;
	//

	// ==============================================================================
	//
	// ==============================================================================

	private static String canonicalBraceAsNecessary(Type type) {
		String str = type.toCanonicalString();
		if (needsBraces(type)) {
			return "(" + str + ")";
		} else {
			return str;
		}
	}

	private static String braceAsNecessary(Type type) {
		String str = type.toString();
		if (needsBraces(type)) {
			return "(" + str + ")";
		} else {
			return str;
		}
	}

	private static String braceAsNecessary(SemanticType type) {
		String str = type.toString();
		if (needsBraces(type)) {
			return "(" + str + ")";
		} else {
			return str;
		}
	}

	private static boolean needsBraces(SemanticType type) {
		if (type instanceof Type.Atom || type instanceof Type.Nominal) {
			return false;
		} else {
			return true;
		}
	}

	// =========================================================================
	// Schema
	// =========================================================================
	private static volatile SyntacticItem.Schema[] SCHEMA = null;

	public static SyntacticItem.Schema[] getSchema() {
		if (SCHEMA == null) {
			SCHEMA = createSchema();
		}
		return SCHEMA;
	}

	private static SyntacticItem.Schema[] createSchema() {
		SyntacticItem.Schema[] schema = AbstractCompilationUnit.getSchema();
		schema = Arrays.copyOf(schema, 256);
		// ==========================================================================
		schema[DECL_unknown] = new Schema(Operands.ZERO, Data.ZERO, "DECL_unknown") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Unknown();
			}
		};
		schema[DECL_module] = new Schema(Operands.FOUR, Data.ZERO, "DECL_module") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Module((Name) operands[0], (Tuple<Decl.Unit>) operands[1],
						(Tuple<Decl.Unit>) operands[2], (Tuple<SyntacticItem.Marker>) operands[3]);
			}
		};
		schema[DECL_unit] = new Schema(Operands.TWO, Data.ZERO, "DECL_unit") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Unit((Name) operands[0], (Tuple<Decl>) operands[1]);
			}
		};
		schema[DECL_import] = new Schema(Operands.ONE, Data.ZERO, "DECL_import") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Import((Tuple<Identifier>) operands[0]);
			}
		};
		schema[DECL_importfrom] = new Schema(Operands.TWO, Data.ZERO, "DECL_importfrom") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Import((Tuple<Identifier>) operands[0], (Identifier) operands[1]);
			}
		};
		schema[DECL_staticvar] = new Schema(Operands.FOUR, Data.ZERO, "DECL_staticvar") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.StaticVariable((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Type) operands[2], (Expr) operands[3]);
			}
		};
		schema[DECL_type] = new Schema(Operands.FIVE, Data.ZERO, "DECL_type") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Type((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Decl.Variable) operands[3], (Tuple<Expr>) operands[4]);
			}
		};
		schema[DECL_rectype] = new Schema(Operands.FIVE, Data.ZERO, "DECL_rectype") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				Decl.Type r = new Decl.Type((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Decl.Variable) operands[3], (Tuple<Expr>) operands[4]);
				r.setRecursive();
				return r;
			}
		};
		schema[DECL_function] = new Schema(Operands.EIGHT, Data.ZERO, "DECL_function") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Function((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Tuple<Decl.Variable>) operands[3],
						(Tuple<Decl.Variable>) operands[4], (Tuple<Expr>) operands[5], (Tuple<Expr>) operands[6],
						(Stmt.Block) operands[7]);
			}
		};
		schema[DECL_method] = new Schema(Operands.EIGHT, Data.ZERO, "DECL_method") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Method((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Tuple<Decl.Variable>) operands[3],
						(Tuple<Decl.Variable>) operands[4], (Tuple<Expr>) operands[5], (Tuple<Expr>) operands[6],
						(Stmt.Block) operands[7]);
			}
		};
		schema[DECL_property] = new Schema(Operands.SIX, Data.ZERO, "DECL_property") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Property((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Tuple<Decl.Variable>) operands[3],
						(Tuple<Decl.Variable>) operands[4], (Tuple<Expr>) operands[5]);
			}
		};
		schema[DECL_lambda] = new Schema(Operands.NINE, Data.ZERO, "DECL_lambda") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Lambda((Tuple<Modifier>) operands[0], (Identifier) operands[1],
						(Tuple<Template.Variable>) operands[2], (Tuple<Decl.Variable>) operands[3],
						(Tuple<Decl.Variable>) operands[4], (Tuple<Identifier>) operands[5],
						(Tuple<Identifier>) operands[6], (Expr) operands[7], (Type.Callable) operands[8]);
			}
		};
		schema[DECL_variable] = new Schema(Operands.THREE, Data.ZERO, "DECL_var") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Variable((Tuple<Modifier>) operands[0], (Identifier) operands[1], (Type) operands[2]);
			}
		};
		schema[DECL_variableinitialiser] = new Schema(Operands.FOUR, Data.ZERO, "DECL_varinit") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Variable((Tuple<Modifier>) operands[0], (Identifier) operands[1], (Type) operands[2],
						(Expr) operands[3]);
			}
		};
		schema[DECL_link] = new Schema(Operands.MANY,Data.ZERO, "ITEM_link") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Link(DECL_link, operands);
			}
		};
		schema[DECL_binding] = new Schema(Operands.TWO, Data.ZERO, "DECL_binding") {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Decl.Binding((Decl.Link) operands[0], (Tuple<SyntacticItem>) operands[1]);
			}
		};
		schema[TEMPLATE_type] = new Schema(Operands.ONE, Data.ZERO, "TEMPLATE_type") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Template.Type((Identifier) operands[0]);
			}
		};
		schema[TEMPLATE_lifetime] = new Schema(Operands.ONE, Data.ZERO, "TEMPLATE_lifetime") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Template.Lifetime((Identifier) operands[0]);
			}
		};
		schema[MOD_native] = new Schema(Operands.ZERO, Data.ZERO, "MOD_native") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Modifier.Native();
			}
		};
		schema[MOD_export] = new Schema(Operands.ZERO, Data.ZERO, "MOD_export") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Modifier.Export();
			}
		};
		schema[MOD_final] = new Schema(Operands.ZERO, Data.ZERO, "MOD_final") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Modifier.Final();
			}
		};
		schema[MOD_private] = new Schema(Operands.ZERO, Data.ZERO, "MOD_private") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Modifier.Private();
			}
		};
		schema[MOD_public] = new Schema(Operands.ZERO, Data.ZERO, "MOD_public") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Modifier.Public();
			}
		};
		schema[ATTR_error] = new Schema(Operands.MANY, Data.TWO, "ATTR_error") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				int errcode = new BigInteger(data).intValue();
				return new SyntaxError(errcode, operands[0], (Tuple<SyntacticItem>) operands[1]);
			}
		};
		// TYPES: 00100000 (32) -- 00111111 (63)
		schema[TYPE_void] = new Schema(Operands.ZERO, Data.ZERO, "TYPE_void") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Void();
			}
		};
		schema[TYPE_any] = new Schema(Operands.ZERO, Data.ZERO, "TYPE_any") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Any();
			}
		};
		schema[TYPE_null] = new Schema(Operands.ZERO, Data.ZERO, "TYPE_null") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Null();
			}
		};
		schema[TYPE_bool] = new Schema(Operands.ZERO, Data.ZERO, "TYPE_bool") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Bool();
			}
		};
		schema[TYPE_int] = new Schema(Operands.ZERO, Data.ZERO, "TYPE_int") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Int();
			}
		};
		schema[TYPE_nominal] = new Schema(Operands.TWO, Data.ZERO, "TYPE_nominal") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Nominal((Decl.Link<Decl.Type>) operands[0], (Tuple<Type>) operands[1]);
			}
		};
		schema[TYPE_staticreference] = new Schema(Operands.ONE, Data.ZERO, "TYPE_staticreference") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Reference((Type) operands[0]);
			}
		};
		schema[TYPE_reference] = new Schema(Operands.MANY, Data.ZERO, "TYPE_reference") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Reference((Type) operands[0], (Identifier) operands[1]);
			}
		};
		schema[TYPE_array] = new Schema(Operands.ONE, Data.ZERO, "TYPE_array") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Array((Type) operands[0]);
			}
		};
		schema[TYPE_record] = new Schema(Operands.TWO, Data.ZERO, "TYPE_record") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Record((Value.Bool) operands[0], (Tuple<Type.Field>) operands[1]);
			}
		};
		schema[TYPE_field] = new Schema(Operands.TWO, Data.ZERO, "TYPE_field") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Field((Identifier) operands[0], (Type) operands[1]);
			}
		};
		schema[TYPE_function] = new Schema(Operands.TWO, Data.ZERO, "TYPE_function") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Function((Tuple<Type>) operands[0], (Tuple<Type>) operands[1]);
			}
		};
		schema[TYPE_method] = new Schema(Operands.FOUR, Data.ZERO, "TYPE_method") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Method((Tuple<Type>) operands[0], (Tuple<Type>) operands[1],
						(Tuple<Identifier>) operands[2], (Tuple<Identifier>) operands[3]);
			}
		};
		schema[TYPE_property] = new Schema(Operands.TWO, Data.ZERO, "TYPE_property") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Property((Tuple<Type>) operands[0], (Tuple<Type>) operands[1]);
			}
		};
		schema[TYPE_union] = new Schema(Operands.MANY, Data.ZERO, "TYPE_union") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Union(ArrayUtils.toArray(Type.class, operands));
			}
		};
		schema[TYPE_byte] = new Schema(Operands.ZERO, Data.ZERO, "TYPE_byte") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Byte();
			}
		};
		schema[TYPE_unknown] = new Schema(Operands.ZERO, Data.ZERO, "TYPE_unresolved") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Unknown();
			}
		};
		schema[TYPE_recursive] = new Schema(Operands.ONE, Data.ZERO, "TYPE_recursive") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Recursive((Ref<Type>) operands[0]);
			}
		};
		schema[TYPE_variable] = new Schema(Operands.ONE, Data.ZERO, "TYPE_variable") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Type.Variable((Identifier) operands[0]);
			}
		};
		schema[SEMTYPE_reference] = new Schema(Operands.TWO, Data.ZERO, "SEMTYPE_reference") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new SemanticType.Reference((SemanticType) operands[0]);
			}
		};
		schema[SEMTYPE_staticreference] = new Schema(Operands.ONE, Data.ZERO, "SEMTYPE_staticreference") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new SemanticType.Reference((SemanticType) operands[0], (Identifier) operands[1]);
			}
		};
		schema[SEMTYPE_array] = new Schema(Operands.ONE, Data.ZERO, "SEMTYPE_array") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new SemanticType.Reference((SemanticType) operands[0]);
			}
		};
		schema[SEMTYPE_record] = new Schema(Operands.TWO, Data.ZERO, "SEMTYPE_record") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new SemanticType.Record(((Value.Bool) operands[0]).get(),
						(Tuple<SemanticType.Field>) operands[1]);
			}
		};
		schema[SEMTYPE_field] = new Schema(Operands.TWO, Data.ZERO, "SEMTYPE_field") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new SemanticType.Field((Identifier) operands[0], (SemanticType) operands[1]);
			}
		};
		schema[SEMTYPE_union] = new Schema(Operands.MANY, Data.ZERO, "SEMTYPE_union") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new SemanticType.Union(ArrayUtils.toArray(SemanticType.class, operands));
			}
		};
		schema[SEMTYPE_intersection] = new Schema(Operands.MANY, Data.ZERO, "SEMTYPE_intersection") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new SemanticType.Intersection(ArrayUtils.toArray(SemanticType.class, operands));
			}
		};
		schema[SEMTYPE_difference] = new Schema(Operands.TWO, Data.ZERO, "SEMTYPE_difference") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new SemanticType.Difference((SemanticType) operands[0], (SemanticType) operands[1]);
			}
		};
		;

		// STATEMENTS: 01000000 (64) -- 001011111 (95)
		schema[STMT_block] = new Schema(Operands.MANY, Data.ZERO, "STMT_block") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Block(ArrayUtils.toArray(Stmt.class, operands));
			}
		};
		schema[STMT_namedblock] = new Schema(Operands.TWO, Data.ZERO, "STMT_namedblock") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.NamedBlock((Identifier) operands[0], (Stmt.Block) operands[1]);
			}
		};
		schema[STMT_caseblock] = new Schema(Operands.TWO, Data.ZERO, "STMT_caseblock") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Case((Tuple<Expr>) operands[0], (Stmt.Block) operands[1]);
			}
		};
		schema[STMT_assert] = new Schema(Operands.ONE, Data.ZERO, "STMT_assert") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Assert((Expr) operands[0]);
			}
		};
		schema[STMT_assign] = new Schema(Operands.TWO, Data.ZERO, "STMT_assign") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Assign((Tuple<LVal>) operands[0], (Tuple<Expr>) operands[1]);
			}
		};
		schema[STMT_assume] = new Schema(Operands.ONE, Data.ZERO, "STMT_assume") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Assume((Expr) operands[0]);
			}
		};
		schema[STMT_debug] = new Schema(Operands.ONE, Data.ZERO, "STMT_debug") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Debug((Expr) operands[0]);
			}
		};
		schema[STMT_skip] = new Schema(Operands.ZERO, Data.ZERO, "STMT_skip") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Skip();
			}
		};
		schema[STMT_break] = new Schema(Operands.ZERO, Data.ZERO, "STMT_break") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Break();
			}
		};
		schema[STMT_continue] = new Schema(Operands.ZERO, Data.ZERO, "STMT_continue") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Continue();
			}
		};
		schema[STMT_dowhile] = new Schema(Operands.FOUR, Data.ZERO, "STMT_dowhile") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.DoWhile((Expr) operands[0], (Tuple<Expr>) operands[1],
						(Tuple<Decl.Variable>) operands[2], (Stmt.Block) operands[3]);
			}
		};
		schema[STMT_fail] = new Schema(Operands.ZERO, Data.ZERO, "STMT_fail") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Fail();
			}
		};
		schema[STMT_if] = new Schema(Operands.TWO, Data.ZERO, "STMT_if") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.IfElse((Expr) operands[0], (Stmt.Block) operands[1]);
			}
		};
		schema[STMT_ifelse] = new Schema(Operands.THREE, Data.ZERO, "STMT_ifelse") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.IfElse((Expr) operands[0], (Stmt.Block) operands[1], (Stmt.Block) operands[2]);
			}
		};
		schema[STMT_return] = new Schema(Operands.MANY, Data.ZERO, "STMT_return") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Return((Tuple<Expr>) operands[0]);
			}
		};
		schema[STMT_switch] = new Schema(Operands.TWO, Data.ZERO, "STMT_switch") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.Switch((Expr) operands[0], (Tuple<Stmt.Case>) operands[1]);
			}
		};
		schema[STMT_while] = new Schema(Operands.FOUR, Data.ZERO, "STMT_while") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Stmt.While((Expr) operands[0], (Tuple<Expr>) operands[1], (Tuple<Decl.Variable>) operands[2],
						(Stmt.Block) operands[3]);
			}
		};
		// EXPRESSIONS: 01100000 (96) -- 10011111 (159)
		schema[EXPR_variablecopy] = new Schema(Operands.TWO, Data.ZERO, "EXPR_variablecopy") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.VariableAccess((Type) operands[0], (Decl.Variable) operands[1]);
			}
		};
		schema[EXPR_variablemove] = new Schema(Operands.TWO, Data.ZERO, "EXPR_variablemove") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				Expr.VariableAccess v = new Expr.VariableAccess((Type) operands[0], (Decl.Variable) operands[1]);
				v.setMove();
				return v;
			}
		};
		schema[EXPR_staticvariable] = new Schema(Operands.TWO, Data.ZERO, "EXPR_staticvariable") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.StaticVariableAccess((Type) operands[0], (Decl.Link<Decl.StaticVariable>) operands[1]);
			}
		};
		schema[EXPR_constant] = new Schema(Operands.TWO, Data.ZERO, "EXPR_constant") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.Constant((Type) operands[0], (Value) operands[1]);
			}
		};
		schema[EXPR_cast] = new Schema(Operands.TWO, Data.ZERO, "EXPR_cast") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.Cast((Type) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_invoke] = new Schema(Operands.TWO, Data.ZERO, "EXPR_invoke") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.Invoke((Decl.Binding<Type.Callable, Decl.Callable>) operands[0],
						(Tuple<Expr>) operands[1]);
			}
		};
		schema[EXPR_indirectinvoke] = new Schema(Operands.FOUR, Data.ZERO, "EXPR_indirectinvoke") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IndirectInvoke((Type) operands[0], (Expr) operands[1], (Tuple<Identifier>) operands[2],
						(Tuple<Expr>) operands[3]);
			}
		};
		// LOGICAL
		schema[EXPR_logicalnot] = new Schema(Operands.ONE, Data.ZERO, "EXPR_logicalnot") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.LogicalNot((Expr) operands[0]);
			}
		};
		schema[EXPR_logicaland] = new Schema(Operands.ONE, Data.ZERO, "EXPR_logicaland") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.LogicalAnd((Tuple<Expr>) operands[0]);
			}
		};
		schema[EXPR_logicalor] = new Schema(Operands.ONE, Data.ZERO, "EXPR_logicalor") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.LogicalOr((Tuple<Expr>) operands[0]);
			}
		};
		schema[EXPR_logiaclimplication] = new Schema(Operands.TWO, Data.ZERO, "EXPR_logicalimplication") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.LogicalImplication((Expr) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_logicaliff] = new Schema(Operands.TWO, Data.ZERO, "EXPR_logicaliff") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.LogicalIff((Expr) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_logicalexistential] = new Schema(Operands.TWO, Data.ZERO, "EXPR_logicalexistential") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.ExistentialQuantifier((Tuple<Decl.Variable>) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_logicaluniversal] = new Schema(Operands.TWO, Data.ZERO, "EXPR_logicaluniversal") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.UniversalQuantifier((Tuple<Decl.Variable>) operands[0], (Expr) operands[1]);
			}
		};
		// COMPARATORS
		schema[EXPR_equal] = new Schema(Operands.TWO, Data.ZERO, "EXPR_equal") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.Equal((Expr) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_notequal] = new Schema(Operands.TWO, Data.ZERO, "EXPR_notequal") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.NotEqual((Expr) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_integerlessthan] = new Schema(Operands.TWO, Data.ZERO, "EXPR_integerlessthan") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IntegerLessThan((Expr) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_integerlessequal] = new Schema(Operands.TWO, Data.ZERO, "EXPR_integerlessequal") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IntegerLessThanOrEqual((Expr) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_integergreaterthan] = new Schema(Operands.TWO, Data.ZERO, "EXPR_integergreaterthan") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IntegerGreaterThan((Expr) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_integergreaterequal] = new Schema(Operands.TWO, Data.ZERO, "EXPR_integergreaterequal") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IntegerGreaterThanOrEqual((Expr) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_is] = new Schema(Operands.TWO, Data.ZERO, "EXPR_is") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.Is((Expr) operands[0], (Type) operands[1]);
			}
		};
		// ARITHMETIC
		schema[EXPR_integernegation] = new Schema(Operands.TWO, Data.ZERO, "EXPR_integernegation") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IntegerNegation((Type) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_integeraddition] = new Schema(Operands.THREE, Data.ZERO, "EXPR_integeraddition") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IntegerAddition((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		};
		schema[EXPR_integersubtraction] = new Schema(Operands.THREE, Data.ZERO, "EXPR_integersubtraction") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IntegerSubtraction((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		};
		schema[EXPR_integermultiplication] = new Schema(Operands.THREE, Data.ZERO, "EXPR_integermultiplication") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IntegerMultiplication((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		};
		schema[EXPR_integerdivision] = new Schema(Operands.THREE, Data.ZERO, "EXPR_integerdivision") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IntegerDivision((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		};
		schema[EXPR_integerremainder] = new Schema(Operands.THREE, Data.ZERO, "EXPR_integerremainder") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.IntegerRemainder((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		};
		// BITWISE
		schema[EXPR_bitwisenot] = new Schema(Operands.TWO, Data.ZERO, "EXPR_bitwisenot") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.BitwiseComplement((Type) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_bitwiseand] = new Schema(Operands.TWO, Data.ZERO, "EXPR_bitwiseand") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.BitwiseAnd((Type) operands[0], (Tuple<Expr>) operands[1]);
			}
		};
		schema[EXPR_bitwiseor] = new Schema(Operands.TWO, Data.ZERO, "EXPR_bitwiseor") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.BitwiseOr((Type) operands[0], (Tuple<Expr>) operands[1]);
			}
		};
		schema[EXPR_bitwisexor] = new Schema(Operands.TWO, Data.ZERO, "EXPR_bitwisexor") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.BitwiseXor((Type) operands[0], (Tuple<Expr>) operands[1]);
			}
		};
		schema[EXPR_bitwiseshl] = new Schema(Operands.THREE, Data.ZERO, "EXPR_bitwiseshl") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.BitwiseShiftLeft((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		};
		schema[EXPR_bitwiseshr] = new Schema(Operands.THREE, Data.ZERO, "EXPR_bitwiseshr") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.BitwiseShiftRight((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		};
		// REFERENCES
		schema[EXPR_dereference] = new Schema(Operands.TWO, Data.ZERO, "EXPR_dereference") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.Dereference((Type) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_new] = new Schema(Operands.THREE, Data.ZERO, "EXPR_new") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.New((Type) operands[0], (Expr) operands[1], (Identifier) operands[2]);
			}
		};
		schema[EXPR_staticnew] = new Schema(Operands.TWO, Data.ZERO, "EXPR_staticnew") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.New((Type) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_lambdaaccess] = new Schema(Operands.TWO, Data.ZERO, "EXPR_lambdaaccess") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.LambdaAccess((Decl.Binding<Type.Callable, Decl.Callable>) operands[0],
						(Tuple<Type>) operands[1]);
			}
		};
		// RECORDS
		schema[EXPR_recordaccess] = new Schema(Operands.THREE, Data.ZERO, "EXPR_recordaccess") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.RecordAccess((Type) operands[0], (Expr) operands[1], (Identifier) operands[2]);
			}
		};
		schema[EXPR_recordborrow] = new Schema(Operands.THREE, Data.ZERO, "EXPR_recordborrow") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				Expr.RecordAccess r = new Expr.RecordAccess((Type) operands[0], (Expr) operands[1],
						(Identifier) operands[2]);
				r.setMove();
				return r;
			}
		};
		schema[EXPR_recordupdate] = new Schema(Operands.FOUR, Data.ZERO, "EXPR_recordupdate") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.RecordUpdate((Type) operands[0], (Expr) operands[1], (Identifier) operands[2],
						(Expr) operands[3]);
			}
		};
		schema[EXPR_recordinitialiser] = new Schema(Operands.THREE, Data.ZERO, "EXPR_recordinitialiser") {
			@SuppressWarnings("unchecked")
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.RecordInitialiser((Type) operands[0], (Tuple<Identifier>) operands[1],
						(Tuple<Expr>) operands[2]);
			}
		};
		// ARRAYS
		schema[EXPR_arrayaccess] = new Schema(Operands.THREE, Data.ZERO, "EXPR_arrayaccess") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.ArrayAccess((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		};
		schema[EXPR_arrayborrow] = new Schema(Operands.THREE, Data.ZERO, "EXPR_arrayborrow") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				Expr.ArrayAccess r = new Expr.ArrayAccess((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
				r.setMove();
				return r;
			}
		};
		schema[EXPR_arraylength] = new Schema(Operands.TWO, Data.ZERO, "EXPR_arraylength") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.ArrayLength((Type) operands[0], (Expr) operands[1]);
			}
		};
		schema[EXPR_arrayupdate] = new Schema(Operands.FOUR, Data.ZERO, "EXPR_arrayupdate") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.ArrayUpdate((Type) operands[0], (Expr) operands[1], (Expr) operands[2],
						(Expr) operands[3]);
			}
		};
		schema[EXPR_arraygenerator] = new Schema(Operands.THREE, Data.ZERO, "EXPR_arraygenerator") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.ArrayGenerator((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		};
		schema[EXPR_arrayinitialiser] = new Schema(Operands.TWO, Data.ZERO, "EXPR_arrayinitialiser") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.ArrayInitialiser((Type) operands[0], (Tuple<Expr>) operands[1]);
			}
		};
		schema[EXPR_arrayrange] = new Schema(Operands.THREE, Data.ZERO, "EXPR_arrayrange") {
			@Override
			public SyntacticItem construct(int opcode, SyntacticItem[] operands, byte[] data) {
				return new Expr.ArrayRange((Type) operands[0], (Expr) operands[1], (Expr) operands[2]);
			}
		};
		return schema;
	}

	private static final AbstractConsumer<HashSet<Decl.Variable>> usedVariableExtractor = new AbstractConsumer<HashSet<Decl.Variable>>() {
		@Override
		public void visitVariableAccess(WyilFile.Expr.VariableAccess expr, HashSet<Decl.Variable> used) {
			used.add(expr.getVariableDeclaration());
		}

		@Override
		public void visitUniversalQuantifier(WyilFile.Expr.UniversalQuantifier expr, HashSet<Decl.Variable> used) {
			visitVariables(expr.getParameters(), used);
			visitExpression(expr.getOperand(), used);
			removeAllDeclared(expr.getParameters(), used);
		}

		@Override
		public void visitExistentialQuantifier(WyilFile.Expr.ExistentialQuantifier expr, HashSet<Decl.Variable> used) {
			visitVariables(expr.getParameters(), used);
			visitExpression(expr.getOperand(), used);
			removeAllDeclared(expr.getParameters(), used);
		}

		@Override
		public void visitType(WyilFile.Type type, HashSet<Decl.Variable> used) {
			// No need to visit types
		}

		private void removeAllDeclared(Tuple<Decl.Variable> parameters, HashSet<Decl.Variable> used) {
			for (int i = 0; i != parameters.size(); ++i) {
				used.remove(parameters.get(i));
			}
		}
	};
}
