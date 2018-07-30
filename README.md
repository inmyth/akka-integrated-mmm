# Integrated MMM Market Making Bot

An automated grid/ping-pong market-making bot supporting various exchanges.

[Exchanges and the handling](./EXCHANGES.md)

[Operation](./OPERATION.md)

[Version notes](./VERSIONS.md)

## General

One bot handles one symbol(pair).

When starting empty: use `lastTicker`, `lastOwn`, or a value as seed starting price.

When re-starting:
- If config remains the same, use `cont` to continue from last session, all uncountered offers will be countered.
- If config has changed, use other seed methods to clear all active orders and start anew. All uncountered offers won't be countered.

## Running
Use [config-template](./config-template) as config reference.

One bot in [bots] will market-make on one pair.

Clone the repo and run this to build and package it into a fat jar.

```
sbt assembly
```

(result jar is in /target)

To run

```
java -jar program.jar <path to config file> <path to log directory> <path to bot cache directory>
```

## Config

#### exchange

Type: `string`<br>

Name of the exchange. See [Exchanges](./EXCHANGES.md) for supported exchanges.

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



