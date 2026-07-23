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
