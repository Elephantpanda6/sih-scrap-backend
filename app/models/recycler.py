from datetime import datetime, timezone
from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, Text, Index
from app.database import Base

def utc_now():
    return datetime.now(timezone.utc)

class AuthorizedRecycler(Base):
    """
    Registry of Government Authorized Recyclers and Dismantlers,
    compliant with Central Pollution Control Board (CPCB) and 
    State Pollution Control Board (SPCB) guidelines under 
    E-Waste (Management) Rules 2022 and Battery Waste Management Rules 2022.
    """
    __tablename__ = "authorized_recyclers"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, nullable=False, index=True)
    authorization_number = Column(String, unique=True, index=True, nullable=False)
    regulatory_board = Column(String, default="CPCB")  # "CPCB", "MPCB", "DPCC", "GPCB", "KSPCB", "TNPCB"
    statutory_rule = Column(String, default="E-Waste (Management) Rules, 2022")
    
    # Location details
    state = Column(String, index=True, nullable=False)
    district = Column(String, index=True, nullable=False)
    address = Column(Text, nullable=False)
    pin_code = Column(String, nullable=False)
    latitude = Column(Float, nullable=False, index=True)
    longitude = Column(Float, nullable=False, index=True)
    
    # Contact info
    contact_person = Column(String, nullable=True)
    contact_phone = Column(String, nullable=False)
    contact_email = Column(String, nullable=True)
    
    # Capacity & Material authorization
    annual_capacity_metric_tonnes = Column(Float, nullable=False)
    accepted_category_codes = Column(String, nullable=False)  # Comma-separated: "e_waste,battery_hazmat,non_ferrous"
    
    is_active_license = Column(Boolean, default=True)
    license_valid_until = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=utc_now)

    __table_args__ = (
        Index("ix_recycler_lat_lon", "latitude", "longitude"),
    )
