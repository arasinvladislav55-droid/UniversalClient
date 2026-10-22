# Сборка программы и выпуск обновлений

Репозиторий: https://github.com/arasinvladislav55-droid/UniversalClient
Релизы: https://github.com/arasinvladislav55-droid/UniversalClient/releases
Ветка: `master`. Лаунчер проверяет файл
`https://raw.githubusercontent.com/arasinvladislav55-droid/UniversalClient/master/version.json`
(ссылка прописана в `core/Updater.java`, `build.ps1`).

## Один раз: настройка
1. Установите **JDK 21**, **Maven** и **WiX Toolset 3.x** (нужен jpackage для .exe), всё должно быть в PATH.
2. Репозиторий должен быть публичным — иначе лаунчер не сможет читать `version.json` без авторизации.

## Сборка установщика
```
powershell -ExecutionPolicy Bypass -File build.ps1 -Notes "Что нового в этой версии"
```
Результат: `dist/UniversalClient-<версия>.exe` (установщик) и `version.json`
(одновременно в `dist/` и **в корне проекта** — читает лаунчер именно корневой).
Первый раз раздайте людям этот .exe. Установка — в профиль пользователя, права администратора не нужны.

## Выпуск обновления (по шагам)
1. Поменяйте `LAUNCHER_VERSION` в `core/Config.java` (например, `2.5.0` → `2.6.0`).
2. Запустите `build.ps1 -Notes "..."`.
3. На GitHub: Releases → Draft a new release → тег `v2.6.0` (версия = как в `LAUNCHER_VERSION`, с буквой `v`) →
   прикрепите `dist/UniversalClient-2.6.0.exe` → Publish release.
4. Закоммитьте и запушьте `version.json` из корня проекта в ветку `master`
   (`git add version.json && git commit -m "2.6.0" && git push`). **Делайте это после публикации релиза**,
   иначе игроки увидят «ОБНОВИТЬ» раньше, чем файл появится.
5. Всё. У игроков при следующем запуске (или в течение 15 минут) вместо «ИГРАТЬ» появится «ОБНОВИТЬ».

Исходники пушите как обычно. Папки `dist/`, `build/`, `target/` в git не попадают (`.gitignore`) — exe лежит только в Releases.

## Как это работает у игрока
Лаунчер читает `version.json`. Если `version` новее встроенной `LAUNCHER_VERSION` — кнопка меняется на «ОБНОВИТЬ»,
запуск игры и вход на серверы блокируются. По нажатию скачивается установщик (проверяется SHA-256), запускается,
лаунчер закрывается. Установщик заменяет старую версию (благодаря неизменному `--win-upgrade-uuid`); настройки и профили в
`%APPDATA%\.universalclient` сохраняются. После установки запустите лаунчер из меню «Пуск».

## Что где в коде
- `core/Updater.java` — проверка `version.json`, скачивание установщика (с проверкой SHA-256), запуск.
- `ui/LauncherApp.java` — проверка при старте и каждые 15 минут, кнопка «ОБНОВИТЬ».

## Важно
- `$UpgradeUuid` в `build.ps1` никогда не меняйте — иначе обновление поставится рядом со старой версией.
- Собирать .exe нужно на Windows.
- Версия в `version.json` всегда должна совпадать с `LAUNCHER_VERSION` собранного exe, иначе лаунчер будет предлагать обновление бесконечно.
- Если хотите не принуждать к обновлению, а лишь предлагать — уберите проверки `pendingUpdate` в `LauncherApp.play` и `joinServer`.
