#!/usr/bin/env python3
"""Generate the DRS-Enhanced ERD from sql/schema.sql.

Reads every CREATE TABLE statement in sql/schema.sql (which is itself
generated from MySqlSchema, so this diagram always matches the database the
server creates) and writes:

    docs/erd/drs-erd.mmd   Mermaid erDiagram source
    docs/erd/drs-erd.svg   the drawn ERD, laid out by area for the report

Keys come from the PRIMARY KEY, UNIQUE KEY and FOREIGN KEY clauses.
Cardinality comes from the foreign keys: every foreign key column is NOT
NULL, so each child row has exactly one parent, and a parent has zero or
more children. Two references are deliberately not foreign keys in the
schema; they are drawn as dotted "logical" links.

Usage, from the project folder:  python3 docs/erd/generate_erd.py
The PNG is the SVG rendered at 2x (see docs/erd/README.md).
"""

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "sql" / "schema.sql"
OUT = ROOT / "docs" / "erd"

# References the schema keeps without a FOREIGN KEY (see MySqlSchema).
LOGICAL_LINKS = [
    ("incidents", "reports", "linked_incident_id", "linked"),
    ("warnings", "warning_acknowledgements", "warning_id",
     "acknowledged by"),
]

# Columns whose protection the security section explains.
NOTES = {
    ("users", "password_hash"): "PBKDF2-HMAC-SHA256",
    ("reports", "reporter_name"): "AES-256-GCM encrypted",
    ("reports", "reporter_contact"): "AES-256-GCM encrypted",
    ("audit_events", "previous_hash"): "hash chain",
    ("audit_events", "entry_hash"): "hash chain",
}

# Short verbs for each foreign key, parent -> child.
LABELS = {
    ("incidents", "incident_reports"): "raised from",
    ("reports", "incident_reports"): "source of",
    ("incidents", "assessments"): "assessed by",
    ("incidents", "assignments"): "tasked by",
    ("assignments", "allocations"): "resourced by",
    ("incidents", "field_updates"): "timeline",
    ("incidents", "warnings"): "warned by",
    ("users", "user_keys"): "signs with",
}


def parse(sql):
    """Return {table: {"columns": [...], "pk": set, "uk": set, "fk": [...]}}."""
    tables = {}
    for match in re.finditer(r"CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\n\)",
                             sql, re.S):
        name, body = match.group(1), match.group(2)
        table = {"columns": [], "pk": set(), "uk": set(), "fk": []}
        lines = [line.strip().rstrip(",") for line in body.splitlines()]
        joined = "\n".join(lines)
        for line in lines:
            if not line:
                continue
            word = line.split()[0]
            if word == "PRIMARY":
                table["pk"].update(re.findall(r"\w+", line.split("(")[1]))
            elif word == "UNIQUE":
                cols = re.findall(r"\w+", line.split("(")[1])
                if len(cols) == 1:
                    table["uk"].update(cols)
            elif word in ("KEY", "CONSTRAINT", "FOREIGN", "REFERENCES"):
                continue
            else:
                parts = line.split()
                table["columns"].append(
                    (parts[0], parts[1].split("(")[0].lower(),
                     "NOT NULL" in line))
        for fk in re.finditer(
                r"FOREIGN KEY \((\w+)\)\s+REFERENCES (\w+) \((\w+)\)",
                joined):
            table["fk"].append(fk.groups())
        tables[name] = table
    return tables


def mermaid(tables):
    out = ["erDiagram", "    direction LR"]
    for name, table in tables.items():
        out.append(f"    {name} {{")
        fk_cols = {fk[0] for fk in table["fk"]}
        for column, kind, _not_null in table["columns"]:
            keys = []
            if column in table["pk"]:
                keys.append("PK")
            if column in fk_cols:
                keys.append("FK")
            if column in table["uk"]:
                keys.append("UK")
            note = NOTES.get((name, column))
            out.append(f"        {kind} {column}"
                       + (" " + ", ".join(keys) if keys else "")
                       + (f' "{note}"' if note else ""))
        out.append("    }")
    for child, table in tables.items():
        for column, parent, _target in table["fk"]:
            label = LABELS.get((parent, child), column)
            out.append(f'    {parent} ||--o{{ {child} : "{label}"')
    for parent, child, column, label in LOGICAL_LINKS:
        out.append(f'    {parent} |o..o{{ {child} : "{label} (no FK)"')
    return "\n".join(out) + "\n"


