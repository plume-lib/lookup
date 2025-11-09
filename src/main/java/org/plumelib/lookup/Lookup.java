package org.plumelib.lookup;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.regex.qual.Regex;
import org.plumelib.options.Option;
import org.plumelib.options.OptionGroup;
import org.plumelib.options.Options;
import org.plumelib.util.EntryReader;
import org.plumelib.util.FilesPlume;
import org.plumelib.util.RegexUtil;

/**
 * Lookup searches a set of files, much like {@code grep} does. However, Lookup searches by entry
 * (by default, paragraphs) rather than by line, respects comments (ignores matches within them),
 * respects {@code \include} directives (searches the named file), and has other features.
 *
 * <p>Each search criterion is a keyword or regular expression. Lookup outputs each <em>entry</em>
 * that matches all the search criteria.
 *
 * <p>By default, search criteria are treated as keywords, and each paragraph is treated as an entry
 * &mdash; in other words, Lookup prints each paragraph (in any of the files) that contains all the
 * keywords, essentially performing paragraph-wise grep.
 *
 * <p>A file can contain one or more entries, each of which is a short entry or a long entry.
 *
 * <ul>
 *   <li>A short entry is a single paragraph (delimited from the next entry by one or two blank
 *       lines (default: 1). Lookup searches all of a short entry.
 *   <li>A long entry is introduced by a line that begins with '{@code >entry}'. The remainder of
 *       that line is a one-line description of the entry. A long entry is terminated by '{@code
 *       <entry}', by the start of a new long entry, or by the start of a new file. Lookup searches
 *       only the first line of a long entry.
 * </ul>
 *
 * <p>All matching entries are printed.
 *
 * <p>By default, Lookup searches the file ~/lookup/root. Files can contain comments and can include
 * other files. Comments start with a % sign in the first column by default. Any comment line is
 * ignored. A comment line does not separate entries as a blank line does. A file can include
 * another file via a line of the form '\include{filename}'.
 *
 * <p>The default behavior can be customized by way of command-line options.
 *
 * <p>The <a id="command-line-options">command-line options</a> are as follows:
 * <!-- start options doc (DO NOT EDIT BY HAND) -->
 *
 * <ul>
 *   <li id="optiongroup:Where-to-search">Where to search
 *       <ul>
 *         <li id="option:entry-file"><b>-f</b> <b>--entry-file=</b><i>string</i>. Specify the
 *             colon-separated search list for the file that contains information to be searched.
 *             Only the first file found is used, though it may itself contain include directives.
 *             [default: ~/lookup/root]
 *         <li id="option:search-body"><b>-b</b> <b>--search-body=</b><i>boolean</i>. Search the
 *             body of long entries in addition to the entry's description. The bodies of short
 *             entries are always searched. [default: false]
 *       </ul>
 *   <li id="optiongroup:What-to-search-for">What to search for
 *       <ul>
 *         <li id="option:regular-expressions"><b>-e</b>
 *             <b>--regular-expressions=</b><i>boolean</i>. Specifies that keywords are regular
 *             expressions. If false, keywords are text matches. [default: false]
 *         <li id="option:case-sensitive"><b>-c</b> <b>--case-sensitive=</b><i>boolean</i>. If true,
 *             keywords matching is case sensistive. By default, both regular expressions and text
 *             keywords are case-insensitive. [default: false]
 *         <li id="option:word-match"><b>-w</b> <b>--word-match=</b><i>boolean</i>. If true, match a
 *             text keyword only as a separate word, not as a substring of a word. This option is
 *             ignored if regular_expressions is true. [default: false]
 *       </ul>
 *   <li id="optiongroup:How-to-print-matches">How to print matches
 *       <ul>
 *         <li id="option:print-all"><b>-a</b> <b>--print-all=</b><i>boolean</i>. By default, if
 *             multiple entries are matched, only a synopsis of each entry is printed. If
 *             'print_all' is selected then the body of each matching entry is printed. [default:
 *             false]
 *         <li id="option:item-num"><b>-i</b> <b>--item-num=</b><i>integer</i>. Specifies which item
 *             to print when there are multiple matches. The index is 1-based; that is, it starts
 *             counting at 1.
 *         <li id="option:show-location"><b>-l</b> <b>--show-location=</b><i>boolean</i>. If true,
 *             show the filename/line number of each matching entry in the output. [default: false]
 *       </ul>
 *   <li id="optiongroup:Customizing-format-of-files-to-be-searched">Customizing format of files to
 *       be searched
 *       <ul>
 *         <li id="option:entry-start-re"><b>--entry-start-re=</b><i>regex</i>. Matches the start of
 *             a long entry. [default: ^&gt;entry *()]
 *         <li id="option:entry-stop-re"><b>--entry-stop-re=</b><i>regex</i>. Matches the end of a
 *             long entry. [default: ^&lt;entry]
 *         <li id="option:description-re"><b>--description-re=</b><i>regex</i>. Matches the
 *             description for a long entry.
 *         <li id="option:comment-re"><b>--comment-re=</b><i>string</i>. Matches an entire comment.
 *             [default: ^%.*]
 *         <li id="option:include-re"><b>--include-re=</b><i>string</i>. Matches an include
 *             directive; group 1 is the file name. [default: \\include\{(.*)\}]
 *       </ul>
 *   <li id="optiongroup:Getting-help">Getting help
 *       <ul>
 *         <li id="option:help"><b>-h</b> <b>--help=</b><i>boolean</i>. Show detailed help
 *             information and exit. [default: false]
 *         <li id="option:verbose"><b>-v</b> <b>--verbose=</b><i>boolean</i>. Print progress
 *             information. [default: false]
 *       </ul>
 * </ul>
 *
 * <!-- end options doc -->
 */
