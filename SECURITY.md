# Security policy

## Supported versions

Only the latest minor release receives security updates. The whole library is API-unstable below v1.0, as described in the README "Compatibility note" section — security fixes may include breaking changes.

## Reporting a vulnerability

For suspected security issues in this library or its bundled upstream (hufrea/byedpi), use one of:

1. **GitHub private security advisory**: [Report a vulnerability](https://github.com/oviron/libbyedpi-android/security/advisories/new)
2. **Email**: awdonkin@gmail.com (PGP-encrypted preferred — public key at repo root: `oviron-signing.pub.asc`)

**Please do not file public issues for security problems.**

## Response SLA

- Acknowledgment within 72 hours
- Initial assessment within 7 days
- Fix or mitigation timeline communicated within 14 days

## Scope

In scope:
- Memory safety bugs in our wrapper (`src/cpp/native-lib.c`)
- JNI shim issues (boundary handling, ref leaks, race conditions)
- Build-pipeline vulnerabilities (compromised CI artifacts)

Out of scope (upstream byedpi responsibility — but please tell us anyway so we can pin to a fixed release):
- Bugs in `src/cpp/byedpi/` itself
- ByeDPI protocol-level issues

Out of scope (consumer responsibility):
- Misuse of the library (e.g. exposing the SOCKS5 listener publicly)
- Issues in apps that consume this library