# Where each table sits in the drawn ERD: column, then order down it.
LAYOUT = [
    ["users", "user_keys", "audit_events"],
    ["reports", "incident_reports"],
    ["incidents"],
    ["assessments", "assignments", "field_updates", "warnings"],
    ["allocations", "warning_acknowledgements"],
]

# Tables drawn level with their parent rather than stacked.
ALIGN = {"allocations": "assignments",
         "warning_acknowledgements": "warnings"}
ROW = 17
HEAD = 24
COL_GAP = 125
BOX_GAP = 28
FONT = "Arial, Helvetica, sans-serif"


def box_width(table):
    longest = max(len(c[0]) for c in table["columns"])
    note = max((len(NOTES.get((table["name"], c[0]), ""))
                for c in table["columns"]), default=0)
    return 70 + longest * 7 + 34 + note * 6


def place(tables):
    """Give every table x, y, w, h following LAYOUT."""
    x = 20
    for column in LAYOUT:
        width = max(box_width(tables[name]) for name in column)
        y = 90
        for name in column:
            table = tables[name]
            if name in ALIGN:
                y = tables[ALIGN[name]]["box"][1]
            table["box"] = (x, y, width,
                            HEAD + ROW * len(table["columns"]) + 4)
            y += table["box"][3] + BOX_GAP
        x += width + COL_GAP
    return x


def row_y(table, column):
    x, y, w, h = table["box"]
    names = [c[0] for c in table["columns"]]
    return y + HEAD + ROW * names.index(column) + ROW / 2


def draw_table(table, out):
    x, y, w, h = table["box"]
    name = table["name"]
    fk_cols = {fk[0] for fk in table["fk"]}
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" '
               f'fill="#ffffff" stroke="#52606d" stroke-width="1.2"/>')
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{HEAD}" '
               f'fill="#1f2d3d"/>')
    out.append(f'<text x="{x + 8}" y="{y + 16}" fill="#ffffff" '
               f'font-weight="bold" font-size="13">{name}</text>')
    for i, (column, kind, not_null) in enumerate(table["columns"]):
        ry = y + HEAD + ROW * i
        if i % 2:
            out.append(f'<rect x="{x + 1}" y="{ry}" width="{w - 2}" '
                       f'height="{ROW}" fill="#f2f4f7"/>')
        keys = []
        if column in table["pk"]:
            keys.append("PK")
        if column in fk_cols:
            keys.append("FK")
        if column in table["uk"]:
            keys.append("UK")
        weight = ' font-weight="bold"' if keys else ""
        out.append(f'<text x="{x + 8}" y="{ry + 12.5}" font-size="10.5" '
                   f'fill="#7b8794">{kind}</text>')
        out.append(f'<text x="{x + 62}" y="{ry + 12.5}" font-size="11.5" '
                   f'fill="#1f2933"{weight}>{column}'
                   + ("" if not_null or keys else "") + "</text>")
        note = NOTES.get((name, column))
        tag = " ".join(keys)
        right = x + w - 8
        if note:
            out.append(f'<text x="{right}" y="{ry + 12.5}" font-size="10" '
                       f'fill="#c2410c" text-anchor="end">{note}</text>')
            right -= len(note) * 5.6 + 8
        if tag:
            out.append(f'<text x="{right}" y="{ry + 12.5}" font-size="10" '
                       f'fill="#2563eb" font-weight="bold" '
                       f'text-anchor="end">{tag}</text>')


def crow(x, y, toward, out, optional=True):
    """Draw 'zero or many' at (x, y); toward = -1 if the line comes from
    the left, 1 from the right."""
    d = toward
    out.append(f'<path d="M{x} {y} L{x + 12 * d} {y - 7} M{x} {y} '
               f'L{x + 12 * d} {y + 7} M{x} {y} L{x + 12 * d} {y}" '
               f'stroke="#1f2933" fill="none"/>')
    if optional:
        out.append(f'<circle cx="{x + 18 * d}" cy="{y}" r="4" '
                   f'fill="#ffffff" stroke="#1f2933"/>')


