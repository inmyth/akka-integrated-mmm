# Operation

How the bot operates depends on the exchange's type. Here is a list the bot operates on

## Exchange Types

### REST Exchanges

REST Exchanges supports only REST methods for trades. This is the most common type of exchange.

Characteristics:

- Order cannot be pre-set with custom id. A raw offer is sent then returned with a response that contains order id.
- To get the status of an order, we do another request gerOrderInfo.

Example: Okex, Binance, Huobi

#### Operations

There are two ways a bot manages orders for REST exchange. By fetching Trade History and Active Orders or by checking orders with GetOrderInfo

#### Trade History Method

The bot basically fetches two pieces of information to operate:
- Trade History or history of filled orders for the bot to counter
- Active Orders to see if the bot needs to seed empty side or trim extra orders.

As the bot simply counters whatever orders that come out in Trade History the only problem is to mark the last offer the bot left out.<br>
For this purpose the bot saves the last countered order on a text file, in the form of order id or timestamp. <br>
The next time it fetches Trade History, it will start countering from this id. <br>
If the boundary doesn't exist in the response, the bot will consider all orders there to be uncountered and counter them all<br>

Active Order on the other hand are needed for seeding or trimming.

Both Trade History and Active Orders are fetched cyclically.

This is the simplest and the best possible method for REST exchange as it doesn't require many calls to the server.

For this method to work, an exchange must have Trade History method that returns one among other things the status (filled, partially-filled, cancelled)

All requests are pooled in a queue and popped by the rate allowed by the exchange.

#### GetOrderInfo Method

Some exchanges have Trade History that doesn't return trade status. We cannot know if it's filled or partially filled.

As the bot only counters one order with one order, countering an order with a lot of partially-filled fractured orders is not desirable.

In this method, the bot maintains in-memory open orders. Every interval, the bot sends getOrderInfo request to check the status of an order.

If the status is filled the bot will counter it and remove the order from the memory.

The bot also checks regularly if it needs to reseed or trim a side.

This method requires a lot of requests to be sent to server and may not work well if there are too many orders.

### Websocket Exchanges

Websocket exchanges supports Websocket methods for trades

Characteristics:

- Offer can be pre-set with a custom id. This is critical since in websocket a request doesn't correspond to a response.
- Successful offer request returns its status (filled, partially-filled, etc)
- Has event stream. Any trade event is broadcast into a channel.

Example: HitBTC

#### Operations

It is similar to GetOrderInfo method except that the bot doesn't need to send getOrderInfo request. The bot simply listens to a subscribed channel for trade events.

As such the bot can act very fast to counter a filled order.


#### TODO
Websocket may get disconnected. To handle such scenario, the bot caches all outbound orders. Successful request will remove the corresponding order from the cache.
There are two routes to stream orders to websocket client.
- In normal operation, orderbook will send a request to cache and to ws client.
- Once websocket is connected, fire up one time loop to send all cached orders to ws client
- Since only successful response can remove the cached request,  the operation can resume normally

### DEX (Distributed Exchange)

Trades are done on or off blockchain directly from wallet. There are many DEX platforms such as Ripple or Stellar we are working on DDEX API which implements 0x.

Characteristics
- Mix of websocket and REST methods
- Many operations depend on blockhain events (counter immediately upon trade executed event vs wait until it is finalized on ledger)

#### Operations

This is similar to Websocket. Trades are done with REST but updates come from websocket stream.

### Stupid Websocket Exchanges

Websocket that just wraps REST methods

Characteristics:
- Basically REST methods are done through websocket

Example: Okex

In REST it is possible to map request and response. In websocket it is not possible.
That's why client uses response-challenge on a param like request id to match it with incoming response.
Having one websocket for one bot(one symbol) can make things easier, since we can eliminate the possibilities of getting responses not related to that bot.
Although it is very likely such server isn't designed to handle HFT (since it just wraps REST) it is still worthwhile to do websocket method if only to save some latency from Http POST/GET.

## Duplicates

Duplicates are orders with the same price and amount. This is the no.1 undesirable. Duplicates are always the result of seeding at the wrong time.

To prevent duplicates, seeding should initiate only when there's no pending trades in the request queue. The goal is that seeding should be based on stable open orders.

The bot also has remove duplicates function that fires off regularly. However the use should not be exploited as sending order reduces the API quota.

## Extension

This app is built with Akka framework that uses Actors. The core ones are:
- BaseActor: reads config
- OpActor : contains FIFO sequence that caches requests and sends them one-by-one with rate allowed by the exchange
- OrderActor: manages order operation for a pair
    - counter : happens immediately after getFilledOrders returns uncounteres orders
    - cancel duplicates : happens if getActiveOrders returns duplicates
    - seed : happens if getActiveOrders returns no duplicates and has any side that is shorter than config's gridLevels
    - trim : happens if `bot.isStrictLevel = true` and getActiveOrders returns any side longer than config's gridLevels
- ExchangeActor: handles API's request, response specific for an exchange

To include a new exchange, these items need to be checked:
- the type of exchange
- if it's REST check if TradeHistory returns
    - execution timestamp (or any sortable id) to know the order the trades are executed
    - status (filled, partially-filled) and see if ever returns a `filled` after an order is partially-filled many times
- figure out how to sign a request
- implement the necessary API



