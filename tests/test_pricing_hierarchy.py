import pytest

def test_hierarchical_pricing_with_rust_and_ewaste(client):
    payload = {
        "items": [
            {
                "subcategory_code": "copper_bare_bright",
                "weight_kg": 3.0,
                "purity_override": 0.98,
                "rust_level_percentage": 0.0
            },
            {
                "subcategory_code": "iron_hms_heavy",
                "weight_kg": 25.0,
                "rust_level_percentage": 30.0,  # 30% rust penalty applied
                "contamination_level_percentage": 5.0
            },
            {
                "subcategory_code": "e_waste_pcb_high",
                "weight_kg": 5.0,
                "rust_level_percentage": 0.0
            }
        ],
        "salvage_reusable_parts_value": 75.0,
        "pickup_distance_km": 5.0,
        "custom_margin_percentage": 0.15
    }

    res = client.post("/api/v1/pricing/calculate-hierarchical", json=payload)
    assert res.status_code == 200
    data = res.json()

    # Assertions on calculation breakdown
    assert data["gross_material_value_inr"] > 3000.0
    assert data["logistics_and_haulage_cost_inr"] == 45.0  # ₹20 base + ₹5/km * 5km
    assert data["final_payable_amount_inr"] > 2500.0
    assert data["total_scrap_weight_kg"] == 33.0

    # Precious & Strategic Metals Recovery Assertions
    # 5kg of 180g/tonne Gold yield = 0.005 tonne * 180 = 0.90g gold
    assert data["total_precious_gold_grams"] >= 0.85
    # 5kg of 650g/tonne Silver yield = 0.005 tonne * 650 = 3.25g silver
    assert data["total_precious_silver_grams"] >= 3.0
    # 5kg of 35g/tonne Palladium yield = 0.005 tonne * 35 = 0.175g palladium
    assert data["total_precious_palladium_grams"] >= 0.15

    # Environmental CO2 Abatement Assertions
    assert data["co2_emissions_saved_kg"] > 50.0

    # Line item verification
    lines_by_code = {line["subcategory_code"]: line for line in data["line_items"]}
    assert lines_by_code["iron_hms_heavy"]["rust_penalty_deduction"] > 0.0
    assert lines_by_code["e_waste_pcb_high"]["estimated_palladium_yield_grams"] > 0.0

    # Dual vernacular spoken audio summaries
    assert "₹" in data["audio_summary_hi"]
    assert "₹" in data["audio_summary_mr"]
    assert "कार्बन" in data["audio_summary_hi"] or "कार्बन" in data["audio_summary_mr"]

def test_floor_price_enforcement(client):
    # Very small, low value rusty scrap with high logistics haulage distance
    payload = {
        "items": [
            {
                "subcategory_code": "paper_corrugated_carton",
                "weight_kg": 1.0,
                "rust_level_percentage": 0.0
            }
        ],
        "pickup_distance_km": 15.0  # Logistics cost (₹20 + ₹75 = ₹95) exceeds material gross (₹14.50)
    }

    res = client.post("/api/v1/pricing/calculate-hierarchical", json=payload)
    assert res.status_code == 200
    data = res.json()
    # Net valuation would be negative, but payout cannot drop below statutory floor of ₹10
    assert data["net_valuation_inr"] < 0.0
    assert data["final_payable_amount_inr"] == 10.0
    assert data["guaranteed_floor_price_inr"] == 10.0

def test_quick_quote_endpoint(client):
    res = client.get("/api/v1/pricing/quick-quote?material_code=copper_bare_bright&weight_kg=5.0&purity_factor=0.95")
    assert res.status_code == 200
    data = res.json()
    assert data["gross_material_value"] > 3000.0
    assert data["recommended_dealer_payout"] > 2500.0
