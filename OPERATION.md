# Rest Basics

Basically the bot operates by using the API's
- getActiveOrders
- getFilledOrders

`getActiveOrders` gets currently active orders, used for seeding empty side or trimming extra offers.

`getFilledOrders` gets filled orders, used for counter.

The bot saves the last order countered on a text file, in the form of order id or timestamp. <br>
The next time it queries getFilledOrders, all filled orders starting from this boundary will be countered. <br>
If the boundary doesn't exist in the response, the bot will consider all orders there to be uncountered.<br>

## Operation

This app is built with Akka framework that uses Actors. The core ones are:
- BaseActor: reads config
- OpActor : contains FIFO sequence that caches requests and sends them one-by-one with rate allowed by the exchange
- OrderActor: manages order operation for a pair
    - counter : happens immediately after getFilledOrders returns uncounteres orders
    - cancel duplicates : happens if getActiveOrders returns duplicates
    - seed : happens if getActiveOrders returns no duplicates and has any side that is shorter than config's gridLevels
    - trim : happens if `bot.isStrictLevel = true` and getActiveOrders returns any side longer than config's gridLevels
- ExchangeActor: handles API's request, response specific for an exchange



