import json
import logging
import os
import re
from pathlib import Path
from uuid import uuid4

from fastapi import Depends, FastAPI, File, HTTPException, UploadFile, status
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.config import settings
from app.db import Base, engine, get_db
from app.models import (
    CorrectionEvent,
    InventoryEvent,
    InventoryItem,
    ModelCalibrationProfile,
    ModelRun,
    PriceOffer,
    Receipt,
    ReceiptItem,
)
from app.schemas import (
    CorrectionEventRead,
    CorrectionEventsWriteRequest,
    CorrectionEventsWriteResponse,
    InventoryEventRead,
    InventoryItemCreate,
    InventoryItemRead,
    InventoryItemUpdate,
    ModelCalibrationRecomputeResponse,
    ModelRunRead,
    PriceOfferCreate,
    PriceOfferRead,
    PriceOfferUpdate,
    ReceiptClassifyRequest,
    ReceiptClassifyResponse,
    ReceiptCreate,
    ReceiptExtractionResult,
    ReceiptExtractedItem,
    ReceiptFieldConfidence,
    ReceiptItemCreate,
    ReceiptItemRead,
    ReceiptItemUpdate,
    ReceiptClassificationResult,
    ReceiptRedactionResult,
    ReceiptRead,
    ReceiptUpdate,
)
from app.services.model_run_utils import to_input_hash
from app.services.item_category_service import categorize_inventory_item
from app.services.item_name_resolution_service import resolve_item_name_with_vendor
from app.services.openai_receipt_service import classify_and_extract_receipt
from app.services.redaction_service import apply_balanced_redaction


app = FastAPI(title=settings.app_name)
logger = logging.getLogger("homescanner.backend")

_MIN_ITEM_NAME_CONFIDENCE = 0.6
_PLACEHOLDER_ITEM_NAMES = {
    "unknown",
    "unknown item",
    "item",
    "product",
    "misc",
    "n/a",
    "na",
    "-",
    ".",
}

_GENERIC_ITEM_NAME_PATTERNS = (
    re.compile(r"^item\s*\d*$", re.IGNORECASE),
    re.compile(r"^product\s*\d*$", re.IGNORECASE),
    re.compile(r"^line\s*item\s*\d*$", re.IGNORECASE),
)


def _is_generic_item_name(name: str) -> bool:
    normalized = name.strip().lower()
    if not normalized:
        return True
    if normalized in _PLACEHOLDER_ITEM_NAMES:
        return True
    return any(pattern.match(normalized) for pattern in _GENERIC_ITEM_NAME_PATTERNS)


def _ensure_receipt_upload_dir() -> Path:
    upload_dir = Path(settings.receipt_upload_dir)
    upload_dir.mkdir(parents=True, exist_ok=True)
    return upload_dir


def _safe_image_ext(filename: str | None, content_type: str | None) -> str:
    if filename and "." in filename:
        ext = filename.rsplit(".", 1)[-1].lower()
        if ext in {"jpg", "jpeg", "png", "webp", "heic", "heif"}:
            return ext
    if content_type == "image/png":
        return "png"
    if content_type == "image/webp":
        return "webp"
    if content_type in {"image/heic", "image/heif"}:
        return "heic"
    return "jpg"


@app.on_event("startup")
def on_startup() -> None:
    Base.metadata.create_all(bind=engine)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "env": settings.app_env}


def _recompute_receipt_total(db: Session, receipt_id: str) -> float:
    total = (
        db.query(func.coalesce(func.sum(ReceiptItem.line_total), 0.0))
        .filter(ReceiptItem.receipt_id == receipt_id, ReceiptItem.deleted == 0)
        .scalar()
    )
    safe_total = round(float(total or 0.0), 2)
    receipt = db.get(Receipt, receipt_id)
    if receipt:
        receipt.total = safe_total
        db.add(receipt)
    return safe_total


