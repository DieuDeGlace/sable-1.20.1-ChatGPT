# Distribution and license boundary

## Stored in this repository

- Sable source and resources under the PolyForm Shield License 1.0.0.
- Sable Companion source retained by upstream Sable under the MIT License.
- The compressed Rapier native required by Sable, with the upstream Apache 2.0
  license and notice.
- Sable Test Tools source under the MIT License.

## Not stored in this repository

- Minecraft client or server files;
- Forge installers, universal JARs or libraries;
- Create, Flywheel, Ponder, Veil or Registrate JARs;
- Maven or Gradle caches;
- local Minecraft instances, worlds, crash reports or logs;
- private paths, credentials or launcher profiles; and
- construction records, internal evidence, work plans or private design notes.

Build-time dependencies are declared by Maven coordinate and downloaded from
their configured repositories. Some small libraries are nested in the produced
Sable JAR because they are required at runtime. Their names and licenses are
listed in `THIRD_PARTY_NOTICES.md`; their standalone JAR files are not committed
to source control.

## Publication check

Before publishing a tree, run:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File tools/audit-public-tree.ps1
```

The audit rejects known private records, local paths, nested repositories and
unexpected binary archives. Its binary allowlist contains only Sable's required
compressed Rapier native.

This document is a record of repository contents, not legal advice. The complete
license texts control redistribution.
