# Security Policy

If you believe you've found a vulnerability in RiedChat (a bug that allows
something that shouldn't be possible), please report it privately - via
**<quardinimus@gmail.com>**.

Please do not report vulnerabilities through public GitHub issues,
discussions, or other open channels - this gives us time to ship a fix
before the risk becomes known to all users.

In your report, please include:

- A description of the vulnerability and its potential impact.
- Steps to reproduce, including proof-of-concept code if available.
- The RiedChat version/commit the vulnerability was verified on.

## Scope

A "vulnerability in RiedChat" is a vulnerability in the Android wrapper
distributed through this repository. This includes, for example, bugs in
the WebView bridges (`AndroidDownloadInterface`, `AndroidVaultBridge`), the
trusted host list (`TrustedHosts`), key storage and protection via the
biometric vault (`VaultKeyManager`, `VaultKeystoreBridge`), and file
download handling.

Out of scope, and should be reported separately:

- Vulnerabilities in the RiedChat web version itself (OMEMO, WebRTC, UI) -
  report those to the [main repository](https://github.com/RiedChat/RiedChat).
- Issues specific to a particular deployment (a misconfigured XMPP server,
  TURN endpoint, weak server-side TLS settings) - report those to the
  operator of that deployment, not the project maintainers.

## Supported Versions

| Version | Supported |
| ------- | --------- |
| main    | Yes       |

The project does not yet maintain multiple stable release branches; security
fixes are only issued for the current code on `main`.

## Response

We aim to acknowledge reports within a reasonable time and keep the
reporter updated as a fix is developed. Unless stated otherwise, the
reporter will be credited in the release notes.
