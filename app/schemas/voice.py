from pydantic import BaseModel, Field
from typing import Optional, List
from enum import Enum

class SupportedLanguage(str, Enum):
    HINDI = "hi"
    MARATHI = "mr"

class CommandIntent(str, Enum):
    PRICE_INQUIRY = "price_inquiry"       # e.g., "bhav sanga", "kitna paisa"
    PICKUP_REQUEST = "pickup_request"     # e.g., "pickup pathva", "gadi bhejo"
    WEIGHT_QUERY = "weight_query"         # e.g., "wajan dakhva", "kitna kilo hai"
    MATERIAL_REGISTRATION = "registration"# default: "5 kilo loha", "don kilo tamba"

class VoiceParseRequest(BaseModel):
    transcript: str = Field(..., examples=["don kilo tamba aani paach kilo lokhand bhav sanga"])
    language: SupportedLanguage = Field(SupportedLanguage.MARATHI, examples=[SupportedLanguage.MARATHI])
    current_latitude: Optional[float] = None
    current_longitude: Optional[float] = None

class ParsedItem(BaseModel):
    subcategory_code: str
    subcategory_name: str
    subcategory_name_local: str
    weight_kg: float
    confidence: float
    spot_rate_per_kg: float
    subtotal_inr: float

class VoiceParseResponse(BaseModel):
    original_transcript: str
    detected_language: str
    detected_intent: CommandIntent
    detected_items: List[ParsedItem]
    total_estimated_price_inr: float
    
    # Vernacular spoken outputs for low-literacy users
    feedback_audio_text_hi: str          # Spoken Hindi audio string
    feedback_audio_text_mr: str          # Spoken Marathi audio string
    feedback_audio_base64: Optional[str] = None  # Base64 data:audio/wav;base64,...
    tts_streaming_url: Optional[str] = None      # /api/v1/voice/tts?language=mr&...
    
    is_valid_command: bool
    recommended_action: str

class AudioDecodeResponse(BaseModel):
    filename: str
    audio_format: str
    decoded_transcript: str
    detected_language: str
    parse_result: VoiceParseResponse
    offline_engine_used: str

class TTSRequest(BaseModel):
    text: str = Field(..., examples=["आजचा बाजार दरानुसार २ किलो तांब्याचे अंदाजे मूल्य ₹१३९० होईल."])
    language: SupportedLanguage = Field(SupportedLanguage.MARATHI, examples=[SupportedLanguage.MARATHI])
