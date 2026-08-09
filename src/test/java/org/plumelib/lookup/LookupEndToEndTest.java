package org.plumelib.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end tests for the Lookup program. Each test runs the program in a new JVM, in a fresh
 * working directory, and compares everything the program produced -- its exit status, its standard
 * output, and its standard error -- to goal files stored in the repository.
 *
 * <p>The test data is in {@code src/test/resources/end-to-end}, one subdirectory per test case; see
 * {@code src/test/resources/end-to-end/README.txt} for the layout of a test case. Every
 * subdirectory is a test case, so a new test case can be added without changing this file.
 */
final class LookupEndToEndTest {

  /** Creates a new LookupEndToEndTest. */
  public LookupEndToEndTest() {}

  /** The classpath resource that contains one subdirectory per test case. */
  private static final String testDataResource = "/end-to-end";

  /** The directory that contains one subdirectory per test case. */
  private static final Path testDataDir = findTestDataDir();

  /** The file names that may appear in a test case directory. */
  private static final Set<String> permittedCaseFiles =
      Set.of(
          "description.txt",
          "input",
          "home",
          "args.txt",
          "goal-out.txt",
          "goal-err.txt",
          "goal-err-prefix.txt",
          "goal-status.txt");

  /**
   * What the test harness substitutes for the working directory's absolute path, wherever that path
   * appears in the program's output. Without the substitution, no goal file could record output
   * that contains an absolute path, because the working directory is a fresh temporary directory.
   */
  private static final String workingDirPlaceholder = "${workdir}";

  /** How long to wait for the program to terminate, in seconds. */
  private static final int timeoutSeconds = 120;

  /**
   * Returns the directory that contains the test cases.
   *
   * @return the directory that contains the test cases
   */
  private static Path findTestDataDir() {
    URL url = LookupEndToEndTest.class.getResource(testDataResource);
    if (url == null) {
      throw new Error("Cannot find resource " + testDataResource + " on the classpath.");
    }
    try {
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new Error("Cannot convert " + url + " to a path.", e);
    }
  }

  /**
   * Returns the name of each test case: that is, of each subdirectory of the test data directory.
   *
   * @return the name of each test case
   * @throws IOException if the test data directory cannot be read
   */
  private static List<String> testCaseNames() throws IOException {
    List<String> result = new ArrayList<>();
    try (Stream<Path> children = Files.list(testDataDir)) {
      for (Path child : children.toList()) {
        if (Files.isDirectory(child)) {
          result.add(child.toFile().getName());
        }
      }
    }
    result.sort(Comparator.naturalOrder());
    assertTrue(!result.isEmpty(), "No test cases in " + testDataDir);
    return result;
  }

  /**
   * Runs one end-to-end test case.
   *
   * @param caseName the name of the test case, which is the name of a subdirectory of the test data
   *     directory
   * @param workingDir the directory in which to run the program
   * @param captureDir a directory in which to capture the program's standard output and standard
   *     error; it is separate from {@code workingDir} so that the capture files cannot be mistaken
   *     for files that the program wrote
   * @throws Exception if the test case cannot be read or the program cannot be run
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("testCaseNames")
  void endToEnd(String caseName, @TempDir Path workingDir, @TempDir Path captureDir)
      throws Exception {
    Path caseDir = testDataDir.resolve(caseName);
    checkCaseDir(caseDir);

    Path inputDir = caseDir.resolve("input");
    if (Files.isDirectory(inputDir)) {
      copyTree(inputDir, workingDir.resolve("input"));
    }
    // The program is run with its home directory set to this one, so that a test case can exercise
    // the default entry file, ~/lookup/root, without depending on who is running the tests.
    Path homeDir = workingDir.resolve("home");
    Files.createDirectories(homeDir);
    Path caseHomeDir = caseDir.resolve("home");
    if (Files.isDirectory(caseHomeDir)) {
      copyTree(caseHomeDir, homeDir);
    }
    Set<String> filesBefore = relativeFileNames(workingDir).keySet();

    List<String> args = arguments(caseDir);
    Path stdoutFile = captureDir.resolve("stdout.txt");
    Path stderrFile = captureDir.resolve("stderr.txt");
    int status = runProgram(workingDir, homeDir, args, stdoutFile, stderrFile);

    String context = "Test case " + caseName + ", arguments " + args + ".";

    assertEquals(goalStatus(caseDir), status, context + "  Wrong exit status.");
    assertEquals(
        goalText(caseDir.resolve("goal-out.txt")),
        normalize(readText(stdoutFile), workingDir),
        context + "  Wrong standard output.");
    checkStderr(caseDir, normalize(readText(stderrFile), workingDir), context);
    assertEquals(
        String.join(System.lineSeparator(), filesBefore),
        String.join(System.lineSeparator(), relativeFileNames(workingDir).keySet()),
        context + "  The program should not create or delete any file.");
  }

  /**
   * Fails if the test case directory contains a file whose name the test harness does not know
   * about; such a file is probably a typo in a goal file's name, which would otherwise be silently
   * ignored.
   *
   * @param caseDir the test case directory
   * @throws IOException if the directory cannot be read
   */
  private static void checkCaseDir(Path caseDir) throws IOException {
    try (Stream<Path> children = Files.list(caseDir)) {
      for (Path child : children.toList()) {
        String name = child.toFile().getName();
        if (!permittedCaseFiles.contains(name)) {
          fail(
              "Test case "
                  + caseDir.toFile().getName()
                  + " contains unrecognized file "
                  + name
                  + ".  Permitted files are "
                  + new TreeSet<>(permittedCaseFiles)
                  + ".");
        }
      }
    }
  }

