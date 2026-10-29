"""Check every exported static menu entry against the external dictionary."""
import csv
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[1]
dictionary = {}
for n, line in enumerate((root / 'locales/menus.zh_CN.tsv').read_text(encoding='utf-8').splitlines(), 1):
    if not line or line.startswith('#'):
        continue
    key, value = line.split('\t')
    if key in dictionary:
        raise ValueError(f'Duplicate dictionary key at line {n}: {key}')
    if not value or not any('\u4e00' <= c <= '\u9fff' for c in value):
        raise ValueError(f'Missing Chinese display at line {n}: {key}')
    dictionary[key] = value
with (root / 'build/menu-inventory.tsv').open(encoding='utf-8', newline='') as stream:
    rows = list(csv.DictReader(stream, delimiter='\t'))
rows = [row for row in rows if not row['path'].startswith('File/Open Recent/')]
missing = [r['path'] for r in rows if '@' + r['path'] not in dictionary and r['original'] not in dictionary and r['original'].strip() not in dictionary]
print(f'Static menu entries: {len(rows)}; covered: {len(rows)-len(missing)}; missing: {len(missing)}; dictionary entries: {len(dictionary)}')
for path in missing:
    print(path)
sys.exit(bool(missing))
