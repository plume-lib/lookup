package org.plumelib.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test the Lookup class.
 *
 * <p>Each test runs Lookup in a subprocess. A subprocess is necessary because Lookup reports some
 * errors by calling {@code System.exit}, and because Lookup's command-line options are static
 * fields that {@code main} does not reset, so in-process runs would interfere with one another.
 */
@SuppressWarnings({
  "nullness", // run-time errors are acceptable
  "initializedfields:contracts.postcondition" // @TempDir causes injection
})
final class LookupTest {

  LookupTest() {}

  /**
   * The separator between output lines. {@code Lookup} writes it via {@code %n}, and {@code
   * EntryReader} uses it when reconstructing an entry's body.
   */
  private static final String lineSep = System.lineSeparator();

  /** Do not assign; JUnit will do so, thanks to the {@code @TempDir} annotation. */
  @TempDir Path tempDir;

  /** The entry file that the tests search. */
  Path entryFile;

  /** The first entry of {@link #entryFile}. It is the only entry with "bar" as a whole word. */
  private static final String entryOne = "one: the word bar appears here";

  /** The second entry of {@link #entryFile}. It contains "bar" only within longer words. */
  private static final String entryTwo = "two: embargo and rebarbative contain it";

  /** The third entry of {@link #entryFile}. "prequx" ends with "qux" but does not start with it. */
  private static final String entryThree = "three: prequx appears here";

  /** The fourth entry of {@link #entryFile}. Its keyword starts with a non-word character. */
  private static final String entryFour = "four: #define DIRECTIVE";

  /**
   * The fifth entry of {@link #entryFile}. "café" ends with a word character that is not an ASCII
   * word character, so {@code \w} matches it only under {@code UNICODE_CHARACTER_CLASS}. The
   * character is written as an escape so that this file's encoding cannot affect the test.
   */
  private static final String entryFive = "five: caf\u00e9 society";

  /** The sixth entry of {@link #entryFile}. It contains a hyphenated word. */
  private static final String entrySix = "six: a re-entrant lock";

  /** Write the entry file that the tests search. */
  @BeforeEach
  void writeEntryFile() throws IOException {
    entryFile = tempDir.resolve("root");
    Files.writeString(
        entryFile,
        String.join(
            "\n",
            entryOne,
            "",
            entryTwo,
            "",
            entryThree,
            "",
            entryFour,
            "",
            entryFive,
            "",
            entrySix,
            ""));
  }

  /**
   * The result of running Lookup in a subprocess.
   *
   * @param exitStatus the exit status of the subprocess
   * @param stdout everything Lookup wrote to standard output
   * @param stderr everything Lookup wrote to standard error
   */
  private record LookupResult(int exitStatus, String stdout, String stderr) {}

  /**
   * Run Lookup in a subprocess, searching {@link #entryFile}.
   *
   * @param args command-line arguments, not including {@code --entry-file}
   * @return the exit status and output of the subprocess
   * @throws IOException if the subprocess cannot be started or its output cannot be read
   * @throws InterruptedException if this thread is interrupted while awaiting the subprocess
   */
  private LookupResult runLookup(String... args) throws IOException, InterruptedException {
    return runLookupOn(entryFile, args);
  }

  /**
   * Run Lookup in a subprocess, searching the given file.
   *
   * @param searchFile the file to search
   * @param args command-line arguments, not including {@code --entry-file}
   * @return the exit status and output of the subprocess
   * @throws IOException if the subprocess cannot be started or its output cannot be read
   * @throws InterruptedException if this thread is interrupted while awaiting the subprocess
   */
  private LookupResult runLookupOn(Path searchFile, String... args)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(Lookup.class.getName());
    command.add("--entry-file=" + searchFile);
    command.addAll(List.of(args));

    // Redirect to files rather than reading the subprocess's streams, which could deadlock.
    Path stdoutFile = tempDir.resolve("stdout");
    Path stderrFile = tempDir.resolve("stderr");
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectOutput(stdoutFile.toFile());
    builder.redirectError(stderrFile.toFile());
    Process process = builder.start();
    if (!process.waitFor(60, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError("Lookup subprocess timed out: " + command);
    }
    int exitStatus = process.exitValue();
    return new LookupResult(exitStatus, Files.readString(stdoutFile), Files.readString(stderrFile));
  }

