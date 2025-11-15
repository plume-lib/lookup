# lookup

Lookup searches a set of files, much like grep does.  However, Lookup has additional features:

* It searches by entry rather than by line.  By default, an entry is a paragraph, which may be separated by one or two blank lines.
* It respects comments, ignoring matches within them.
* It respects `\include` directives (it searches the named file)
* and more.

## Installation

1. Create file `lookup/build/libs/lookup-all.jar`.

   ```sh
   git clone https://github.com/plume-lib/lookup.git
   cd lookup
   ./gradlew -q assemble
   ```

2. Add aliases to your shell startup file (e.g., `~/.bash_profile`).

   ```sh
   alias lookup='java -ea -jar SOMEDIRECTORY/lookup/build/libs/lookup-all.jar -a'
   alias doc='lookup -f ${HOME}/wisdom/root_user --two-blank-lines'
   alias bibfind='lookup -l -f ${HOME}/bib/bibroot'
   alias rolo='lookup -f ${HOME}/private/addresses.tex --comment-re='
   alias quotefind='lookup -f ${HOME}/misc/quotes1 -f ${HOME}/misc/quotes'
   ```

   You might need to log out and back in for these aliases to take effect.

3. Start using the aliases.

## Usage examples

See the [uwisdom](https://github.com/mernst/uwisdom) project.

For usage details, pass `-h` (for example, run `lookup -h`) or see the [Lookup
program
documentation](http://plumelib.org/lookup/api/org/plumelib/lookup/Lookup.html).
