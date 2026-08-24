# Sable Test Tools

Sable Test Tools is an optional development mod for manual Sable testing. It is
not required by normal players and is never embedded in the production Sable
JAR.

Build it together with Sable by running:

```text
build.cmd test
```

The resulting `sable-test-tools-forge-1.20.1-*.jar` is written to `dist` beside
the Sable JAR.

For a short bug-fix iteration, `build.cmd quick` produces the same two artifact
types and runs the same source/JAR boundary checks, while skipping the Sable
unit-test task. A final `build.cmd test` is still required before a test result
is treated as verified.

The mod provides:

- a selection wand for choosing blocks and creating a physical sub-level;
- a gravity gun for holding and moving a physical object; and
- commands for configuring test parameters and inspecting a test session.

Use it only in disposable test worlds. It exposes tools intended to create
unusual game states and is not a gameplay or server-administration mod.

The source is kept in `test-tools/src`. Forge-side unit tests remain in
`forge/src/test`, and the optional development validation mod remains in
`forge/src/validation`; the production build checks that validation classes do
not enter the user JAR.
