# lookup

Lookup searches a set of files, much like grep does.  However, Lookup
searches by entry (by default, paragraphs) rather than by line,
respects comments (ignores matches within them), respects
`\include` directives (searches the named file), and has other
options.

For details, see the [documentation](http://plumelib.org/lookup/api/org/plumelib/lookup/Lookup.html).

For an example application, see the [uwisdom](https://github.com/mernst/uwisdom/tree/wiki) project and its [README](https://github.com/mernst/uwisdom/blob/wiki/README.adoc).
