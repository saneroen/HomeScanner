from datetime import datetime

from pydantic import BaseModel, Field


class InventoryItemBase(BaseModel):
    normalized_key: str = Field(min_length=1, max_length=255)
    display_name: str = Field(min_length=1, max_length=255)
    category: str = Field(default="OTHER", max_length=100)
    purchase_count: int = Field(default=0, ge=0)
    last_purchase_price: float | None = Field(default=None, ge=0)
    last_purchased_at: datetime | None = None


class InventoryItemCreate(InventoryItemBase):
    pass


class InventoryItemUpdate(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=255)
    category: str | None = Field(default=None, max_length=100)
    purchase_count: int | None = Field(default=None, ge=0)
    last_purchase_price: float | None = Field(default=None, ge=0)
    last_purchased_at: datetime | None = None


class InventoryItemRead(InventoryItemBase):
    id: str
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class PriceOfferBase(BaseModel):
    vendor: str = Field(min_length=1, max_length=255)
    source_url: str = Field(min_length=1)
    listed_price: float = Field(gt=0)
    normalized_unit_price: float = Field(gt=0)
    currency: str = Field(default="USD", max_length=10)
    confidence_score: float = Field(default=0.0, ge=0, le=1)


class PriceOfferCreate(PriceOfferBase):
    item_id: str


class PriceOfferUpdate(BaseModel):
    vendor: str | None = Field(default=None, min_length=1, max_length=255)
    source_url: str | None = Field(default=None, min_length=1)
    listed_price: float | None = Field(default=None, gt=0)
    normalized_unit_price: float | None = Field(default=None, gt=0)
    currency: str | None = Field(default=None, max_length=10)
    confidence_score: float | None = Field(default=None, ge=0, le=1)


class PriceOfferRead(PriceOfferBase):
    id: str
    item_id: str
    captured_at: datetime

    model_config = {"from_attributes": True}


class ReceiptItemBase(BaseModel):
    name: str = Field(min_length=1, max_length=255)
    qty: float = Field(default=1, gt=0)
    unit_price: float = Field(default=0, ge=0)
    line_total: float = Field(default=0, ge=0)
    deleted: int = Field(default=0, ge=0, le=1)


class ReceiptItemCreate(ReceiptItemBase):
    pass


class ReceiptItemUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=255)
    qty: float | None = Field(default=None, gt=0)
    unit_price: float | None = Field(default=None, ge=0)
    line_total: float | None = Field(default=None, ge=0)
    deleted: int | None = Field(default=None, ge=0, le=1)


class ReceiptItemRead(ReceiptItemBase):
    id: str
    receipt_id: str
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class ReceiptBase(BaseModel):
    title: str = Field(default="Receipt", max_length=255)
    status: str = Field(default="CAPTURED_RAW", max_length=50)
    image_uri: str | None = None
    total: float | None = Field(default=None, ge=0)
    source_type: str = Field(default="manual", max_length=30)
    source_ref: str | None = None
    ingest_confidence: float | None = Field(default=None, ge=0, le=1)
    ingest_status_reason: str | None = Field(default=None, max_length=255)
    classification_label: str = Field(default="UNCLASSIFIED", max_length=30)
    classification_confidence: float | None = Field(default=None, ge=0, le=1)


class ReceiptCreate(ReceiptBase):
    pass


class ReceiptUpdate(BaseModel):
    title: str | None = Field(default=None, max_length=255)
    status: str | None = Field(default=None, max_length=50)
    image_uri: str | None = None
    total: float | None = Field(default=None, ge=0)


class ReceiptRead(ReceiptBase):
    id: str
    created_at: datetime
    updated_at: datetime
    items: list[ReceiptItemRead] = []

    model_config = {"from_attributes": True}


class InventoryEventRead(BaseModel):
    id: str
    item_id: str
    receipt_id: str | None
    receipt_item_id: str | None
    quantity: float
    unit_price: float
    line_total: float
    occurred_at: datetime

    model_config = {"from_attributes": True}


class ReceiptFieldConfidence(BaseModel):
    name: float = Field(default=0, ge=0, le=1)
    qty: float = Field(default=0, ge=0, le=1)
    unit_price: float = Field(default=0, ge=0, le=1)
    line_total: float = Field(default=0, ge=0, le=1)


class ReceiptExtractedItem(BaseModel):
    name: str
    qty: float
    unit_price: float
    line_total: float
    confidence: ReceiptFieldConfidence


class ReceiptClassificationResult(BaseModel):
    is_receipt: bool
    confidence: float = Field(ge=0, le=1)
    reason_codes: list[str] = []


class ReceiptExtractionResult(BaseModel):
    merchant: str | None = None
    date: str | None = None
    subtotal: float | None = None
    tax: float | None = None
    total: float | None = None
    items: list[ReceiptExtractedItem] = []
    sensitive: dict[str, str] = {}


class ReceiptClassifyRequest(BaseModel):
    force: bool = False
    model_version: str = "openai-vision"
    latency_ms: int = 0
    classification: ReceiptClassificationResult | None = None
    extraction: ReceiptExtractionResult | None = None

    model_config = {"protected_namespaces": ()}


class ReceiptRedactionResult(BaseModel):
    policy: str = "BALANCED"
    secret_fields_count: int = 0
    masked_fields: list[str] = []


class ReceiptClassifyResponse(BaseModel):
    receipt_id: str
    classification: ReceiptClassificationResult
    extraction: ReceiptExtractionResult
    redaction: ReceiptRedactionResult
    validator: dict[str, object]
    status: str


class CorrectionEventWrite(BaseModel):
    field_path: str = Field(min_length=1, max_length=255)
    old_value: str
    new_value: str
    confidence_before: float | None = Field(default=None, ge=0, le=1)
    user_action: str = Field(default="manual_edit", max_length=50)


class CorrectionEventsWriteRequest(BaseModel):
    events: list[CorrectionEventWrite] = []


class CorrectionEventsWriteResponse(BaseModel):
    receipt_id: str
    stored: int


class CorrectionEventRead(CorrectionEventWrite):
    id: str
    receipt_id: str
    created_at: datetime

    model_config = {"from_attributes": True}


class ModelRunRead(BaseModel):
    id: str
    receipt_id: str
    task_type: str
    model_version: str
    input_hash: str
    output_json: str
    confidence: float
    latency_ms: int
    error_code: str | None
    created_at: datetime

    model_config = {"from_attributes": True, "protected_namespaces": ()}


class ModelCalibrationRecomputeResponse(BaseModel):
    recalculated_profiles: int
