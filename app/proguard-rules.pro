# kotlinx.serialization: R8 не должен выкидывать сгенерированные сериализаторы DTO.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class io.github.vihrea1337.galleria.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit: интерфейс API читается через рефлексию; suspend-методы — через Continuation.
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface io.github.vihrea1337.galleria.data.remote.ArticApi { *; }

# Навигация: маршруты — @Serializable-классы.
-keep class io.github.vihrea1337.galleria.ui.navigation.** { *; }
