# Основные правила ProGuard для RetroEmulator

# Сохраняем все классы Winlator
-keep class com.winlator.** { *; }
-keep class com.retroemulator.** { *; }

# Нативные методы
-keepclasseswithmembernames class * {
    native <methods>;
}

# Wine core
-keep class com.winlator.core.** { *; }
-keep class com.winlator.wine.** { *; }
-keep class com.winlator.xserver.** { *; }
-keep class com.winlator.box86.** { *; }
-keep class com.winlator.box64.** { *; }

# Не удаляем R классы
-keep class **.R$* { *; }

# AndroidX
-keep class androidx.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
