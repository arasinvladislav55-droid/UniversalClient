# Сборка установщика Universal Client (Windows).
# Запуск:  powershell -ExecutionPolicy Bypass -File build.ps1
#
# Нужно: JDK 21 (jpackage), Maven, WiX Toolset 3.x (https://github.com/wixtoolset/wix3/releases) в PATH.
# Версия берётся из Config.LAUNCHER_VERSION — меняете её в ОДНОМ месте (и в pom.xml для порядка).

param([string]$Notes = "Исправления и улучшения")

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# ── НАСТРОЙКИ ──────────────────────────────────────────────
$AppName      = "UniversalClient"
$Vendor       = "Universal Client"
# Адрес, откуда пользователи скачают установщик (папка релиза на GitHub или ваш сайт):
$DownloadBase = "https://github.com/arasinvladislav55-droid/UniversalClient/releases/download/v{VERSION}"
# НЕ МЕНЯТЬ НИКОГДА — по этому GUID Windows понимает, что новый установщик = обновление старого:
$UpgradeUuid  = "e7796d9e-d7da-4c90-94e1-20df77ed7fde"
# ───────────────────────────────────────────────────────────

$cfg = Get-Content "src/main/java/org/example/core/Config.java" -Raw
if ($cfg -notmatch 'LAUNCHER_VERSION\s*=\s*"(\d+(\.\d+){1,2})"') { throw "Не нашёл LAUNCHER_VERSION в Config.java" }
$Version = $Matches[1]
Write-Host "==> Версия: $Version" -ForegroundColor Cyan

Write-Host "==> mvn package" -ForegroundColor Cyan
mvn -q clean package
if ($LASTEXITCODE -ne 0) { throw "Maven завершился с ошибкой" }

# Чистим старую сборку. Если Windows не даёт удалить (папка открыта в Проводнике, exe запущен) — понятная ошибка.
foreach ($d in @("build", "dist")) {
    if (Test-Path $d) {
        for ($i = 1; $i -le 3; $i++) {
            Remove-Item -Recurse -Force $d -ErrorAction SilentlyContinue
            if (-not (Test-Path $d)) { break }
            Start-Sleep -Seconds 1
        }
        if (Test-Path $d) {
            throw "Не удалось удалить папку '$d'. Закройте окна Проводника с ней, установщик и антивирус-проверку, удалите папку вручную и запустите build.ps1 снова."
        }
    }
}
New-Item -ItemType Directory -Force build/input, dist | Out-Null
Copy-Item target/UniversalClient-Launcher.jar build/input/

# Игре нужна почти полная Java, поэтому в установщик кладём расширенный набор модулей.
$modules = "java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.sql,java.xml,java.scripting,jdk.unsupported,jdk.crypto.ec,jdk.zipfs,jdk.localedata,jdk.jfr"

Write-Host "==> jpackage" -ForegroundColor Cyan
jpackage `
  --type exe `
  --name $AppName `
  --app-version $Version `
  --vendor $Vendor `
  --description "Universal Client Launcher" `
  --input build/input `
  --main-jar UniversalClient-Launcher.jar `
  --main-class org.example.Main `
  --icon src/main/resources/icon.ico `
  --add-modules $modules `
  --java-options "-Dfile.encoding=UTF-8" `
  --jlink-options "--strip-debug --no-man-pages --no-header-files" `
  --win-menu --win-shortcut --win-dir-chooser `
  --win-per-user-install `
  --win-upgrade-uuid $UpgradeUuid `
  --dest dist
if ($LASTEXITCODE -ne 0) { throw "jpackage завершился с ошибкой" }

$exe = Get-ChildItem dist -Filter "$AppName-$Version.exe" | Select-Object -First 1
if (-not $exe) { throw "Не найден dist\$AppName-$Version.exe — jpackage ничего не создал" }
$hash = (Get-FileHash $exe.FullName -Algorithm SHA256).Hash.ToLower()
$base = $DownloadBase.Replace("{VERSION}", $Version)

$json = [ordered]@{
    version = $Version
    url     = "$base/$($exe.Name)"
    sha256  = $hash
    notes   = $Notes
} | ConvertTo-Json
$utf8 = New-Object System.Text.UTF8Encoding($false)   # без BOM — иначе лаунчер не разберёт json
[System.IO.File]::WriteAllText("$PWD\dist\version.json", $json, $utf8)
# Копия в КОРЕНЬ проекта: именно этот файл читает лаунчер с GitHub (ветка master) — его нужно закоммитить.
[System.IO.File]::WriteAllText("$PWD\version.json", $json, $utf8)

Write-Host ""
Write-Host "Готово!" -ForegroundColor Green
Write-Host "  Установщик : dist\$($exe.Name)"
Write-Host "  version.json: обновлён в корне проекта — закоммитьте и запушьте его (см. UPDATING.md)"