  /**
   * Returns the command-line arguments for the test case: the lines of the case's {@code args.txt},
   * one argument per line. A test case with no {@code args.txt} runs the program with no arguments.
   *
   * @param caseDir the test case directory
   * @return the command-line arguments for the test case
   * @throws IOException if the file cannot be read
   */
  private static List<String> arguments(Path caseDir) throws IOException {
    Path argsFile = caseDir.resolve("args.txt");
    if (!Files.isRegularFile(argsFile)) {
      return List.of();
    }
    return Files.readAllLines(argsFile, StandardCharsets.UTF_8);
  }

  /**
   * Runs the program.
   *
   * @param workingDir the working directory for the program
   * @param homeDir the home directory for the program
   * @param args the command-line arguments for the program
   * @param stdoutFile where to write the program's standard output
   * @param stderrFile where to write the program's standard error
   * @return the program's exit status
   * @throws IOException if the program cannot be started
   * @throws InterruptedException if this thread is interrupted while the program runs
   */
  private static int runProgram(
      Path workingDir, Path homeDir, List<String> args, Path stdoutFile, Path stderrFile)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add(javaExecutable());
    command.add("-cp");
    command.add(classpath());
    command.add("-Duser.home=" + homeDir);
    // The program's input files are UTF-8 no matter what the platform's encoding is, so make its
    // output UTF-8 too; otherwise a goal file could not record non-ASCII output.
    command.add("-Dstdout.encoding=UTF-8");
    command.add("-Dstderr.encoding=UTF-8");
    command.add(Lookup.class.getName());
    command.addAll(args);
    Process process =
        new ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectOutput(stdoutFile.toFile())
            .redirectError(stderrFile.toFile())
            .start();
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new Error("The program did not terminate within " + timeoutSeconds + " seconds.");
    }
    return process.exitValue();
  }

  /**
   * Returns the java executable of the JVM that is running the tests.
   *
   * @return the java executable of the JVM that is running the tests
   */
  private static String javaExecutable() {
    String javaHome = System.getProperty("java.home");
    if (javaHome == null) {
      return "java";
    }
    Path java = Path.of(javaHome, "bin", "java");
    if (Files.isRegularFile(java)) {
      return java.toString();
    }
    Path javaExe = Path.of(javaHome, "bin", "java.exe");
    if (Files.isRegularFile(javaExe)) {
      return javaExe.toString();
    }
    return "java";
  }

  /**
   * Returns the classpath with which to run the program, which is the classpath of the JVM that is
   * running the tests.
   *
   * @return the classpath with which to run the program
   */
  private static String classpath() {
    String result = System.getProperty("java.class.path");
    if (result == null) {
      throw new Error("Property java.class.path is not set.");
    }
    return result;
  }

  /**
   * Returns the expected exit status of the test case, which is 0 if the test case does not say.
   *
   * @param caseDir the test case directory
   * @return the expected exit status of the test case
   * @throws IOException if the goal file cannot be read
   */
  private static int goalStatus(Path caseDir) throws IOException {
    Path goalStatusFile = caseDir.resolve("goal-status.txt");
    if (!Files.isRegularFile(goalStatusFile)) {
      return 0;
    }
    return Integer.parseInt(readText(goalStatusFile).trim());
  }

  /**
   * Fails if the program's standard error is not what the test case says it should be. The test
   * case gives either the whole of the standard error (in {@code goal-err.txt}) or a prefix of it
   * (in {@code goal-err-prefix.txt}, for output such as a stack trace that contains line numbers
   * that would make a whole-text goal file brittle). If the test case gives neither file, the
   * program should write nothing to standard error.
   *
   * @param caseDir the test case directory
   * @param stderr what the program wrote to standard error
   * @param context a description of the test case, for failure messages
   * @throws IOException if a goal file cannot be read
   */
  private static void checkStderr(Path caseDir, String stderr, String context) throws IOException {
    Path prefixFile = caseDir.resolve("goal-err-prefix.txt");
    if (Files.isRegularFile(prefixFile)) {
      // Remove the goal file's final line separator, which is not part of the prefix.
      String prefix = goalText(prefixFile).stripTrailing();
      assertTrue(
          stderr.startsWith(prefix),
          context
              + "  Standard error should start with:%n%s%nbut was:%n%s".formatted(prefix, stderr));
      return;
    }
    assertEquals(
        goalText(caseDir.resolve("goal-err.txt")), stderr, context + "  Wrong standard error.");
  }

  /**
   * Returns every file (not directory) under the given directory, as a map from the file's name
   * relative to the given directory (using "/" as the separator) to the file.
   *
   * @param dir the directory to walk
   * @return the files under the given directory
   * @throws IOException if the directory cannot be read
   */
  private static Map<String, Path> relativeFileNames(Path dir) throws IOException {
    Map<String, Path> result = new TreeMap<>();
    try (Stream<Path> paths = Files.walk(dir)) {
      for (Path path : paths.toList()) {
        if (Files.isRegularFile(path)) {
          result.put(dir.relativize(path).toString().replace(File.separatorChar, '/'), path);
        }
      }
    }
    return result;
  }

  /**
   * Returns the contents of the given file, as UTF-8 text.
   *
   * @param file the file to read
   * @return the contents of the given file
   * @throws IOException if the file cannot be read
   */
  private static String readText(Path file) throws IOException {
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  /**
   * Returns the contents of the given goal file, or the empty string if the goal file does not
   * exist. A goal file is written with the working directory's path already replaced by {@link
   * #workingDirPlaceholder}, so it needs no such replacement.
   *
   * @param file the goal file to read
   * @return the contents of the given goal file, or the empty string
   * @throws IOException if the file cannot be read
   */
  private static String goalText(Path file) throws IOException {
    return Files.isRegularFile(file) ? readText(file).replace("\r\n", "\n") : "";
  }

  /**
   * Returns the given output of the program, in a form that a goal file can record: Windows line
   * separators are replaced by Unix ones (the program terminates each line it writes with the
   * platform's line separator), and the working directory's path is replaced by {@link
   * #workingDirPlaceholder} (the working directory is a fresh temporary directory).
   *
   * @param text output of the program
   * @param workingDir the directory in which the program ran
   * @return the given text, in a form that a goal file can record
   * @throws IOException if the working directory's real path cannot be determined
   */
  private static String normalize(String text, Path workingDir) throws IOException {
    String result = text.replace("\r\n", "\n");
    // The program might print either form of the path: it prints the working directory as the JVM
    // was told it, but a temporary directory's path can also contain a symbolic link that some
    // other operation resolves.
    for (Path dir : List.of(workingDir.toAbsolutePath(), workingDir.toRealPath())) {
      result = result.replace(dir.toString(), workingDirPlaceholder);
    }
    return result;
  }

  /**
   * Copies a directory tree.
   *
   * @param from the directory to copy
   * @param to the directory to copy it to; it and its parents are created if necessary
   * @throws IOException if a file cannot be copied
   */
  private static void copyTree(Path from, Path to) throws IOException {
    Files.createDirectories(to);
    for (Map.Entry<String, Path> file : relativeFileNames(from).entrySet()) {
      Path target = to.resolve(file.getKey().replace('/', File.separatorChar));
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.copy(file.getValue(), target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
