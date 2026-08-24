# Building the Forge 1.20.1 mod

## Recommended Windows build

Run the following file from Explorer or a terminal:

```text
build.cmd release
```

The user-installable JAR and its SHA-256 checksum are written to `dist`.
Development-only validation classes and the Sable Test Tools mod are not placed
inside the production JAR.

The bootstrap downloads Microsoft OpenJDK 17 and Gradle 8.14.3 only when a
suitable local copy is unavailable. Both downloads use pinned SHA-256 hashes.
Downloaded tools, Gradle caches and build outputs are ignored by Git.

Available options:

```text
build.cmd release -Offline
build.cmd test
build.cmd quick
build.cmd audit
```

`release` builds the production mod and runs its tests. `test` follows the same
path and also builds Sable Test Tools. `quick` follows the same audited artifact
path but skips the Sable unit-test task for short local iterations. It must not
be used as release evidence. `audit` checks repository contents without
building. `-Offline` requires the JDK, Gradle distribution and all Maven
dependencies to already be available locally.

Forge-generated diagnostic logs are moved outside the repository after each
build. Set the optional `SABLE_BUILD_LOG_DIR` environment variable when those
logs need to be retained in a specific external location; otherwise the system
temporary directory is used.

The unclassified JAR in `forge/build/libs` is the production mod. Files ending
in `-sources.jar` are for source browsing and are not installed as mods. The
repository does not store a Gradle distribution or Wrapper JAR; `build.cmd` is
the supported build entry point. The legacy `build-user-jar.cmd` and
`build-with-test-tools.cmd` names remain as thin compatibility wrappers.

## Runtime dependencies

The build does not download mods into a Minecraft instance. Install the matching
Forge, Create, Flywheel and Veil versions listed in the repository README using
their official distribution channels.
