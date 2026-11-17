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

  goal-out.txt         What the program should write to standard output.  If none of this file,
                       goal-out-contains.txt, and goal-out-suffix.txt is present, the program
                       should write nothing to standard output.

  goal-err.txt         What the program should write to standard error.  If none of this file,
                       goal-err-contains.txt, and goal-err-suffix.txt is present, the program
                       should write nothing to standard error.

  goal-out-contains.txt
  goal-err-contains.txt
                       Strings that standard output (for goal-out-contains.txt) or standard error
                       (for goal-err-contains.txt) should contain, one per line.  Each line must
                       occur somewhere in the output; the lines can occur in any order, and the
                       output can contain other text.  An empty file permits any output.  Use this
                       file instead of goal-out.txt or goal-err.txt when the exact text of the
                       output is specific to one implementation, as the wording of a usage message
                       or of a stack trace is.

  goal-out-suffix.txt
  goal-err-suffix.txt
                       What standard output (for goal-out-suffix.txt) or standard error (for
                       goal-err-suffix.txt) should end with.  Use this file instead of goal-out.txt
                       or goal-err.txt when the end of the output is the same for every
                       implementation but what precedes it is not.  A test case can have both a
                       contains file and a suffix file for the same stream; then the output must
                       satisfy both.  A test case cannot have goal-out.txt together with
                       goal-out-contains.txt or goal-out-suffix.txt, nor goal-err.txt together
                       with goal-err-contains.txt or goal-err-suffix.txt.

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
   same as an empty one.  Split goal-out-contains.txt and goal-err-contains.txt into lines as step 5
   splits args.txt.

9. The test case passes if all of the following hold:
   * The exit status equals the integer in goal-status.txt (ignoring surrounding whitespace), or 0
     if there is no goal-status.txt.
   * If goal-out-contains.txt or goal-out-suffix.txt exists:  each line of goal-out-contains.txt
     (if it exists) is a substring of the normalized standard output, and the normalized standard
     output ends with the contents of goal-out-suffix.txt (if it exists).  Otherwise, the
     normalized standard output equals goal-out.txt exactly.
   * The same, for standard error and goal-err-contains.txt, goal-err-suffix.txt, and
     goal-err.txt.
   * The set of the names of the files under WORKDIR equals the set recorded in step 4.  The
     program writes no files, and deletes none.

The comparisons are of text, not of bytes:  a goal-out.txt or goal-err.txt file that ends with a
line separator expects the program's output to end with one, and one that does not end with one
expects output that does not.


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

Where the exact text of the Java implementation's output is specific to that implementation -- the
usage message, the diagnostics of the command-line-option library, and stack traces -- the test case
uses goal-out-contains.txt or goal-err-contains.txt.  Those files check only what any implementation
should mention, such as the names of the options, the offending option and value, or the file that
could not be read; goal-status.txt still checks the exit status.  In test cases verbose and
verbose-progress, the format of the option settings that --verbose prints comes from the
command-line-option library, so goal-out-contains.txt checks only that the settings name every
option and the entry file, and goal-out-suffix.txt checks the rest of the output exactly.

The test cases whose description starts with "BUG:" record incorrect behavior, such as a stack
trace or a diagnostic on standard output.  An implementation that fixes such a bug fails that test
case; update its goal files when the bug is fixed.
Some error messages come from the operating system; for example, "Is a directory" is the message
on Linux.


THE BYTES OF THE TEST DATA

Because these files are test data, their exact bytes matter: some of them intentionally have
trailing whitespace, lack a final line separator, use CRLF line separators, contain bytes that are
not valid UTF-8, or contain a CR that is not part of a line separator, and two are compressed.
prek.toml excludes this directory from the hooks that would "fix" such things, and .gitattributes
keeps git from translating line separators in it.