@SuppressWarnings("deprecation") // uses deprecated classes in this package
public final class Lookup {

  /** This class is a collection of methods; it does not represent anything. */
  private Lookup() {
    throw new Error("do not instantiate");
  }

  // This uses only the first file because the default search path might be
  // something like user:system and you might want only your version of the
  // system files.  It might be useful to also support (via another flag,
  // or by taking over this one, or by the syntax of the separator, or in
  // some other way) specifying multiple files on the command line.
  /**
   * Specify the colon-separated search list for the file that contains information to be searched.
   * Only the first file found is used, though it may itself contain include directives.
   */
  @OptionGroup("Where to search")
  @Option("-f The colon-separated search list of files of information; may only be supplied once")
  public static String entry_file = "~/lookup/root";

  /**
   * Search the body of long entries in addition to the entry's description. The bodies of short
   * entries are always searched.
   */
  @Option("-b Search body of long entries for matches")
  public static boolean search_body = false;

  /** Specifies that keywords are regular expressions. If false, keywords are text matches. */
  @OptionGroup("What to search for")
  @Option("-e Keywords are regular expressions")
  public static boolean regular_expressions = false;

  /**
   * If true, keywords matching is case sensistive. By default, both regular expressions and text
   * keywords are case-insensitive.
   */
  @Option("-c Keywords are case sensistive")
  public static boolean case_sensitive = false;

  /**
   * If true, match a text keyword only as a separate word, not as a substring of a word. This
   * option may be supplied together with {@code --regular-expressions}.
   */
  @Option("-w Only match search terms against complete words")
  public static boolean word_match = false;

  /**
   * By default, if multiple entries are matched, only a synopsis of each entry is printed. If
   * 'print_all' is selected then the body of each matching entry is printed.
   */
  @OptionGroup("How to print matches")
  @Option("-a Print the entire entry for each match")
  public static boolean print_all = false;

  /**
   * Specifies which item to print when there are multiple matches. The index is 1-based; that is,
   * it starts counting at 1.
   */
  @Option("-i Choose a specific item when there are multiple matches; index is 1-based")
  public static @Nullable Integer item_num = null;

  /** If true, show the filename/line number of each matching entry in the output. */
  @Option("-l Show the location of each matching entry")
  public static boolean show_location = false;

  /** Matches the start of a long entry. */
  @OptionGroup("Customizing format of files to be searched")
  @Option("If true, entries are separated by two blank lines")
  public static boolean two_blank_lines = false;

  /** Matches the start of a long entry. */
  @Option("Regex that denotes the start of a long entry")
  public static @Regex(1) Pattern entry_start_re = Pattern.compile("^>entry *()");

  /** Matches the end of a long entry. */
  @Option("Regex that denotes the end of a long entry")
  public static Pattern entry_stop_re = Pattern.compile("^<entry");

  /** Matches the description for a long entry. */
  @Option("Regex that finds an entry's description (for long entries)")
  public static @Nullable Pattern description_re = null;

  // If "", gets set to null immediately after option processing.
  /** Matches an entire comment. */
  @Option("Regex that matches an entire comment (not just a comment start)")
  public static @Nullable @Regex String comment_re = "^%.*";

  /** Matches an include directive; group 1 is the file name. */
  @Option("Regex that matches an include directive; group 1 is the file name")
  public static @Regex(1) String include_re = "\\\\include\\{(.*)\\}";

