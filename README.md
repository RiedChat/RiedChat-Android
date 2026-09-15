<div align="center">
  <img src="https://raw.githubusercontent.com/RiedChat/RiedChat/refs/heads/main/public/icons/icon-192.png" alt="RiedChat-Android" width="120" height="120">

  # RiedChat-Android

  Android-клиент для веб-версии RiedChat

  [![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](./LICENSE)
  [![GitHub](https://img.shields.io/badge/GitHub-RiedChat-black?logo=github)](https://github.com/RiedChat/RiedChat)

  [![Русский](https://img.shields.io/badge/lang-Русский-blue)](README.md)
  [![English](https://img.shields.io/badge/lang-English-lightgrey)](README.en.md)
</div>

## О проекте

RiedChat-Android - это клиент для веб-версии RiedChat на Android, позволяющий
пользоваться ей нативно, без браузера.

**Основной репозиторий:** [github.com/RiedChat/RiedChat](https://github.com/RiedChat/RiedChat)

## Технологии

Java (Android), WebView-обёртка над готовой сборкой веб-версии
([riedchat.github.io/RiedChat](https://riedchat.github.io/RiedChat/)), AndroidX
(core, fragment, biometric). Доступ к JS-мостам (загрузки, биометрический vault)
разрешён только домену `riedchat.github.io`. Биометрия защищает локальное
хранилище ключей (`VaultKeyManager`, `VaultKeystoreBridge`), файлы из WebView
сохраняются через системный DownloadManager.

## Безопасность

О найденных уязвимостях сообщайте согласно [SECURITY.md](./SECURITY.md), а не
через публичные issues.

## Участие в разработке

Pull request'ы приветствуются. Перед первым вкладом ознакомьтесь с
[CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md); отправка pull request означает
согласие с условиями [CLA.md](./CLA.md).

## Лицензия

Распространяется на условиях GNU Affero General Public License, как указано
в файле [LICENSE](./LICENSE).

```
Copyright (C) 2026 RiedChat Contributors

Эта программа - свободное программное обеспечение: вы можете распространять
и/или изменять её на условиях GNU Affero General Public License, опубликованной
Free Software Foundation, либо версии 3 этой лицензии, либо (по вашему выбору)
любой более поздней версии.

Эта программа распространяется в надежде, что она будет полезна, но БЕЗ КАКИХ-ЛИБО
ГАРАНТИЙ; даже без подразумеваемой гарантии ТОВАРНОЙ ПРИГОДНОСТИ или ПРИГОДНОСТИ
ДЛЯ ОПРЕДЕЛЁННОЙ ЦЕЛИ. Подробнее см. текст GNU Affero General Public License.

Вы должны были получить копию GNU Affero General Public License вместе с этой
программой. Если нет - см. <https://www.gnu.org/licenses/>.
```

