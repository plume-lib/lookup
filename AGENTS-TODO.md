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

- [ ] Convert the cases of `LookupTest.java` that no test case covers:
  supplementary-plane word, `-e -w caf\w`, `-w` with `-c`, `-w` conjunction
  with no common entry, `-e #define`, `-e -w foo-`, `-e \Qfoo`,
  `--item-num` with `--show-location`, `--item-num=1` with one match.
  Then delete `LookupTest.java`.
- [ ] Harness: give the program an empty standard input, set the `HOME`
  environment variable, and support running an external command via
  `-Pe2e.command=...` (system property `e2e.command`).
- [ ] README.txt: document invocation in language-neutral terms (working
  directory, arguments, standard input, environment, compared files,
  normalization, external command).
- [ ] New test cases: option syntax (separate value, explicit boolean value,
  underscore names, options after keywords, short names `-e -c -b -i -h`),
  invalid option values (`--item-num=abc`, negative, invalid
  `--entry-start-re`, `--description-re`, `--include-re`,
  `--multiline-comment-start-re`).
- [ ] New test cases: entry file (`~` expansion, empty component, empty
  `--entry-file`), non-UTF-8 input.
- [ ] New test cases: includes (gzipped include, include in a comment,
  include cycle, `~` in include, long entry ended by include, location of an
  included entry's line number).
- [ ] New test cases: comments (multi-line comment on one line, multi-line
  comment containing blank lines), long entries (blank lines in a long entry,
  synopsis and `--show-location` synopsis of long entries), regex across lines,
  case-sensitive regex, synopsis shows first line, verbose progress.
- [ ] `./gradlew spotlessApply build` passes; markdownlint passes.
- [ ] Delete this file and commit.
