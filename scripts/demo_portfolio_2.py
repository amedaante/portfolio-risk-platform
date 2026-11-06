import requests

BASE = "http://localhost:8080/api/portfolios"

portfolio = requests.post(BASE, json={"name": "Conservative Bond-Heavy Portfolio", "baseCurrency": "USD"}).json()
pid = portfolio["id"]
print("portfolio id:", pid)

# Weighted toward UST10Y and SPY, light on high-vol single names (TSLA/GOOGL excluded entirely)
positions = [
    {"instrumentId": "UST10Y", "instrumentType": "BOND",   "quantity": 8000, "price": 101.08, "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "SPY",    "instrumentType": "ETF",    "quantity": 500,  "price": 535.10, "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "AAPL",   "instrumentType": "EQUITY", "quantity": 100,  "price": 161.83, "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "MSFT",   "instrumentType": "EQUITY", "quantity": 100,  "price": 509.85, "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "EURUSD", "instrumentType": "FX",     "quantity": 10000, "price": 0.8661, "currency": "USD", "valuationDate": "2026-09-25"},
]

for p in positions:
    r = requests.post(f"{BASE}/{pid}/positions", json=p)
    print(p["instrumentId"], r.status_code)

print(requests.get(f"{BASE}/{pid}").json())