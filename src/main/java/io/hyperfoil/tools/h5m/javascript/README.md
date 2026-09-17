# JavaScript grammar

The ANTLR4 grammar files in this package come from the upstream
[grammars-v4](https://github.com/antlr/grammars-v4/tree/master/javascript/javascript) repository,
licensed under the MIT license (copyright notices are preserved in each file):

- `JavaScriptLexer.g4`
- `JavaScriptParser.g4`
- `JavaScriptLexerBase.java`
- `JavaScriptParserBase.java`

The grammars are kept unmodified; the generated code lands in `io.hyperfoil.tools.h5m.javascript`
because the `antlr4-maven-plugin` derives the package from the location of the `.g4` files relative
to its configured `sourceDirectory` (`src/main/java`).

The lexer, parser, listener and visitor classes are not checked in: they are generated at build time
by that plugin (see `pom.xml`) into `target/generated-sources/antlr4`. To pick up a newer upstream
grammar, just copy the four files above over the local ones.

`RefactorJs.java` is project code built on top of the generated parser.
