# Security Policy

## Supported versions

Until stable releases are published, security fixes are made only on the
current default branch.

After versioned releases begin, only the latest release and the current default
branch are expected to receive security fixes.

| Version | Supported |
| --- | --- |
| Current default branch | Yes |
| Latest published release | Yes |
| Older releases | No |
| Unofficial forks and modified builds | No |

## Reporting a vulnerability

Please do **not** disclose an exploitable vulnerability in a public GitHub
issue, discussion, pull request, Telegram chat, or social media post.

The preferred reporting method is GitHub private vulnerability reporting:

1. open the repository's **Security** tab;
2. choose **Advisories**;
3. select **Report a vulnerability**;
4. provide the information requested below.

If private vulnerability reporting is not available, contact the repository
owner through an official ArchiTech private contact channel and ask for a
private reporting method. Do not include exploit details in a public message.

## What to include

A useful report contains:

- a clear description of the vulnerability;
- the affected launcher version or commit;
- operating system and Java version;
- exact reproduction steps;
- a proof of concept, when safe to provide privately;
- the expected security impact;
- relevant logs with tokens and personal data removed;
- any suggested mitigation.

Never send real access tokens, passwords, private keys, or unrelated user data.

## Response targets

The maintainers aim to:

- acknowledge a complete report within 7 days;
- provide an initial assessment within 14 days;
- keep the reporter informed when meaningful progress is made;
- coordinate disclosure after a fix or mitigation is available.

These are targets rather than guarantees. Complex issues may require more time.

## Responsible disclosure

Please allow a reasonable period for investigation and remediation before
publishing technical details.

Do not:

- access or modify data belonging to other users;
- disrupt production services;
- perform denial-of-service testing;
- use social engineering;
- persist after demonstrating the minimum necessary impact;
- publicly disclose credentials, tokens, private endpoints, or working
  exploitation instructions before coordination.

Good-faith research that follows this policy will be treated as an effort to
improve the project.

## Scope

Security reports are especially relevant when they involve:

- authentication or token storage;
- arbitrary file write or deletion;
- path traversal or ZIP extraction;
- unsigned or unverified updates;
- manifest manipulation;
- remote code execution;
- unsafe process launching or argument injection;
- leakage of personal data or credentials;
- compromise of the release or update pipeline.

Vulnerabilities in Minecraft, NeoForge, Java, operating systems, or unrelated
third-party services should also be reported to the relevant upstream project.
You may notify ArchiTech when such an issue directly affects the launcher.

## Public disclosure and credit

After remediation, the maintainers may publish a security advisory describing
the issue and affected versions.

Reporter credit will be given when requested and appropriate. Reporters may
also request anonymity.
