# Plan: improve the end-to-end tests of plume-lib/lookup

**This temporary plan file must be deleted before any pull request is merged.**

Goal: in the git worktree
`/home/mernst/java/plume-lib/lookup-fork-mernst-branch-e2e-tests` (branch
`e2e-tests`), add file-based end-to-end tests for the Lookup program, in the
existing format `src/test/resources/end-to-end/` (one directory per test case;
see its `README.txt`), run by `LookupEndToEndTest.java`.  The test data must be
usable against an implementation in another language.  Convert end-to-end tests
that exist in another form (`LookupTest.java`, which runs Lookup in a
subprocess) into that format, and delete the old form.  Do not change
`src/main`.  Record a bug as a test case whose description starts with "BUG:"
and whose goal files record current behavior.  Commit at each milestone on
branch `e2e-tests`; do not push, do not create a pull request, and do not
rewrite history.  `./gradlew build` must pass at the end.  When every entry is
done, delete this file and commit the deletion.

## Checklist

- [x] Convert `LookupTest.java` into test cases and delete it.
- [x] Harness: empty standard input, `HOME`, `-Pe2e.command=...`.
- [x] New test cases (options, entry file, includes, comments, long entries,
  search, verbose).
- [ ] README.txt: document invocation in language-neutral terms (working
  directory, arguments, standard input, environment, compared files,
  normalization, external command).
- [ ] `./gradlew spotlessApply build` passes; markdownlint passes.
- [ ] Delete this file and commit.
