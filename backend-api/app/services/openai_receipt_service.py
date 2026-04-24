import base64
import io
import json
import logging
import os
import re
import time
from typing import Any
from urllib.parse import urlparse

from openai import OpenAI
from PIL import Image, UnidentifiedImageError

from app.config import settings

_EXTRACTION_PASSES_PER_RECEIPT = 20
_LOG_JSON_SNIPPET_CHARS = 2000
logger = logging.getLogger("homescanner.backend")

_GENERIC_NAME_PATTERNS = (
    re.compile(r"^item\s*\d*$", re.IGNORECASE),
    re.compile(r"^product\s*\d*$", re.IGNORECASE),
    re.compile(r"^line\s*item\s*\d*$", re.IGNORECASE),
)

_BAD_NAMES = {"", "unknown", "unknown item", "item", "product", "misc", "n/a", "na", "-", "."}


def _extract_json(text: str) -> dict[str, Any]:
    text = text.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.lower().startswith("json"):
            text = text[4:].strip()
    return json.loads(text)


def _run_receipt_extraction_pass(client: OpenAI, model: str, image_input: dict[str, Any], pass_index: int) -> dict[str, Any]:
    response = client.chat.completions.create(
        model=model,
        messages=[
            {
                "role": "system",
                "content": (
                    "You are a receipt extraction engine. Return ONLY valid JSON with top-level keys: "
                    "classification and extraction. classification={is_receipt:boolean,confidence:number,reason_codes:string[]}. "
                    "extraction={merchant:string|null,date:string|null,subtotal:number|null,tax:number|null,total:number|null,"
                    "items:[{name:string,qty:number,unit_price:number,line_total:number,confidence:{name:number,qty:number,unit_price:number,line_total:number}}],"
                    "sensitive:object<string,string>}. No markdown."
                ),
            },
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": (
                            f"Pass {pass_index}: classify receipt + extract fields. "
                            "Use careful numeric validation and avoid placeholder item names."
                        ),
                    },
                    image_input,
                ],
            },
        ],
        response_format={"type": "json_object"},
    )
    content = response.choices[0].message.content or "{}"
    return _extract_json(content)


def _run_reconciliation_pass(client: OpenAI, model: str, pass_outputs: list[dict[str, Any]]) -> dict[str, Any]:
    response = client.chat.completions.create(
        model=model,
        messages=[
            {
                "role": "system",
                "content": (
                    "You are a strict receipt reconciler. You are given 20 extraction attempts for the same receipt image. "
                    "Return ONLY valid JSON with top-level keys classification and extraction in the exact schema. "
                    "Your goals: (1) maximize line-item recall, (2) correct item names/prices/qty, (3) resolve vendor name, "
                    "(4) keep subtotal/tax/total numerically coherent. "
                    "Do NOT collapse distinct lines. You may merge only exact duplicate items with same name and unit_price by increasing qty. "
                    "Compute item_sum = sum(items[].line_total). Ensure drift_ratio = abs(item_sum-total)/max(total,1). "
                    "Target drift_ratio <= 0.07 (7%). "
                    "If drift_ratio > 0.07, include reason_codes containing 'item_sum_drift_exceeds_7pct' and lower confidence. "
                    "If uncertain on a field, keep best estimate but lower confidence."
                ),
            },
            {
                "role": "user",
                "content": (
                    "Reconcile these 20 extraction passes for the same receipt into one final output. "
                    "Prioritize complete item list and 7% drift rule.\n"
                    + json.dumps(pass_outputs)
                ),
            },
        ],
        response_format={"type": "json_object"},
    )
    content = response.choices[0].message.content or "{}"
    return _extract_json(content)


def _safe_float(value: Any, default: float | None = None) -> float | None:
    if value is None:
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _valid_item_name(name: str) -> bool:
    normalized = (name or "").strip().lower()
    if not normalized or normalized in _BAD_NAMES or normalized.isnumeric() or len(normalized) < 2:
        return False
    return not any(p.match(normalized) for p in _GENERIC_NAME_PATTERNS)


