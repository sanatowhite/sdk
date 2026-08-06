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

# core-foo -> io.sanato.apptemplate.core.foo (hyphens become dots, matching the
# existing core-* namespace convention — see settings.gradle.kts's namespace note).
SUFFIX="${MODULE_NAME#core-}"
NAMESPACE="io.sanato.apptemplate.core.${SUFFIX//-/.}"
PACKAGE_PATH="io/sanato/apptemplate/core/${SUFFIX//-/\/}"

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

## 公开 API

TODO:入口类列表,每个方法一行说明。

## 已知限制 / 不要做的事

TODO。
EOF

# 追加到 settings.gradle.kts 的非-JITPACK include 列表——按字母序插入到
# ":core-telemetry" 之后、":debug-tools" 之前那一段,和现有模块保持同样的分组。
echo "==> Registering :$MODULE_NAME in settings.gradle.kts"
perl -0777 -pi -e "s/(\"\:core-telemetry\",\n)/\${1}        \":$MODULE_NAME\",\n/" settings.gradle.kts

echo ""
echo "✅ Created $MODULE_DIR and registered it in settings.gradle.kts."
echo "   Next steps:"
echo "   1. Fill in $MODULE_DIR/README.md (this repo's per-module docs are meant to be AI-readable, not skipped)."
echo "   2. If :app needs to consume it: add implementation(project(\":$MODULE_NAME\")) to app/build.gradle.kts"
echo "      and wire any Hilt bindings under app/di/."
echo "   3. Run ./gradlew :$MODULE_NAME:assembleDebug verifyModuleGraph to confirm it configures cleanly."
