#!/usr/bin/env bash
#
# Fork this repo, then run:
#   ./scripts/bootstrap.sh com.yourcompany.yourapp "Your App Name"
#
# This renames the template's io.sanato.apptemplate namespace to your own
# applicationId everywhere it appears (package declarations, imports,
# namespace/applicationId in Gradle, AndroidManifest, JVM descriptors inside
# baseline profiles, proguard rules) and updates the app's display name.
#
# :updatechecker (io.sanato.updatechecker), every published SDK module
# (io.sanato.appkit.* — see PUBLISHED_MODULES below for the full current list,
# it must stay in sync with the root build.gradle.kts sdkModules list), and
# :logkit (io.sanato.logkit, not published yet but frozen the same way — see
# UNPUBLISHED_PROTECTED_MODULES below) are never touched — see the
# "three-segment token" note in Step 2 below for why that's true by
# construction, not just by carefully-written exclude globs.
set -euo pipefail

# ── Step 0: preflight ─────────────────────────────────────────────
if [ $# -lt 1 ]; then
  echo "Usage: $0 <new.application.id> [\"App Display Name\"]" >&2
  exit 1
fi

NEW_APP_ID="$1"
NEW_APP_NAME="${2:-}"
readonly TEMPLATE_APP_ID="io.sanato.apptemplate"
readonly TEMPLATE_APP_ID_SLASH="io/sanato/apptemplate"

# Every module published as an external Maven coordinate — none of these carry
# io.sanato.apptemplate at all (they live under io.sanato.appkit.*, structurally
# divergent at the third path segment, same trick as updatechecker), but they
# each get an explicit --exclude-dir / assertion below anyway: defense in depth,
# not reliance on "the string just happens not to be there". Extend this list
# whenever a new published module is added — it must match the root
# build.gradle.kts `sdkModules` list (minus the leading `:`), or verifyModuleGraph's
# smoke-test step at the end of this script would be the only thing to catch the
# drift, and only if the missing module happens to break something.
readonly PUBLISHED_MODULES="updatechecker backupkit backupkit-drive core-common core-common-hilt core-init core-init-hilt core-ui core-net core-data core-data-hilt core-telemetry core-telemetry-hilt net-telemetry-hilt core-auth auth-firebase auth-net-hilt debug-tools telemetry-firebase feature-settings feature-feedback feature-licenses feature-update feature-auth sdk-bom"

# :logkit isn't in sdkModules — it isn't published yet (see CLAUDE.md's
# ":logkit 五条铁律" #1, gated out of the JITPACK path). Kept as a separate
# list rather than folded into PUBLISHED_MODULES so that list stays a clean
# mirror of root build.gradle.kts's sdkModules (checked nowhere else but that
# comment above — don't let this list drift from it either).
readonly UNPUBLISHED_PROTECTED_MODULES="logkit"
readonly ALL_PROTECTED_MODULES="$PUBLISHED_MODULES $UNPUBLISHED_PROTECTED_MODULES"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# 幂等守卫:找不到模板 token 就判定已经跑过一次了,不重复执行(也不报错,
# 方便 CI/脚本重复调用时不用自己判断)。
if ! grep -rq "$TEMPLATE_APP_ID" --include="*.kt" --include="*.kts" --include="*.xml" \
      --exclude-dir=build --exclude-dir=.git "${STANDALONE_DIR_EXCLUDES[@]}" . 2>/dev/null; then
  echo "No '$TEMPLATE_APP_ID' token found — this repo looks like it has already been bootstrapped. Nothing to do."
  exit 0
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "::error:: Working tree is not clean. Commit or stash your changes before running bootstrap.sh." >&2
  git status --short >&2
  exit 1
fi

# applicationId 校验:格式 + 每一段都不能是 Java/Kotlin 关键字(比如
# com.acme.new.app 会让 Kotlin 编译器直接崩,报错完全指不到根因,这里提前挡住)。
if ! [[ "$NEW_APP_ID" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
  echo "::error:: '$NEW_APP_ID' is not a valid applicationId (expected lowercase, dot-separated, e.g. com.acme.myapp)." >&2
  exit 1
fi
if [[ "$NEW_APP_ID" == android.* || "$NEW_APP_ID" == com.google.* ]]; then
  echo "::error:: applicationId must not start with 'android.' or 'com.google.' (reserved namespaces)." >&2
  exit 1
fi
readonly KOTLIN_KEYWORDS="as break class continue do else false for fun if in interface is null object package return super this throw true try typealias typeof val var when while"
IFS='.' read -ra SEGMENTS <<<"$NEW_APP_ID"
for segment in "${SEGMENTS[@]}"; do
  for keyword in $KOTLIN_KEYWORDS; do
    if [ "$segment" = "$keyword" ]; then
      echo "::error:: applicationId segment '$segment' is a Kotlin keyword and cannot be used as a package name." >&2
      exit 1
    fi
  done
done

NEW_APP_ID_SLASH="${NEW_APP_ID//./\/}"
BRANCH_NAME="bootstrap/${NEW_APP_ID##*.}"
git switch -c "$BRANCH_NAME"
echo "==> Working on branch $BRANCH_NAME (abort any time with: git switch main && git branch -D $BRANCH_NAME)"

# ⚠️ `git switch main` alone does NOT discard staged changes (git mv stages
# renames immediately) — they carry over onto main's working tree uncommitted.
# A real rollback needs a hard reset on the branch being abandoned *before*
# switching away from it.
rollback() {
  echo "::error:: $1 Rolling back." >&2
  git reset --hard HEAD >/dev/null
  git clean -fd >/dev/null
  git switch main --quiet
  git branch -D "$BRANCH_NAME" >/dev/null
  exit 1
}

# ── Step 1: move source directories ──────────────────────────────
# 覆盖所有 source root:main/test/androidTest/debug/release/staging/testFixtures。
# 每个 ALL_PROTECTED_MODULES 里的目录整个不参与遍历——glob 里从不出现它们的路径。
echo "==> Moving source directories to the new package path"
OLD_PATH_SUFFIX="io/sanato/apptemplate"
NEW_PATH_SUFFIX="$NEW_APP_ID_SLASH"

FIND_PRUNE_ARGS=(-not -path "*/build/*" -not -path "./.git/*" -not -path "./tools/*")
for m in $ALL_PROTECTED_MODULES; do
  FIND_PRUNE_ARGS+=(-not -path "./$m/*")
done

find . -type d -path "*/$OLD_PATH_SUFFIX" \
  "${FIND_PRUNE_ARGS[@]}" \
  | while read -r old_dir; do
    new_dir="${old_dir%$OLD_PATH_SUFFIX}$NEW_PATH_SUFFIX"
    mkdir -p "$(dirname "$new_dir")"
    if [ "$old_dir" != "$new_dir" ]; then
      # macOS 文件系统大小写不敏感——如果新旧路径只差大小写,git mv 直接同名会
      # 静默失败,必须先挪到一个临时名字再挪回目标名字。
      if [ "$(echo "$old_dir" | tr '[:upper:]' '[:lower:]')" = "$(echo "$new_dir" | tr '[:upper:]' '[:lower:]')" ]; then
        tmp_dir="${old_dir}.bootstrap-tmp-$$"
        git mv "$old_dir" "$tmp_dir"
        git mv "$tmp_dir" "$new_dir"
      else
        git mv "$old_dir" "$new_dir"
      fi
    fi
  done

# 搬完之后清空祖先目录——只在 */java、*/kotlin 子树下做,避免误删合法的空 res 目录。
find . \( -path "*/java/io/sanato" -o -path "*/kotlin/io/sanato" \) -type d -empty \
  "${FIND_PRUNE_ARGS[@]}" -delete 2>/dev/null || true

# ── Step 2: text replacement ──────────────────────────────────────
# 三种形态缺一不可:
#   点分   io.sanato.apptemplate      → package/import/namespace/applicationId/md
#   斜杠分 io/sanato/apptemplate      → 最容易漏:baseline-prof.txt 里是 JVM 描述符
#                                       Lio/sanato/apptemplate/MainActivity;,
#                                       CI 路径,kover includes
# 用 perl -0777 -pi 而不是 sed -i——macOS 要 sed -i ''、GNU 不接受,跨平台脚本
# 必踩的坑,perl 两边行为一致。
#
# ⚠️ 替换 token 绝不能是两段的 io.sanato——三段 token 与 io.sanato.updatechecker/
# io.sanato.logkit 在第三段(apptemplate vs updatechecker/logkit)就分叉,即使
# 下面的路径排除失效,也不会误伤这两个模块的任何一行。这是设计层面的双保险,
# 不是靠脚本小心。
echo "==> Rewriting $TEMPLATE_APP_ID references to $NEW_APP_ID"
# ⚠️ 用 # 做 perl s/// 的分隔符,不用默认的 /——两个 token 本身都含 /(斜杠分形态),
# 用 / 当分隔符会被内容里的 / 提前截断,报 "Backslash found where operator expected"。
#
# ⚠️ build-logic/**【不】排除:SanatoAndroidApplicationConventionPlugin.kt 里
# `applicationId = "io.sanato.apptemplate"` 这一行的注释自己写着"bootstrap.sh
# 唯一的替换点"——之前这里排除了整个 build-logic/,导致这一行永远替换不到,
# 每个 fork 跑完 bootstrap.sh 后 `:app` 的 applicationId 仍然是
# io.sanato.apptemplate,google-services 插件会直接报"找不到匹配的 client"
# 硬失败(在隔离 clone 里跑通整个流程时抓到的真实 bug,不是假设)。
# build-logic 下再无第二处 io.sanato.apptemplate 引用(已核实),排除它整体没有
# 保护到任何东西,只是把这一行漏掉了。
TEXT_PRUNE_ARGS=(-not -path "./.git/*" -not -path "*/build/*" -not -path "./tools/*")
for m in $ALL_PROTECTED_MODULES; do
  TEXT_PRUNE_ARGS+=(-not -path "./$m/*")
done

find . -type f \( -name "*.kt" -o -name "*.kts" -o -name "*.xml" -o -name "*.pro" \
  -o -name "*.md" -o -name "*.txt" -o -name "*.toml" -o -name "*.properties" -o -name "*.json" \) \
  "${TEXT_PRUNE_ARGS[@]}" \
  -print0 | xargs -0 perl -0777 -pi \
    -e "s#\Q$TEMPLATE_APP_ID_SLASH\E#$NEW_APP_ID_SLASH#g;" \
    -e "s#\Q$TEMPLATE_APP_ID\E#$NEW_APP_ID#g;"

# ── Step 3: immediate assertions (abort + rollback on failure) ────
echo "==> Verifying protected modules were not touched"
for m in $ALL_PROTECTED_MODULES; do
  if [ -n "$(git status --porcelain -- "$m")" ]; then
    rollback "bootstrap.sh modified files under $m/ — protected modules must never be rewritten."
  fi
done

echo "==> Verifying :updatechecker and :logkit still reference their own package"
for entry in "updatechecker:io.sanato.updatechecker" "logkit:io.sanato.logkit"; do
  dir="${entry%%:*}"
  own_id="${entry#*:}"
  if ! grep -rq "$own_id" --include="*.kt" "$dir/" 2>/dev/null; then
    rollback "$dir/ no longer references $own_id — something went badly wrong."
  fi
done

GREP_EXCLUDE_ARGS=(--exclude-dir=build --exclude-dir=.git --exclude-dir=build-logic --exclude-dir=tools)
for m in $ALL_PROTECTED_MODULES; do
  GREP_EXCLUDE_ARGS+=(--exclude-dir="$m")
done

# `|| true` on the whole assignment: grep legitimately returns exit 1 when it finds
# zero matches, which is the PASSING case here — under `pipefail` that would
# otherwise trip `set -e` and abort the script on the success path.
REMAINING_TEMPLATE_REFS=$(grep -rl "$TEMPLATE_APP_ID" --include="*.kt" --include="*.kts" \
  --include="*.xml" --include="*.md" --include="*.json" "${GREP_EXCLUDE_ARGS[@]}" . 2>/dev/null | wc -l | tr -d ' ') || true
if [ "$REMAINING_TEMPLATE_REFS" != "0" ]; then
  grep -rl "$TEMPLATE_APP_ID" --include="*.kt" --include="*.kts" --include="*.xml" --include="*.md" --include="*.json" \
    "${GREP_EXCLUDE_ARGS[@]}" . 2>/dev/null >&2
  rollback "$REMAINING_TEMPLATE_REFS file(s) still reference $TEMPLATE_APP_ID after replacement."
fi

# ── Step 4: project identity ──────────────────────────────────────
echo "==> Updating rootProject.name and version.properties"
perl -0777 -pi -e "s/^rootProject\.name\s*=.*$/rootProject.name = \"${NEW_APP_ID##*.}\"/m" settings.gradle.kts

if [ -n "$NEW_APP_NAME" ]; then
  echo "==> Setting app_name to \"$NEW_APP_NAME\" in every values-* directory"
  find app/src/main/res -type d -name "values*" | while read -r values_dir; do
    strings_file="$values_dir/strings.xml"
    if [ -f "$strings_file" ]; then
      perl -0777 -pi -e "s/(<string name=\"app_name\">).*?(<\/string>)/\${1}$NEW_APP_NAME\${2}/" "$strings_file"
    fi
  done
fi

# ⚠️ 只替换这两行,不能整份覆盖——这个文件同时还带着 sdkGroup=/sdkVersion=
# 两个 key(SDK 模块发布坐标用,和这里的 app versionCode/versionName 是两条独立
# 的轴,见 ADR 0005/0008)。早期版本用 `cat > ... <<EOF` 整份覆盖过这个文件,
# 会把 sdkGroup/sdkVersion 一起删掉,导致任何 apply 了
# sanato.android.library.published 的模块在配置期直接崩——这个脚本自己的
# smoke test 步骤会当场抓到,但抓到时已经晚了,不如从根上不产生这个 bug。
perl -pi -e 's/^versionCode=.*$/versionCode=1/; s/^versionName=.*$/versionName=0.1.0/' gradle/version.properties

# ── Step 5: launcher icon (honest limitation, not a silent no-op) ─
echo ""
echo "⚠️  bootstrap.sh does not generate a real launcher icon (no ImageMagick/binary"
echo "    image processing dependency by design). app/src/main/res/drawable/ic_launcher_*.xml"
echo "    are still the template's placeholder vector shapes."
echo "    Open Android Studio > right-click app/ > New > Image Asset to replace them."
echo ""

# ── Step 6: render the fork's own README from the template ───────
if [ -f docs/templates/README.app.md ]; then
  echo "==> Rendering README.md for the forked app"
  perl -pi -e "s/\{\{APP_NAME\}\}/${NEW_APP_NAME:-$NEW_APP_ID}/g; s/\{\{APP_ID\}\}/$NEW_APP_ID/g" \
    docs/templates/README.app.md > README.md
fi

# ── Step 7: self-destruct + smoke test ────────────────────────────
echo "==> Removing bootstrap.sh (single-use) and staging all changes"
git add -A
git rm -f --cached scripts/bootstrap.sh >/dev/null 2>&1 || true
rm -f scripts/bootstrap.sh
git add -A

echo "==> Smoke test: assembling debug + running :updatechecker/:logkit tests + verifying module/publish-set graphs"
if ./gradlew :app:assembleDebug :updatechecker:test :logkit:test verifyModuleGraph verifySdkModuleList verifySdkBomConstraints --stacktrace; then
  git commit -m "Bootstrap: rename $TEMPLATE_APP_ID -> $NEW_APP_ID"
  echo ""
  echo "✅ Bootstrap complete on branch $BRANCH_NAME. Review the diff, then merge to main:"
  echo "     git diff main..$BRANCH_NAME --stat"
  echo "     git switch main && git merge $BRANCH_NAME"
else
  echo ""
  echo "::error:: Smoke test failed — changes were NOT committed, left uncommitted on branch $BRANCH_NAME for you to inspect." >&2
  exit 1
fi