  /**
   * Returns the synopsis line that Lookup prints for a match, when {@code --show-location} is not
   * supplied.
   *
   * @param itemNum the 1-based index of the match
   * @param description the matching entry's description
   * @return one line of Lookup's multiple-match synopsis output
   */
  private static String synopsis(int itemNum, String description) {
    return "  -i=" + itemNum + " " + description + lineSep;
  }

  // Searching

  /**
   * Test that {@code --word-match} restricts a regular expression to whole words. Without {@code
   * --word-match}, "bar" also matches inside "embargo" and "rebarbative".
   */
  @Test
  void testWordMatchAppliesToRegularExpressions() throws IOException, InterruptedException {
    LookupResult withoutWordMatch = runLookup("--regular-expressions", "bar");
    assertEquals(0, withoutWordMatch.exitStatus());
    assertEquals(
        "2 matches found. Use -i to print a specific match or -a to see them all."
            + lineSep
            + synopsis(1, entryOne)
            + synopsis(2, entryTwo),
        withoutWordMatch.stdout());

    LookupResult withWordMatch = runLookup("--regular-expressions", "--word-match", "bar");
    assertEquals(0, withWordMatch.exitStatus());
    assertEquals(entryOne + lineSep, withWordMatch.stdout());
  }

  /**
   * Test that {@code --word-match} applies word boundaries to a whole regular expression, not just
   * to its first and last alternatives. "prequx" ends with "qux", so it would match the incorrectly
   * grouped regex {@code \bbar|qux\b}, but not the correct {@code \b(?:bar|qux)\b}.
   */
  @Test
  void testWordMatchGroupsAlternation() throws IOException, InterruptedException {
    LookupResult result = runLookup("--regular-expressions", "--word-match", "bar|qux");
    assertEquals(0, result.exitStatus());
    assertEquals(entryOne + lineSep, result.stdout());
  }

  /** Test that {@code --word-match} without {@code --regular-expressions} still matches words. */
  @Test
  void testWordMatchWithoutRegularExpressions() throws IOException, InterruptedException {
    LookupResult result = runLookup("--word-match", "bar");
    assertEquals(0, result.exitStatus());
    assertEquals(entryOne + lineSep, result.stdout());
  }

  /**
   * Test that {@code --word-match} does not quote a regular expression's metacharacters. "." is
   * matched as a wildcard, not literally, so "bar" is found.
   */
  @Test
  void testWordMatchKeepsRegularExpressionMetacharacters()
      throws IOException, InterruptedException {
    LookupResult result = runLookup("--regular-expressions", "--word-match", "b.r");
    assertEquals(0, result.exitStatus());
    assertEquals(entryOne + lineSep, result.stdout());
  }

  /**
   * Test that a search term whose first character is not a word character is diagnosed under {@code
   * --word-match}, rather than silently finding almost nothing. A word boundary adjacent to a
   * non-word character requires a word character on its other side, and "#" is not one.
   */
  @Test
  void testWordMatchNonWordCharacterAtStart() throws IOException, InterruptedException {
    for (String[] args :
        List.of(
            new String[] {"--regular-expressions", "--word-match", "#define"},
            new String[] {"--word-match", "#define"})) {
      LookupResult result = runLookup(args);
      assertEquals(254, result.exitStatus(), result.stderr());
      assertTrue(
          result
              .stderr()
              .startsWith(
                  "Error: cannot apply --word-match to #define: it starts with non-word"
                      + " character '#'"),
          result.stderr());
      assertEquals("", result.stdout(), "should not also report a search result");
    }

    // Without --word-match, the same search term is found.
    LookupResult withoutWordMatch = runLookup("--regular-expressions", "#define");
    assertEquals(0, withoutWordMatch.exitStatus());
    assertEquals(entryFour + lineSep, withoutWordMatch.stdout());
  }

  /** Test that a search term whose last character is not a word character is likewise diagnosed. */
  @Test
  void testWordMatchNonWordCharacterAtEnd() throws IOException, InterruptedException {
    LookupResult result = runLookup("--regular-expressions", "--word-match", "foo-");
    assertEquals(254, result.exitStatus(), result.stderr());
    assertTrue(
        result
            .stderr()
            .startsWith(
                "Error: cannot apply --word-match to foo-: it ends with non-word character"
                    + " '-'"),
        result.stderr());
    assertEquals("", result.stdout(), "should not also report a search result");
  }

