0.0.5
- specified Application in build.sbt

0.0.4
- added trim
- refactored orderbook

0.0.3
- removed isHardReset
- changes startPrice to seed

0.0.2
- Yobit

0.0.1
- Refactoring from Okex MMM
- Supports okexRest

TODO
- [] Long test on Yobit
- Config `startOrderbookEmpty` renamed to `seed`. lastTicker, lastOwn, price ->
    - [x] Rest methods should not return shutdown except for config error, creds error (except Okex)
    - [] WS needs to reconnect WS
- remove `isHardReset`.
    - [] For Rest this is already handled by order check scheduler
    - [] For WS, cache orders before they are sent and remove them once response is returned. WS reconnect will fire up send all orders from the cache.
- [] ~~Config startOrderbookExists : keep, clear~~ resolve is default

