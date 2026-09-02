import logging
from fastapi import APIRouter, Depends, Query, UploadFile, File, HTTPException, status
from fastapi.responses import Response
from sqlalchemy.orm import Session
from typing import Optional
from app.database import get_db
from app.schemas.voice import (
    VoiceParseRequest, 
    VoiceParseResponse, 
    SupportedLanguage,
    AudioDecodeResponse,
    TTSRequest
)
from app.services.voice_service import (
    VoiceService, 
    MARATHI_NUMBERS, 
    HINDI_NUMBERS, 
    SUBCATEGORY_VOCABULARY
)

logger = logging.getLogger("sih.scrap.voice_api")
router = APIRouter(prefix="/voice", tags=["Vernacular Hindi & Marathi Voice Engine"])

@router.post("/parse", response_model=VoiceParseResponse)
def parse_vernacular_speech(req: VoiceParseRequest, db: Session = Depends(get_db)):
    """
    Parses spoken voice command transcripts exclusively in Hindi or Marathi.
    Identifies functional intent (bhav sanga / pickup pathva / wajan dakhva),
    extracts scrap subcategories, calculates instant spot valuation,
    and returns fluent Marathi and Hindi audio strings and synthesized base64 WAV audio.
    """
    logger.info(f"Received parse request: '{req.transcript}' ({req.language})")
    return VoiceService.parse_transcript(
        transcript=req.transcript, 
        db=db, 
        preferred_lang=req.language
    )

@router.post("/decode-audio", response_model=AudioDecodeResponse)
async def decode_audio_voice_command(
    file: UploadFile = File(...),
    preferred_language: SupportedLanguage = Query(SupportedLanguage.MARATHI),
    db: Session = Depends(get_db)
):
    """
    Offline Acoustic Voice Decoding Endpoint:
    Receives voice audio stream/file (WAV, MP3, OGG, FLAC) from mobile field app.
    Decodes acoustic waveform completely offline on CPU without external cloud APIs.
    Identifies trade intent, extracts scrap subcategories and weights, and returns
    full valuation breakdown with native audio confirmations.
    """
    if not file.filename:
        raise HTTPException(status_code=400, detail="Audio file must have a valid filename")

    audio_bytes = await file.read()
    if len(audio_bytes) < 44:
        raise HTTPException(status_code=400, detail="Uploaded audio file is empty or corrupted")

    # 1. Run local offline acoustic decoder
    decoded_transcript, detected_lang = VoiceService.decode_audio_waveform(
        audio_bytes=audio_bytes, 
        filename=file.filename
    )

    # 2. Parse the decoded transcript
    parse_result = VoiceService.parse_transcript(
        transcript=decoded_transcript,
        db=db,
        preferred_lang=detected_lang
    )

    return AudioDecodeResponse(
        filename=file.filename,
        audio_format=file.content_type or "audio/wav",
        decoded_transcript=decoded_transcript,
        detected_language="marathi" if detected_lang == SupportedLanguage.MARATHI else "hindi",
        parse_result=parse_result,
        offline_engine_used="Offline CPU Acoustic Recognizer (Vosk / Formant Waveform Pipeline)"
    )

@router.get("/tts")
def stream_vernacular_tts_audio(
    text: str = Query(..., examples=["दोन किलो तांबे आणि पाच किलो लोखंड भाव सांगा"]),
    language: SupportedLanguage = Query(SupportedLanguage.MARATHI)
):
    """
    Streams synthesized native Marathi / Hindi WAV audio directly for mobile players
    and web `<audio controls>` components without external cloud TTS engines.
    """
    audio_wav = VoiceService.synthesize_vernacular_speech(text, language)
    return Response(content=audio_wav, media_type="audio/wav")

@router.post("/synthesize-speech")
def synthesize_speech(req: TTSRequest):
    """
    Returns audio WAV bytes and base64 payload for device offline caching.
    """
    audio_wav = VoiceService.synthesize_vernacular_speech(req.text, req.language)
    import base64
    b64_str = base64.b64encode(audio_wav).decode('ascii')
    return {
        "text": req.text,
        "language": req.language,
        "media_type": "audio/wav",
        "audio_base64": f"data:audio/wav;base64,{b64_str}"
    }

@router.get("/vocabulary")
def get_vernacular_vocabulary(language: SupportedLanguage = Query(SupportedLanguage.MARATHI)):
    """
    Returns verified Hindi & Marathi keywords for client-side grammar matching 
    and offline acoustic models.
    """
    lang_key = "mr" if language == SupportedLanguage.MARATHI else "hi"
    numbers = list(MARATHI_NUMBERS.keys()) if language == SupportedLanguage.MARATHI else list(HINDI_NUMBERS.keys())
    
    vocab_list = []
    for code, terms in SUBCATEGORY_VOCABULARY.items():
        vocab_list.append({
            "subcategory_code": code,
            "spoken_terms": terms.get(lang_key, [])
        })

    sample_prompts = [
        "दोन किलो तांबे आणि पाच किलो लोखंड भाव सांगा",
        "दहा किलो रद्दी आणि अर्धा किलो पितळ पिकअप पाठवा",
        "पाच किलो लोहा और दो किलो तांबा भाव बताओ",
        "पंधरा किलो वर्तमानपत्र रद्दी वजन दाखवा",
        "ardha kilo tamba aani adhich kilo pital bhav sanga"
    ]

    return {
        "selected_language": language,
        "sample_spoken_numbers": numbers[:30],
        "materials_vocabulary": vocab_list,
        "example_voice_commands": sample_prompts
    }
