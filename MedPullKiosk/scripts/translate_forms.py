#!/usr/bin/env python3
"""
Bake patient-facing questions and per-language translations into the declarative
form schemas under app/src/main/assets/schemas/*.json using AWS Translate.

For every field in every schema this does two things:

  1. Derives an English patient-facing `question` from the field's `label`
     (and, for static_label fields, from the quoted text in its `ai_note`),
     unless a `question` was already authored by hand.
  2. Translates the `question`, `label`, and `options` into every supported
     locale and stores them under an `i18n` block:

        "i18n": {
          "es": { "question": "...", "label": "...", "options": ["...", "..."] },
          ...
        }

The app loads these deterministically — no LLM call per field — so questions are
spoken (TTS) and shown in the patient's language, while the canonical English
`options` values are preserved for skip rules / consent / export.

The pipeline is:
  * idempotent  — re-running with no schema changes is a no-op
  * incremental — only fills missing `question` / `i18n` entries, so any
    hand-edited translation already in a schema is preserved
  * safe        — `ai_note` (internal AI guidance, never shown to patients) is
    NOT translated, and the canonical English `options` are never overwritten

Usage:
  python3 scripts/translate_forms.py               # fill everything missing
  python3 scripts/translate_forms.py --force       # re-translate every field
  python3 scripts/translate_forms.py --locales es,fr
  python3 scripts/translate_forms.py --dry-run     # show what would change
  python3 scripts/translate_forms.py --schemas coastal_gateway_intake.json

Required: AWS credentials configured (`aws configure`) with translate:* permission.
Falls back to us-east-1, matching the kiosk app's deployment region.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

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
# App language code → AWS Translate target code. Mirrors translate_strings.py
# and com.medpull.kiosk.data.remote.aws.TranslationService.mapLanguageCode.

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
SCHEMAS_DIR = APP_DIR / "src" / "main" / "assets" / "schemas"


# ── English question derivation ──────────────────────────────────────────────
#
# Keep this in sync (in spirit) with IntakeConversationEngine.fallbackQuestion —
# the runtime still derives questions this way for schema-less (uploaded) forms.

PAREN_SUFFIX_RE = re.compile(r"\s*\([^)]*\)\s*$")
QUOTED_RE = re.compile(r"['‘’\"“”](.+)['‘’\"“”]", re.S)

QUESTION_STARTS = (
    "what", "which", "how", "where", "when", "why", "who", "whom", "whose",
    "do ", "does ", "did ", "is ", "are ", "has ", "have ", "can ", "will ",
    "was ", "were ", "should ", "may ", "would ",
)


def strip_paren(label: str) -> str:
    return PAREN_SUFFIX_RE.sub("", label).strip()


def derive_question(field: dict) -> str:
    """Produce a warm, patient-facing English question for a field."""
    label = (field.get("label") or "").strip()
    ftype = field.get("type", "")
    ai_note = field.get("ai_note") or ""

    if ftype == "static_label":
        # Static labels are spoken framing text. The patient-facing copy is the
        # quoted string inside the ai_note ("Deliver: '...'" / "Tell ...: '...'").
        m = QUOTED_RE.search(ai_note)
        if m:
            return m.group(1).strip()
        return strip_paren(label)

    if ftype == "signature":
        return "Please sign below."

    clean = strip_paren(label)

    if ftype == "multi_select":
        # Multi-select shows only the question heading + option buttons, so name
        # the category and tell the patient they can pick more than one.
        return f"{clean} — select all that apply."

    if clean.endswith("?"):
        return clean

    lower = clean.lower()
    if lower.startswith(QUESTION_STARTS):
        return clean.rstrip(".") + "?"

    return f"What is your {clean.rstrip('.?')}?"


# ── Translation ─────────────────────────────────────────────────────────────


class Translator:
    def __init__(self, region: str):
        self.client = boto3.client("translate", region_name=region)
        self._cache: Dict[Tuple[str, str], str] = {}
        self.calls = 0

    def translate(self, text: str, target: str) -> str:
        text = (text or "").strip()
        if not text:
            return text
        key = (target, text)
        if key in self._cache:
            return self._cache[key]
        try:
            response = self.client.translate_text(
                Text=text,
                SourceLanguageCode=SOURCE_LANGUAGE,
                TargetLanguageCode=target,
            )
        except (BotoCoreError, ClientError) as exc:
            raise RuntimeError(
                f"AWS Translate failed for [{target}] {text!r}: {exc}"
            ) from exc
        self.calls += 1
        translated = response["TranslatedText"]
        self._cache[key] = translated
        return translated


# ── Schema walking ────────────────────────────────────────────────────────────


def iter_fields(schema: dict):
    """Yield each field dict across all sections."""
    for section in schema.get("sections", []):
        for field in section.get("fields", []):
            yield field


def process_field(
    field: dict,
    locales: List[str],
    translator: Optional[Translator],
    force: bool,
    dry_run: bool,
    stats: Dict[str, int],
) -> None:
    label = (field.get("label") or "").strip()
    if not label:
        return

    # 1) English patient-facing question (preserve hand-authored unless --force).
    if force or not field.get("question"):
        field["question"] = derive_question(field)
        stats["questions"] += 1

    question = field["question"]
    options = field.get("options") or []

    # 2) Per-locale translations.
    i18n: dict = field.get("i18n") or {}
    for locale in locales:
        aws_code = TARGET_LOCALES[locale]
        entry = i18n.get(locale) or {}

        need_q = force or "question" not in entry
        need_l = force or "label" not in entry
        need_o = bool(options) and (force or "options" not in entry
                                    or len(entry.get("options") or []) != len(options))

        if not (need_q or need_l or need_o):
            continue

        if dry_run:
            stats["fields_changed"] += 1
            i18n[locale] = entry
            continue

        assert translator is not None
        try:
            if need_q:
                entry["question"] = translator.translate(question, aws_code)
            if need_l:
                entry["label"] = translator.translate(label, aws_code)
            if need_o:
                entry["options"] = [translator.translate(o, aws_code) for o in options]
        except RuntimeError as exc:
            print(f"    SKIP {field.get('id')} [{locale}]: {exc}", file=sys.stderr)
            stats["skipped"] += 1
            # Keep whatever we already have; don't write a partial broken entry.
            if entry:
                i18n[locale] = entry
            continue

        i18n[locale] = entry
        stats["fields_changed"] += 1

    if i18n:
        field["i18n"] = i18n


def process_schema(
    path: Path,
    locales: List[str],
    translator: Optional[Translator],
    force: bool,
    dry_run: bool,
) -> Dict[str, int]:
    schema = json.loads(path.read_text(encoding="utf-8"))
    stats = {"questions": 0, "fields_changed": 0, "skipped": 0}

    for field in iter_fields(schema):
        process_field(field, locales, translator, force, dry_run, stats)

    print(
        f"  {path.name}: {stats['questions']} questions derived, "
        f"{stats['fields_changed']} locale entries written"
        + (f", {stats['skipped']} skipped" if stats['skipped'] else "")
    )

    if not dry_run and (stats["questions"] or stats["fields_changed"]):
        path.write_text(
            json.dumps(schema, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )

    return stats


# ── Main pipeline ───────────────────────────────────────────────────────────


def run(
    schema_files: List[Path],
    locales: List[str],
    force: bool,
    dry_run: bool,
    region: str,
) -> int:
    if not schema_files:
        print(f"No schemas found in {SCHEMAS_DIR}", file=sys.stderr)
        return 2

    translator: Optional[Translator] = None
    if not dry_run:
        translator = Translator(region=region)

    print(f"Schemas dir: {SCHEMAS_DIR.relative_to(APP_DIR.parent)}")
    print(f"Locales: {', '.join(locales)}")

    total = {"questions": 0, "fields_changed": 0, "skipped": 0}
    for path in schema_files:
        s = process_schema(path, locales, translator, force, dry_run)
        for k in total:
            total[k] += s[k]

    if translator:
        print(f"AWS Translate calls: {translator.calls}")
    if total["skipped"]:
        print(f"Skipped (errors): {total['skipped']}", file=sys.stderr)
    print(
        f"Done. {total['questions']} questions derived, "
        f"{total['fields_changed']} locale entries written."
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--locales",
        default=",".join(TARGET_LOCALES.keys()),
        help="Comma-separated locales to translate (default: all)",
    )
    parser.add_argument(
        "--schemas",
        default="",
        help="Comma-separated schema filenames to process (default: all *.json)",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Re-derive questions and re-translate every field",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print what would change without calling AWS Translate",
    )
    parser.add_argument(
        "--region",
        default=os.environ.get("AWS_REGION", DEFAULT_REGION),
        help=f"AWS region (default: $AWS_REGION or {DEFAULT_REGION})",
    )
    args = parser.parse_args()

    requested = [l.strip() for l in args.locales.split(",") if l.strip()]
    invalid = [l for l in requested if l not in TARGET_LOCALES]
    if invalid:
        print(f"Unknown locale(s): {invalid}", file=sys.stderr)
        print(f"Supported: {list(TARGET_LOCALES.keys())}", file=sys.stderr)
        return 2

    if args.schemas.strip():
        names = {n.strip() for n in args.schemas.split(",") if n.strip()}
        schema_files = sorted(p for p in SCHEMAS_DIR.glob("*.json") if p.name in names)
    else:
        schema_files = sorted(SCHEMAS_DIR.glob("*.json"))

    return run(
        schema_files=schema_files,
        locales=requested,
        force=args.force,
        dry_run=args.dry_run,
        region=args.region,
    )


if __name__ == "__main__":
    sys.exit(main())