def _is_extraction_consistent(extraction: dict[str, Any]) -> bool:
    items = extraction.get("items") or []
    if not items:
        return False

    # all item names should be usable
    if any(not _valid_item_name(str((row or {}).get("name") or "")) for row in items):
        return False

    total = _safe_float(extraction.get("total"))
    subtotal = _safe_float(extraction.get("subtotal"))
    tax = _safe_float(extraction.get("tax"))

    # item arithmetic sanity
    line_sum = 0.0
    for row in items:
        qty = _safe_float((row or {}).get("qty"), 1.0) or 1.0
        unit_price = _safe_float((row or {}).get("unit_price"), 0.0) or 0.0
        line_total = _safe_float((row or {}).get("line_total"), qty * unit_price) or (qty * unit_price)
        if abs(round(qty * unit_price, 2) - round(line_total, 2)) > 1.0:
            return False
        line_sum += line_total

    if total is None:
        return False

    # total should approximately match line sum
    if abs(round(line_sum, 2) - round(total, 2)) > 2.0:
        return False

    # subtotal + tax should approximately match total when present
    if subtotal is not None and tax is not None:
        if abs(round(subtotal + tax, 2) - round(total, 2)) > 0.5:
            return False

    return True


def _item_total_drift_ratio(extraction: dict[str, Any]) -> float | None:
    total = _safe_float(extraction.get("total"))
    items = extraction.get("items") or []
    if total is None:
        return None
    line_sum = 0.0
    for row in items:
        qty = _safe_float((row or {}).get("qty"), 1.0) or 1.0
        unit_price = _safe_float((row or {}).get("unit_price"), 0.0) or 0.0
        line_total = _safe_float((row or {}).get("line_total"), qty * unit_price)
        line_sum += (line_total if line_total is not None else qty * unit_price)
    if total <= 0:
        return 0.0 if line_sum == 0 else 1.0
    return abs(line_sum - total) / max(total, 1.0)


def _score_total_consistency(extraction: dict[str, Any]) -> float:
    subtotal = _safe_float(extraction.get("subtotal"))
    tax = _safe_float(extraction.get("tax"))
    total = _safe_float(extraction.get("total"))
    if subtotal is None or tax is None or total is None:
        return 0.8
    expected = round(subtotal + tax, 2)
    actual = round(total, 2)
    diff = abs(expected - actual)
    if diff <= 0.05:
        return 1.0
    if diff <= 1.0:
        return 0.7
    return 0.3


_OPENAI_SUPPORTED_MIME_TYPES = {
    "JPEG": "image/jpeg",
    "PNG": "image/png",
    "GIF": "image/gif",
    "WEBP": "image/webp",
}


def _to_supported_image_data_url(image_bytes: bytes) -> str:
    """Return a data URL with image bytes in an OpenAI-supported format.

    If the source image format is unsupported, it is converted to JPEG.
    """
    try:
        with Image.open(io.BytesIO(image_bytes)) as img:
            src_format = (img.format or "").upper()

            if src_format in _OPENAI_SUPPORTED_MIME_TYPES:
                output = io.BytesIO()
                save_kwargs: dict[str, Any] = {}
                if src_format == "JPEG" and img.mode not in {"RGB", "L"}:
                    img = img.convert("RGB")
                img.save(output, format=src_format, **save_kwargs)
                encoded = base64.b64encode(output.getvalue()).decode("utf-8")
                return f"data:{_OPENAI_SUPPORTED_MIME_TYPES[src_format]};base64,{encoded}"

            # Convert unsupported formats (e.g., HEIC/HEIF/TIFF/BMP) to JPEG.
            converted = io.BytesIO()
            rgb_img = img.convert("RGB")
            rgb_img.save(converted, format="JPEG", quality=92)
            encoded = base64.b64encode(converted.getvalue()).decode("utf-8")
            return f"data:image/jpeg;base64,{encoded}"
    except UnidentifiedImageError as exc:
        raise RuntimeError("receipt image bytes are not a valid image format") from exc


