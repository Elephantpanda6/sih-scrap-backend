from pydantic import BaseModel, Field
from typing import List, Tuple, Optional

class VisualDetectionItem(BaseModel):
    detected_subcategory_code: str
    detected_name: str
    detected_name_hi: str
    detected_name_mr: str
    confidence_score: float
    surface_rust_oxidation_pct: float
    recommended_purity_factor: float
    estimated_weight_range_kg: Tuple[float, float]
    dismantling_and_segregation_tip_hi: str
    dismantling_and_segregation_tip_mr: str

class OfflineVisionAnalysisResponse(BaseModel):
    filename: str
    image_resolution: str
    primary_category_detected: str
    surface_rust_percentage: float
    contamination_percentage: float
    metallic_reflectance_score: float
    texture_edge_density: float
    cleanliness_grade: str
    detected_components: List[VisualDetectionItem]
    estimated_price_range_inr: Tuple[float, float]
    voice_feedback_hi: str
    voice_feedback_mr: str
    next_step_action: str
    model_architecture: str = 'MobileNet-V3-Small / Embedded CPU Multi-Spectral Heuristic'