def bars(x, y, toward, out, optional=False):
    """Draw 'exactly one' (two bars) or 'zero or one' (bar and ring)."""
    d = toward
    out.append(f'<path d="M{x + 6 * d} {y - 7} L{x + 6 * d} {y + 7}" '
               f'stroke="#1f2933"/>')
    if optional:
        out.append(f'<circle cx="{x + 14 * d}" cy="{y}" r="4" '
                   f'fill="#ffffff" stroke="#1f2933"/>')
    else:
        out.append(f'<path d="M{x + 11 * d} {y - 7} L{x + 11 * d} '
                   f'{y + 7}" stroke="#1f2933"/>')


def label_text(x, y, text, anchor, labels):
    """Queue a relationship label; drawn after the tables, with a white
    halo, so no box hides it."""
    labels.append(f'<text x="{x}" y="{y}" font-size="10.5" fill="#52606d" '
                  f'text-anchor="{anchor}" stroke="#ffffff" '
                  f'stroke-width="4" paint-order="stroke">{text}</text>')


def link(parent, child, column, label, out, labels, logical=False,
         target=None):
    px, py_, pw, ph = parent["box"]
    cx, cy_, cw, ch = child["box"]
    y2 = row_y(child, column)
    y1 = row_y(parent, target) if target else py_ + HEAD / 2 + 3
    dash = ' stroke-dasharray="6 4"' if logical else ""
    if abs(px - cx) < 5:
        # Same column: run down the left edge.
        x = px - 18
        out.append(f'<path d="M{px} {y1} L{x} {y1} L{x} {y2} L{cx} {y2}" '
                   f'stroke="#1f2933" fill="none"{dash}/>')
        bars(px, y1, -1, out, optional=logical)
        crow(cx, y2, -1, out)
        label_text(x - 6, (y1 + y2) / 2 + 4, label, "end", labels)
        return
    if cx > px:
        x1, x2, d1, d2 = px + pw, cx, 1, -1
    else:
        x1, x2, d1, d2 = px, cx + cw, -1, 1
    mid = (x1 + x2) / 2
    out.append(f'<path d="M{x1} {y1} L{mid} {y1} L{mid} {y2} L{x2} {y2}" '
               f'stroke="#1f2933" fill="none"{dash}/>')
    bars(x1, y1, d1, out, optional=logical)
    crow(x2, y2, d2, out)
    if d2 == -1:
        label_text(x2 - 24, y2 - 6, label, "end", labels)
    else:
        label_text(x2 + 24, y2 - 6, label, "start", labels)


def svg(tables):
    for name, table in tables.items():
        table["name"] = name
    width = place(tables)
    height = max(t["box"][1] + t["box"][3] for t in tables.values()) + 30
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" '
           f'height="{height}" viewBox="0 0 {width} {height}" '
           f'font-family="{FONT}">',
           f'<rect width="{width}" height="{height}" fill="#ffffff"/>',
           '<text x="20" y="34" font-size="20" font-weight="bold" '
           'fill="#1f2933">DRS-Enhanced MySQL schema (ERD)</text>',
           '<text x="20" y="56" font-size="12" fill="#52606d">Generated '
           'from sql/schema.sql. PK primary key, FK foreign key, UK unique '
           'key. Foreign keys reference each table\'s readable code (UK). '
           'Crow\'s foot = zero or more; two bars = exactly one; dashed = '
           'link kept without a foreign key.</text>']
    labels = []
    for child, table in tables.items():
        for column, parent, target in table["fk"]:
            link(tables[parent], table, column,
                 LABELS.get((parent, child), column), out, labels,
                 target=target)
    for parent, child, column, label in LOGICAL_LINKS:
        link(tables[parent], tables[child], column, label + " (no FK)", out,
             labels, logical=True)
    for table in tables.values():
        draw_table(table, out)
    out.extend(labels)
    out.append("</svg>")
    return "\n".join(out) + "\n"


def main():
    tables = parse(SCHEMA.read_text(encoding="utf-8"))
    diagram = mermaid(tables)
    (OUT / "drs-erd.mmd").write_text(diagram, encoding="utf-8")
    (OUT / "drs-erd.svg").write_text(svg(tables), encoding="utf-8")
    fks = sum(len(t["fk"]) for t in tables.values())
    print(f"{len(tables)} tables, {fks} foreign keys -> docs/erd/drs-erd.mmd")


if __name__ == "__main__":
    main()
