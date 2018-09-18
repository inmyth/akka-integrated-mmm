1.0.7
- WsActor now doesn't wait for onDisconnect to reconnect

1.0.6
- added Livecoin error condition
- added rest timeout handler (Livecoin suffers from super long timeout)

1.0.5
- fixed orderGI: counter the order cache which has the original order info not the API response

1.0.4
- fixed orderGI: order info should update offer not replace it

1.0.2
- minor fix Livecoin filled amount

1.0.1
- escaped secret for Btcalpha

1.0.0
- added Hitbtc
- automatic websocket reconnect and cache drain

0.8.3
- fixed Btcalpha pair name and request

0.8.2
- Livecoin added errorRetry "Cannot get a connection"
- first version of untested integrated HitBtc

0.8.1
- changed yobit endpoint to yobit.io
- changed yobit rate to 1500ms

0.8.0
- added bitcoinalpha

0.7.2
- refactored Livecoin getOrder
- removed Livecoin pagination (doesn't work)

0.7.1
- added Livecoin status

0.7.0
- added Livecoin
- Bitcalpha in progress

0.6.1
- fixed Yobit status 3 from filled to partially-filled

0.6.0
- brought back GI (GetOrderInfo) method: checking order status by calling GetOrderInfo
- avoided waiting for GetOrderInfo by using provisional offer: offer whose values come from request and id from newOrder
- fixed find duplicates: returns more than one if more than one dupe
- seed starts only when theres' no NewOrder and CancelOrder
- ProvisionalOffer is set with serverTs to allow duplicates removal
- trims and remove duplicates requests are now merged

0.5.4
- added KillDupes enum
- more readme

0.5.3
- made getActive arrive before getFilled: seed happens before counter
- added delete duplicates

0.5.2
- fixed bug in grow

0.5.1
- fixed bug on unfunded newOrder : needs to clear the set in op for each successful request
- fixed wiring

0.5.0
- revamped
- input last order sent/ts
- active orders cannot queue when new orders are in q
- active orders need to be partitioned and sorted
- active orders : seed and trim, filled orders : counter. no dependency relation between them
- counter mechanism: store last countered id on bot cache, read it before getFilled is called, and use it as lower bound of all uncountered orders
    - different exchange has different indicator

0.4.3
- fixed small bug in dump

0.4.2
- counter needs to cancel all seeds

0.4.1
- fixed bug in reseed

0.4.0
- Introduced a new variable in Exchange `seedIfEmpty` to seed only if the side has no open orders.
      - because during GetOrderInfo sequence multiple orders may get filled, seed while half-empty cannot work well
- Yobit status 3 and _ is now cancelled

0.3.7
- solution 0.3.6 was not perfect. After receiving a new order id, this id must be queried again with GetInfo. During this duration, there was a hole where `CheckInQueue` cannot find any pending orders. This caused multiple to be pushed.
    - OpRest now holds a set to hold pending NewOrders or successful new orders that are still waiting for their info. `CheckInQueue` will also check any pending orders here.

0.3.6
- doubles were caused by multiple GetOrderInfo (with same id) being queued too many times (order check just keeps pushing new requests every interval). If multiple GetOrderInfo check a same filled order then the bot will create more than one counter.
    - fixed it by removing inbound GetOrderInfo requests which have the same order Id as the ones in queue.
- Grow is put in the same place as counter. Removed grow from balancer scheduler
    - GotOrderCancel is not a place to put grow. This is used to clear orderbook the first time.

0.3.5
- doubles were caused by GotOrderInfo's starting refresh before orderbook init was complete (startPrice not yet available)
    - fixed by not launching refresh if the cancellable is None.
- changed isInQueue

0.3.4
- fixed isInQueue
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
