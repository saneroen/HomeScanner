# HomeScanner Backend API (FastAPI + PostgreSQL)

This backend is a separate service in [`backend-api/`](backend-api/) for inventory and market price data.

## Stack

- FastAPI
- SQLAlchemy 2.x
- PostgreSQL (via psycopg)

## Quick start

1. Create and activate a virtual environment.
2. Install dependencies:

```bash
pip install -r requirements.txt
```

3. Configure environment variables:

```bash
cp .env.example .env
```

4. Ensure PostgreSQL is running and the database in `DATABASE_URL` exists.

5. Run the API:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## Run with Docker Compose

From [`backend-api/`](backend-api/):

1. Create local env file (first time only):

```bash
cp .env.example .env
```

2. Open [`.env`](backend-api/.env) and set a valid OpenAI key:

```env
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4.1
OPENAI_RECEIPT_PASSES=3
RECEIPT_UPLOAD_DIR=/app/data/receipts
```

3. Start services:

```bash
docker compose up -d --build
```

4. Check container status:
 
```bash
docker compose ps
```

5. Tail backend logs:

```bash
docker compose logs -f api
```

This starts:
- PostgreSQL on `localhost:5432`
- FastAPI on `localhost:8000`

Stop services:

```bash
docker compose down
```

## Endpoints

- `GET /health`
- `POST /inventory-items`
- `GET /inventory-items`
- `GET /inventory-items/{item_id}`
- `PATCH /inventory-items/{item_id}`
- `DELETE /inventory-items/{item_id}`
- `POST /price-offers`
- `GET /price-offers?item_id=...`
- `GET /price-offers/{offer_id}`
- `PATCH /price-offers/{offer_id}`
- `DELETE /price-offers/{offer_id}`
- `POST /receipts`
- `GET /receipts`
- `GET /receipts/{receipt_id}`
- `PATCH /receipts/{receipt_id}`
- `POST /receipts/{receipt_id}/image` (multipart image upload)
- `POST /receipts/{receipt_id}/classify`
- `GET /receipts/{receipt_id}/classification`

## Notes

- Tables are created on startup via [`on_startup()`](backend-api/app/main.py:81).
- PostgreSQL connection is configured in [`Settings`](backend-api/app/config.py:4).
- Receipt image uploads are stored in [`receipt_upload_dir`](backend-api/app/config.py:12), defaulting to `/app/data/receipts`.
- In Docker Compose, receipt uploads persist through the `receipt_images` volume mounted at `/app/data/receipts`.
- Mobile flow for scans should be: create receipt -> upload image via multipart -> classify.
