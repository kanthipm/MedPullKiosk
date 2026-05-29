#!/usr/bin/env python3
"""
Bulk-translate the canonical English Android string resources into every
supported locale using AWS Translate.

Source file: app/src/main/res/values/strings.xml
Output:      app/src/main/res/values-{locale}/strings.xml for each target locale

The pipeline is:
  * idempotent — re-running with no source changes is a no-op
  * incremental — only translates keys missing from each target file, so any
    hand-edited translations already in a locale file are preserved
  * placeholder-safe — Android format tokens (`%s`, `%1$s`, `%d`) are masked
    before sending to AWS Translate so the engine doesn't garble them
  * skips strings marked `translatable="false"` (brand names, etc.)

Usage:
  python3 scripts/translate_strings.py            # translate everything missing
  python3 scripts/translate_strings.py --force    # re-translate every key
  python3 scripts/translate_strings.py --locales es,fr  # only listed locales
  python3 scripts/translate_strings.py --dry-run  # show what would change

Required: AWS CLI credentials configured (`aws configure`) with translate:*
permission. The AWS_REGION env var or the credentials region is used; falls
back to us-east-1 which matches the kiosk app's deployment region.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from xml.sax.saxutils import escape as xml_escape

try:
    import boto3
    from botocore.exceptions import BotoCoreError, ClientError
except ImportError:
    print(
        "boto3 is required. Install it with:\n"
        "  pip3 install --user --break-system-packages boto3",
        file=sys.stderr,
    )
    sys.exit(1)


# ── Locale catalog ──────────────────────────────────────────────────────────
# Maps the Android resource qualifier (also the in-app language code) to the
# AWS Translate target language code. These two happen to match for every
# language in our 8-language set; if they ever diverge (e.g. zh-Hant), update
# here and in com.medpull.kiosk.utils.LocaleManager + TranslationService.

TARGET_LOCALES: Dict[str, str] = {
    "es": "es",   # Spanish
    "zh": "zh",   # Chinese (Simplified)
    "fr": "fr",   # French
    "ja": "ja",   # Japanese
    "pt": "pt",   # Portuguese
    "ar": "ar",   # Arabic (RTL)
    "ru": "ru",   # Russian
}

SOURCE_LANGUAGE = "en"
DEFAULT_REGION = "us-east-1"


# ── Paths ───────────────────────────────────────────────────────────────────

SCRIPT_DIR = Path(__file__).resolve().parent
APP_DIR = SCRIPT_DIR.parent / "app"
RES_DIR = APP_DIR / "src" / "main" / "res"
SOURCE_XML = RES_DIR / "values" / "strings.xml"


# ── XML parsing (preserves order + comments) ────────────────────────────────
#
# Android resources are line-oriented in practice: tooling and code review
# expect grouped sections with `<!-- comment -->` headers. xml.etree.ElementTree
# drops comments by default, and lxml is not always available, so we use a
# light line-based parser that lifts <string> entries while letting us round-
# trip the rest of the file verbatim.

STRING_RE = re.compile(
    r'^(?P<indent>\s*)<string\s+name="(?P<name>[^"]+)"'
    r'(?P<attrs>(?:\s+\w+="[^"]*")*)>(?P<body>.*?)</string>\s*$',
    re.DOTALL,
)


class StringEntry:
    """One <string> tag with its name, attrs, body, and indent."""

    __slots__ = ("name", "indent", "extra_attrs", "body", "translatable")

    def __init__(self, name: str, indent: str, extra_attrs: str, body: str):
        self.name = name
        self.indent = indent
        self.extra_attrs = extra_attrs  # raw attribute string, leading space
        self.body = body
        # If any attribute marks it explicitly non-translatable, honor that.
        self.translatable = 'translatable="false"' not in extra_attrs


def load_strings(path: Path) -> Tuple[Dict[str, StringEntry], List[str]]:
    """Return (entries-by-name, raw-source-lines)."""
    entries: Dict[str, StringEntry] = {}
    lines = path.read_text(encoding="utf-8").splitlines(keepends=False)
    for line in lines:
        m = STRING_RE.match(line)
        if not m:
            continue
        entry = StringEntry(
            name=m.group("name"),
            indent=m.group("indent"),
            extra_attrs=m.group("attrs"),
            body=m.group("body"),
        )
        entries[entry.name] = entry
    return entries, lines


# ── Placeholder masking ─────────────────────────────────────────────────────
#
# Android format tokens like `%1$s` or `%d` confuse the translator. Replace
# them with opaque ASCII anchors that no natural language touches, translate,
# then restore.

PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[sdif]")


def mask_placeholders(text: str) -> Tuple[str, List[str]]:
    placeholders: List[str] = []

    def replace(match: re.Match) -> str:
        placeholders.append(match.group(0))
        return f"__PH_{len(placeholders) - 1}__"

    return PLACEHOLDER_RE.sub(replace, text), placeholders


def restore_placeholders(text: str, placeholders: List[str]) -> str:
    for index, token in enumerate(placeholders):
        text = text.replace(f"__PH_{index}__", token)
    return text


# ── Android XML escape handling ─────────────────────────────────────────────
#
# Android uses backslash escapes for `\'`, `\"`, and `\n` inside <string>
# bodies. AWS Translate needs the actual character, so we unescape before
# sending and re-escape after.

def unescape_android(body: str) -> str:
    return (
        body.replace("\\'", "'")
            .replace('\\"', '"')
            .replace("\\n", "\n")
            .replace("&amp;", "&")
            .replace("&apos;", "'")
            .replace("&quot;", '"')
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    )


def escape_android(body: str) -> str:
    # First handle XML entities, then Android-specific escapes.
    body = (
        body.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    )
    # Apostrophe inside <string> must be backslash-escaped in Android XML.
    body = body.replace("'", "\\'")
    body = body.replace("\n", "\\n")
    return body


# ── Translation ─────────────────────────────────────────────────────────────


class Translator:
    def __init__(self, region: str):
        self.client = boto3.client("translate", region_name=region)
        self._cache: Dict[Tuple[str, str], str] = {}
        self.calls = 0

    def translate(self, text: str, target: str) -> str:
        key = (target, text)
        if key in self._cache:
            return self._cache[key]

        masked, placeholders = mask_placeholders(text)
        try:
            response = self.client.translate_text(
                Text=masked,
                SourceLanguageCode=SOURCE_LANGUAGE,
                TargetLanguageCode=target,
            )
        except (BotoCoreError, ClientError) as exc:
            raise RuntimeError(
                f"AWS Translate failed for [{target}] {text!r}: {exc}"
            ) from exc

        self.calls += 1
        translated = restore_placeholders(response["TranslatedText"], placeholders)
        self._cache[key] = translated
        return translated


# ── Output ──────────────────────────────────────────────────────────────────


def write_locale_file(
    locale: str,
    source_entries: Dict[str, StringEntry],
    source_lines: List[str],
    translations: Dict[str, str],
) -> str:
    """
    Generate the XML for `values-{locale}/strings.xml`.

    We walk the source file line-by-line so the locale file mirrors the
    original's order, comments, and blank lines. Each <string> line is
    rewritten with the translated body (or kept verbatim for non-translatable
    entries).
    """
    out_lines: List[str] = []
    for line in source_lines:
        m = STRING_RE.match(line)
        if not m:
            out_lines.append(line)
            continue

        name = m.group("name")
        entry = source_entries[name]

        # Preserve non-translatable entries (brand names, etc.) verbatim.
        if not entry.translatable:
            out_lines.append(line)
            continue

        translated = translations.get(name, entry.body)
        rebuilt = (
            f'{entry.indent}<string name="{name}"{entry.extra_attrs}>'
            f"{translated}</string>"
        )
        out_lines.append(rebuilt)

    return "\n".join(out_lines) + "\n"


# ── Main pipeline ───────────────────────────────────────────────────────────


def run(
    locales: List[str],
    force: bool,
    dry_run: bool,
    region: str,
) -> int:
    if not SOURCE_XML.exists():
        print(f"Source not found: {SOURCE_XML}", file=sys.stderr)
        return 2

    source_entries, source_lines = load_strings(SOURCE_XML)
    print(f"Source: {SOURCE_XML.relative_to(APP_DIR.parent)} ({len(source_entries)} keys)")

    translator: Optional[Translator] = None
    if not dry_run:
        translator = Translator(region=region)

    total_new = 0
    total_skipped = 0

    for locale in locales:
        target_dir = RES_DIR / f"values-{locale}"
        target_file = target_dir / "strings.xml"
        aws_code = TARGET_LOCALES[locale]

        existing: Dict[str, StringEntry] = {}
        if target_file.exists():
            existing, _ = load_strings(target_file)

        needed: List[str] = []
        translations: Dict[str, str] = {}
        for name, src_entry in source_entries.items():
            if not src_entry.translatable:
                continue
            if not force and name in existing:
                translations[name] = existing[name].body
                continue
            needed.append(name)

        print(
            f"  [{locale}] {len(needed)} to translate, "
            f"{len(translations)} already present"
        )

        if needed and not dry_run:
            assert translator is not None
            for name in needed:
                src_entry = source_entries[name]
                plain = unescape_android(src_entry.body)
                try:
                    out = translator.translate(plain, target=aws_code)
                except RuntimeError as exc:
                    print(f"    SKIP {name}: {exc}", file=sys.stderr)
                    translations[name] = src_entry.body
                    total_skipped += 1
                    continue
                translations[name] = escape_android(out)
                total_new += 1
        elif needed and dry_run:
            for name in needed:
                print(f"    DRY  {name} ({source_entries[name].body[:60]}…)")

        if not dry_run:
            target_dir.mkdir(parents=True, exist_ok=True)
            content = write_locale_file(
                locale=locale,
                source_entries=source_entries,
                source_lines=source_lines,
                translations=translations,
            )
            target_file.write_text(content, encoding="utf-8")
            print(f"    -> {target_file.relative_to(APP_DIR.parent)}")

    if translator:
        print(f"AWS Translate calls: {translator.calls}")
    if total_skipped:
        print(f"Skipped (errors): {total_skipped}", file=sys.stderr)
    print(f"Translated {total_new} new entries.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--locales",
        default=",".join(TARGET_LOCALES.keys()),
        help="Comma-separated locales to translate (default: all)",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Re-translate every key, even if it already exists in target",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print what would change without calling AWS Translate",
    )
    parser.add_argument(
        "--region",
        default=os.environ.get("AWS_REGION", DEFAULT_REGION),
        help=f"AWS region (default: ${{AWS_REGION}} or {DEFAULT_REGION})",
    )
    args = parser.parse_args()

    requested = [l.strip() for l in args.locales.split(",") if l.strip()]
    invalid = [l for l in requested if l not in TARGET_LOCALES]
    if invalid:
        print(f"Unknown locale(s): {invalid}", file=sys.stderr)
        print(f"Supported: {list(TARGET_LOCALES.keys())}", file=sys.stderr)
        return 2

    return run(
        locales=requested,
        force=args.force,
        dry_run=args.dry_run,
        region=args.region,
    )


if __name__ == "__main__":
    sys.exit(main())
