package org.plumelib.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

  /** Write the entry file that the tests search. */
  @BeforeEach
  void writeEntryFile() throws IOException {
    entryFile = tempDir.resolve("root");
    Files.writeString(
        entryFile, String.join("\n", entryOne, "", entryTwo, "", entryThree, "", entryFour, ""));
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
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(Lookup.class.getName());
    command.add("--entry-file=" + entryFile);
    command.addAll(List.of(args));

    // Redirect to files rather than reading the subprocess's streams, which could deadlock.
    Path stdoutFile = tempDir.resolve("stdout");
    Path stderrFile = tempDir.resolve("stderr");
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectOutput(stdoutFile.toFile());
    builder.redirectError(stderrFile.toFile());
    Process process = builder.start();
    int exitStatus = process.waitFor();
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
   * Test that a search term whose first character is not a word character matches nothing under
   * {@code --word-match}, as {@code --word-match}'s documentation warns. A word boundary must be
   * preceded by a word character, and "#" is not one.
   */
  @Test
  void testWordMatchNonWordCharacter() throws IOException, InterruptedException {
    LookupResult withWordMatch = runLookup("--regular-expressions", "--word-match", "#define");
    assertEquals(0, withWordMatch.exitStatus());
    assertEquals("Nothing found." + lineSep, withWordMatch.stdout());

    // Without --word-match, the same search term is found.
    LookupResult withoutWordMatch = runLookup("--regular-expressions", "#define");
    assertEquals(0, withoutWordMatch.exitStatus());
    assertEquals(entryFour + lineSep, withoutWordMatch.stdout());
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
        result.stderr().startsWith("Error: cannot apply --word-match to regex \\Qfoo: "),
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
}
