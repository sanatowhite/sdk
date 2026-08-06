# `debug-private-key.pem`

这是一份**刻意签入仓库**的 THROWAWAY P-256 私钥,配对的公钥就是
`logkit/src/main/java/io/sanato/logkit/BuiltInRecipientKey.kt` 里内置的默认公钥。

**它不是生产密钥。** 之所以把私钥也签进仓库(违反"私钥永不签入"的一般原则),
是因为这是一个 App 模板项目——不这么做的话,任何 fork 出去、跑 debug build
的人都读不到自己刚产出的加密日志,第一次接触这个 SDK 就得先学会跑
`../../scripts/logkit-keygen.sh`,体验很差。

**任何真正要发布的 App 必须做这两件事**(见 `TEMPLATE.md` 的 fork checklist):

1. 跑 `./scripts/logkit-keygen.sh`,生成自己的密钥对。
2. 用新公钥替换 `BuiltInRecipientKey.kt` 里的 `PUBLIC_KEY_SPKI_DER`,
   **删除**这个目录,新私钥只留在自己的密码管理器里,绝不提交。

沿用这份模板密钥发布的 App,等于把自己用户的加密日志发给了模板作者——
这个仓库的 CI/gitleaks 配置(`.gitleaks.toml`)知道这份密钥是刻意公开的
测试固件,不会因为它拦截构建,但那不代表你的 fork 可以沿用它上线。