def classify_and_extract_receipt(image_uri: str | None) -> tuple[dict[str, Any], dict[str, Any], int]:
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY is not configured")
    if not image_uri:
        raise RuntimeError("receipt image_uri is missing")
    parsed_uri = urlparse(image_uri)
    if parsed_uri.scheme in {"file", "content"}:
        raise RuntimeError("receipt image_uri is device-local and not accessible from backend")

    if parsed_uri.scheme == "local":
        rel_path = f"{parsed_uri.netloc}{parsed_uri.path}".lstrip("/")
        base_dir = os.path.abspath(settings.receipt_upload_dir)
        image_path = os.path.abspath(os.path.join(base_dir, rel_path))
        if not image_path.startswith(base_dir):
            raise RuntimeError("receipt image_uri points outside configured upload directory")
        if not os.path.exists(image_path):
            raise RuntimeError("receipt image file not found in backend storage")

        with open(image_path, "rb") as f:
            image_bytes = f.read()
        image_input: dict[str, Any] = {
            "type": "image_url",
            "image_url": {"url": _to_supported_image_data_url(image_bytes)},
        }
    else:
        image_input = {"type": "image_url", "image_url": {"url": image_uri}}

    client = OpenAI(api_key=settings.openai_api_key)

    start = time.perf_counter()
    pass_outputs: list[dict[str, Any]] = []
    parsed: dict[str, Any] | None = None

    for i in range(_EXTRACTION_PASSES_PER_RECEIPT):
        candidate = _run_receipt_extraction_pass(client, settings.openai_model, image_input, i + 1)
        pass_outputs.append(candidate)
        candidate_classification = dict(candidate.get("classification") or {})
        candidate_extraction = dict(candidate.get("extraction") or {})
        logger.info(
            "receipt_pass.output pass=%s is_receipt=%s confidence=%s merchant=%s items=%s subtotal=%s tax=%s total=%s",
            i + 1,
            candidate_classification.get("is_receipt"),
            candidate_classification.get("confidence"),
            candidate_extraction.get("merchant"),
            len(candidate_extraction.get("items") or []),
            candidate_extraction.get("subtotal"),
            candidate_extraction.get("tax"),
            candidate_extraction.get("total"),
        )
        raw_json = json.dumps(candidate, ensure_ascii=False)
        if len(raw_json) > _LOG_JSON_SNIPPET_CHARS:
            raw_json = raw_json[:_LOG_JSON_SNIPPET_CHARS] + "...<truncated>"
        logger.info("receipt_pass.raw_json pass=%s payload=%s", i + 1, raw_json)

    # finalize by sending all pass outputs back to OpenAI for one consensus result
    parsed = _run_reconciliation_pass(client, settings.openai_model, pass_outputs)
    parsed_classification = dict(parsed.get("classification") or {})
    parsed_extraction = dict(parsed.get("extraction") or {})
    logger.info(
        "receipt_pass.final is_receipt=%s confidence=%s merchant=%s items=%s subtotal=%s tax=%s total=%s",
        parsed_classification.get("is_receipt"),
        parsed_classification.get("confidence"),
        parsed_extraction.get("merchant"),
        len(parsed_extraction.get("items") or []),
        parsed_extraction.get("subtotal"),
        parsed_extraction.get("tax"),
        parsed_extraction.get("total"),
    )
    final_json = json.dumps(parsed, ensure_ascii=False)
    if len(final_json) > _LOG_JSON_SNIPPET_CHARS:
        final_json = final_json[:_LOG_JSON_SNIPPET_CHARS] + "...<truncated>"
    logger.info("receipt_pass.final_raw_json payload=%s", final_json)

    if parsed is None:
        parsed = {"classification": {}, "extraction": {}}

    latency_ms = int((time.perf_counter() - start) * 1000)

    classification = dict(parsed.get("classification") or {})
    extraction = dict(parsed.get("extraction") or {})

    # deterministic 7% drift guardrail
    drift_ratio = _item_total_drift_ratio(extraction)
    reason_codes = list(classification.get("reason_codes") or [])
    if drift_ratio is not None and drift_ratio > 0.07:
        if "item_sum_drift_exceeds_7pct" not in reason_codes:
            reason_codes.append("item_sum_drift_exceeds_7pct")
        classification["reason_codes"] = reason_codes

    # apply deterministic consistency down-weighting
    base_conf = _safe_float(classification.get("confidence"), 0.0) or 0.0
    consistency_score = _score_total_consistency(extraction)
    if drift_ratio is not None and drift_ratio > 0.07:
        consistency_score *= 0.5
    classification["confidence"] = round(max(0.0, min(1.0, base_conf * consistency_score)), 4)

    return classification, extraction, latency_ms
