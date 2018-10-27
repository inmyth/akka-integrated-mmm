(the first item in each exchange is the supported seed method)

### hitbtc
- ws: supports `lastTicker` and custom start price

### okexRest
- rest: supports `lastTicker`, `cont` and custom start price
- *Make sure API key and secret are correct.* Okex returns errors reserved for sign error (10007, 10005) even a Cloudlare website for non-sign error.
As such 10007, 10005 and html response will be retried.
- no mention of API limit
- TradeHistory
    - return sorted without key.
    - Use order id (Long) as indicator

### yobit
- restGI: supports `lastTicker`, `lastOwn` and custom start price
- The HmacSHA512 signature has to be in lowercased hex
- ~Trade returns order status.~ Trade returns order status which always indicates that the order is unfilled.
- ActiveOrder returns only the current amount which might have been partially filled. To get complete info, we still need to call OrderInfo on each order
- "Admissible quantity of requests to API from user's software is 100 units per minute."
- TradeHistory
    - return sorted with special key (Long)
    - Use this key as indicator
- Trade History response doesn't have filled / partially filled status. To rebuild a fully filled order, need gather multiple fragmented trades.

### fcoin
- rest: supports `lastTicker`,`cont` and custom start price
- Timestamp is required to sign
- API limit = 100 / 10 seconds per user
- TradeHistory
    - return sorted without key.
    - Use order id (String) as indicator

### livecoin
- rest: supports `lastTicker`, `cont` and custom start price
- get client orders's pagination is broken (startRow doesn't work)
- may have super long timeout (more than 2 minutes)
- expect all active order to be at most 100 at a time

### btcalpha
- restGI: supports `lastTicker`, `lastOwn`,  and custom start price
- **All IP 35.* (Google Cloud) are blocked by BtcAlpha**
- **replace official pair name with underscore** (NOAH/BTC to NOAH_BTC)
- api secret has to be escaped
    - single-quotes: escaped with //'
    - double-quotes: escaped with ///"
- doesn't have partially-filled status


