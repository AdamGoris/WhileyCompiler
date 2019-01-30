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
package wyc.task;

import static wyc.util.ErrorMessages.RESOLUTION_ERROR;
import static wyc.util.ErrorMessages.errorMessage;

import java.io.*;
import java.util.*;

import wyal.lang.WyalFile;
import wyal.util.TypeChecker;
import wyfs.lang.Content;
import wyfs.lang.Path;
import wyfs.util.Trie;
import wyil.check.AmbiguousCoercionCheck;
import wyil.check.DefiniteAssignmentCheck;
import wyil.check.DefiniteUnassignmentCheck;
import wyil.check.FlowTypeCheck;
import wyil.check.FunctionalCheck;
import wyil.check.StaticVariableCheck;
import wyil.lang.WyilFile;
import wyil.lang.WyilFile.Decl;
import wyil.transform.MoveAnalysis;
import wyil.transform.NameResolution;
import wyil.transform.RecursiveTypeAnalysis;
import wyil.transform.VerificationConditionGenerator;
import wytp.provers.AutomatedTheoremProver;
import wybs.lang.*;
import wybs.lang.CompilationUnit.Name;
import wybs.lang.SyntaxError.InternalFailure;
import wybs.util.*;
import wyc.io.WhileyFileParser;
import wyc.lang.*;
import wycc.cfg.Configuration;
import wycc.util.ArrayUtils;
import wycc.util.Logger;
import wycc.util.Pair;

/**
 * Responsible for managing the process of turning source files into binary code
 * for execution. Each source file is passed through a pipeline of stages that
 * modify it in a variet	y of ways. The main stages are:
 * <ol>
 * <li>
 * <p>
 * <b>Lexing and Parsing</b>, where the source file is converted into an
 * Abstract Syntax Tree (AST) representation.
 * </p>
 * </li>
 * <li>
 * <p>
 * <b>Name Resolution</b>, where the fully qualified names of all external
 * symbols are determined.
 * </p>
 * </li>
 * <li>
 * <p>
 * <b>Type Propagation</b>, where the types of all expressions are determined by
 * propagation from e.g. declared parameter types.
 * </p>
 * </li>
 * <li>
 * <p>
 * <b>WYIL Generation</b>, where the the AST is converted into the Whiley
 * Intermediate Language (WYIL). A number of passes are then made over this
 * before it is ready for code generation.
 * </p>
 * </li>
 * <li>
 * <p>
 * <b>Code Generation</b>. Here, the executable code is finally generated. This
 * could be Java bytecode, or something else (e.g. JavaScript).
 * </p>
 * </li>
 * </ol>
 * Every stage of the compiler can be configured by setting various options.
 * Stages can also be bypassed (typically for testing) and new ones can be
 * added.
 *
 * @author David J. Pearce
 *
 */
public final class CompileTask implements Build.Task {

	/**
	 * The master project for identifying all resources available to the
	 * builder. This includes all modules declared in the project being compiled
	 * and/or defined in external resources (e.g. jar files).
	 */
	private final Build.Project project;

	/**
	 * The source root to find Whiley files. This is far from ideal.
	 */
	private final Path.Root sourceRoot;

	/**
	 * Specify whether verification enabled or not
	 */
	private boolean verify;

	public CompileTask(Build.Project project, Path.Root sourceRoot) {
		this.project = project;
		this.sourceRoot = sourceRoot;
	}

	public String id() {
		return "wyc.builder";
	}

	@Override
	public Build.Project project() {
		return project;
	}

