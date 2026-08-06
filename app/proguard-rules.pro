# ──────────────────────────────────────────────────────────────────
# kotlinx.serialization 官方推荐规则集。
#
# 这条不是可选优化——Navigation Compose 的类型安全路由(io.sanato.apptemplate.navigation.Routes)
# 用的就是 @Serializable data object,以及 Phase 5 起 core-net 的网络 DTO 也会用
# kotlinx-serialization-json。缺了这几条规则,release(minifyEnabled)构建下
# Retrofit/序列化会直接崩,且这次连导航也会一起崩——这个耦合点在 debug 构建里完全
# 看不出来,只在 minified release 里暴露(见 CLAUDE.md 的 release-smoke CI job)。
# ──────────────────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class io.sanato.apptemplate.**$$serializer { *; }
-keepclassmembers class io.sanato.apptemplate.** {
    *** Companion;
}
-keepclasseswithmembers class io.sanato.apptemplate.** {
    kotlinx.serialization.KSerializer serializer(...);
}
