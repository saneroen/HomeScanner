import json
from typing import Any

from openai import OpenAI

from app.config import settings


def resolve_item_name_with_vendor(item_name: str, vendor_name: str | None) -> dict[str, Any]:
    """Resolve a line-item name against vendor context.

    Returns JSON-like dict:
    {
      "found_product": bool,
      "canonical_name": str | None,
      "confidence": float
    }
    """
    raw_name = (item_name or "").strip()
    if not raw_name:
        return {"found_product": False, "canonical_name": None, "confidence": 0.0}

    if not settings.openai_api_key:
        return {"found_product": True, "canonical_name": raw_name, "confidence": 0.0}

    vendor = (vendor_name or "unknown vendor").strip()
    client = OpenAI(api_key=settings.openai_api_key)

    response = client.chat.completions.create(
        model=settings.openai_model,
        response_format={"type": "json_object"},
        messages=[
            {
                "role": "system",
                "content": (
                    "You normalize retail receipt item names using vendor context. "
                    "Return ONLY JSON with keys: found_product:boolean, canonical_name:string|null, confidence:number. "
                    "If the item text is too ambiguous/noisy/unreliable, set found_product=false and canonical_name=null. "
                    "If it appears to be the same product with minor spelling/format issues, return canonical_name as clean title-case name."
                ),
            },
            {
                "role": "user",
                "content": f"vendor={vendor}\nitem_name={raw_name}\nReturn JSON only.",
            },
        ],
    )

    content = response.choices[0].message.content or "{}"
    parsed = json.loads(content)

    found_product = bool(parsed.get("found_product", False))
    canonical_name_raw = parsed.get("canonical_name")
    canonical_name = str(canonical_name_raw).strip() if canonical_name_raw else None
    try:
        confidence = float(parsed.get("confidence", 0.0) or 0.0)
    except (TypeError, ValueError):
        confidence = 0.0
    confidence = max(0.0, min(1.0, confidence))

    if found_product and canonical_name:
        return {
            "found_product": True,
            "canonical_name": canonical_name,
            "confidence": confidence,
        }

    return {"found_product": False, "canonical_name": None, "confidence": confidence}

