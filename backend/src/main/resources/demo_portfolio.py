import requests

BASE = "http://localhost:8080/api/portfolios"

portfolio = requests.post(BASE, json={"name": "Demo Portfolio", "baseCurrency": "USD"}).json()
pid = portfolio["id"]
print("portfolio id:", pid)

positions = [
    {"instrumentId": "AAPL",   "instrumentType": "EQUITY", "quantity": 500,   "price": 161.83, "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "MSFT",   "instrumentType": "EQUITY", "quantity": 300,   "price": 509.85, "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "GOOGL",  "instrumentType": "EQUITY", "quantity": 400,   "price": 98.00,  "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "TSLA",   "instrumentType": "EQUITY", "quantity": 150,   "price": 254.53, "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "SPY",    "instrumentType": "ETF",    "quantity": 1000,  "price": 535.10, "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "UST10Y", "instrumentType": "BOND",   "quantity": 2000,  "price": 101.08, "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "USDINR", "instrumentType": "FX",     "quantity": 50000, "price": 73.38,  "currency": "USD", "valuationDate": "2026-09-25"},
    {"instrumentId": "EURUSD", "instrumentType": "FX",     "quantity": 30000, "price": 0.8661, "currency": "USD", "valuationDate": "2026-09-25"},
]

for p in positions:
    r = requests.post(f"{BASE}/{pid}/positions", json=p)
    print(p["instrumentId"], r.status_code)

print(requests.get(f"{BASE}/{pid}").json())