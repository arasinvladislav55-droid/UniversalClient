Discord Rich Presence — как это устроено

Для пользователей лаунчера НИЧЕГО настраивать не нужно: Application ID приложения
"Universal Client" уже встроен в программу (он публичный, не секретный и одинаков для всех).
Достаточно, чтобы на компьютере был запущен Discord (десктоп-клиент).

Для владельца приложения (один раз, чтобы в статусе была картинка):
1. https://discord.com/developers/applications  →  Universal Client  →  Rich Presence → Art Assets.
2. Add Image(s): загрузите logo.png из этой папки и назовите ассет  logo  (строчными).
3. Название приложения ("Universal Client") отображается как "Играет в Universal Client".

Если нужно подменить ID (например, для своей копии лаунчера), впишите "discordAppId" в файл
%APPDATA%\.universalclient\launcher.json — он имеет приоритет над встроенным.
Или измените DEFAULT_APP_ID в core/DiscordPresence.java.