  /**
   * Test that {@code --word-match} rejects a hyphenated search term, even though that search would
   * have succeeded. This documents a deliberate tradeoff: {@code \bre-\b} does match "re-entrant",
   * but the check rejects every search term that ends with a literal non-word character.
   */
  @Test
  void testWordMatchRejectsHyphenatedTerm() throws IOException, InterruptedException {
    LookupResult rejected = runLookup("--word-match", "re-");
    assertEquals(254, rejected.exitStatus(), rejected.stderr());
    assertTrue(
        rejected.stderr().startsWith("Error: cannot apply --word-match to re-: it ends with"),
        rejected.stderr());

    // Without --word-match, the same search term is found.
    LookupResult withoutWordMatch = runLookup("re-");
    assertEquals(0, withoutWordMatch.exitStatus(), withoutWordMatch.stderr());
    assertEquals(entrySix + lineSep, withoutWordMatch.stdout());
  }

  /**
   * Test that the check in {@link #testWordMatchNonWordCharacterAtStart} does not reject a regular
   * expression that might legitimately match a word character. It is a heuristic, so it must err
   * toward permitting a search: rejecting one is fatal.
   */
  @Test
  void testWordMatchableCheckIsConservative() throws IOException, InterruptedException {
    // An alternation: "#foo" starts with a non-word character, but "bar" does not.
    LookupResult alternation = runLookup("--regular-expressions", "--word-match", "#foo|bar");
    assertEquals(0, alternation.exitStatus(), alternation.stderr());
    assertEquals(entryOne + lineSep, alternation.stdout());

    // A leading metacharacter, which might match a word character.
    LookupResult characterClass = runLookup("--regular-expressions", "--word-match", "[#]bar");
    assertEquals(0, characterClass.exitStatus(), characterClass.stderr());
    // The heuristic permits this search, which then finds nothing.  That is the accepted cost of
    // never rejecting a search that might have worked.
    assertEquals("Nothing found." + lineSep, characterClass.stdout());
  }

  /**
   * Test that {@code \w} means the same thing whether or not {@code --word-match} is supplied.
   * "café" ends with a non-ASCII word character, which {@code \w} matches only under {@code
   * UNICODE_CHARACTER_CLASS}.
   */
  @Test
  void testUnicodeCharacterClassIsConsistent() throws IOException, InterruptedException {
    LookupResult withoutWordMatch = runLookup("--regular-expressions", "caf\\w");
    assertEquals(0, withoutWordMatch.exitStatus(), withoutWordMatch.stderr());
    assertEquals(entryFive + lineSep, withoutWordMatch.stdout());

    LookupResult withWordMatch = runLookup("--regular-expressions", "--word-match", "caf\\w");
    assertEquals(0, withWordMatch.exitStatus(), withWordMatch.stderr());
    assertEquals(entryFive + lineSep, withWordMatch.stdout());
  }

  /** Test that an entry matches only if it contains every keyword. */
  @Test
  void testMultipleKeywords() throws IOException, InterruptedException {
    // "bar" is a whole word only in entry one; "here" is a whole word in entries one and three.
    LookupResult bothPresent = runLookup("--word-match", "bar", "here");
    assertEquals(0, bothPresent.exitStatus(), bothPresent.stderr());
    assertEquals(entryOne + lineSep, bothPresent.stdout());

    // Each keyword matches an entry, but no entry contains both.
    LookupResult noEntryHasBoth = runLookup("--word-match", "bar", "embargo");
    assertEquals(0, noEntryHasBoth.exitStatus(), noEntryHasBoth.stderr());
    assertEquals("Nothing found." + lineSep, noEntryHasBoth.stdout());
  }

  /** Test that {@code --case-sensitive} applies to a {@code --word-match} search. */
  @Test
  void testCaseSensitiveWordMatch() throws IOException, InterruptedException {
    // Matching is case-insensitive by default.
    LookupResult insensitive = runLookup("--word-match", "BAR");
    assertEquals(0, insensitive.exitStatus(), insensitive.stderr());
    assertEquals(entryOne + lineSep, insensitive.stdout());

    LookupResult sensitiveMismatch = runLookup("--word-match", "--case-sensitive", "BAR");
    assertEquals(0, sensitiveMismatch.exitStatus(), sensitiveMismatch.stderr());
    assertEquals("Nothing found." + lineSep, sensitiveMismatch.stdout());

    LookupResult sensitiveMatch = runLookup("--word-match", "--case-sensitive", "bar");
    assertEquals(0, sensitiveMatch.exitStatus(), sensitiveMatch.stderr());
    assertEquals(entryOne + lineSep, sensitiveMatch.stdout());
  }

