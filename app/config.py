import os
import logging
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    PROJECT_NAME: str = "SIH Smart Scrap & E-Waste Valuation Engine"
    VERSION: str = "2.2.0"
    API_V1_STR: str = "/api/v1"
    
    # Security & JWT Tokens
    SECRET_KEY: str = os.getenv("SECRET_KEY", "sih2026-cpcb-enterprise-scrap-auth-token-998877")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7  # 7 days
    
    # Database Configuration
    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///./scrap_system.db")
    
    # Circular Economy Business Constraints
    DEFAULT_MARGIN: float = 0.15          # 15% platform / aggregator margin
    BASE_PICKUP_FEE_INR: float = 20.0     # Flat initial logistics charge
    PER_KM_CHARGE_INR: float = 5.0        # Haulage rate per km
    MIN_PRICE_FLOOR_INR: float = 10.0     # Statutory minimum payout floor
    
    # Offline Voice & AI Configuration
    VOSK_MODEL_PATH_HI: str = os.getenv("VOSK_MODEL_PATH_HI", "models/vosk-model-small-hi-0.22")
    VOSK_MODEL_PATH_MR: str = os.getenv("VOSK_MODEL_PATH_MR", "models/vosk-model-small-mr-0.22")
    VISION_MODEL_PATH: str = os.getenv("VISION_MODEL_PATH", "models/mobilenetv3_scrap_v1.onnx")
    
    # Audio Processing Settings
    AUDIO_SAMPLE_RATE_HZ: int = 16000
    AUDIO_CHANNELS: int = 1
    
    # Logging Configuration
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")
    
    model_config = SettingsConfigDict(case_sensitive=True)

settings = Settings()

# Configure standardized enterprise logging
logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s (%(filename)s:%(lineno)d) - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)
logger = logging.getLogger("sih.scrap.backend")
