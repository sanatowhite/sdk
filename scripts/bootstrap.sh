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
# :updatechecker (io.sanato.updatechecker) is never touched — see the
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
readonly UPDATECHECKER_APP_ID="io.sanato.updatechecker"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# 幂等守卫:找不到模板 token 就判定已经跑过一次了,不重复执行(也不报错,
# 方便 CI/脚本重复调用时不用自己判断)。
if ! grep -rq "$TEMPLATE_APP_ID" --include="*.kt" --include="*.kts" --include="*.xml" \
      --exclude-dir=build --exclude-dir=.git --exclude-dir=updatechecker . 2>/dev/null; then
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

# ── Step 1: move source directories ──────────────────────────────
# 覆盖所有 source root:main/test/androidTest/debug/release/staging/testFixtures。
# updatechecker/ 整个不参与遍历——glob 里从不出现它的路径。
echo "==> Moving source directories to the new package path"
OLD_PATH_SUFFIX="io/sanato/apptemplate"
NEW_PATH_SUFFIX="$NEW_APP_ID_SLASH"

find . -type d -path "*/$OLD_PATH_SUFFIX" \
  -not -path "./updatechecker/*" -not -path "*/build/*" -not -path "./.git/*" \
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
  -not -path "./updatechecker/*" -not -path "*/build/*" -delete 2>/dev/null || true

# ── Step 2: text replacement ──────────────────────────────────────
# 三种形态缺一不可:
#   点分   io.sanato.apptemplate      → package/import/namespace/applicationId/md
#   斜杠分 io/sanato/apptemplate      → 最容易漏:baseline-prof.txt 里是 JVM 描述符
#                                       Lio/sanato/apptemplate/MainActivity;,
#                                       CI 路径,kover includes
# 用 perl -0777 -pi 而不是 sed -i——macOS 要 sed -i ''、GNU 不接受,跨平台脚本
# 必踩的坑,perl 两边行为一致。
#
# ⚠️ 替换 token 绝不能是两段的 io.sanato——三段 token 与 io.sanato.updatechecker
# 在第三段(apptemplate vs updatechecker)就分叉,即使下面的路径排除失效,也不会
# 误伤 updatechecker 的任何一行。这是设计层面的双保险,不是靠脚本小心。
echo "==> Rewriting $TEMPLATE_APP_ID references to $NEW_APP_ID"
find . -type f \( -name "*.kt" -o -name "*.kts" -o -name "*.xml" -o -name "*.pro" \
  -o -name "*.md" -o -name "*.txt" -o -name "*.toml" -o -name "*.properties" \) \
  -not -path "./.git/*" -not -path "*/build/*" \
  -not -path "./updatechecker/*" -not -path "./build-logic/*" \
  -print0 | xargs -0 perl -0777 -pi \
    -e "s/\Q$TEMPLATE_APP_ID_SLASH\E/$NEW_APP_ID_SLASH/g;" \
    -e "s/\Q$TEMPLATE_APP_ID\E/$NEW_APP_ID/g;"

# ── Step 3: immediate assertions (abort + rollback on failure) ────
echo "==> Verifying :updatechecker was not touched"
if [ -n "$(git status --porcelain -- updatechecker)" ]; then
  echo "::error:: bootstrap.sh modified files under updatechecker/ — this must never happen. Rolling back." >&2
  git switch main
  git branch -D "$BRANCH_NAME"
  exit 1
fi

REMAINING_TEMPLATE_REFS=$(grep -rl "$TEMPLATE_APP_ID" --include="*.kt" --include="*.kts" \
  --include="*.xml" --include="*.md" --exclude-dir=build --exclude-dir=.git \
  --exclude-dir=updatechecker --exclude-dir=build-logic . 2>/dev/null | wc -l | tr -d ' ')
if [ "$REMAINING_TEMPLATE_REFS" != "0" ]; then
  echo "::error:: $REMAINING_TEMPLATE_REFS file(s) still reference $TEMPLATE_APP_ID after replacement. Rolling back." >&2
  grep -rl "$TEMPLATE_APP_ID" --include="*.kt" --include="*.kts" --include="*.xml" --include="*.md" \
    --exclude-dir=build --exclude-dir=.git --exclude-dir=updatechecker --exclude-dir=build-logic . 2>/dev/null >&2
  git switch main
  git branch -D "$BRANCH_NAME"
  exit 1
fi

if ! grep -rq "$UPDATECHECKER_APP_ID" --include="*.kt" updatechecker/ 2>/dev/null; then
  echo "::error:: updatechecker/ no longer references $UPDATECHECKER_APP_ID — something went badly wrong. Rolling back." >&2
  git switch main
  git branch -D "$BRANCH_NAME"
  exit 1
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

cat > gradle/version.properties <<EOF
versionCode=1
versionName=0.1.0
EOF

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

echo "==> Smoke test: assembling debug + running :updatechecker tests + verifying module graph"
if ./gradlew :app:assembleDebug :updatechecker:test verifyModuleGraph --stacktrace; then
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
