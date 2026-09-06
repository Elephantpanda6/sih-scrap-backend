from fastapi import APIRouter, UploadFile, File, HTTPException
from app.schemas.vision import OfflineVisionAnalysisResponse
from app.services.vision_service import VisionService
router = APIRouter(prefix='/vision', tags=['Offline Scrap Computer Vision'])

@router.post('/analyze-image', response_model=OfflineVisionAnalysisResponse)
async def analyze_scrap_image_offline(file: UploadFile=File(...)):
    if not file.content_type or not file.content_type.startswith('image/'):
        raise HTTPException(status_code=400, detail='Uploaded file must be a valid JPEG/PNG image')
    image_bytes = await file.read()
    return VisionService.analyze_scrap_image(image_bytes, file.filename or 'captured_scrap.jpg')