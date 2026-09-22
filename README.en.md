<div align="center">
  <img src="https://raw.githubusercontent.com/RiedChat/RiedChat/refs/heads/main/public/icons/icon-192.png" alt="RiedChat-Android" width="120" height="120">

  # RiedChat-Android

  Android client for the RiedChat web version

  [![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](./LICENSE)
  [![GitHub](https://img.shields.io/badge/GitHub-RiedChat-black?logo=github)](https://github.com/RiedChat/RiedChat)

  [![Русский](https://img.shields.io/badge/lang-Русский-lightgrey)](README.md)
  [![English](https://img.shields.io/badge/lang-English-blue)](README.en.md)
</div>

## About

RiedChat-Android is a client for the RiedChat web version on Android, letting
you use it natively, without a browser.

**Main repository:** [github.com/RiedChat/RiedChat](https://github.com/RiedChat/RiedChat)

## Technology

Java (Android), a WebView wrapper around the prebuilt web version
([riedchat.github.io/RiedChat](https://riedchat.github.io/RiedChat/)), AndroidX
(core, fragment, biometric). Access to the JS bridges (downloads, biometric
vault) is restricted to the `riedchat.github.io` host. Biometrics protect the
local key store (`VaultKeyManager`, `VaultKeystoreBridge`); files from the
WebView are saved through the system DownloadManager.

## Security

Please report vulnerabilities per [SECURITY.md](./SECURITY.md), not through
public issues.

## Contributing

Pull requests are welcome. Before your first contribution, read
[CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md); submitting a pull request means
you agree to the terms of [CLA.md](./CLA.md).

## License

Distributed under the GNU Affero General Public License, as stated in the
[LICENSE](./LICENSE) file.

```
Copyright (C) 2026 RiedChat Contributors

This program is free software: you can redistribute it and/or modify it
under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or (at your
option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
```
