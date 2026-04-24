from __future__ import annotations


def mask_value(value: str, keep_last: int = 4) -> str:
    if len(value) <= keep_last:
        return "*" * len(value)
    return "*" * (len(value) - keep_last) + value[-keep_last:]


def apply_balanced_redaction(sensitive: dict[str, str] | None) -> tuple[dict[str, str], list[str]]:
    if not sensitive:
        return {}, []

    masked: dict[str, str] = {}
    masked_fields: list[str] = []
    for key, value in sensitive.items():
        masked[key] = mask_value(str(value))
        masked_fields.append(key)
    return masked, masked_fields