  /** Show detailed help information and exit. */
  @OptionGroup("Getting help")
  @Option("-h Show detailed help information")
  public static boolean help = false;

  /** Print progress information. */
  @Option("-v Print progress information")
  public static boolean verbose = false;

  /** Platform-specific line separator. */
  private static final String lineSep = System.lineSeparator();

  /** One-line synopsis of usage. */
  private static final String usageString = "lookup [options] <keyword> ...";

  /**
   * Look for the specified keywords in the file(s) and print the corresponding entries.
   *
   * @param args command-line arguments; see documentation
   * @throws IOException if there is a problem reading a file
   */
  @SuppressWarnings({
    "StringSplitter" // don't add dependence on Guava
  })
  public static void main(String[] args) throws IOException {

    Options options = new Options(usageString, Lookup.class);
    String[] keywords = options.parse(true, args);

    // TODO: validate arguments.  Check that various options are @Regex or @Regex(1).

    // If help was requested, print it and exit
    if (help) {
      options.printUsage();
      System.exit(0);
    }

    if (verbose) {
      System.out.printf("Options settings: %n%s%n", options.settings());
    }

    // Make sure at least one keyword was specified
    if (keywords.length == 0) {
      System.out.println("Error: No keywords specified");
      options.printUsage();
      System.exit(254);
    }

    // comment_re starts out non-null and the option processing code can't
    // make it null, so no null pointer exception is possible in the
    // if statement predicate that immediately follows this assertion.
    assert comment_re != null : "@AssumeAssertion(nullness): application invariant";

    // If the comment regular expression is empty, turn off comment processing
    if (comment_re.equals("")) {
      comment_re = null;
    }

    // Find the first readable root file.
    String rootFile = null;
    for (String candidate_unexpanded : entry_file.split(":")) {
      String candidate = FilesPlume.expandFilename(candidate_unexpanded);
      if (Files.isReadable(Path.of(candidate))) {
        rootFile = candidate;
        break;
      }
    }
    if (rootFile == null) {
      System.out.println("Error: Can't read any entry files.");
      for (String unreadable : entry_file.split(":")) {
        System.out.printf("  entry file %s%n", FilesPlume.expandFilename(unreadable));
      }
      System.exit(254);
    }

    try (EntryReader reader = new EntryReader(rootFile, two_blank_lines, comment_re, include_re)) {

      // Set up the regular expressions for long entries.
      reader.setEntryStartStop(entry_start_re, entry_stop_re);

      List<EntryReader.Entry> matchingEntries = new ArrayList<>();

      // Precompute the regular expressions, for efficiency.
      int flags = case_sensitive ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      if (word_match) {
        flags |= Pattern.UNICODE_CHARACTER_CLASS;
      }
      List<Pattern> patterns = new ArrayList<>();
      if (regular_expressions) {
        for (String keyword : keywords) {
          if (!RegexUtil.isRegex(keyword)) {
            System.out.println("Error: not a regex: " + keyword);
            System.exit(254);
          }
          patterns.add(Pattern.compile(keyword, flags));
        }
      } else if (word_match) {
        for (String keyword : keywords) {
          String keywordRegex = "\\b" + Pattern.quote(keyword) + "\\b";
          patterns.add(Pattern.compile(keywordRegex, flags));
        }
      } else if (!case_sensitive) {
        for (int i = 0; i < keywords.length; i++) {
          keywords[i] = keywords[i].toLowerCase(Locale.ROOT);
        }
      }

      try {
        // Process each entry looking for matches
        int entryCnt = 0;

        EntryReader.Entry entry = reader.getEntry();
        while (entry != null) {
          entryCnt++;
          if (verbose && ((entryCnt % 1000) == 0)) {
            System.out.printf("%d matches in %d entries\r", matchingEntries.size(), entryCnt);
          }
          String toSearch =
              (search_body || entry.shortEntry) ? entry.body : entry.getDescription(description_re);
          boolean found = true;
          if (!patterns.isEmpty()) {
            for (Pattern pattern : patterns) {
              if (!pattern.matcher(toSearch).find()) {
                found = false;
                break;
              }
            }
          } else {
            if (!case_sensitive) {
              toSearch = toSearch.toLowerCase(Locale.ROOT);
            }
            for (String keyword : keywords) {
              if (!toSearch.contains(keyword)) {
                found = false;
                break;
              }
            }
          }
          if (found) {
            matchingEntries.add(entry);
          }
          entry = reader.getEntry();
        }
      } catch (FileNotFoundException e) {
        System.out.printf(
            "Error: Can't read %s at line %d in file %s%n",
            e.getMessage(), reader.getLineNumber(), reader.getFileName());
        System.exit(254);
      }

      // Print the results
      int numMatchingEntries = matchingEntries.size();
      if (numMatchingEntries == 0) {
        System.out.println("Nothing found.");
      } else if (numMatchingEntries == 1) {
        EntryReader.Entry e = matchingEntries.get(0);
        if (show_location) {
          System.out.printf("%s:%d:%n", e.filename, e.lineNumber);
        }
        System.out.print(e.body);
      } else { // there are multiple matches
        if (item_num != null) {
          if (item_num < 1) {
            System.out.printf("Illegal --item-num %d, should be positive%n", item_num);
            System.exit(1);
          }
          if (item_num > numMatchingEntries) {
            System.out.printf(
                "Illegal --item-num %d, should be <= %d%n", item_num, numMatchingEntries);
            System.exit(1);
          }
          EntryReader.Entry e = matchingEntries.get(item_num - 1);
          if (show_location) {
            System.out.printf("%s:%d:%n", e.filename, e.lineNumber);
          }
          System.out.print(e.body);
        } else {
          int i = 0;
          if (print_all) {
            System.out.printf("%d matches found (separated by dashes below)%n", numMatchingEntries);
          } else {
            System.out.printf(
                "%d matches found. Use -i to print a specific match or -a to see them all.%n",
                numMatchingEntries);
          }

          for (EntryReader.Entry e : matchingEntries) {
            i++;
            if (print_all) {
              if (show_location) {
                System.out.printf(
                    "%n-------------------------%n%s:%d:%n", e.filename, e.lineNumber);
              } else {
                System.out.printf("%n-------------------------%n");
              }
              System.out.print(e.body);
            } else {
              if (show_location) {
                System.out.printf("  -i=%d %s:%d: %s%n", i, e.filename, e.lineNumber, e.firstLine);
              } else {
                System.out.printf("  -i=%d %s%n", i, e.getDescription(description_re));
              }
            }
          }
        }
      }
    }
  }

