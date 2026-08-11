#!/usr/bin/env bash
#
# Scaffold a new core-* module: directory skeleton + minimal build.gradle.kts
# (applying the right convention plugin) + registers it in settings.gradle.kts.
#
# Usage:
#   ./scripts/new-module.sh core-foo             # plain Kotlin/Android library module
#   ./scripts/new-module.sh core-foo --compose    # + Compose UI + Roborazzi screenshot testing
#
# This only handles the "core-*" library-module case — :app, :benchmark,
# :baselineprofile, and :telemetry-firebase are one-off enough that hand-editing
# them directly is clearer than trying to templatize every variant here.
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <module-name> [--compose]" >&2
  echo "  module-name should look like 'core-foo' (lowercase, hyphenated)" >&2
  exit 1
fi

MODULE_NAME="$1"
WITH_COMPOSE=false
if [ "${2:-}" = "--compose" ]; then
  WITH_COMPOSE=true
fi

if ! [[ "$MODULE_NAME" =~ ^core-[a-z][a-z0-9-]*$ ]]; then
  echo "::error:: '$MODULE_NAME' doesn't look like 'core-<name>' (lowercase, hyphenated)." >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

MODULE_DIR="$MODULE_NAME"
if [ -d "$MODULE_DIR" ]; then
  echo "::error:: $MODULE_DIR already exists." >&2
  exit 1
fi

# core-foo -> io.sanato.appkit.core.foo (hyphens become dots, matching the
# existing core-* namespace convention — see settings.gradle.kts's namespace note).
# io.sanato.appkit.* (not io.sanato.apptemplate.*) is the published-module namespace
# root: it's what keeps bootstrap.sh's package rename structurally unable to touch
# any published module, the same way io.sanato.updatechecker already is.
SUFFIX="${MODULE_NAME#core-}"
NAMESPACE="io.sanato.appkit.core.${SUFFIX//-/.}"
PACKAGE_PATH="io/sanato/appkit/core/${SUFFIX//-/\/}"

echo "==> Creating $MODULE_DIR (namespace $NAMESPACE)"
mkdir -p "$MODULE_DIR/src/main/kotlin/$PACKAGE_PATH"
mkdir -p "$MODULE_DIR/src/test/kotlin/$PACKAGE_PATH"

if [ "$WITH_COMPOSE" = true ]; then
  cat >"$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
    alias(libs.plugins.sanato.android.library.compose)
    alias(libs.plugins.sanato.android.roborazzi)
}

android {
    namespace = "$NAMESPACE"
}

dependencies {
    implementation(project(":core-common"))
}
EOF
else
  cat >"$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
    alias(libs.plugins.sanato.android.library)
}

android {
    namespace = "$NAMESPACE"
}

dependencies {
    implementation(project(":core-common"))
}
EOF
fi

cat >"$MODULE_DIR/README.md" <<EOF
# :$MODULE_NAME

## 这是什么 / 不是什么

TODO:一句话职责边界 + 明确排除的能力。

**不是**:TODO。

## 独立引入

只依赖 \`:core-common\`(见上面 \`dependencies {}\`)——如果确实只依赖它,把这一句
换成"只要一并引入 core-common 就行,它很小,做的是 XXX";如果后续加了别的模块
依赖,这里要同步更新。

## AI 接入指南(可直接执行)

TODO:替换成这个模块真实的、可直接照抄执行的接入步骤——命令、期望的退出码/输出、
以及"不要做 XXX"的设计取舍(防止未来的 AI 会话把它当成可以"优化掉"的东西)。
参照 \`:updatechecker/README.md\`"完整发布步骤"或 \`:auth-firebase/README.md\`
"为什么这个模块可以带三方依赖"的形状——一份是可跑的操作清单,一份是不明显的
约束背后的推理链。见根 \`CLAUDE.md\`"Every new module ships with its own
CLAUDE.md + an AI-executable README"一节。

## 公开 API

TODO:入口类列表,每个方法一行说明。

## 已知限制 / 不要做的事

TODO。
EOF

cat >"$MODULE_DIR/CLAUDE.md" <<EOF
# CLAUDE.md — \`:$MODULE_NAME\`

Directory-scoped guidance for Claude Code when working inside this module.
Only put things here that are **specific to this module** — anything true
repo-wide (JDK version, spotless, the four gate commands) belongs in the root
\`CLAUDE.md\`, not duplicated here.

TODO: fill in before considering this module done (see root \`CLAUDE.md\`'s
"Every new module ships with its own CLAUDE.md + an AI-executable README"):

- What this module is for, and its one-sentence boundary (mirror the README's
  "这是什么 / 不是什么").
- Its dependency-direction rule, if it has one narrower than
  \`verifyModuleGraph\`'s general rule (e.g. "never depend on :core-net").
- Anything about its testing setup that isn't obvious from \`build.gradle.kts\`
  (Robolectric SDK override, a fake it ships via \`testFixtures\`, etc).
- The one command to run after touching this module, if it's not just
  \`./gradlew :$MODULE_NAME:test\`.
EOF

# 追加到 settings.gradle.kts 的非-JITPACK include 列表——按字母序插入到
# ":core-telemetry" 之后、":debug-tools" 之前那一段,和现有模块保持同样的分组。
echo "==> Registering :$MODULE_NAME in settings.gradle.kts"
perl -0777 -pi -e "s/(\"\:core-telemetry\",\n)/\${1}        \":$MODULE_NAME\",\n/" settings.gradle.kts

echo ""
echo "✅ Created $MODULE_DIR and registered it in settings.gradle.kts."
echo "   Next steps:"
echo "   1. Fill in $MODULE_DIR/README.md AND $MODULE_DIR/CLAUDE.md — this repo requires"
echo "      both for every module, not just a human-oriented description (see root"
echo "      CLAUDE.md's \"Every new module ships with its own CLAUDE.md + an AI-executable README\")."
echo "   2. If :app needs to consume it: add implementation(project(\":$MODULE_NAME\")) to app/build.gradle.kts"
echo "      and wire any Hilt bindings under app/di/."
echo "   3. Run ./gradlew :$MODULE_NAME:assembleDebug verifyModuleGraph to confirm it configures cleanly."
