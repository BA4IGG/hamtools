#!/usr/bin/env bash
# 从《业余电台操作证书考试题库》官方 PDF 重建离线题库 assets。
#
# 用法：
#   sh tools/exam/prepare_exam_sources.sh "<题库PDF所在目录>"
#
# 依赖 workbuddy-pdf CLI（读取 PDF 用）。中文目录名对文字提取没问题，
# 但页面渲染/结构识别后端处理不了中文路径，所以附图 PDF 会先复制成 ASCII 文件名。
set -euo pipefail

SRC="${1:-}"
if [ -z "$SRC" ]; then
  echo "用法：sh tools/exam/prepare_exam_sources.sh \"<题库PDF所在目录>\"" >&2
  exit 2
fi

CLI="${WORKBUDDY_PDF_CLI:-}"
if [ -z "$CLI" ]; then
  if command -v workbuddy-pdf >/dev/null 2>&1; then
    CLI="workbuddy-pdf"
  else
    CLI="D:/workbuddy/resources/app.asar.unpacked/resources/plugins/workbuddy-builtin/skills/workbuddy-pdf/scripts/run.sh"
  fi
fi
RUN="$CLI"
case "$CLI" in
  *.sh) RUN="sh $CLI" ;;
esac

WORK=".workbuddy/examtmp"
mkdir -p "$WORK/png"

for cls in A B C; do
  echo "提取 ${cls} 类题库文本…"
  $RUN "$SRC/${cls}类题库.pdf" "$WORK/$cls.txt" -f txt -q
done

echo "提取附图版面与页面图片…"
cp "$SRC/总题库附图标记.pdf" "$WORK/figures.pdf"
$RUN "$WORK/figures.pdf" "$WORK/structure.json" -f structure -q
$RUN "$WORK/figures.pdf" "$WORK/png" -f png --dpi 180 -q

echo "生成 assets…"
python tools/exam/build_exam_assets.py --dpi 180
echo "完成：app/src/main/assets/exam/"