  /**
   * Returns the next entry. If no more entries are available, returns null.
   *
   * @param reader where to read the entry from
   * @return the next entry, or null
   * @throws IOException if there is a problem reading a file
   */
  public static EntryReader.@Nullable Entry old_getEntry(EntryReader reader) throws IOException {

    try {

      // Skip any preceding blank lines.
      String line = reader.readLine();
      while (line != null && line.isBlank()) {
        line = reader.readLine();
      }
      if (line == null) {
        return null;
      }

      EntryReader.Entry entry;
      String filename = reader.getFileName();
      long lineNumber = reader.getLineNumber();

      // If this is a long entry
      if (line.startsWith(">entry")) {

        // Get the current filename
        String currentFilename = reader.getFileName();

        // Remove '>entry' from the line
        line = line.replaceFirst("^>entry *", "");
        String firstLine = line;

        StringBuilder body = new StringBuilder();
        // Read until we find the termination of the entry
        while ((line != null)
            && !line.startsWith(">entry")
            && !line.equals("<entry")
            && currentFilename.equals(reader.getFileName())) {
          body.append(line);
          body.append(lineSep);
          line = reader.readLine();
        }

        // If this entry was terminated by the start of the next one,
        // put that line back
        if ((line != null)
            && (line.startsWith(">entry") || !currentFilename.equals(reader.getFileName()))) {
          reader.putback(line);
        }

        entry = new EntryReader.Entry(firstLine, body.toString(), filename, lineNumber, false);

      } else { // blank separated entry

        String firstLine = line;

        StringBuilder body = new StringBuilder();
        // Read until we find another blank line.
        while (line != null && !line.isBlank()) {
          body.append(line);
          body.append(lineSep);
          line = reader.readLine();
        }

        entry = new EntryReader.Entry(firstLine, body.toString(), filename, lineNumber, true);
      }

      return entry;

    } catch (FileNotFoundException e) {
      System.out.printf(
          "Error: Can't read %s at line %d in file %s%n",
          e.getMessage(), reader.getLineNumber(), reader.getFileName());
      System.exit(254);
      return null;
    }
  }

  /**
   * Returns the first line of entry.
   *
   * @param entry the entry whose first line to return
   * @return the first line of entry
   */
  public static String firstLine(String entry) {

    int ii = entry.indexOf(lineSep);
    if (ii == -1) {
      return entry;
    }
    return entry.substring(0, ii);
  }
}
