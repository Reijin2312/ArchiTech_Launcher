# Third-Party Notices

This project uses third-party software. Each third-party component remains
subject to its own copyright and license terms.

This list reflects the direct dependencies declared in `build.gradle` as of
**23 July 2026**. It should be reviewed whenever dependencies, packaging, or
the bundled runtime change.

## Runtime dependencies

| Component | Version | License | Purpose |
| --- | ---: | --- | --- |
| OpenJFX / JavaFX Controls and FXML | 21 | `GPL-2.0-only WITH Classpath-exception-2.0` | Desktop user interface |
| Vatuu `discord-rpc` | 1.6.2 | MIT | Discord Rich Presence integration |
| Jackson Core | 2.16.1 | Apache-2.0 | JSON streaming |
| Jackson Annotations | 2.16.1 | Apache-2.0 | JSON annotations |
| Jackson Databind | 2.16.1 | Apache-2.0 | JSON object mapping |
| Jackson Datatype JSR-310 | 2.16.1 | Apache-2.0 | Java date/time JSON support |
| SLF4J API and Simple provider | 2.0.13 | MIT | Logging |

## Source-derived components

### Emotecraft built-in animations

Repository: `https://github.com/KosmX/emotes`

The files under `src/main/resources/animations/emotecraft/` are adapted copies
of Emotecraft's built-in animation assets. Included clips: `waving`, `clap`,
`palm`, `here`, `point`, and `backflip`.

Emotecraft and these assets are licensed under GNU GPL version 3. The complete
GPL-3.0 license text is provided in this repository's `LICENSE` file. Original
author metadata is retained inside each animation JSON file.

### Inspect Animations

Repository: `https://codeberg.org/SoundsoftheSun/inspect-animations`

The native JavaFX skin renderer adapts the procedural held-item transforms and
third-person arm poses for the turn, toss, flip, and flourish inspections from
Inspect Animations. No upstream binary or texture assets are bundled.

Copyright (c) 2026 SoundsoftheSun. Licensed under the MIT License.

### skinview3d and skinview-utils

Repositories: `https://github.com/bs-community/skinview3d` and
`https://github.com/bs-community/skinview-utils`

The native JavaFX skin renderer adapts the Minecraft model proportions, UV
atlas mapping, joint placement, animation curves, legacy-skin conversion, and
slim-arm detection from these MIT-licensed projects. Their JavaScript runtime
is not bundled with the launcher.

Copyright (c) 2014-2018 Kent Rasmussen

Copyright (c) 2017-2022 Haowei Wen, Sean Boult and contributors

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

### Vatuu `discord-rpc`

Repository: `https://github.com/Vatuu/discord-rpc`

Copyright (c) 2018 Nicolas Adamoglou

Licensed under the MIT License.

The upstream repository is archived and describes the library as discontinued
because Discord RPC was deprecated. This notice is informational and does not
change its MIT license.

### SLF4J

Project: `https://www.slf4j.org/`

Copyright (c) 2004-2022 QOS.ch Sarl (Switzerland)

Licensed under the MIT License.

### Jackson

Project: `https://github.com/FasterXML/jackson`

The Jackson components listed above are licensed under the Apache License,
Version 2.0.

### OpenJFX

Project: `https://openjfx.io/`

OpenJFX modules are distributed under GNU GPL version 2 with the Classpath
Exception. The Classpath Exception permits linking independent modules under
their own license, subject to the conditions of the exception.

## Test-only dependencies

| Component | Version | License | Purpose |
| --- | ---: | --- | --- |
| JUnit Jupiter / JUnit Platform | 5.10.0 BOM | EPL-2.0 | Unit and integration testing |

Test dependencies are not intended to be included in the production launcher
artifact.

## Build tooling

| Component | Version | License | Purpose |
| --- | ---: | --- | --- |
| OpenJFX Gradle Plugin | 0.1.0 | BSD-3-Clause | JavaFX dependency and module configuration |
| Shadow Gradle Plugin | 8.1.1 | Apache-2.0 | Fat JAR creation |
| Gradle Wrapper / Gradle | repository-defined | Apache-2.0 | Build automation |

Build tools are used to produce the application and are not necessarily part of
the distributed launcher binary.

## Minecraft, NeoForge, and external services

Minecraft, Minecraft assets, and related names and marks are owned by Microsoft,
Mojang Studios, and their respective rights holders. They are not licensed
under this project's GPL license.

NeoForge and components downloaded or installed by the launcher remain subject
to their respective licenses.

Discord and Telegram names, logos, and services remain subject to the rights and
policies of their respective owners.

ArchiTech Launcher is not affiliated with or endorsed by Microsoft, Mojang
Studios, NeoForged, Discord, or Telegram unless expressly stated by the relevant
party.

## Distribution note

A fat JAR, installer, or bundled Java runtime may contain third-party binary
components. Release packaging must preserve all license files, copyright
notices, attribution notices, and source-offer obligations required by those
components.

This file is a human-readable inventory and is not a substitute for including
the complete third-party license texts required by each dependency.
