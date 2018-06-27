# Integrated MMM Market Making Bot

An automated grid/ping-pong market making bot supporting various exchanges.

Below are exchange classifications and the handling strategy.

## REST Exchanges

REST Exchanges supports only REST methods for trades.

Characteristics:

- It is most likely that the order cannot be set with custom id.
- A successful order request returns only server response (e.g "result=true" and the order id).
It doesn't return the status of the order itself (filled, unfilled, ...)
- To get the status of an order, we send get-order-info request

Example: Okex, Binance, Huobi


The bot first gets the current active orders. The bot will then get the starting price from its own last trade, market ticker, or set price.
Depending on the settings the bot clears all existing orders, seed new orders from the starting price and leave the orderbook as is.
From here on, every order filled will be countered. The bot will also seed the shrinking side.
Every order on the orderbook is checked regularly with a time gap between orders to avoid overloading the server.
A checking scheduler kicks off once there's idleness for some time in the orderbook.

## Websocket Exchanges

Websocket exchanges supports Websocket methods for trades

Characteristics:

- Order can be set with custom id
- Successful order request returns that order status
- Supports events stream. Filled/cancel/etc orders are broadcast automatically.

Example: HitBTC

Websocket may disconnect. To handle such scenarion, the bot caches all outbound orders. Successful request will remove the corresponding order from the cache.
There are two routes to stream orders to websocket client.
- In normal operation, orderbook will send an order (as counter, seed) to cache and to ws client.
- On websocket connected, fire up one time stream to send all cached orders to ws client
- Since only successful response can remove the cached order, those two routes are safe


## Stupid Websocket Exchanges

These exchanges that websocket to wrap REST methods

Characteristics:

- No events stream
- Manual order check
- Since the requests and responses are the same as REST, some methods are not possible.

In REST it is possible to map request and response. In websocket it is not possible.
That's why client uses response-challenge on a param like request id to match it with incoming response.
Having one websocket for one bot(one symbol) can make things easier, since we can eliminate the possibilities of getting responses not related to that bot.
Although it is very likely such server isn't designed to handle HFT (since it just wraps REST) it is still worthwhile to do websocket method if only to save some latency from Http POST/GET.


Example: Okex


## Currently supported exchanges

okexRest

















