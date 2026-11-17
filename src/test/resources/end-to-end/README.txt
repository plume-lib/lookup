This directory holds the data for the end-to-end tests of the Lookup program.  The test data is
independent of the language in which Lookup is implemented:  each test case gives the program's
input files and command-line arguments, and the output and exit status that the program should
produce.  The test harness src/test/java/org/plumelib/lookup/LookupEndToEndTest.java runs the test
cases against the Java implementation of Lookup, or against any other command (see "Testing another
implementation" below).  A harness written in another language can run the same test cases by
following this file.

Each subdirectory of this directory is one test case; the name of the subdirectory is the name of
the test case.  To add a test case, add a subdirectory; no harness code needs to change.


THE FILES OF A TEST CASE

A test case directory can contain the following, all of them optional.  A test case directory that
contains any other file is an error in the test case (probably a misspelled goal file name).

  description.txt      What the test case checks.  The test harness ignores this file.  A
                       description that starts with "BUG:" describes a way in which the program
                       behaves differently than it should; the goal files record what the program
                       currently does.

  input/               Files to search.  The test harness copies this directory into the working
                       directory, so the program is not run in the directory that holds the files
                       it searches; that is what makes it meaningful to test how an include
                       directive's file name is resolved.  A test case names these files as
                       "input/...", usually in a "--entry-file" argument.

  home/                The contents of the home directory that the program runs with.  Use this
                       directory to exercise the default entry file, ~/lookup/root, and other file
                       names that start with "~".

  args.txt             The program's command-line arguments, one per line.  A line's entire
                       contents are one argument, so a blank line is an empty argument, and an empty
                       file runs the program with no arguments.  If this file is absent, the program
                       is run with no arguments.

  goal-out.txt         What the program should write to standard output.  If this file is absent,
                       the program should write nothing to standard output.

  goal-err.txt         What the program should write to standard error.  If neither this file nor
                       goal-err-prefix.txt is present, the program should write nothing to standard
                       error.

  goal-err-prefix.txt  What the program's standard error should start with.  Use this file instead
                       of goal-err.txt when the rest of the output would make the goal file
                       brittle, as a stack trace's line numbers would.

  goal-status.txt      The program's exit status, as a decimal integer.  If this file is absent, the
                       exit status should be 0.

All files are UTF-8 text.


HOW TO RUN A TEST CASE

1. Create a fresh, empty working directory; call it WORKDIR.

2. If the test case has an input/ directory, copy it, recursively, to WORKDIR/input.

3. Create the directory WORKDIR/home.  If the test case has a home/ directory, copy its contents,
   recursively, into WORKDIR/home.

4. Record the set of the names of the files (not directories) under WORKDIR.

5. Read the arguments from args.txt:  split the file into lines at each LF, CRLF, or CR, and discard
   the line separators.  A final line separator does not start another, empty, line.  The arguments
   are passed to the program directly, not through a shell, so no character in them is special.

6. Run the program, with:
   * its working directory set to WORKDIR,
   * the arguments from step 5,
   * its standard input empty (the program does not read standard input),
   * the environment variable HOME set to the absolute path of WORKDIR/home, and every other
     environment variable inherited from the harness.  The program's home directory is
     WORKDIR/home, so that a test case cannot read the files of whoever runs the tests.  (Java
     ignores HOME, so the Java harness also passes "-Duser.home=WORKDIR/home" to the JVM.)
   * its standard output and standard error captured, and decoded as UTF-8.  (The Java harness
     passes "-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8" to the JVM.)

7. Normalize the captured standard output and standard error:
   a. Replace each CRLF by LF, because the program ends each line it writes with the line
      separator of the platform it runs on.  A CR that is not followed by LF is kept.
   b. Replace each occurrence of the absolute path of WORKDIR by the 9 characters "${workdir}",
      because WORKDIR is a fresh temporary directory whose path no goal file could record.  Do so
      both for the path as the harness created it and for the path with symbolic links resolved,
      because the program might print either.

8. Read each goal file, replacing each CRLF by LF.  A missing goal-out.txt or goal-err.txt is the
   same as an empty one.

9. The test case passes if all of the following hold:
   * The exit status equals the integer in goal-status.txt (ignoring surrounding whitespace), or 0
     if there is no goal-status.txt.
   * The normalized standard output equals goal-out.txt exactly.
   * If goal-err-prefix.txt exists:  the normalized standard error starts with the contents of
     goal-err-prefix.txt, after trailing whitespace (including the final line separator) is removed
     from those contents.  Otherwise, the normalized standard error equals goal-err.txt exactly.
   * The set of the names of the files under WORKDIR equals the set recorded in step 4.  The
     program writes no files, and deletes none.

The comparisons are of text, not of bytes:  a goal file that ends with a line separator expects the
program's output to end with one, and a goal file that does not end with one expects output that
does not.


TESTING ANOTHER IMPLEMENTATION

To run the test cases against another implementation of Lookup with the Java harness, set the
Gradle property e2e.command to the command that runs it:

  ./gradlew test --tests LookupEndToEndTest -Pe2e.command="/path/to/lookup"
  ./gradlew test --tests LookupEndToEndTest -Pe2e.command="python3 /path/to/lookup.py"

The harness splits the command into words at whitespace (there is no quoting) and appends each test
case's arguments.  If the first word contains a path separator but is not absolute, it is relative
to the directory from which the tests run, which for Gradle is the project's root directory;
otherwise the first word is looked up on the PATH.  When e2e.command is not set, the harness runs
the Java implementation in a new JVM.

Some goal files record details that are specific to the Java implementation:
  * the usage message (for example, in test case help) and the diagnostics of the
    command-line-option library, which another implementation needs to reproduce exactly, and
  * the stack traces of the test cases whose description starts with "BUG:".  An implementation that
    fixes such a bug fails that test case; update its goal files when the bug is fixed.
Some error messages come from the operating system; for example, "Is a directory" is the message
on Linux.


THE BYTES OF THE TEST DATA

Because these files are test data, their exact bytes matter: some of them intentionally have
trailing whitespace, lack a final line separator, use CRLF line separators, contain bytes that are
not valid UTF-8, or contain a CR that is not part of a line separator, and two are compressed.
prek.toml excludes this directory from the hooks that would "fix" such things, and .gitattributes
keeps git from translating line separators in it.
