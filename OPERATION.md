# Operation

This app is built with Akka framework that uses Actors. The core ones are:
- BaseActor: reads config
- OpActor : contains FIFO sequence that caches requests and sends them one-by-one with rate allowed by the exchange
- OrderbookActor: contains open orders and order management for a pair
- ExchangeActor: handles API's request, response for an exchange

The bot maintains all orders in runtime memory.
It will handle server issues with retry mechanism (e.g order that fails to deliver due to temporary server error will be resent later) while trading error will be ignored (e.g lack of fund)

Bot should be started or restarted when
1. config is created or changed
2. an irrecoverable error occurs (wrong api/secret, user ban, permanent endpoint change)

## Orderbook

Operation here is divided by two: initialization and normal.

During initialization bot will retrieve active orders from the exchange. Depending on the strategy, it will immediately start (by calling `maintain`) or clear the orderbook first and reseed it.

All callbacks in Orderbooks except GetOpenOrderInfo are used during initialization.

### Maintain

Maintain grows (replacing missing levels) or trim (cancelling overgrown levels) a side. This is triggered after some time interval of idleness in GetOrderInfo.

Grow cannot be started at the same time as Counter because it might read from empty buys / sels during seeding, produces incorrect seeds if there are multiple counters.

### Queue

To avoid API limit, the bot groups together all requests to a same exchange in a queue. The queue is popped at fixed interval.

Open orders are checked regularly. If through GetInfo API an order is found filled, it is immediately countered and the side is reseeded.

There are possible race problems: if requests are pushed too fast into queue, some identical requests may get executed. If this request if GetOrderInfo for a filled order, the bot will counter and seed multiple times.
So GetInfo with an orderId will only be pushed if there's no similar request in queue.

Also all REST exchanges have the same characteristic: new order request will only return its id, not its status. So another call is needed to get the info.

So all new orders are also put in a set while waiting the get info request to complete. Any new seeds from Orderbook will also take into count pending orders in this set since those orders have not been registered in the orderbook.

### Doubles

This is the most complicated issue. Doubles are caused by seeding which fires off at the wrong moment (seeding with reference orders which are filled but with not yet refreshed in the orderbook).

For REST this is almost unavoidable because there is no even broadcast. The status of the orders can only be known by completing all the GetInfo requests in queue.

To avoid it, exchange is hard-coded with a param `seedIfEmpty` which only makes the bot seed only when the orderbook is empty.


## Future work

For future UI integration, an order should be sent through the bot to get its id registered. Cancellation will be effective once an offer has been checked with GetOrderInfo.
