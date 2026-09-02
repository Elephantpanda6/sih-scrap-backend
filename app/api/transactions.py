from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List
from app.database import get_db
from app.models.transaction import ScrapTransaction
from app.models.category import MaterialSubCategory
from app.models.user import User
from app.schemas.transaction import TransactionCreate, TransactionUpdate, TransactionOut
from app.api.deps import get_current_user

router = APIRouter(prefix="/transactions", tags=["Pickup & Transactions"])

@router.post("/", response_model=TransactionOut, status_code=status.HTTP_201_CREATED)
def create_pickup_order(
    tx_in: TransactionCreate, 
    current_user: User = Depends(get_current_user), 
    db: Session = Depends(get_db)
):
    """
    Creates a new scrap pickup request/transaction by a scrap dealer.
    """
    subcat = db.query(MaterialSubCategory).filter(MaterialSubCategory.code == tx_in.material_code).first()
    if subcat:
        rate = subcat.current_spot_rate
        material_name = subcat.name
        purity = tx_in.purity_factor or subcat.default_purity
    else:
        rate = 35.0
        material_name = tx_in.material_code.replace("_", " ").title()
        purity = tx_in.purity_factor or 0.90
    
    # Base price calculation with 15% dealer margin applied
    calculated_price = round(tx_in.weight_kg * rate * purity * 0.85, 2)
    
    tx = ScrapTransaction(
        dealer_id=current_user.id,
        material_code=tx_in.material_code,
        material_name=material_name,
        weight_kg=tx_in.weight_kg,
        purity_factor=purity,
        calculated_price=calculated_price,
        pickup_address=tx_in.pickup_address,
        pickup_latitude=tx_in.pickup_latitude,
        pickup_longitude=tx_in.pickup_longitude,
        notes=tx_in.notes,
        voice_input_transcript=tx_in.voice_input_transcript,
        status="pending"
    )
    db.add(tx)
    db.commit()
    db.refresh(tx)
    return tx

@router.get("/", response_model=List[TransactionOut])
def list_transactions(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """
    Returns transactions relevant to the logged-in user:
    - Scrap dealer sees their submitted orders.
    - Buyer sees available/accepted orders.
    """
    if current_user.role in ["buyer", "admin"]:
        return db.query(ScrapTransaction).all()
    return db.query(ScrapTransaction).filter(ScrapTransaction.dealer_id == current_user.id).all()

@router.get("/{tx_id}", response_model=TransactionOut)
def get_transaction(tx_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    tx = db.query(ScrapTransaction).filter(ScrapTransaction.id == tx_id).first()
    if not tx:
        raise HTTPException(status_code=404, detail="Transaction not found")
    return tx

@router.put("/{tx_id}/status", response_model=TransactionOut)
def update_transaction_status(
    tx_id: int, 
    update_in: TransactionUpdate, 
    current_user: User = Depends(get_current_user), 
    db: Session = Depends(get_db)
):
    """
    Update transaction status (e.g. buyer accepts or marks collected).
    """
    tx = db.query(ScrapTransaction).filter(ScrapTransaction.id == tx_id).first()
    if not tx:
        raise HTTPException(status_code=404, detail="Transaction not found")
        
    if update_in.status:
        tx.status = update_in.status
    if update_in.buyer_id:
        tx.buyer_id = update_in.buyer_id
    elif current_user.role == "buyer":
        tx.buyer_id = current_user.id
    if update_in.final_agreed_price is not None:
        tx.final_agreed_price = update_in.final_agreed_price
    if update_in.notes:
        tx.notes = update_in.notes
        
    db.commit()
    db.refresh(tx)
    return tx
