# Third-party notices

This file records components distributed inside the Sable Forge 1.20.1 port artifact. It does not replace their license terms.

## Sable Companion

- Component: `sable_companion`
- Copyright: Copyright (c) 2026 RyanHCode
- License: MIT License
- License text: `sable_companion/LICENSE`

## Rapier

- Component: the modified Rapier native implementation distributed under `natives/sable_rapier`
- Copyright notice included by the upstream project: Copyright 2020 Sébastien Crozet
- License: Apache License 2.0
- License text and notice: `sable_rapier/src/main/resources/natives/sable_rapier/LICENSE-RAPIER`
- Source reference recorded by the project: <https://github.com/ryanhcode/rapier>

## lz4-java

- Maven coordinate: `at.yawk.lz4:lz4-java:1.11.0`
- Project: <https://github.com/yawkat/lz4-java>
- License declared by the artifact POM: Apache License 2.0
- Apache License 2.0 text is included in this repository at `sable_rapier/src/main/resources/natives/sable_rapier/LICENSE-RAPIER`.

## MixinExtras

- Maven coordinate: `io.github.llamalad7:mixinextras-forge:0.5.3`
- Project: <https://github.com/LlamaLad7/MixinExtras>
- License declared by the artifact POM: MIT License
- The nested artifact contains `LICENSE_MixinExtras`.

## Molang Compiler

- Maven coordinate: `gg.moonflower:molang-compiler:3.1.1.19`
- Project: <https://github.com/Ocelot5836/molang-compiler>
- License: MIT License
- The library is nested because Veil 1.0.0.296 requires it during construction, while manual Forge/HMCL installations do not consume Veil's platform dependency metadata.

## imgui-java

- Maven coordinates: `io.github.spair:imgui-java-binding:1.86.11`, `imgui-java-lwjgl3:1.86.11`, and `imgui-java-natives-windows:1.86.11`
- Project: <https://github.com/SpaiR/imgui-java>
- License declared by the artifact POM: Apache License 2.0
- Apache License 2.0 text is included in this repository at `sable_rapier/src/main/resources/natives/sable_rapier/LICENSE-RAPIER`.
- These libraries are nested because Veil's client constructor links them even when its editor UI is not opened.

## Build-time and external runtime dependencies

Minecraft, Forge, Create, Flywheel, Ponder, Veil, Gradle distributions and other Maven dependencies are not copied into the source or runtime delivery package. They are resolved from their official distribution or Maven locations under their own terms. Only the explicitly listed runtime libraries are nested in the produced mod JAR.