  /**
   * Test that {@code --search-body} searches the body of a long entry. Without {@code
   * --search-body}, only the long entry's description (its first line) is searched.
   */
  @Test
  void testSearchBody() throws IOException, InterruptedException {
    Path longEntryFile = tempDir.resolve("long-entries");
    Files.writeString(
        longEntryFile,
        """
        >entry a long entry
        its body mentions xyzzy
        <entry
        """);
    // Lookup prints the whole body, with the ">entry " marker removed from the first line.
    String body = "a long entry" + lineSep + "its body mentions xyzzy" + lineSep;

    // The description is always searched.
    LookupResult description = runLookupOn(longEntryFile, "long");
    assertEquals(0, description.exitStatus(), description.stderr());
    assertEquals(body, description.stdout());

    // The body is not searched by default.
    LookupResult withoutSearchBody = runLookupOn(longEntryFile, "xyzzy");
    assertEquals(0, withoutSearchBody.exitStatus(), withoutSearchBody.stderr());
    assertEquals("Nothing found." + lineSep, withoutSearchBody.stdout());

    LookupResult withSearchBody = runLookupOn(longEntryFile, "--search-body", "xyzzy");
    assertEquals(0, withSearchBody.exitStatus(), withSearchBody.stderr());
    assertEquals(body, withSearchBody.stdout());
  }

  // Invalid regular expressions

  /** Test that an invalid regular expression is diagnosed. */
  @Test
  void testInvalidRegularExpression() throws IOException, InterruptedException {
    LookupResult result = runLookup("--regular-expressions", "(");
    assertEquals(254, result.exitStatus());
    assertEquals("Error: not a regex: (" + lineSep, result.stderr());
  }

  /**
   * Test that a search term that is a valid regular expression on its own, but that {@code
   * --word-match} composes into an invalid one, is diagnosed rather than crashing. In {@code
   * \b(?:\Qfoo)\b}, the {@code \Q} quotes the rest of the regex, so the group is never closed.
   */
  @Test
  void testInvalidComposedRegularExpression() throws IOException, InterruptedException {
    LookupResult result = runLookup("--regular-expressions", "--word-match", "\\Qfoo");
    assertEquals(254, result.exitStatus());
    // The message blames --word-match, which is what made the regex invalid.
    assertTrue(
        result.stderr().startsWith("Error: cannot apply --word-match to \\Qfoo: "),
        result.stderr());
    // The message shows the composed regex, and explains what is wrong with it.
    assertTrue(result.stderr().contains("\\b(?:\\Qfoo)\\b"), result.stderr());
    assertTrue(result.stderr().contains("Unclosed group"), result.stderr());
    // "\Qfoo" is itself a regex, so the message must not claim otherwise.
    assertFalse(result.stderr().contains("not a regex"), result.stderr());
    assertFalse(result.stderr().contains("Exception"), "unexpected stack trace");

    // Without --word-match, the same search term is a valid regex.
    LookupResult withoutWordMatch = runLookup("--regular-expressions", "\\Qfoo");
    assertEquals(0, withoutWordMatch.exitStatus());
    assertEquals("Nothing found." + lineSep, withoutWordMatch.stdout());
  }

  // Printing matches

  /** Test the output for a single match, with and without {@code --show-location}. */
  @Test
  void testPrintSingleMatch() throws IOException, InterruptedException {
    LookupResult withoutLocation = runLookup("--word-match", "bar");
    assertEquals(0, withoutLocation.exitStatus());
    assertEquals(entryOne + lineSep, withoutLocation.stdout());

    LookupResult withLocation = runLookup("--word-match", "--show-location", "bar");
    assertEquals(0, withLocation.exitStatus());
    assertEquals(entryFile + ":1:" + lineSep + entryOne + lineSep, withLocation.stdout());
  }

  /** Test the output for {@code --item-num}, with and without {@code --show-location}. */
  @Test
  void testPrintItemNum() throws IOException, InterruptedException {
    LookupResult withoutLocation = runLookup("--item-num=2", "bar");
    assertEquals(0, withoutLocation.exitStatus());
    assertEquals(entryTwo + lineSep, withoutLocation.stdout());

    LookupResult withLocation = runLookup("--item-num=2", "--show-location", "bar");
    assertEquals(0, withLocation.exitStatus());
    assertEquals(entryFile + ":3:" + lineSep + entryTwo + lineSep, withLocation.stdout());
  }