def _validate_extraction_payload(extraction: dict) -> tuple[bool, list[str]]:
    issues: list[str] = []
    subtotal = extraction.get("subtotal")
    tax = extraction.get("tax")
    total = extraction.get("total")
    merchant = extraction.get("merchant")
    items = extraction.get("items") or []

    if not merchant:
        issues.append("missing_merchant")
    if not items:
        issues.append("missing_items")

    bad_name_count = 0
    low_name_conf_count = 0
    for row in items:
        raw_name = str((row or {}).get("name") or "").strip()
        normalized_name = raw_name.lower()
        if (
            not raw_name
            or _is_generic_item_name(raw_name)
            or len(raw_name) < 2
            or normalized_name.isnumeric()
        ):
            bad_name_count += 1

        confidence = (row or {}).get("confidence") or {}
        name_conf = _safe_float(confidence.get("name"), default=0.0) or 0.0
        if name_conf < _MIN_ITEM_NAME_CONFIDENCE:
            low_name_conf_count += 1

    if bad_name_count > 0:
        issues.append("invalid_item_names")
    if items and low_name_conf_count > 0:
        issues.append("low_item_name_confidence")

    if subtotal is not None and tax is not None and total is not None:
        expected_total = round(float(subtotal) + float(tax), 2)
        actual_total = round(float(total), 2)
        if abs(expected_total - actual_total) > 0.05:
            issues.append("amount_math_inconsistent")

    # Guardrail for wildly incorrect per-item values vs total receipt value.
    try:
        total_value = float(total) if total is not None else None
    except (TypeError, ValueError):
        total_value = None
    if total_value is not None and total_value > 0:
        huge_line_items = [
            row for row in items if float((row or {}).get("line_total") or 0) > (total_value * 5)
        ]
        if huge_line_items:
            issues.append("line_total_outlier")

    return len(issues) == 0, issues


def _resolve_extracted_item_names(extraction: dict) -> tuple[dict, list[str]]:
    """Normalize item names with vendor context and collect review issues."""
    issues: list[str] = []
    merchant = extraction.get("merchant")
    items = extraction.get("items") or []
    resolved_items: list[dict] = []

    unresolved_count = 0
    for row in items:
        row_copy = dict(row or {})
        current_name = str(row_copy.get("name") or "").strip()

        if not current_name:
            unresolved_count += 1
            resolved_items.append(row_copy)
            continue

        try:
            resolved = resolve_item_name_with_vendor(current_name, merchant)
        except Exception:
            logger.exception(
                "item_name_resolution.failed merchant=%s item_name=%s",
                merchant,
                current_name,
            )
            resolved = {"found_product": True, "canonical_name": current_name, "confidence": 0.0}

        if resolved.get("found_product") and resolved.get("canonical_name"):
            row_copy["name"] = str(resolved.get("canonical_name"))
        else:
            unresolved_count += 1

        resolved_items.append(row_copy)

    if unresolved_count > 0:
        issues.append("unresolved_item_names")

    updated = {**extraction, "items": resolved_items}
    return updated, issues


def _replace_receipt_items_from_extraction(db: Session, receipt: Receipt, extraction: dict) -> None:
    for existing in receipt.items:
        db.delete(existing)
    db.flush()

    for row in extraction.get("items", []):
        qty = float(row.get("qty", 1) or 1)
        unit_price = float(row.get("unit_price", 0) or 0)
        line_total = float(row.get("line_total", round(qty * unit_price, 2)) or round(qty * unit_price, 2))
        item = ReceiptItem(
            receipt_id=receipt.id,
            name=str(row.get("name", "Unknown Item")),
            qty=qty,
            unit_price=unit_price,
            line_total=line_total,
            deleted=0,
        )
        db.add(item)


