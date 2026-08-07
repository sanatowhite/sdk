# kotlinx.serialization 官方推荐规则集,只 keep 本模块自己命名空间下的 @Serializable
# 类型——Navigation Compose 的类型安全路由(LicensesRoute)用的就是
# @Serializable data object。这条规则不是可选优化:release(minifyEnabled)构建下
# 缺了它,导航会直接崩,且这个耦合点在 debug 构建里完全看不出来。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class io.sanato.appkit.feature.licenses.**$$serializer { *; }
-keepclassmembers class io.sanato.appkit.feature.licenses.** {
    *** Companion;
}
-keepclasseswithmembers class io.sanato.appkit.feature.licenses.** {
    kotlinx.serialization.KSerializer serializer(...);
}
