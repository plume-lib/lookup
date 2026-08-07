This directory holds the data for the end-to-end tests, which are run by
src/test/java/org/plumelib/lookup/LookupEndToEndTest.java.

Each subdirectory of this directory is one test case; the name of the subdirectory is the name of
the test case.  A test case runs the program once, in a fresh working directory, and compares
everything the program produced to the goal files.  To add a test case, add a subdirectory; no Java
code needs to change.

A test case directory may contain the following, all of them optional:

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
                       directory to exercise the default entry file, ~/lookup/root.  The test
                       harness always sets the program's home directory, so that a test case cannot
                       read the files of whoever runs the tests.

  args.txt             The program's command-line arguments, one per line.  A line's entire
                       contents are one argument, so a blank line is an empty argument, and a file
                       with no lines runs the program with no arguments.

  goal-out.txt         What the program should write to standard output.  If this file is absent,
                       the program should write nothing to standard output.

  goal-err.txt         What the program should write to standard error.  If neither this file nor
                       goal-err-prefix.txt is present, the program should write nothing to standard
                       error.

  goal-err-prefix.txt  What the program's standard error should start with.  Use this file instead
                       of goal-err.txt when the rest of the output would make the goal file
                       brittle, as a stack trace's line numbers would.

  goal-status.txt      The program's exit status.  If this file is absent, the exit status should
                       be 0.

The test harness compares text, not bytes.  It treats CRLF and LF as the same line separator,
because the program ends each line it writes with the line separator of the platform it runs on.
In the program's output, it replaces the working directory's path by "${workdir}", because the
working directory is a fresh temporary directory whose path no goal file could record.

The program writes no files, so the test harness also checks that the working directory afterwards
holds exactly the files that the test harness put there.

Because these files are test data, their exact bytes matter: some of them intentionally have
trailing whitespace, lack a final line separator, or use CRLF line separators, and one is
compressed.  prek.toml excludes this directory from the hooks that would "fix" such things, and
.gitattributes keeps git from translating line separators in it.
