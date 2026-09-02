from datetime import datetime, timezone
from sqlalchemy import Column, Integer, String, Float, ForeignKey, DateTime, Text
from sqlalchemy.orm import relationship
from app.database import Base

def utc_now():
    return datetime.now(timezone.utc)

class MaterialCategory(Base):
    """
    Primary top-tier scrap material classification under CPCB guidelines:
    Ferrous, Non-Ferrous, E-Waste (WEEE), Battery & Hazmat, Plastics, Paper/Cardboard.
    """
    __tablename__ = "material_categories"

    id = Column(Integer, primary_key=True, index=True)
    code = Column(String, unique=True, index=True, nullable=False)
    name = Column(String, nullable=False)
    name_hi = Column(String, nullable=False)
    name_mr = Column(String, nullable=False)
    description = Column(Text, nullable=True)
    created_at = Column(DateTime, default=utc_now)

    subcategories = relationship(
        "MaterialSubCategory", 
        back_populates="category", 
        cascade="all, delete-orphan",
        order_by="MaterialSubCategory.id"
    )


class MaterialSubCategory(Base):
    """
    Granular secondary scrap commodity specification with physical density,
    spot mandi rates, volatility bounds, degradation rates, and precious metal recovery metrics.
    """
    __tablename__ = "material_subcategories"

    id = Column(Integer, primary_key=True, index=True)
    category_id = Column(Integer, ForeignKey("material_categories.id"), nullable=False)
    code = Column(String, unique=True, index=True, nullable=False)
    name = Column(String, nullable=False)
    name_hi = Column(String, nullable=False)
    name_mr = Column(String, nullable=False)
    
    # Physical & Metallurgical Properties
    standard_density_kg_m3 = Column(Float, nullable=False, default=1000.0)
    default_purity = Column(Float, nullable=False, default=0.95)
    
    # Market Pricing Bounds in Indian Rupees (₹/kg)
    current_spot_rate = Column(Float, nullable=False)
    volatility_min_rate = Column(Float, nullable=False)
    volatility_max_rate = Column(Float, nullable=False)
    unit = Column(String, default="kg")
    
    # Quality, Oxidation & Degradation Factors
    degradation_factor_rust = Column(Float, default=0.15)       # Max discount rate for oxidation/rust
    oxidation_multiplier = Column(Float, default=1.0)           # Multiplier based on environmental exposure
    contamination_factor = Column(Float, default=0.10)          # Deduction for foreign materials (dirt/concrete/plastic)
    
    # Precious Metals & Strategic Rare Metal Recovery (grams per metric tonne of scrap)
    precious_metal_yield_gold_g_tonne = Column(Float, default=0.0)
    precious_metal_yield_silver_g_tonne = Column(Float, default=0.0)
    precious_metal_yield_palladium_g_tonne = Column(Float, default=0.0)
    
    # Indian Tax and Trade Classification
    hsn_code = Column(String, nullable=True)  # GST HSN Code (e.g., 7404, 8549, 7204)
    updated_at = Column(DateTime, default=utc_now, onupdate=utc_now)

    category = relationship("MaterialCategory", back_populates="subcategories")
