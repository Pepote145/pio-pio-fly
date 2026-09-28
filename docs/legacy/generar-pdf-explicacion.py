from pathlib import Path
import re
import textwrap

SOURCE_MARKDOWN = Path("docs/explicacion-clases-piopiofly.md")
OUTPUT_PDF = Path("docs/explicacion-clases-piopiofly.pdf")

PAGE_WIDTH = 595.28
PAGE_HEIGHT = 841.89
MARGIN_X = 52
TOP_Y = PAGE_HEIGHT - 56
BOTTOM_Y = 54

FONT_REGULAR = "F1"
FONT_BOLD = "F2"


def escape_pdf(text: str) -> str:
    return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def max_chars(font_size: int, indent: int = 0) -> int:
    usable_width = PAGE_WIDTH - MARGIN_X * 2 - indent
    return max(20, int(usable_width / (font_size * 0.56)))


def add_line(pages, current_page, y, content, font, size, indent=0):
    line_height = size * 1.34
    if y - line_height < BOTTOM_Y:
        pages.append(current_page[:])
        current_page.clear()
        y = TOP_Y
    current_page.append((MARGIN_X + indent, y, font, size, content))
    return y - line_height


def add_paragraph(pages, current_page, y, raw_text, font, size, indent=0,
                  first_prefix="", continuation_prefix=""):
    width = max_chars(size, indent)
    wrapped = textwrap.wrap(
        raw_text.strip(),
        width=width,
        break_long_words=False,
        break_on_hyphens=False
    ) or [""]

    for index, part in enumerate(wrapped):
        prefix = first_prefix if index == 0 else continuation_prefix
        y = add_line(pages, current_page, y, prefix + part, font, size, indent)

    return y


def build_pages(markdown_path: Path):
    lines = markdown_path.read_text(encoding="utf-8").splitlines()
    pages = []
    current_page = []
    y = TOP_Y
    numbered_re = re.compile(r"^(\d+)\.\s+(.*)$")

    for raw_line in lines:
        line = raw_line.rstrip()
        if not line:
            y -= 7
            continue

        if line.startswith("# "):
            y -= 6
            y = add_paragraph(pages, current_page, y, line[2:], FONT_BOLD, 22)
            y -= 6
            continue

        if line.startswith("## "):
            y -= 4
            y = add_paragraph(pages, current_page, y, line[3:], FONT_BOLD, 17)
            y -= 4
            continue

        if line.startswith("### "):
            y -= 2
            y = add_paragraph(pages, current_page, y, line[4:], FONT_BOLD, 13)
            y -= 2
            continue

        if line.startswith("- "):
            y = add_paragraph(
                pages,
                current_page,
                y,
                line[2:],
                FONT_REGULAR,
                11,
                indent=12,
                first_prefix="- ",
                continuation_prefix="  "
            )
            continue

        numbered_match = numbered_re.match(line)
        if numbered_match:
            y = add_paragraph(
                pages,
                current_page,
                y,
                numbered_match.group(2),
                FONT_REGULAR,
                11,
                indent=12,
                first_prefix=f"{numbered_match.group(1)}. ",
                continuation_prefix="   "
            )
            continue

        y = add_paragraph(pages, current_page, y, line, FONT_REGULAR, 11)

    if current_page:
        pages.append(current_page)

    return pages


def build_pdf_objects(pages):
    object_map = {}
    page_count = len(pages)

    font_regular_id = 1
    font_bold_id = 2
    content_start_id = 3
    page_start_id = content_start_id + page_count
    pages_id = page_start_id + page_count
    catalog_id = pages_id + 1
    object_count = catalog_id

    object_map[font_regular_id] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    object_map[font_bold_id] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>"

    for index, page in enumerate(pages):
        stream_lines = ["BT"]
        for x, y, font, size, content in page:
            stream_lines.append(
                f"/{font} {size} Tf 1 0 0 1 {x:.2f} {y:.2f} Tm ({escape_pdf(content)}) Tj"
            )
        stream_lines.append("ET")
        stream = "\n".join(stream_lines).encode("latin-1", errors="replace")
        content_id = content_start_id + index
        object_map[content_id] = (
            b"<< /Length "
            + str(len(stream)).encode()
            + b" >>\nstream\n"
            + stream
            + b"\nendstream"
        )

    for index in range(page_count):
        page_id = page_start_id + index
        content_id = content_start_id + index
        object_map[page_id] = (
            f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 {PAGE_WIDTH:.2f} {PAGE_HEIGHT:.2f}] "
            f"/Resources << /Font << /F1 {font_regular_id} 0 R /F2 {font_bold_id} 0 R >> >> "
            f"/Contents {content_id} 0 R >>"
        ).encode("latin-1")

    kids = " ".join(f"{page_start_id + index} 0 R" for index in range(page_count))
    object_map[pages_id] = (
        f"<< /Type /Pages /Kids [{kids}] /Count {page_count} >>"
    ).encode("latin-1")
    object_map[catalog_id] = (
        f"<< /Type /Catalog /Pages {pages_id} 0 R >>"
    ).encode("latin-1")

    return object_map, object_count, catalog_id


def write_pdf(output_path: Path, object_map, object_count: int, catalog_id: int):
    pdf = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0] * (object_count + 1)

    for object_id in range(1, object_count + 1):
        offsets[object_id] = len(pdf)
        pdf.extend(f"{object_id} 0 obj\n".encode("latin-1"))
        pdf.extend(object_map[object_id])
        pdf.extend(b"\nendobj\n")

    xref_start = len(pdf)
    pdf.extend(f"xref\n0 {object_count + 1}\n".encode("latin-1"))
    pdf.extend(b"0000000000 65535 f \n")

    for object_id in range(1, object_count + 1):
        pdf.extend(f"{offsets[object_id]:010d} 00000 n \n".encode("latin-1"))

    pdf.extend(
        f"trailer\n<< /Size {object_count + 1} /Root {catalog_id} 0 R >>\n"
        f"startxref\n{xref_start}\n%%EOF\n".encode("latin-1")
    )
    output_path.write_bytes(pdf)


def main():
    pages = build_pages(SOURCE_MARKDOWN)
    object_map, object_count, catalog_id = build_pdf_objects(pages)
    write_pdf(OUTPUT_PDF, object_map, object_count, catalog_id)
    print(f"PDF generado en {OUTPUT_PDF} ({len(pages)} paginas).")


if __name__ == "__main__":
    main()
