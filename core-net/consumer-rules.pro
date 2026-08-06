# kotlinx.serialization 官方推荐规则集,只 keep 本模块自己命名空间下的 @Serializable
# 类型——消费方(:app 或独立复用这个模块的项目)自己的 @Serializable DTO 需要在
# 消费方自己的 proguard-rules.pro 里加同样的规则,这条只保证 core-net 自身。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class io.sanato.apptemplate.core.net.**$$serializer { *; }
-keepclassmembers class io.sanato.apptemplate.core.net.** {
    *** Companion;
}
-keepclasseswithmembers class io.sanato.apptemplate.core.net.** {
    kotlinx.serialization.KSerializer serializer(...);
}
