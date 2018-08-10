### okexRest
- Okex returns errors reserved for sign error (10007, 10005) even a Cloudlare website for non-sign error.
As such 10007, 10005 and html response will be retried. *Make sure API key and secret are correct.*
- no mention of API limit
- TradeHistory
    - return sorted without key.
    - Use order id (Long) as indicator

### yobit
- The HmacSHA512 signature has to be in lowercased hex
- ~Trade returns order status.~ Trade returns order status which always indicates that the order is unfilled.
- ActiveOrder returns only the current amount which might have been partially filled. To get complete info, we still need to call OrderInfo on each order
- "Admissible quantity of requests to API from user's software is 100 units per minute."
- TradeHistory
    - return sorted with special key (Long)
    - Use this key as indicator
- Trade History response doesn't have filled / partially filled status. To rebuild a fully filled order, need gather multiple fragmented trades.

### fcoin
- Timestamp is required to sign
- API limit = 100 / 10 seconds per user
- TradeHistory
    - return sorted without key.
    - Use order id (String) as indicator


