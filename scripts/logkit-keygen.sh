#!/usr/bin/env bash
#
# 生成一份 :logkit 用的 P-256 密钥对(ECIES 混合信封的收件方密钥)。
#
# 私钥只此一份——丢了 = 以后所有日志永久不可读。备份到密码管理器,
# 绝不提交进仓库、绝不发到聊天工具、绝不进 CI 日志。
#
# 用法:
#   ./scripts/logkit-keygen.sh [--out-dir <dir>]
#
# 生成后把打印出来的两个常量粘进
# logkit/src/main/java/io/sanato/logkit/BuiltInRecipientKey.kt
# (公钥不是秘密,可以提交)。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${HOME}/.logkit-keys"

while [ $# -gt 0 ]; do
  case "$1" in
    --out-dir)
      OUT_DIR="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

# 拒绝把私钥生成到仓库工作树内——这是这个脚本唯一的安全阀,别绕过它。
OUT_DIR_REAL="$(mkdir -p "$OUT_DIR" && cd "$OUT_DIR" && pwd)"
if [[ "$OUT_DIR_REAL" == "$REPO_ROOT"* ]]; then
  echo "refusing to write keys inside the repo working tree ($OUT_DIR_REAL is under $REPO_ROOT)" >&2
  exit 1
fi

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl not found on PATH" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S 2>/dev/null || echo "manual")"
PRIV_EC_PEM="$OUT_DIR/logkit-private-$STAMP.ec.pem"
PRIV_PKCS8_PEM="$OUT_DIR/logkit-private-$STAMP.pem"
PUB_DER="$OUT_DIR/logkit-public-$STAMP.der"

openssl ecparam -name prime256v1 -genkey -noout -out "$PRIV_EC_PEM"
openssl pkcs8 -topk8 -nocrypt -in "$PRIV_EC_PEM" -out "$PRIV_PKCS8_PEM"
openssl pkey -in "$PRIV_EC_PEM" -pubout -outform DER -out "$PUB_DER"
chmod 600 "$PRIV_EC_PEM" "$PRIV_PKCS8_PEM"
rm -f "$PRIV_EC_PEM.tmp" 2>/dev/null || true

# logkit-decrypt --private-key 只接受 PKCS#8 PEM,SEC1(--out 的 .ec.pem)留着
# 只是为了万一你更喜欢用 openssl 命令直接摆弄它——正常流程只需要 PKCS8 那份。

PUB_BYTES_SIGNED="$(python3 - "$PUB_DER" <<'PYEOF'
import sys
data = open(sys.argv[1], "rb").read()
def sb(b):
    return str(b) if b < 128 else str(b - 256)
print(", ".join(sb(b) for b in data))
PYEOF
)"

KEY_ID_HEX="$(openssl dgst -sha256 -binary "$PUB_DER" | head -c 4 | xxd -p)"

cat <<EOF

======================================================================
密钥已生成:
  私钥(PKCS8,给 logkit-decrypt --private-key 用): $PRIV_PKCS8_PEM
  公钥(DER):                                       $PUB_DER

⚠️  私钥只此一份。现在就把它备份到密码管理器,然后考虑把这个目录里的
    文件从磁盘上移走。丢了这份私钥 = 以后所有用这个公钥加密的日志
    永久不可读,没有恢复手段。

把下面两行粘进
logkit/src/main/java/io/sanato/logkit/BuiltInRecipientKey.kt,
替换掉现有的 PUBLIC_KEY_SPKI_DER 数组:

    val PUBLIC_KEY_SPKI_DER: ByteArray =
        byteArrayOf(
            $PUB_BYTES_SIGNED,
        )

keyId(SHA-256(DER) 前 4 字节,仅供人工核对,代码里是从上面这个数组算出来的,
不需要你手填): 0x$KEY_ID_HEX
======================================================================
EOF