def _ingest_receipt_into_inventory(db: Session, receipt: Receipt) -> None:
    """Create/update inventory items from receipt items once per receipt."""
    active_items = (
        db.query(ReceiptItem)
        .filter(ReceiptItem.receipt_id == receipt.id, ReceiptItem.deleted == 0)
        .all()
    )
    if not active_items:
        return

    # Idempotency guard: if this receipt has already produced inventory events,
    # do not ingest again.
    existing_event = (
        db.query(InventoryEvent.id)
        .filter(InventoryEvent.receipt_id == receipt.id)
        .first()
    )
    if existing_event:
        return

    for row in active_items:
        normalized_key = row.name.strip().lower()
        inventory_item = (
            db.query(InventoryItem)
            .filter(InventoryItem.normalized_key == normalized_key)
            .first()
        )

        if inventory_item is None:
            inferred_category = "OTHER"
            try:
                inferred_category = categorize_inventory_item(row.name, receipt.title)
            except Exception:
                logger.exception(
                    "inventory.category_inference_failed receipt_id=%s item_name=%s",
                    receipt.id,
                    row.name,
                )
            inventory_item = InventoryItem(
                normalized_key=normalized_key,
                display_name=row.name,
                category=inferred_category,
                purchase_count=0,
            )
            db.add(inventory_item)
            db.flush()

        inventory_item.purchase_count = (inventory_item.purchase_count or 0) + 1
        inventory_item.last_purchase_price = row.unit_price
        inventory_item.last_purchased_at = receipt.updated_at

        event = InventoryEvent(
            item_id=inventory_item.id,
            receipt_id=receipt.id,
            receipt_item_id=row.id,
            quantity=row.qty,
            unit_price=row.unit_price,
            line_total=row.line_total,
        )
        db.add(event)


def _safe_float(value: object, default: float | None = None) -> float | None:
    if value is None:
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


@app.post("/inventory-items", response_model=InventoryItemRead, status_code=status.HTTP_201_CREATED)
def create_inventory_item(payload: InventoryItemCreate, db: Session = Depends(get_db)) -> InventoryItem:
    existing = db.query(InventoryItem).filter(InventoryItem.normalized_key == payload.normalized_key).first()
    if existing:
        raise HTTPException(status_code=409, detail="normalized_key already exists")

    entity = InventoryItem(**payload.model_dump())
    db.add(entity)
    db.commit()
    db.refresh(entity)
    return entity


@app.get("/inventory-items", response_model=list[InventoryItemRead])
def list_inventory_items(db: Session = Depends(get_db)) -> list[InventoryItem]:
    return db.query(InventoryItem).order_by(InventoryItem.display_name.asc()).all()


@app.get("/inventory-items/{item_id}", response_model=InventoryItemRead)
def get_inventory_item(item_id: str, db: Session = Depends(get_db)) -> InventoryItem:
    entity = db.get(InventoryItem, item_id)
    if not entity:
        raise HTTPException(status_code=404, detail="inventory item not found")
    return entity


@app.patch("/inventory-items/{item_id}", response_model=InventoryItemRead)
def update_inventory_item(
    item_id: str,
    payload: InventoryItemUpdate,
    db: Session = Depends(get_db),
) -> InventoryItem:
    entity = db.get(InventoryItem, item_id)
    if not entity:
        raise HTTPException(status_code=404, detail="inventory item not found")

    for key, value in payload.model_dump(exclude_unset=True).items():
        setattr(entity, key, value)

    db.add(entity)
    db.commit()
    db.refresh(entity)
    return entity


