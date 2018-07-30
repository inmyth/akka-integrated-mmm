# Operation

This app is built with Akka framework that uses Actors. The core ones are:
- BaseActor: reads config
- OpActor : contains FIFO sequence that caches requests and sends them one-by-one with rate allowed by the exchange
- OrderActor: manages order operation (counter, seed, trim) for a symbol(pair)
- ExchangeActor: handles API's request, response for an exchange

## Rest Bot Operation

Basically the bot operates by using the API's
- getActiveOrders
- getFilledOrders

`getActiveOrders` gets currently active orders, used for seeding empty side or trimming extra offers.

`getFilledOrders` gets filled orders, used for counter.

The bot keeps track of the last order countered on a text file, in the form of order id or any other indicator. The next time it queries filled orders, it collects all new filled orders from the indicated order and counters them.
The file is then updated and the process is repeated.
If the indicator doesn't exist in the response, the bot will consider all orders there to be uncountered and counter them all.

## Orderbook

Operation here is divided by two: initialization and normal.

During initialization bot will retrieve active orders from the exchange. Depending on the strategy, it will immediately start (by calling `maintain`) or clear the orderbook first and reseed it.

### Maintain

Maintain grows (replacing missing levels) or trim (cancelling overgrown levels) a side. This is triggered after some time interval of idleness in GetOrderInfo.

Grow starts only when a side is empty, and doesn't run at the same time as any order op (NewOrder or CancelOrder) because it might read from empty buys / sels during seeding, produces incorrect seeds if there are multiple counters.

## Queue

To avoid API limit, the bot groups together all requests to a same exchange in a queue. The queue is popped at fixed interval.


