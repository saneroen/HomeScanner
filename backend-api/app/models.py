from datetime import datetime
from uuid import uuid4

from sqlalchemy import DateTime, Float, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


class InventoryItem(Base):
    __tablename__ = "inventory_items"

    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=lambda: str(uuid4()))
    normalized_key: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    display_name: Mapped[str] = mapped_column(String(255), index=True)
    category: Mapped[str] = mapped_column(String(100), index=True, default="OTHER")
    purchase_count: Mapped[int] = mapped_column(Integer, default=0)
    last_purchase_price: Mapped[float | None] = mapped_column(Float, nullable=True)
    last_purchased_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    offers: Mapped[list["PriceOffer"]] = relationship(
        "PriceOffer", back_populates="item", cascade="all, delete-orphan"
    )
    events: Mapped[list["InventoryEvent"]] = relationship(
        "InventoryEvent", back_populates="item", cascade="all, delete-orphan"
    )


class PriceOffer(Base):
    __tablename__ = "price_offers"

    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=lambda: str(uuid4()))
    item_id: Mapped[str] = mapped_column(String(64), ForeignKey("inventory_items.id", ondelete="CASCADE"), index=True)
    vendor: Mapped[str] = mapped_column(String(255), index=True)
    source_url: Mapped[str] = mapped_column(Text)
    listed_price: Mapped[float] = mapped_column(Float)
    normalized_unit_price: Mapped[float] = mapped_column(Float, index=True)
    currency: Mapped[str] = mapped_column(String(10), default="USD")
    confidence_score: Mapped[float] = mapped_column(Float, default=0.0)
    captured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), index=True)

    item: Mapped[InventoryItem] = relationship("InventoryItem", back_populates="offers")


class Receipt(Base):
    __tablename__ = "receipts"

    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=lambda: str(uuid4()))
    title: Mapped[str] = mapped_column(String(255), default="Receipt")
    status: Mapped[str] = mapped_column(String(50), index=True, default="CAPTURED_RAW")
    image_uri: Mapped[str | None] = mapped_column(Text, nullable=True)
    total: Mapped[float | None] = mapped_column(Float, nullable=True)
    source_type: Mapped[str] = mapped_column(String(30), default="manual", index=True)
    source_ref: Mapped[str | None] = mapped_column(String(255), nullable=True)
    ingest_confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    ingest_status_reason: Mapped[str | None] = mapped_column(String(255), nullable=True)
    classification_label: Mapped[str] = mapped_column(String(30), default="UNCLASSIFIED", index=True)
    classification_confidence: Mapped[float | None] = mapped_column(Float, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), index=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    items: Mapped[list["ReceiptItem"]] = relationship(
        "ReceiptItem", back_populates="receipt", cascade="all, delete-orphan"
    )


class ReceiptItem(Base):
    __tablename__ = "receipt_items"

    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=lambda: str(uuid4()))
    receipt_id: Mapped[str] = mapped_column(String(64), ForeignKey("receipts.id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(255), index=True)
    qty: Mapped[float] = mapped_column(Float, default=1.0)
    unit_price: Mapped[float] = mapped_column(Float, default=0.0)
    line_total: Mapped[float] = mapped_column(Float, default=0.0)
    deleted: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    receipt: Mapped[Receipt] = relationship("Receipt", back_populates="items")


class InventoryEvent(Base):
    __tablename__ = "inventory_events"

    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=lambda: str(uuid4()))
    item_id: Mapped[str] = mapped_column(
        String(64), ForeignKey("inventory_items.id", ondelete="CASCADE"), index=True
    )
    receipt_id: Mapped[str | None] = mapped_column(String(64), ForeignKey("receipts.id", ondelete="SET NULL"), nullable=True)
    receipt_item_id: Mapped[str | None] = mapped_column(String(64), ForeignKey("receipt_items.id", ondelete="SET NULL"), nullable=True)
    quantity: Mapped[float] = mapped_column(Float, default=1.0)
    unit_price: Mapped[float] = mapped_column(Float, default=0.0)
    line_total: Mapped[float] = mapped_column(Float, default=0.0)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), index=True)

    item: Mapped[InventoryItem] = relationship("InventoryItem", back_populates="events")


class ModelRun(Base):
    __tablename__ = "model_runs"

    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=lambda: str(uuid4()))
    receipt_id: Mapped[str] = mapped_column(String(64), ForeignKey("receipts.id", ondelete="CASCADE"), index=True)
    task_type: Mapped[str] = mapped_column(String(100), index=True)
    model_version: Mapped[str] = mapped_column(String(100), default="receipt-0.6b-v1")
    input_hash: Mapped[str] = mapped_column(String(128), index=True)
    output_json: Mapped[str] = mapped_column(Text)
    confidence: Mapped[float] = mapped_column(Float, default=0.0)
    latency_ms: Mapped[int] = mapped_column(Integer, default=0)
    error_code: Mapped[str | None] = mapped_column(String(100), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), index=True)


class CorrectionEvent(Base):
    __tablename__ = "correction_events"

    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=lambda: str(uuid4()))
    receipt_id: Mapped[str] = mapped_column(String(64), ForeignKey("receipts.id", ondelete="CASCADE"), index=True)
    field_path: Mapped[str] = mapped_column(String(255), index=True)
    old_value: Mapped[str] = mapped_column(Text)
    new_value: Mapped[str] = mapped_column(Text)
    confidence_before: Mapped[float | None] = mapped_column(Float, nullable=True)
    user_action: Mapped[str] = mapped_column(String(50), default="manual_edit")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), index=True)


class ModelCalibrationProfile(Base):
    __tablename__ = "model_calibration_profiles"

    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=lambda: str(uuid4()))
    merchant_key: Mapped[str] = mapped_column(String(255), index=True)
    field_name: Mapped[str] = mapped_column(String(100), index=True)
    threshold: Mapped[float] = mapped_column(Float, default=0.7)
    sample_size: Mapped[int] = mapped_column(Integer, default=0)
    last_updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
