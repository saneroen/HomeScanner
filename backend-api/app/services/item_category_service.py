import json
from typing import Final

from openai import OpenAI

from app.config import settings

_ALLOWED_CATEGORIES: Final[list[str]] = [
    "GROCERIES",
    "HOUSEHOLD",
    "PERSONAL_CARE",
    "BABY",
    "PET",
    "PHARMACY",
    "ELECTRONICS",
    "CLOTHING",
    "HOME",
    "OFFICE",
    "OTHER",
]


def categorize_inventory_item(item_name: str, vendor_name: str | None) -> str:
    """Classify an inventory item into a stable category using OpenAI.

    Falls back to OTHER on missing config or model errors.
    """
    safe_name = (item_name or "").strip()
    if not safe_name:
        return "OTHER"
    if not settings.openai_api_key:
        return "OTHER"

    client = OpenAI(api_key=settings.openai_api_key)
    vendor = (vendor_name or "unknown").strip()

    response = client.chat.completions.create(
        model=settings.openai_model,
        response_format={"type": "json_object"},
        messages=[
            {
                "role": "system",
                "content": (
                    "You classify purchase line items into one category. "
                    "Return ONLY JSON like {\"category\":\"...\"}. "
                    f"Allowed categories: {', '.join(_ALLOWED_CATEGORIES)}. "
                    "Choose the single best fit."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"vendor={vendor}\n"
                    f"item_name={safe_name}\n"
                    "Return JSON now."
                ),
            },
        ],
    )

    content = response.choices[0].message.content or "{}"
    payload = json.loads(content)
    category = str(payload.get("category") or "OTHER").upper().strip()
    if category not in _ALLOWED_CATEGORIES:
        return "OTHER"
    return category

