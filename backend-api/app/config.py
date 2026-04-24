from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "HomeScanner Backend API"
    app_env: str = "development"
    app_host: str = "0.0.0.0"
    app_port: int = 8000
    database_url: str = "postgresql+psycopg://postgres:postgres@localhost:5432/homescanner"
    openai_api_key: str | None = None
    openai_model: str = "gpt-4.1"
    openai_receipt_passes: int = 3
    receipt_upload_dir: str = "/app/data/receipts"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