@app.delete("/inventory-items/{item_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_inventory_item(item_id: str, db: Session = Depends(get_db)) -> None:
    entity = db.get(InventoryItem, item_id)
    if not entity:
        raise HTTPException(status_code=404, detail="inventory item not found")

    db.delete(entity)
    db.commit()


@app.post("/price-offers", response_model=PriceOfferRead, status_code=status.HTTP_201_CREATED)
def create_price_offer(payload: PriceOfferCreate, db: Session = Depends(get_db)) -> PriceOffer:
    item = db.get(InventoryItem, payload.item_id)
    if not item:
        raise HTTPException(status_code=404, detail="inventory item not found")

    entity = PriceOffer(**payload.model_dump())
    db.add(entity)
    db.commit()
    db.refresh(entity)
    return entity


@app.get("/price-offers", response_model=list[PriceOfferRead])
def list_price_offers(item_id: str | None = None, db: Session = Depends(get_db)) -> list[PriceOffer]:
    query = db.query(PriceOffer)
    if item_id:
        query = query.filter(PriceOffer.item_id == item_id)
    return query.order_by(PriceOffer.captured_at.desc()).all()


@app.get("/price-offers/{offer_id}", response_model=PriceOfferRead)
def get_price_offer(offer_id: str, db: Session = Depends(get_db)) -> PriceOffer:
    entity = db.get(PriceOffer, offer_id)
    if not entity:
        raise HTTPException(status_code=404, detail="price offer not found")
    return entity


@app.patch("/price-offers/{offer_id}", response_model=PriceOfferRead)
def update_price_offer(
    offer_id: str,
    payload: PriceOfferUpdate,
    db: Session = Depends(get_db),
) -> PriceOffer:
    entity = db.get(PriceOffer, offer_id)
    if not entity:
        raise HTTPException(status_code=404, detail="price offer not found")

    for key, value in payload.model_dump(exclude_unset=True).items():
        setattr(entity, key, value)

    db.add(entity)
    db.commit()
    db.refresh(entity)
    return entity


@app.delete("/price-offers/{offer_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_price_offer(offer_id: str, db: Session = Depends(get_db)) -> None:
    entity = db.get(PriceOffer, offer_id)
    if not entity:
        raise HTTPException(status_code=404, detail="price offer not found")

    db.delete(entity)
    db.commit()


@app.post("/receipts", response_model=ReceiptRead, status_code=status.HTTP_201_CREATED)
def create_receipt(payload: ReceiptCreate, db: Session = Depends(get_db)) -> Receipt:
    receipt = Receipt(**payload.model_dump())
    db.add(receipt)
    db.commit()
    db.refresh(receipt)
    return receipt


@app.get("/receipts", response_model=list[ReceiptRead])
def list_receipts(db: Session = Depends(get_db)) -> list[Receipt]:
    return db.query(Receipt).order_by(Receipt.created_at.desc()).all()


@app.get("/receipts/{receipt_id}", response_model=ReceiptRead)
def get_receipt(receipt_id: str, db: Session = Depends(get_db)) -> Receipt:
    receipt = db.get(Receipt, receipt_id)
    if not receipt:
        raise HTTPException(status_code=404, detail="receipt not found")
    return receipt


@app.patch("/receipts/{receipt_id}", response_model=ReceiptRead)
def update_receipt(receipt_id: str, payload: ReceiptUpdate, db: Session = Depends(get_db)) -> Receipt:
    receipt = db.get(Receipt, receipt_id)
    if not receipt:
        raise HTTPException(status_code=404, detail="receipt not found")

    for key, value in payload.model_dump(exclude_unset=True).items():
        setattr(receipt, key, value)

    db.add(receipt)
    db.commit()
    db.refresh(receipt)
    return receipt


@app.delete("/receipts/{receipt_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_receipt(receipt_id: str, db: Session = Depends(get_db)) -> None:
    receipt = db.get(Receipt, receipt_id)
    if not receipt:
        raise HTTPException(status_code=404, detail="receipt not found")

    db.delete(receipt)
    db.commit()


@app.post("/receipts/{receipt_id}/image", response_model=ReceiptRead)
async def upload_receipt_image(
    receipt_id: str,
    image: UploadFile = File(...),
    db: Session = Depends(get_db),
) -> Receipt:
    receipt = db.get(Receipt, receipt_id)
    if not receipt:
        raise HTTPException(status_code=404, detail="receipt not found")
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="uploaded file must be an image")

    upload_root = _ensure_receipt_upload_dir()
    receipt_dir = upload_root / receipt_id
    receipt_dir.mkdir(parents=True, exist_ok=True)

    ext = _safe_image_ext(image.filename, image.content_type)
    file_name = f"{uuid4().hex}.{ext}"
    file_path = receipt_dir / file_name

    content = await image.read()
    if not content:
        raise HTTPException(status_code=400, detail="uploaded image is empty")

    with open(file_path, "wb") as f:
        f.write(content)

    relative_path = os.path.relpath(file_path, upload_root).replace("\\", "/")
    receipt.image_uri = f"local://{relative_path}"
    receipt.status = "PROCESSING"
    db.add(receipt)
    db.commit()
    db.refresh(receipt)
    logger.info("receipt.image_uploaded receipt_id=%s image_uri=%s", receipt_id, receipt.image_uri)
    return receipt


@app.post("/receipts/{receipt_id}/items", response_model=ReceiptItemRead, status_code=status.HTTP_201_CREATED)
def add_receipt_item(
    receipt_id: str,
    payload: ReceiptItemCreate,
    db: Session = Depends(get_db),
) -> ReceiptItem:
    receipt = db.get(Receipt, receipt_id)
    if not receipt:
        raise HTTPException(status_code=404, detail="receipt not found")

    item = ReceiptItem(receipt_id=receipt_id, **payload.model_dump())
    db.add(item)
    db.flush()
    _recompute_receipt_total(db, receipt_id)
    db.commit()
    db.refresh(item)
    return item


@app.patch("/receipts/{receipt_id}/items/{item_id}", response_model=ReceiptItemRead)
def update_receipt_item(
    receipt_id: str,
    item_id: str,
    payload: ReceiptItemUpdate,
    db: Session = Depends(get_db),
) -> ReceiptItem:
    item = db.get(ReceiptItem, item_id)
    if not item or item.receipt_id != receipt_id:
        raise HTTPException(status_code=404, detail="receipt item not found")

    for key, value in payload.model_dump(exclude_unset=True).items():
        setattr(item, key, value)

    db.add(item)
    db.flush()
    _recompute_receipt_total(db, receipt_id)
    db.commit()
    db.refresh(item)
    return item


@app.delete("/receipts/{receipt_id}/items/{item_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_receipt_item(receipt_id: str, item_id: str, db: Session = Depends(get_db)) -> None:
    item = db.get(ReceiptItem, item_id)
    if not item or item.receipt_id != receipt_id:
        raise HTTPException(status_code=404, detail="receipt item not found")
    db.delete(item)
    db.flush()
    _recompute_receipt_total(db, receipt_id)
    db.commit()


@app.post("/receipts/{receipt_id}/confirm", response_model=ReceiptRead)
def confirm_receipt(receipt_id: str, db: Session = Depends(get_db)) -> Receipt:
    receipt = db.get(Receipt, receipt_id)
    if not receipt:
        raise HTTPException(status_code=404, detail="receipt not found")

    _recompute_receipt_total(db, receipt_id)
    _ingest_receipt_into_inventory(db, receipt)

    receipt.status = "CONFIRMED"
    db.add(receipt)
    db.commit()
    db.refresh(receipt)
    return receipt


@app.post("/receipts/{receipt_id}/classify", response_model=ReceiptClassifyResponse)
def classify_receipt(
    receipt_id: str,
    payload: ReceiptClassifyRequest | None = None,
    db: Session = Depends(get_db),
) -> ReceiptClassifyResponse:
    logger.info("classify_receipt.start receipt_id=%s", receipt_id)
    receipt = db.get(Receipt, receipt_id)
    if not receipt:
        logger.warning("classify_receipt.missing_receipt receipt_id=%s", receipt_id)
        raise HTTPException(status_code=404, detail="receipt not found")

    model_version = payload.model_version if payload else settings.openai_model
    latency_ms = payload.latency_ms if payload else 0

    if payload and payload.classification and payload.extraction:
        logger.info("classify_receipt.source=client_payload receipt_id=%s", receipt_id)
        classification_payload = payload.classification.model_dump()
        extraction_payload = payload.extraction.model_dump()
    else:
        try:
            logger.info(
                "classify_receipt.source=openai receipt_id=%s image_uri_present=%s model=%s",
                receipt_id,
                bool(receipt.image_uri),
                settings.openai_model,
            )
            classification_payload, extraction_payload, inferred_latency_ms = classify_and_extract_receipt(receipt.image_uri)
            latency_ms = inferred_latency_ms
            model_version = settings.openai_model
            logger.info(
                "classify_receipt.openai_ok receipt_id=%s latency_ms=%s is_receipt=%s confidence=%.3f items=%s",
                receipt_id,
                latency_ms,
                classification_payload.get("is_receipt"),
                float(classification_payload.get("confidence", 0.0) or 0.0),
                len(extraction_payload.get("items") or []),
            )
        except Exception:
            logger.exception("classify_receipt.openai_error receipt_id=%s", receipt_id)
            classification_payload = {
                "is_receipt": False,
                "confidence": 0.0,
                "reason_codes": ["model_error"],
            }
            extraction_payload = {
                "merchant": None,
                "date": None,
                "subtotal": None,
                "tax": None,
                "total": None,
                "items": [],
                "sensitive": {},
            }

    classification = ReceiptClassificationResult(
        is_receipt=bool(classification_payload.get("is_receipt", False)),
        confidence=float(classification_payload.get("confidence", 0.0)),
        reason_codes=list(classification_payload.get("reason_codes", [])),
    )

    masked_sensitive, masked_fields = apply_balanced_redaction(extraction_payload.get("sensitive"))
    redaction = ReceiptRedactionResult(
        policy="BALANCED",
        secret_fields_count=len(masked_fields),
        masked_fields=masked_fields,
    )

    classification_run = ModelRun(
        receipt_id=receipt_id,
        task_type="receipt_classification",
        model_version=model_version,
        input_hash=to_input_hash({"receipt_id": receipt_id, "image_uri": receipt.image_uri, "force": payload.force if payload else False}),
        output_json=json.dumps(classification_payload),
        confidence=classification.confidence,
        latency_ms=latency_ms,
    )
    db.add(classification_run)

    if classification.is_receipt:
        extraction_run = ModelRun(
            receipt_id=receipt_id,
            task_type="receipt_extraction",
            model_version=model_version,
            input_hash=to_input_hash({"receipt_id": receipt_id, "mode": "extract"}),
            output_json=json.dumps({**extraction_payload, "sensitive": masked_sensitive}),
            confidence=classification.confidence,
            latency_ms=latency_ms,
        )
        db.add(extraction_run)

    resolution_issues: list[str] = []
    if classification.is_receipt:
        extraction_payload, resolution_issues = _resolve_extracted_item_names(extraction_payload)
        accepted, issues = _validate_extraction_payload(extraction_payload)
        for issue in resolution_issues:
            if issue not in issues:
                issues.append(issue)
        accepted = accepted and not resolution_issues
    else:
        accepted, issues = (False, ["classified_non_receipt"])
    logger.info(
        "classify_receipt.validation receipt_id=%s accepted=%s issues=%s",
        receipt_id,
        accepted,
        issues,
    )

    receipt.classification_label = "CLASSIFIED_RECEIPT" if classification.is_receipt else "CLASSIFIED_NON_RECEIPT"
    receipt.classification_confidence = classification.confidence
    receipt.ingest_confidence = classification.confidence
    receipt.ingest_status_reason = ",".join(issues) if issues else None
    receipt.source_type = receipt.source_type or "manual"
    if not classification.is_receipt:
        receipt.status = "FAILED"
    elif accepted:
        receipt.status = "CONFIRMED"
    else:
        receipt.status = "NEEDS_REVIEW"

    logger.info(
        "classify_receipt.status receipt_id=%s status=%s merchant=%s total=%s",
        receipt_id,
        receipt.status,
        extraction_payload.get("merchant"),
        extraction_payload.get("total"),
    )

    extraction = ReceiptExtractionResult(
        merchant=extraction_payload.get("merchant"),
        date=extraction_payload.get("date"),
        subtotal=_safe_float(extraction_payload.get("subtotal")),
        tax=_safe_float(extraction_payload.get("tax")),
        total=_safe_float(extraction_payload.get("total")),
        items=[
            ReceiptExtractedItem(
                name=str(item.get("name", "Unknown Item")),
                qty=float(item.get("qty", 1) or 1),
                unit_price=float(item.get("unit_price", 0) or 0),
                line_total=float(item.get("line_total", 0) or 0),
                confidence=ReceiptFieldConfidence(
                    name=float((item.get("confidence") or {}).get("name", 0) or 0),
                    qty=float((item.get("confidence") or {}).get("qty", 0) or 0),
                    unit_price=float((item.get("confidence") or {}).get("unit_price", 0) or 0),
                    line_total=float((item.get("confidence") or {}).get("line_total", 0) or 0),
                ),
            )
            for item in extraction_payload.get("items", [])
        ],
    )

    if classification.is_receipt and accepted:
        if extraction.merchant:
            receipt.title = extraction.merchant
        _replace_receipt_items_from_extraction(db, receipt, extraction_payload)
        db.flush()
        if extraction.total is not None:
            receipt.total = extraction.total
        else:
            _recompute_receipt_total(db, receipt_id)
        _ingest_receipt_into_inventory(db, receipt)

    db.add(receipt)
    db.commit()

    logger.info("classify_receipt.done receipt_id=%s status=%s", receipt_id, receipt.status)

    return ReceiptClassifyResponse(
        receipt_id=receipt_id,
        classification=classification,
        extraction=extraction,
        redaction=redaction,
        validator={"accepted": accepted, "issues": issues},
        status=receipt.status,
    )


@app.get("/receipts/{receipt_id}/classification", response_model=ReceiptClassifyResponse)
def get_receipt_classification(receipt_id: str, db: Session = Depends(get_db)) -> ReceiptClassifyResponse:
    receipt = db.get(Receipt, receipt_id)
    if not receipt:
        raise HTTPException(status_code=404, detail="receipt not found")

    latest_classification = (
        db.query(ModelRun)
        .filter(ModelRun.receipt_id == receipt_id, ModelRun.task_type == "receipt_classification")
        .order_by(ModelRun.created_at.desc())
        .first()
    )
    latest_extraction = (
        db.query(ModelRun)
        .filter(ModelRun.receipt_id == receipt_id, ModelRun.task_type == "receipt_extraction")
        .order_by(ModelRun.created_at.desc())
        .first()
    )

    classification_payload = json.loads(latest_classification.output_json) if latest_classification else {}
    extraction_payload = json.loads(latest_extraction.output_json) if latest_extraction else {}

    classification = ReceiptClassificationResult(
        is_receipt=bool(classification_payload.get("is_receipt", receipt.classification_label == "CLASSIFIED_RECEIPT")),
        confidence=float(classification_payload.get("confidence", receipt.classification_confidence or 0.0)),
        reason_codes=list(classification_payload.get("reason_codes", [])),
    )

    extraction = ReceiptExtractionResult(
        merchant=extraction_payload.get("merchant", receipt.title),
        date=extraction_payload.get("date"),
        subtotal=_safe_float(extraction_payload.get("subtotal")),
        tax=_safe_float(extraction_payload.get("tax")),
        total=_safe_float(extraction_payload.get("total"), receipt.total),
        items=[
            ReceiptExtractedItem(
                name=str(item.get("name", "Unknown Item")),
                qty=float(item.get("qty", 1) or 1),
                unit_price=float(item.get("unit_price", 0) or 0),
                line_total=float(item.get("line_total", 0) or 0),
                confidence=ReceiptFieldConfidence(
                    name=float((item.get("confidence") or {}).get("name", 0) or 0),
                    qty=float((item.get("confidence") or {}).get("qty", 0) or 0),
                    unit_price=float((item.get("confidence") or {}).get("unit_price", 0) or 0),
                    line_total=float((item.get("confidence") or {}).get("line_total", 0) or 0),
                ),
            )
            for item in extraction_payload.get("items", [])
        ],
    )

    masked_fields = list((extraction_payload.get("sensitive") or {}).keys())
    return ReceiptClassifyResponse(
        receipt_id=receipt_id,
        classification=classification,
        extraction=extraction,
        redaction=ReceiptRedactionResult(policy="BALANCED", secret_fields_count=len(masked_fields), masked_fields=masked_fields),
        validator={"accepted": receipt.ingest_status_reason is None, "issues": [receipt.ingest_status_reason] if receipt.ingest_status_reason else []},
        status=receipt.status,
    )


@app.post("/receipts/{receipt_id}/corrections", response_model=CorrectionEventsWriteResponse)
def add_correction_events(
    receipt_id: str,
    payload: CorrectionEventsWriteRequest,
    db: Session = Depends(get_db),
) -> CorrectionEventsWriteResponse:
    receipt = db.get(Receipt, receipt_id)
    if not receipt:
        raise HTTPException(status_code=404, detail="receipt not found")

    inserted = 0
    for event in payload.events:
        row = CorrectionEvent(
            receipt_id=receipt_id,
            field_path=event.field_path,
            old_value=event.old_value,
            new_value=event.new_value,
            confidence_before=event.confidence_before,
            user_action=event.user_action,
        )
        db.add(row)
        inserted += 1

    db.commit()
    return CorrectionEventsWriteResponse(receipt_id=receipt_id, stored=inserted)


@app.get("/receipts/{receipt_id}/corrections", response_model=list[CorrectionEventRead])
def list_correction_events(receipt_id: str, db: Session = Depends(get_db)) -> list[CorrectionEvent]:
    receipt = db.get(Receipt, receipt_id)
    if not receipt:
        raise HTTPException(status_code=404, detail="receipt not found")

    return (
        db.query(CorrectionEvent)
        .filter(CorrectionEvent.receipt_id == receipt_id)
        .order_by(CorrectionEvent.created_at.desc())
        .all()
    )


@app.post("/model-calibration/recompute", response_model=ModelCalibrationRecomputeResponse)
def recompute_model_calibration(db: Session = Depends(get_db)) -> ModelCalibrationRecomputeResponse:
    corrections = db.query(CorrectionEvent).all()
    grouped: dict[tuple[str, str], int] = {}

    for correction in corrections:
        merchant_key = "global"
        field_name = correction.field_path.split(".")[-1]
        grouped[(merchant_key, field_name)] = grouped.get((merchant_key, field_name), 0) + 1

    upserts = 0
    for (merchant_key, field_name), count in grouped.items():
        profile = (
            db.query(ModelCalibrationProfile)
            .filter(
                ModelCalibrationProfile.merchant_key == merchant_key,
                ModelCalibrationProfile.field_name == field_name,
            )
            .first()
        )
        threshold = max(0.5, 0.95 - min(0.4, count * 0.01))
        if profile is None:
            profile = ModelCalibrationProfile(
                merchant_key=merchant_key,
                field_name=field_name,
                threshold=threshold,
                sample_size=count,
            )
            db.add(profile)
        else:
            profile.threshold = threshold
            profile.sample_size = count
            db.add(profile)
        upserts += 1

    db.commit()
    return ModelCalibrationRecomputeResponse(recalculated_profiles=upserts)


@app.get("/model-runs/{run_id}", response_model=ModelRunRead)
def get_model_run(run_id: str, db: Session = Depends(get_db)) -> ModelRun:
    run = db.get(ModelRun, run_id)
    if not run:
        raise HTTPException(status_code=404, detail="model run not found")
    return run


@app.get("/inventory-items/{item_id}/events", response_model=list[InventoryEventRead])
def list_inventory_events(item_id: str, db: Session = Depends(get_db)) -> list[InventoryEvent]:
    item = db.get(InventoryItem, item_id)
    if not item:
        raise HTTPException(status_code=404, detail="inventory item not found")
    return (
        db.query(InventoryEvent)
        .filter(InventoryEvent.item_id == item_id)
        .order_by(InventoryEvent.occurred_at.desc())
        .all()
    )
