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
Depending on the settings the bot clears all existing orders, seed new orders from the starting price or leave the active orders in orderbook.
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

Websocket may disconnect. To handle such scenario, the bot caches all outbound orders. Successful request will remove the corresponding order from the cache.
There are two routes to stream orders to websocket client.
- In normal operation, orderbook will send an order (as counter, seed) to cache and to ws client.
- On websocket connected, fire up one time stream to send all cached orders to ws client
- Since only successful response can remove the cached order, those two routes are safe


## Stupid Websocket Exchanges

Websocket methods just wrap REST methods

Characteristics:

- No events stream
- Manual order check
- Since the requests and responses are the same as REST, some methods are not possible.

Example: Okex


In REST it is possible to map request and response. In websocket it is not possible.
That's why client uses response-challenge on a param like request id to match it with incoming response.
Having one websocket for one bot(one symbol) can make things easier, since we can eliminate the possibilities of getting responses not related to that bot.
Although it is very likely such server isn't designed to handle HFT (since it just wraps REST) it is still worthwhile to do websocket method if only to save some latency from Http POST/GET.


## Supported exchanges

### okexRest

- Okex returns errors reserved for sign error (10007, 10005) even a Cloudlare website for non-sign error.
As such 10007, 10005 and html response will be retried. *Make sure API key and secret are correct.*

### yobit
- The HmacSHA512 signature has to be in lowercased hex
- ~Trade returns order status.~ Trade returns order status which always indicates that the order is unfilled.
- Apparently it takes a few seconds from order entering the server to get matched in orderbook.
- ActiveOrder returns only the current amount which might have been partially filled. To get complete info, we still need to call OrderInfo on each order

### fcoin

## Operation

The bot maintains all orders in runtime memory.
It will handle server issues with retry mechanism (e.g order that fails to deliver due to temporary server error will be resent later) while trading error will be ignored (e.g lack of fund)

Bot should be started or restarted when
1. config is created or changed
2. an irrecoverable error occurs (wrong api/secret, user ban, permanent endpoint change)

## Config

#### exchange

Type: `string`<br>

Enum style name of the exghange

#### credentials

**pKey**

Type: `string`<br>

Api Key

**nonce**

Type: `string`<br>

Nonce. Set empty string if it's not needed.

**signature**

Type: `string`<br>

Secret key or signature of secret key signed with nonce.

#### pair

Type: `string`<br>

Pair or symbol in format accepted by the exchange.

#### seed

Type: `string`<br>
Enum: lastOwn, lastTicker, cont

Determines the seed (starting price) when the bot starts from shutdown state, with empty orderbook in the runtime.

**lastOwn** : cancels any active orders, sets the starting price from the bot last traded order

**lastTicker**: cancels any active orders, sets the starting price from last market ticker

**cont**: starts from currently active orders without seeding

#### gridSpace

Type: `string`<br>

Price level between orders, behavior determined by strategy.

#### buyGridLevels, sellGridLevels

Type: `int`<br>

Number of orders to be maintained in orderbook.

#### buyOrderQuantity, sellOrderQuantity

Type: `string`<br>

Amount of order.

#### quantityPower

Type: `int`<br>

Only used in **ppt**. Rate of quantity appreciation / depreciation which is denoted as (sqrt(gridSpace))^quantityPower.

#### counterScale, baseScale

Type: `int`<br>

Scale of minimum amount of counter / base currency accepted by the exchange.

Examples:
- If minimum quantity 1 -> scale = 0
- If minimum quantity 0.001 -> scale = 3
- If minimum quantity 1000 -> scale = -3

#### isHardReset (deprecated)

Type: `boolean`<br>

If true then when the bot starts it will remove all orders and seed new orders with recalculated middle price. This method will clear any holes (missing levels) but lose all ping/pong information from the old orders.

If false then the bot will only fill the hole closest to market middle price. This will preserve the ping/pong info of each order but not fill all possible holes.


#### isStrictLevels

Type: `boolean`<br>

If true then number of orders is kept according to buyGridLevels pr sellGridLevels by removing tail orders. WARNING : this may cause holes in orderbook.

#### isNoQtyCutoff

Type: `boolean`<br>

If true then order's quantity will never become zero. Instead it will be replaced with the currency's minimum amount.


#### maxPrice, minPrice (Optional)

Type: `string`<br>

Maximum and minimum price the bot will operate on

#### strategy

Type: `string`<br>

Strategy to be used. Refer to strategy section for valid names.


## Strategies

### Proportional

code: `ppt`<br>

Both base quantity and unit price are spaced by percentage point of the previous offerCreate level.

For sell direction p1 = (1 + gridSpace / 100) * p0 and q1 = q0 / ((1 + gridSpace / 100)^0.5)^quantityPower

For buy direction p1 = p0  / (1 + gridSpace / 100) and q1 = q0 * ((1 + gridSpace / 100)^0.5)^quantityPower

Pay attention minimum quantity. Ideally minimum quantity should be smaller than (gridSpace / 100 * quantity)

### Full-fixed

Code: `fullfixed`<br>

The new unit price is spaced rigidly by gridSpace, e.g. if a buy order with price X is consumed then a new sell order selling the same quantity with price X + gridSpace will be created.<br>
If a sell order with price Y is consumed then a buy order with the same quantity and price Y - gridSpace will be created.



