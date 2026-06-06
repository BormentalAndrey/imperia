#!/bin/bash
# Упаковка игры в отдельный APK
# Использование: ./pack_game.sh <папка_с_игрой> <название_игры> <package_name>

GAME_FOLDER="$1"
GAME_NAME="$2"
PACKAGE_NAME="com.retroemulator.${3}"

if [ -z "$GAME_FOLDER" ] || [ -z "$GAME_NAME" ]; then
    echo "Использование: $0 <папка_с_игрой> <Название Игры> <package_suffix>"
    exit 1
fi

echo "🎮 Упаковка игры: $GAME_NAME"
echo "📁 Исходная папка: $GAME_FOLDER"
echo "📦 Package: $PACKAGE_NAME"

# Проверяем наличие apktool
if ! command -v apktool &> /dev/null; then
    echo "❌ apktool не установлен. Установите: apt install apktool"
    exit 1
fi

# Копируем базовый APK (собранный ранее)
BASE_APK="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$BASE_APK" ]; then
    echo "❌ Базовый APK не найден. Сначала соберите проект: ./gradlew assembleRelease"
    exit 1
fi

echo "🔧 Распаковываем базовый APK..."
apktool d "$BASE_APK" -o temp_apk -f

echo "📋 Копируем файлы игры..."
mkdir -p temp_apk/assets/games/
cp -r "$GAME_FOLDER"/* temp_apk/assets/games/

echo "🏷️ Меняем название и иконку..."
# Меняем package name
find temp_apk -type f -name "*.xml" -exec sed -i "s/com.retroemulator.launcher/$PACKAGE_NAME/g" {} +
find temp_apk -name "AndroidManifest.xml" -exec sed -i "s/Retro PC Emulator/$GAME_NAME/g" {} +

# Копируем иконку если есть
if [ -f "$GAME_FOLDER/icon.png" ]; then
    cp "$GAME_FOLDER/icon.png" temp_apk/res/mipmap-hdpi/ic_launcher.png
fi

echo "🔨 Собираем APK..."
APK_NAME="${GAME_NAME// /_}.apk"
apktool b temp_apk -o "$APK_NAME"

echo "🔑 Подписываем APK..."
# Генерируем ключ если нет
if [ ! -f "debug.keystore" ]; then
    keytool -genkey -v -keystore debug.keystore -alias debug \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Debug, OU=Debug, O=Debug, L=Debug, ST=Debug, C=US"
fi

jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
    -keystore debug.keystore -storepass android \
    "$APK_NAME" debug

echo "✅ Готово: $APK_NAME"
echo "📱 Установите на телефон и нажмите ярлык для запуска!"

# Очистка
rm -rf temp_apk