	public CompileTask setVerification(boolean flag) {
		this.verify = flag;
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<Path.Entry<?>> build(Collection<Pair<Path.Entry<?>, Path.Root>> delta, Build.Graph graph)
			throws IOException {
		// Identify the source compilation groups
		HashSet<Path.Entry<?>> targets = new HashSet<>();
		for (Pair<Path.Entry<?>, Path.Root> p : delta) {
			Path.Entry<?> entry = p.first();
			if (entry.contentType() == WhileyFile.ContentType) {
				targets.addAll(graph.getChildren(entry));
			}
		}
		// Compile each one in turn
		for (Path.Entry<?> target : targets) {
			// FIXME: there is a problem here. That's because not every parent will be in
			// the delta. Therefore, this is forcing every file to be recompiled.
			List sources = graph.getParents(target);
			build((Path.Entry<WyilFile>) target, (List<Path.Entry<WhileyFile>>) sources);
		}
		// Done
		return targets;
	}

	public void build(Path.Entry<WyilFile> target, List<Path.Entry<WhileyFile>> sources) throws IOException {
		build(project, target, sources);
		if (verify) {
			verify(project, sourceRoot, target, sources);
		}
	}

	public static void build(Build.Project project, Path.Entry<WyilFile> target, List<Path.Entry<WhileyFile>> sources)
			throws IOException {
		Logger logger = project.getLogger();
		try {
			Runtime runtime = Runtime.getRuntime();
			long startTime = System.currentTimeMillis();
			long startMemory = runtime.freeMemory();
			long tmpTime = startTime;
			long tmpMemory = startMemory;

			// ========================================================================
			// Parse source files
			// ========================================================================
			WyilFile wf = compile(sources, target);

			logger.logTimedMessage("Parsed " + sources.size() + " source file(s).", System.currentTimeMillis() - tmpTime,
					tmpMemory - runtime.freeMemory());

			// ========================================================================
			// Type Checking & Code Generation
			// ========================================================================

			runtime = Runtime.getRuntime();
			tmpTime = System.currentTimeMillis();
			tmpMemory = runtime.freeMemory();

			new NameResolution(project,wf).apply();
			new FlowTypeCheck().check(wf);
			new DefiniteAssignmentCheck().check(wf);
			new DefiniteUnassignmentCheck().check(wf);
			new FunctionalCheck().check(wf);
			new StaticVariableCheck().check(wf);
			new AmbiguousCoercionCheck().check(wf);
			new MoveAnalysis().apply(wf);
			new RecursiveTypeAnalysis().apply(wf);
			// new CoercionCheck(this);

			logger.logTimedMessage("Generated code for " + sources.size() + " source file(s).",
					System.currentTimeMillis() - tmpTime, tmpMemory - runtime.freeMemory());

			// ========================================================================
			// Done
			// ========================================================================

			// Flush any changes to disk
			target.flush();

			long endTime = System.currentTimeMillis();
			logger.logTimedMessage("Whiley => Wyil: compiled " + sources.size() + " file(s)", endTime - startTime,
					startMemory - runtime.freeMemory());
		} catch(SyntaxError e) {
			//
			SyntacticItem item = e.getElement();
			// FIXME: translate from WyilFile to WhileyFile. This is a temporary hack
			if(e.getEntry().contentType() == WyilFile.ContentType) {
				Decl.Unit unit = item.getAncestor(Decl.Unit.class);
				// Determine which source file this entry is contained in
				Path.Entry<WhileyFile> sf = getWhileySourceFile(unit.getName(),sources);
				//
				throw new SyntaxError(e.getMessage(),sf,item,e.getCause());
			} else {
				throw e;
			}
		}
	}


	public static void verify(Build.Project project, Path.Root sourceRoot, Path.Entry<WyilFile> target, List<Path.Entry<WhileyFile>> sources)
			throws IOException {
		Logger logger = project.getLogger();
		// FIXME: this is really a bit of a kludge right now. The basic issue is that,
		// in the near future, the VerificationConditionGenerator will operate directly
		// on the WyilFile rather than creating a WyalFile. Then, the theorem prover can
		// work on the WyilFile directly as well and, hence, this will become more like
		// a compilation stage (as per others above).
		try {
			Runtime runtime = Runtime.getRuntime();
			long startTime = System.currentTimeMillis();
			long startMemory = runtime.freeMemory();
			//
			wytp.types.TypeSystem typeSystem = new wytp.types.TypeSystem(project);
			// FIXME: this unfortunately puts it in the wrong directory.
			Path.Entry<WyalFile> wyalTarget = project.getRoot().get(target.id(),WyalFile.ContentType);
			if (wyalTarget == null) {
				wyalTarget = project.getRoot().create(target.id(), WyalFile.ContentType);
				wyalTarget.write(new WyalFile(wyalTarget));
			}
			WyalFile contents = new VerificationConditionGenerator(new WyalFile(wyalTarget)).translate(target.read());
			new TypeChecker(typeSystem, contents, target).check();
			wyalTarget.write(contents);
			wyalTarget.flush();
			// Now try to verfify it
			AutomatedTheoremProver prover = new AutomatedTheoremProver(typeSystem);
			// FIXME: this is horrendous :(
			prover.check(contents, sourceRoot);

			long endTime = System.currentTimeMillis();
			logger.logTimedMessage("verified code for 1 file(s)", endTime - startTime,
					startMemory - runtime.freeMemory());
		} catch(SyntaxError e) {
			//
			SyntacticItem item = e.getElement();
			// FIXME: translate from WyilFile to WhileyFile. This is a temporary hack
			if(item != null && e.getEntry() != null && e.getEntry().contentType() == WyilFile.ContentType) {
				Decl.Unit unit = item.getAncestor(Decl.Unit.class);
				// Determine which source file this entry is contained in
				Path.Entry<WhileyFile> sf = getWhileySourceFile(unit.getName(),sources);
				//
				throw new SyntaxError(e.getMessage(),sf,item,e.getCause());
			} else {
				throw e;
			}
		}
	}

	/**
	 * Compile one or more WhileyFiles into a given WyilFile
	 *
	 * @param source The source file being compiled.
	 * @param target The target file being generated.
	 * @return
	 * @throws IOException
	 */
	private static WyilFile compile(List<Path.Entry<WhileyFile>> sources, Path.Entry<WyilFile> target) throws IOException {
		// Read target WyilFile. This may have already been compiled in a previous run
		// and, in such case, we are invalidating some or all of the existing file.
		WyilFile wyil = target.read();
		// Parse all modules
		for(int i=0;i!=sources.size();++i) {
			Path.Entry<WhileyFile> source = sources.get(i);
			WhileyFileParser wyp = new WhileyFileParser(wyil, source.read());
			// FIXME: what to do with module added to heap? The problem is that this might
			// be replaced a module, for example.
			wyil.getModule().putUnit(wyp.read());
		}
		//
		return wyil;
	}

	private static Path.Entry<WhileyFile> getWhileySourceFile(Name name, List<Path.Entry<WhileyFile>> sources) {
		String nameStr = name.toString().replace("::", "/");
		//
		for (Path.Entry<WhileyFile> e : sources) {
			if (e.id().toString().equals(nameStr)) {
				return e;
			}
		}
		throw new IllegalArgumentException("unknown unit");
	}
}
