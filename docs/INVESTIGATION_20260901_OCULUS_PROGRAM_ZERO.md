# Oculus program-zero recovery, 2026-09-01

## Scope

This record covers the shader-only black/floating Create geometry reported in
the DeceasedCraft client. It does not attribute the last native program writer
to a particular optional mod, and it does not change Flywheel backend support
decisions.

Primary evidence is the archived DeceasedCraft log whose decompressed SHA-256
is `E4D580CC1CC5720CF634ECA9D3FBD602230141058F473277D2BEDF9D8ECE5AB8`.
The screenshot was captured at 22:46:05, between two logged
`GL_INVALID_OPERATION ... No active program` reports at 22:46:01 and 22:46:11.

## Observed behavior

- Complementary shaders were enabled at 22:36:32.
- Flywheel fell back from `indirect` to `off` before the first error.
- The first `No active program` report appeared at 22:36:55.
- At least 2,025,608 occurrences were reported or counted by the bounded flood
  guard during the run.
- Voxy initialized 51 seconds after the first error, so it was not required to
  start this failure.
- Disabling shaders stopped the error. Re-enabling shaders reproduced it.
- A request for instancing remained `off` because Flywheel reported
  `instancingSupported=false`; bypassing that safety check is not a fix.
- The client exited normally and did not report a shader compilation failure,
  context loss, or fatal Mixin error.

## Failure mechanism

Oculus 1.8.0 injects a private `iris$program` cache into
`GlStateManager._glUseProgram`. If the requested ID equals that cache, Oculus
cancels the real OpenGL call. Oculus `ExtendedShader` also caches the last
applied Java shader object.

Several renderers can legitimately use native `glUseProgram` at their own
boundaries. Such a call can leave `GL_CURRENT_PROGRAM=0` while the Oculus cache
still contains the expected non-zero ID. Sable's previous recovery detected
the mismatch but called only `ProgramManager.glUseProgram(expected)`. That
normal call enters the Oculus cache and can be cancelled, so it is not a
reliable recovery in the exact state it is intended to repair.

The precise last native writer is not identified by this archived log. That
unknown does not prevent repairing the broken cache invariant at the existing
Oculus compatibility boundary.

## Minimal repair

At `ExtendedShader.apply` entry, while the existing session/config gate is on:

1. Read the expected shader ID and real `GL_CURRENT_PROGRAM`.
2. Return without changing state if they already match.
3. Call the canonical `ProgramManager.glUseProgram` path first so Oculus can
   update its cache normally.
4. Re-read the real program and call native `GL20C.glUseProgram` only when the
   mismatch remains after the canonical attempt.

This is not a global per-frame force, an error suppressor, a renderer rebuild,
or a mod/block allow-list. It is a fallback inside the already established
Oculus adapter and only writes state after observing a concrete mismatch.

## Validation boundary

Automated acceptance must cover the compiled handler's control flow and Mixin
annotation, the runtime A/B gate, final production bytecode, public-tree audit,
and the normal Forge release build.

The `release` and `test` modes run both the compiled ASM contract and the final
reobfuscated-JAR verifier. The `quick` mode intentionally skips JUnit and is not
an equivalent acceptance artifact.
Runtime acceptance remains user-run and must include:

- the same DeceasedCraft scene with shaders enabled;
- `No active program` count returning to zero;
- the affected moving parts remaining correctly positioned and textured;
- shaders off and back on again;
- a shader resource reload and a world/server transition;
- Flywheel's supported `off`/`indirect` choices without forcing instancing.

If the artifact remains while `No active program` is zero, treat it as a
separate vertex/model compatibility defect rather than expanding this repair.
