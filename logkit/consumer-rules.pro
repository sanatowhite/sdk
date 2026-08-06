# :logkit 无反射、无 manifest 声明的组件、公开入口 LogKit 从宿主代码可达,
# R8 按可达性分析即可保留一切需要的东西——无需任何 -keep 规则。
# 这个文件存在只是因为 build.gradle.kts 的 consumerProguardFiles(...) 引用它。
