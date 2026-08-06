package io.sanato.apptemplate.update

/**
 * fork 出去的项目必须把这个换成自己的更新配置 JSON 地址——bootstrap.sh 不会
 * 自动处理这个值,因为它指向的是"你自己的静态发行仓库"(比如
 * `sanatowhite/version_check` 那种轻量应用商店),不是模板脚本能猜到的东西。
 * JSON schema 见 `:updatechecker` 的 `UpdateConfigParser`/`UpdateInfo`。
 */
const val UPDATE_CONFIG_URL = "https://raw.githubusercontent.com/OWNER/REPO/main/update.json"
