package me.mbcu.integrated.mmm.actors

import java.util

import akka.actor.{Actor, ActorRef, Props}
import me.mbcu.integrated.mmm.actors.WsActor._
import com.neovisionaries.ws.client
import com.neovisionaries.ws.client.{WebSocketFactory, WebSocketListener, WebSocketState}
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode
import me.mbcu.integrated.mmm.utils.MyLogging
import play.api.libs.json.{JsValue, Json}

import scala.util.{Failure, Success, Try}

object WsActor {
  def props(): Props = Props(new WsActor())

  object WsConnected

  object WsDisconnected

  case class WsConnect(url: String)

  case class SendJs(jsValue: JsValue)

  case class WsGotText(text: String)

  case class WSError(msg :String, shutdownCode : Option[Int] = None)
}

class WsActor() extends Actor with MyLogging{
  private var ws : Option[com.neovisionaries.ws.client.WebSocket] = None
  private var main: Option[ActorRef] = None

  override def receive: Receive = {

    case "start" => main = Some(sender)

    case WsConnect(url) =>
      val factory = new WebSocketFactory
      val websocket = factory.createSocket(url)
      websocket.addListener(ScalaWebSocketListener)
      ws = Some(websocket)
      Try(websocket.connect()) match {
        case Success(succ) => println(succ)
        case Failure(fail) => main.foreach(_ ! WSError(s"WsActor#'start websocket.connect': $fail", ScalaWebSocketListener.fatal))
      }

    case SendJs(jsValue) =>
      val json : String = Json.stringify(jsValue)
      ws.foreach(_.sendText(json))

  }

  object ScalaWebSocketListener extends WebSocketListener {
    val fatal = Some(ShutdownCode.fatal.id)
    val recover = Some(ShutdownCode.recover.id)

    override def onConnected(websocket: client.WebSocket, headers: util.Map[String, util.List[String]]): Unit = main foreach (_ ! WsConnected)

    def onTextMessage(websocket: com.neovisionaries.ws.client.WebSocket, data: String) : Unit =
      main foreach (_ ! WsGotText(data))

    override def onStateChanged(websocket: client.WebSocket, newState: WebSocketState): Unit = {}
    override def handleCallbackError(x$1: com.neovisionaries.ws.client.WebSocket, t: Throwable): Unit =
      main foreach(_ ! WSError(s"WsActor#handleCallbackError: ${t.getMessage}", fatal))

    override def onBinaryFrame(x$1: com.neovisionaries.ws.client.WebSocket, f: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onBinaryMessage(x$1: com.neovisionaries.ws.client.WebSocket, d: Array[Byte]): Unit = {}
    override def onCloseFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onConnectError(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketException): Unit =
      main foreach(_ ! WSError(s"WsActor#onConnectError: ${x$2.getMessage}", recover))

    override def onContinuationFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onDisconnected(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame, x$3: com.neovisionaries.ws.client.WebSocketFrame, closedByServer: Boolean): Unit =
      main foreach(_ ! WsDisconnected)

    override def onError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException): Unit =
      main foreach(_ ! WSError(s"WsActor#onError: ${e.getMessage}", fatal))


    override def onFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onFrameError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException, x$3: com.neovisionaries.ws.client.WebSocketFrame): Unit =
      main foreach(_ ! WSError(s"WsActor#onFrameError: ${e.getMessage}", recover))


    override def onFrameSent(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onFrameUnsent(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onMessageDecompressionError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException, x$3: Array[Byte]): Unit =
      main foreach(_ ! WSError(s"WsActor#onMessageDecompressionError: ${e.getMessage}", recover))


    override def onMessageError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException, x$3: java.util.List[com.neovisionaries.ws.client.WebSocketFrame]): Unit =
      main foreach(_ ! WSError(s"WsActor#onMessageError: ${e.getMessage}", recover))

    override def onPingFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onPongFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onSendError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException, x$3: com.neovisionaries.ws.client.WebSocketFrame): Unit =
      main foreach(_ ! WSError(s"WsActor#onSendError: ${e.getMessage}", fatal))

    override def onSendingFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onSendingHandshake(x$1: com.neovisionaries.ws.client.WebSocket, x$2: String, x$3: java.util.List[Array[String]]): Unit = {}
    override def onTextFrame(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.WebSocketFrame): Unit = {}
    override def onTextMessageError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException, x$3: Array[Byte]): Unit =
      main foreach(_ ! WSError(s"WsActor#onTextMessageError: ${e.getMessage}", recover))

    override def onThreadCreated(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.ThreadType, x$3: Thread): Unit = {}
    override def onThreadStarted(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.ThreadType, x$3: Thread): Unit = {}
    override def onThreadStopping(x$1: com.neovisionaries.ws.client.WebSocket, x$2: com.neovisionaries.ws.client.ThreadType, x$3: Thread): Unit = {}
    override def onUnexpectedError(x$1: com.neovisionaries.ws.client.WebSocket, e: com.neovisionaries.ws.client.WebSocketException): Unit =
      main foreach(_ ! WSError(s"WsActor#onUnexpectedError: ${e.getMessage}", fatal))

  }



}
