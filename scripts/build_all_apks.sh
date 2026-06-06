#!/bin/bash
# Сборка APK для всех игр в папке games/

echo "🔨 Сборка всех игр..."

# Сначала собираем базовый APK
cd ..
./gradlew assembleRelease
cd scripts

# Упаковываем каждую игру
for game_dir in ../games/*/; do
    if [ -d "$game_dir" ]; then
        game_name=$(basename "$game_dir")
        echo "📦 Упаковка: $game_name"
        ./pack_game.sh "$game_dir" "$game_name" "$game_name"
    fi
done

echo "✅ Все игры упакованы!"
ls -la *.apk
