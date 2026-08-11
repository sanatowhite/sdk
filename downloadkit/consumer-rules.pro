# TaskStore 用 kotlinx.serialization 编解码 .meta sidecar 文件——同 :core-net
# consumer-rules.pro 的规则集，只 keep 本模块自己命名空间下的 @Serializable 类型。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class io.sanato.appkit.download.**$$serializer { *; }
-keepclassmembers class io.sanato.appkit.download.** {
    *** Companion;
}
-keepclasseswithmembers class io.sanato.appkit.download.** {
    kotlinx.serialization.KSerializer serializer(...);
}