  /** Test the output for {@code --print-all}, with and without {@code --show-location}. */
  @Test
  void testPrintAll() throws IOException, InterruptedException {
    String header = "2 matches found (separated by dashes below)" + lineSep;
    String dashes = lineSep + "-------------------------" + lineSep;

    LookupResult withoutLocation = runLookup("--print-all", "bar");
    assertEquals(0, withoutLocation.exitStatus());
    assertEquals(
        header + dashes + entryOne + lineSep + dashes + entryTwo + lineSep,
        withoutLocation.stdout());

    LookupResult withLocation = runLookup("--print-all", "--show-location", "bar");
    assertEquals(0, withLocation.exitStatus());
    assertEquals(
        header + dashes + entryFile + ":1:" + lineSep + entryOne + lineSep + dashes + entryFile
            + ":3:" + lineSep + entryTwo + lineSep,
        withLocation.stdout());
  }

  /** Test the multiple-match synopsis output, with and without {@code --show-location}. */
  @Test
  void testPrintSynopsis() throws IOException, InterruptedException {
    String header =
        "2 matches found. Use -i to print a specific match or -a to see them all." + lineSep;

    LookupResult withoutLocation = runLookup("bar");
    assertEquals(0, withoutLocation.exitStatus());
    assertEquals(header + synopsis(1, entryOne) + synopsis(2, entryTwo), withoutLocation.stdout());

    LookupResult withLocation = runLookup("--show-location", "bar");
    assertEquals(0, withLocation.exitStatus());
    assertEquals(
        header
            + synopsis(1, entryFile + ":1: " + entryOne)
            + synopsis(2, entryFile + ":3: " + entryTwo),
        withLocation.stdout());
  }

  /** Test that no match is reported when nothing matches. */
  @Test
  void testNoMatch() throws IOException, InterruptedException {
    LookupResult result = runLookup("nonexistentkeyword");
    assertEquals(0, result.exitStatus());
    assertEquals("Nothing found." + lineSep, result.stdout());
  }

  /** Test that an out-of-range {@code --item-num} is diagnosed. */
  @Test
  void testIllegalItemNum() throws IOException, InterruptedException {
    LookupResult tooLarge = runLookup("--item-num=3", "bar");
    assertEquals(1, tooLarge.exitStatus());
    assertTrue(
        tooLarge.stderr().startsWith("Illegal --item-num 3, should be <= 2"), tooLarge.stderr());

    LookupResult tooSmall = runLookup("--item-num=0", "bar");
    assertEquals(1, tooSmall.exitStatus());
    assertTrue(
        tooSmall.stderr().startsWith("Illegal --item-num 0, should be positive"),
        tooSmall.stderr());
  }

  /**
   * Test that an out-of-range {@code --item-num} is diagnosed even when there is only one match.
   * "prequx" appears in exactly one entry.
   */
  @Test
  void testIllegalItemNumWithOneMatch() throws IOException, InterruptedException {
    LookupResult tooLarge = runLookup("--item-num=5", "prequx");
    assertEquals(1, tooLarge.exitStatus());
    assertTrue(
        tooLarge.stderr().startsWith("Illegal --item-num 5, should be <= 1"), tooLarge.stderr());
    assertEquals("", tooLarge.stdout(), "should not also print the single match");

    LookupResult tooSmall = runLookup("--item-num=0", "prequx");
    assertEquals(1, tooSmall.exitStatus());
    assertTrue(
        tooSmall.stderr().startsWith("Illegal --item-num 0, should be positive"),
        tooSmall.stderr());

    // The one legal value still prints the single match.
    LookupResult legal = runLookup("--item-num=1", "prequx");
    assertEquals(0, legal.exitStatus(), legal.stderr());
    assertEquals(entryThree + lineSep, legal.stdout());
  }

  /**
   * Test that a non-positive {@code --item-num} is diagnosed even when nothing matches, since that
   * value is illegal no matter how many matches there are. A positive {@code --item-num} cannot be
   * checked against the number of matches, so it is accepted.
   */
  @Test
  void testIllegalItemNumWithNoMatch() throws IOException, InterruptedException {
    LookupResult tooSmall = runLookup("--item-num=0", "nonexistentkeyword");
    assertEquals(1, tooSmall.exitStatus());
    assertTrue(
        tooSmall.stderr().startsWith("Illegal --item-num 0, should be positive"),
        tooSmall.stderr());
    assertEquals("", tooSmall.stdout(), "should not also report a search result");

    LookupResult positive = runLookup("--item-num=5", "nonexistentkeyword");
    assertEquals(0, positive.exitStatus(), positive.stderr());
    assertEquals("Nothing found." + lineSep, positive.stdout());
  }
}
