# Contributing to ArchiTech Launcher

Thank you for helping improve ArchiTech Launcher.

## License of contributions

By submitting code, documentation, tests, build scripts, or other material to
this repository, you represent that:

1. you have the right to submit the contribution;
2. the contribution does not knowingly contain proprietary or unlawfully
   copied material; and
3. you agree that the contribution will be licensed under the GNU General
   Public License version 3 only (`GPL-3.0-only`), unless the maintainers
   explicitly agree in writing to different terms for a clearly identified
   file.

Do not submit third-party assets or code unless their license is documented and
compatible with the project.

## Development requirements

- JDK 21
- Git
- the Gradle Wrapper included in the repository

No system-wide Gradle installation is required.

## Building and testing

On Windows:

```powershell
.\gradlew.bat clean test shadowJar
```

On Linux or macOS:

```bash
./gradlew clean test shadowJar
```

A contribution should compile and all tests should pass before it is submitted.

## Pull requests

Keep pull requests focused. A bug fix should not also contain unrelated
formatting changes, package renames, or broad architectural refactoring.

A useful pull request description should include:

- the problem being solved;
- the behavior before the change;
- the behavior after the change;
- relevant logs or reproduction steps;
- tests added or updated;
- any compatibility, migration, or security impact.

## Tests

Behavioral changes should include tests where practical.

The most important areas to cover are:

- downloads, retries, cancellation, and integrity checks;
- manifest parsing and mod synchronization;
- path traversal and ZIP extraction protections;
- authentication and token persistence;
- launch argument generation;
- configuration migration and invalid configuration handling.

## Code and repository hygiene

Do not commit:

- `.idea/`
- `.gradle/`
- `build/`
- `bin/`
- `out/`
- compiled `.class` files;
- local launcher configuration;
- access tokens, credentials, private keys, or secrets;
- generated game files, Minecraft assets, mods, or native libraries.

Use UTF-8 for source and documentation files.

## Commit messages

Prefer concise imperative or Conventional Commit-style messages, for example:

```text
fix: enforce strict mod synchronization
test: cover failed download replacement
docs: clarify launcher configuration
refactor: extract game process manager
```

## Security issues

Do not report exploitable security vulnerabilities in a public issue.

Follow [SECURITY.md](SECURITY.md) and use GitHub private vulnerability
reporting where available.

## Branding and assets

The GPL license for the source code does not license the ArchiTech name, logo,
icons, or visual identity for use by derivative projects.

See:

- [TRADEMARKS.md](TRADEMARKS.md)
- [ASSET_LICENSE.md](ASSET_LICENSE.md)
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
