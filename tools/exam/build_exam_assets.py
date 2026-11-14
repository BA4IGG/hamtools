#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从《业余电台操作证书考试题库》官方 PDF 生成 App 使用的离线题库资源。

输入（工作目录 build/exam，由 prepare_exam_sources.sh 或手工准备）：
  A.txt B.txt C.txt          —— workbuddy-pdf -f txt 从三类题库提取的纯文本
  structure.json             —— workbuddy-pdf -f structure 从附图标记 PDF 提取的版面
  page-0001.png / ...        —— workbuddy-pdf -f png --dpi 180 渲染的附图页

输出：
  app/src/main/assets/exam/questions.json
  app/src/main/assets/exam/figures/page-1.png ...

字段约定（官方题库）：
  [I] 全局题号（MC1/MC2/MC3/MC4-四位序号）；[J] 题目原编号（附图按此编号标记）；
  [P] 章节；[Q] 题干；[A][B][C][D] 选项；[T] 答案（可含多个字母，即多选题）。
  J 与 P 不参与答题，仅用于溯源与附图关联。

注意：C 类 PDF 的每一页在换页符后会夹带一行独立页码数字，必须先删除，
否则会混入答案/选项尾部（例如 "A\n\n3"、"…发信机4"）。
"""
from __future__ import annotations

import argparse
import collections
import json
import re
import shutil
from pathlib import Path

LEVELS = ("A", "B", "C")
# 附图标记 PDF 中 "LK060" 是 "LK0603" 的印刷/识别截断
FIGURE_ALIAS = {"LK060": "LK0603"}
FIGURE_PAGE_LEFT, FIGURE_PAGE_RIGHT = 145.0, 452.0
FIGURE_FIRST_ROW_TOP = 60.0
FIGURE_PAGE_BOTTOM = 820.0
# 附图 PDF 三页的「题号标签 / 图形」上下关系并不一致：
#   第 1、3 页（index 0、2）：图在上，题号在图的正下方；
#   第 2 页（index 1）：题号在图的上方，图形紧随其下。
# 必须按页分别取标签上方 / 下方，否则第 2 页会整页裁出空白。
FIGURE_BELOW_LABEL_PAGES = {1}
NUMERIC_HINT = re.compile(r"\d|[Ωω]|Hz|dB|[dD][Bb]|瓦|伏|安|米|λ|π|°|%|k?W|mA|μ|pF|MHz|GHz")


def strip_page_numbers(text: str) -> str:
    """删除整行只有 1~3 位数字的页码行（C 类 PDF 换页处夹带）。"""
    return re.sub(r"(?m)^\s*\d{1,3}\s*$", "", text)


def clean(value: str) -> str:
    """把 PDF 折行还原成单行：中文直接拼接，英文/数字之间补一个空格。"""
    value = value.replace("\f", " ")
    value = re.sub(r"(?<=[A-Za-z0-9])[ \t]*\r?\n[ \t]*(?=[A-Za-z0-9])", " ", value)
    value = re.sub(r"[ \t]*\r?\n[ \t]*", "", value)
    return re.sub(r"[ \t]{2,}", " ", value).strip()


def parse_bank(text: str):
    """按 [J] 起块解析，返回 {全局题号: 题目字典}。"""
    text = strip_page_numbers(text)
    parts = re.split(r"(?m)^\[([A-Z])\]", text)
    blocks, current = [], None
    for i in range(1, len(parts), 2):
        tag, content = parts[i], parts[i + 1] if i + 1 < len(parts) else ""
        if tag == "J":
            if current:
                blocks.append(current)
            current = collections.defaultdict(list)
            current["J"].append(content)
        elif current is not None:
            current[tag].append(content)
    if current:
        blocks.append(current)

    def field(block, key) -> str:
        return clean("".join(block.get(key, [])))

    bank = {}
    for block in blocks:
        id_match = re.search(r"MC[1-4]-\d{4}", field(block, "I"))
        answer_match = re.match(r"[A-D]{1,4}", field(block, "T"))
        if not id_match or not answer_match:
            raise ValueError(f"题块缺少题号或答案：{field(block, 'I')[:40]!r}")
        j_match = re.match(r"[A-Z]{2}\d{3,4}", field(block, "J"))
        options = [field(block, key) for key in "ABCD"]
        if not all(options):
            raise ValueError(f"选项缺失：{id_match.group(0)}")
        bank[id_match.group(0)] = {
            "i": id_match.group(0),
            "j": j_match.group(0) if j_match else field(block, "J"),
            "p": field(block, "P"),
            "q": field(block, "Q"),
            "o": options,
            "t": answer_match.group(0),
        }
    return bank


def merge_banks(banks):
    """按全局题号去重合并；同一题号在 AB 类重复出现时保留最干净的一份。"""
    merged = {}
    for level in LEVELS:  # A 最干净，C 最全但夹页码，优先取 A/B 的文本
        for key, item in banks[level].items():
            if key not in merged:
                merged[key] = dict(item, levels=[])
            merged[key]["levels"].append(level)
    return merged


def validate(bank):
    """校验致命错误；官方题库个别题目存在重复干扰项（如 MC1-1012 的 B/D 同为 50.0 伏），
    这是原文如此，不影响判分，只记录不阻断。"""
    errors, warnings = [], []
    for key, item in bank.items():
        answer = item["t"]
        if len(set(answer)) != len(answer) or len(answer) > 4:
            errors.append(f"{key}: 答案异常 {answer}")
        if any(ch not in "ABCD" for ch in answer):
            errors.append(f"{key}: 答案含非法字符 {answer}")
        if not item["q"]:
            errors.append(f"{key}: 题干为空")
        if len(set(item["o"])) != 4:
            warnings.append(f"{key}: 原文存在重复选项")
    if errors:
        raise ValueError("题库校验失败：\n" + "\n".join(errors[:20]))
    print(f"  校验提示：{len(warnings)} 题存在原文重复选项（{', '.join(warnings[:4])}）")


def difficulty(item, figure_ids) -> int:
    """0~100 的静态难度：题号顺序 + 题型 + 篇幅 + 数值/单位 + 附图。

    这不是官方难度（题库本身没有难度标注），只用于「由易到难」的稳定排序。
    答题过程中的个人掌握度由 App 端艾宾浩斯调度器另行叠加。
    """
    serial = int(item["i"].split("-")[1])
    score = serial / 1375.0 * 36.0
    if len(item["t"]) > 1:
        score += 14.0
    score += min(len(item["q"]), 140) / 140.0 * 18.0
    avg_option = sum(len(o) for o in item["o"]) / 4.0
    score += min(avg_option, 70) / 70.0 * 12.0
    if NUMERIC_HINT.search(item["q"]):
        score += 8.0
    if item["j"] in figure_ids:
        score += 6.0
    return max(1, min(100, round(score)))


def collect_figure_tokens(structure):
    """从 structure.json 收集附图题号标签的真实坐标（PDF points）。

    返回 {(页码索引, 标签y): [(题号, 中心x), ...]}。
    """
    def runs(node, out):
        if isinstance(node, dict):
            if node.get("type") == "paragraph":
                for run in node.get("runs", []):
                    text = run.get("text", "")
                    if text.strip():
                        out.append((text, run.get("box", {})))
            else:
                for value in node.values():
                    runs(value, out)
        elif isinstance(node, list):
            for value in node:
                runs(value, out)

    rows = collections.defaultdict(list)
    for page_index, section in enumerate(structure["sections"]):
        collected = []
        runs(section["blocks"], collected)
        for text, box in collected:
            tokens = re.findall(r"LK\d{3,4}", text)
            if not tokens:
                continue
            x, y, width = box.get("x", 0), box.get("y", 0), box.get("width", 0)
            step = width / len(tokens) if len(tokens) > 1 else 0
            for index, token in enumerate(tokens):
                center_x = x + step * (index + 0.5) if step else x + width / 2
                rows[(page_index, round(y, 1))].append(
                    (FIGURE_ALIAS.get(token, token), center_x)
                )
    return rows


def build_figure_boxes(rows):
    """每个题号对应一个裁切矩形（PDF points），方向按页判断。

    同一行的题号按 x 中心切列；单行只有一个题号时用整页内容宽度。
    「图在上」：上边界取上一行标签 y + 10（第一行取 60），下边界取本行标签 y - 6；
    「图在下」：上边界取本行标签 y + 10，下边界取下一行标签 y - 6（最后一行取页底）。
    """
    boxes = {}
    by_page = collections.defaultdict(list)
    for (page, y), tokens in rows.items():
        by_page[page].append((y, sorted(tokens, key=lambda t: t[1])))
    for page, row_list in by_page.items():
        row_list.sort()
        below = page in FIGURE_BELOW_LABEL_PAGES
        for row_index, (label_y, tokens) in enumerate(row_list):
            if below:
                top = label_y + 10
                bottom = row_list[row_index + 1][0] - 6 if row_index + 1 < len(row_list) else FIGURE_PAGE_BOTTOM
            else:
                top = FIGURE_FIRST_ROW_TOP if row_index == 0 else row_list[row_index - 1][0] + 10
                bottom = label_y - 6
            if bottom - top < 30:
                raise ValueError(f"第 {page + 1} 页题号 {tokens[0][0]} 的附图裁切框过薄：{top}~{bottom}")
            centers = [cx for _, cx in tokens]
            for col_index, (token, _) in enumerate(tokens):
                if len(tokens) == 1:
                    left, right = FIGURE_PAGE_LEFT, FIGURE_PAGE_RIGHT
                else:
                    left = FIGURE_PAGE_LEFT if col_index == 0 else (centers[col_index - 1] + centers[col_index]) / 2
                    right = FIGURE_PAGE_RIGHT if col_index == len(tokens) - 1 else (centers[col_index] + centers[col_index + 1]) / 2
                boxes[token] = [page, round(left, 1), round(top, 1), round(right, 1), round(bottom, 1)]
    return boxes


def main():
    parser = argparse.ArgumentParser(description="生成业余无线电考试题库 assets")
    parser.add_argument("--work", default=".workbuddy/examtmp", help="PDF 提取中间目录")
    parser.add_argument("--out", default="app/src/main/assets/exam", help="assets 输出目录")
    parser.add_argument("--dpi", type=int, default=180, help="附图页渲染 dpi（必须与中间 PNG 一致）")
    parser.add_argument("--version", default="v20250728", help="题库版本标记")
    args = parser.parse_args()

    work, out = Path(args.work), Path(args.out)
    banks = {level: parse_bank((work / f"{level}.txt").read_text(encoding="utf-8")) for level in LEVELS}
    merged = merge_banks(banks)
    validate(merged)

    structure = json.loads((work / "structure.json").read_text(encoding="utf-8"))
    figure_boxes = build_figure_boxes(collect_figure_tokens(structure))
    figure_ids = set(figure_boxes)

    questions = []
    for key in sorted(merged, key=lambda k: (int(k.split("-")[1]), k)):
        item = merged[key]
        entry = {
            "i": item["i"],
            "lv": "".join(item["levels"]),
            "p": item["p"],
            "j": item["j"],
            "q": item["q"],
            "o": item["o"],
            "t": item["t"],
            "m": 1 if len(item["t"]) > 1 else 0,
            "d": difficulty(item, figure_ids),
        }
        if item["j"] in figure_boxes:
            entry["f"] = figure_boxes[item["j"]]
        questions.append(entry)

    payload = {
        "version": args.version,
        "total": len(questions),
        "figureDpi": args.dpi,
        "figurePages": len(structure["sections"]),
        "questions": questions,
    }
    out.mkdir(parents=True, exist_ok=True)
    (out / "figures").mkdir(exist_ok=True)
    # 二进制写回，避免 Windows 上 Path.write_text 把 LF 改成 CRLF
    (out / "questions.json").write_bytes(
        (json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
    )
    page_files = sorted((work / "png").glob("page-*.png"))
    for index, source in enumerate(page_files, 1):
        shutil.copyfile(source, out / "figures" / f"page-{index}.png")

    stats = collections.Counter("".join(item["levels"]) for item in merged.values())
    print(f"题库版本 {args.version}：{len(questions)} 题")
    print("  类别归属：", dict(stats))
    for level in LEVELS:
        pool = [q for q in questions if level in q["lv"]]
        print(f"  {level} 类：共 {len(pool)}，单选 {sum(1 - q['m'] for q in pool)}，多选 {sum(q['m'] for q in pool)}")
    print(f"  附图编号：{len(figure_ids)}，题目命中 {sum(1 for q in questions if 'f' in q)} 题")
    print(f"  未匹配到题库的附图编号：{sorted(figure_ids - {q['j'] for q in questions})}")
    print(f"  难度范围：{min(q['d'] for q in questions)} ~ {max(q['d'] for q in questions)}")
    print(f"  附图页 PNG：{len(page_files)} 张，dpi={args.dpi}")
    print(f"  questions.json：{(out / 'questions.json').stat().st_size / 1024:.1f} KB")


if __name__ == "__main__":
    main()
