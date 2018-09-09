package me.mbcu.integrated.mmm.actors

import java.util

import akka.actor.{Actor, ActorRef, Props}
import com.neovisionaries.ws.client
import com.neovisionaries.ws.client.{WebSocketFactory, WebSocketListener, WebSocketState}
import me.mbcu.integrated.mmm.actors.WsActor._
import me.mbcu.integrated.mmm.ops.Definitions
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode
import me.mbcu.integrated.mmm.utils.MyLogging
import play.api.libs.json.{JsValue, Json}

object WsActor {
  def props(): Props = Props(new WsActor())

  object WsConnected

  case class WsDisconnected(reconnectMs: Int)

  case class WsConnect(url: String)

  case class SendJs(jsValue: JsValue)

  case class WsGotText(text: String)

  case class WsError(msg: String)

}

class WsActor() extends Actor with MyLogging{
  private var ws : Option[com.neovisionaries.ws.client.WebSocket] = None
  private var main: Option[ActorRef] = None

  override def receive: Receive = {

    case "start" =>
      main = Some(sender)
      main foreach(_ ! WsDisconnected(Definitions.Settings.wsInitSeconds))

    case WsConnect(url) =>
      ws match {
        case None =>
          val factory = new WebSocketFactory
          val websocket = factory.createSocket(url)
          websocket.addListener(ScalaWebSocketListener)
          ws = Some(websocket)
          websocket.connect

        case _ => // don't init a connecting client

      }

    case SendJs(jsValue) =>
      ws match {
        case Some(w) =>
          if (w.isOpen){
            ws.foreach(_.sendText(Json.stringify(jsValue)))
          } else {
            error("WsActor# websocket is not open")
          }
        case _ =>
      }

    case WsError(msg) =>
      error(msg)
      ws foreach(_.disconnect("server down")) // is it safe called multiple times ? If this is called after new client is init then ...

    case a: WsDisconnected =>
      ws = None
      main foreach(_ ! a)
  }

  object ScalaWebSocketListener extends WebSocketListener {
    val fatal = Some(ShutdownCode.fatal.id)
    val recover = Some(ShutdownCode.recover.id)

    override def onConnected(websocket: client.WebSocket, headers: util.Map[String, util.List[String]]): Unit = main foreach (_ ! WsConnected)

    def onTextMessage(websocket: com.neovisionaries.ws.client.WebSocket, data: String) : Unit =
      main foreach (_ ! WsGotText(data))

    override def onStateChanged(websocket: client.WebSocket, newState: WebSocketState): Unit = {}
    override def handleCallbackError(x$1: com.neovisionaries.ws.client.WebSocket, t: Throwable): Unit =
      self ! WsError(s"WsActor#handleCallbackError: ${t.getMessage}")

    override def onBinaryFrame(x$1: com.neovisionaries.ws.client.WebSocket, f: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onBinaryMessage(x$1: com.neovisionaries.ws.client.WebSocket, d: Array[Byte]): Unit = {}
    override def onCloseFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onConnectError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException): Unit =
    self ! WsError(s"WsActor#onConnectError: ${e.getMessage}")


    override def onContinuationFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onDisconnected(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame, x$3: com.neovisionaries.ws.client.WebSocketFrame, closedByServer: Boolean): Unit =
        self ! WsDisconnected(Definitions.Settings.wsInitSeconds)


    override def onError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException): Unit =
    self ! WsError(s"WsActor#onError: ${e.getMessage}")


    override def onFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onFrameError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException, x$3: com.neovisionaries.ws.client.WebSocketFrame): Unit =
    self ! WsError(s"WsActor#onFrameError: ${e.getMessage}")

    override def onFrameSent(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onFrameUnsent(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onMessageDecompressionError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException, x$3: Array[Byte]): Unit =
    self ! WsError(s"WsActor#onMessageDecompressionError: ${e.getMessage}")

    override def onMessageError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException, x$3: java.util.List[com.neovisionaries.ws.client.WebSocketFrame]): Unit =
    self ! WsError(s"WsActor#onMessageError: ${e.getMessage}")

    override def onPingFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onPongFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onSendError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException, x$3: com.neovisionaries.ws.client.WebSocketFrame): Unit =
    self ! WsError(s"WsActor#onSendError: ${e.getMessage}")

    override def onSendingFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onSendingHandshake(x$1: com.neovisionaries.ws.client.WebSocket, x$2: String, x$3: java.util.List[Array[String]]): Unit = {}
    override def onTextFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onTextMessageError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException, x$3: Array[Byte]): Unit =
    self ! WsError(s"WsActor#onTextMessageError: ${e.getMessage}")

    override def onThreadCreated(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.ThreadType, x$3: Thread): Unit = {}
    override def onThreadStarted(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.ThreadType, x$3: Thread): Unit = {}
    override def onThreadStopping(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.ThreadType, x$3: Thread): Unit = {}
    override def onUnexpectedError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException): Unit =
    self ! WsError(s"WsActor#onUnexpectedError: ${e.getMessage}")

  }



}
