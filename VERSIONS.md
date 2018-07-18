0.3.5
- doubles were caused by GotOrderInfo's starting refresh before orderbook init was complete (startPrice not yet available)
    - fixed by not launching refresh if the cancellable is None.
- changed isInQueue

0.3.4
- fixed wrong isInQueue
- enumerized As
- all request should have As. Refresh orders will first check As in queue along with GetOrderInfo which sets off after NewOrder

0.3.3
- do check on request sequence whenever "refresh order" is called. This should prevent double orders, possibly caused by balancer ("reseed") called when the queue still has reseed orders.
- if not ok, check the Strategy for reseed
NOTE: even more secure way is not to pop the request from the queue but remove it once the request has been executed and returned response.

0.2.3
- make errorRetry's send email optional

0.2.2
- added exchange and pair in log orderbook

0.2.1
- cleaned up codes
- add docs and template

0.2.0
- grouped all bot of same exchanges with one sender sequence
- created ConfigTest for groupBots
- refactored OpActor, OrderbookActor. Orderbook now sends orders to Op's queue but gets the results directly from RestActor

0.1.1
- added Fcoin

0.1.0
- Refactor OpRestActor, now uses queue for unified request delay
- Strategy requires ceil and floor on price with its scaling factor

0.0.6
- Yobit new offer needed to avoid scientific notation

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

