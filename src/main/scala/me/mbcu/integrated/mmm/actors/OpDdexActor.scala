package me.mbcu.integrated.mmm.actors

import java.math.BigInteger

import akka.actor.{Actor, ActorRef, Cancellable}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.{NewOrder, SendRest}
import me.mbcu.integrated.mmm.ops.common.{AbsExchange, AbsOpActor, Bot}
import me.mbcu.integrated.mmm.utils.MyLogging
import org.bouncycastle.util.encoders.Hex
import org.web3j.crypto.{Credentials, ECKeyPair, Sign}

import scala.concurrent.ExecutionContextExecutor

class OpDdexActor(exchangeDef: AbsExchange, bots: Seq[Bot], fileActor: ActorRef) extends AbsOpActor(exchangeDef, bots, fileActor) with MyLogging{
  private implicit val ec: ExecutionContextExecutor = global
  private var base: Option[ActorRef] = None
  private var rest: Option[ActorRef] = None
  private var dqCancellable: Option[Cancellable] = None
  private val q = new scala.collection.mutable.Queue[SendRest]
  private val nos = scala.collection.mutable.Set[NewOrder]()

  override def receive: Receive = {

    case "start" =>
      base = Some(sender())
      self ! "test"

//7196933fe363871920c59be78aa5c478bf6e6271532db5d0ce3b090518f91f03
    case "test" =>
//      val privateKey = ""
//      import org.web3j.crypto.Hash
//      import org.web3j.utils.Numeric
//      val test = "HYDRO-AUTHENTICATION@" + "aa"
//      val hash = Hash.sha3(Numeric.toHexStringNoPrefix(test.getBytes))
//      val message = "\\u0019Ethereum Signed Message:\n" + hash.length + hash
//      val message2 = "\\\\x19Ethereum Signed Message:\n" + hash.length + hash
//
//      println(hash)
//
//      println(s"Message: $message")
//
//      val cred = Credentials.create(privateKey)
//      println(s"Address:${cred.getAddress}")
//      val signatureData = Sign.signMessage(message.getBytes(), cred.getEcKeyPair)
//      import org.web3j.utils.Numeric
//      val r = Numeric.toHexString(signatureData.getR)
//      val s = Numeric.toHexString(signatureData.getS)
//      val v = "%02X".format(signatureData.getV)
//
//      val signed = s"${Numeric.toHexString(signatureData.getR)}${Numeric.toHexString(signatureData.getS).substring(2)}${"%02X".format(signatureData.getV).toLowerCase()}"
//      println(signed)

      import org.web3j.crypto.Credentials
      import org.web3j.crypto.Hash
      import org.web3j.crypto.Sign
      import org.web3j.utils.Numeric
      val privateKey1 = "fba4137335dc20dc23ad3dcd9f4ad728370b09131a6a14abf6c839748700780d"
      val credentials = Credentials.create(privateKey1)
      System.out.println("Address: " + credentials.getAddress)

//      val hash = Hash.sha3(Numeric.toHexStringNoPrefix("TEST".getBytes))
//      System.out.println("Hash" + hash)
//
//      val message = "      val message = "\u0019Ethereum Signed Message:\n32" + hash.length + hash
      //      println(s"Message: $message")" + hash.length + hash
//      println(s"Message: $message")
//
//      val data = message.getBytes
//
//      val signature = Sign.signMessage(data, credentials.getEcKeyPair)
//
//      System.out.println("R: " + Numeric.toHexString(signature.getR))
//      System.out.println("S: " + Numeric.toHexString(signature.getS))
//      System.out.println("V: " + Integer.toString(signature.getV))
//          val signed = s"${Numeric.toHexString(signature.getR)}${Numeric.toHexString(signature.getS).substring(2)}${"%02X".format(signature.getV).toLowerCase()}"
//      println(signed)
//
//
//      val pub = Sign.signedMessageToKey(message.getBytes, signature)
//      println(s"Pub key: $pub")
//      println(credentials.getEcKeyPair.getPublicKey)

      // sign(keccak256("\x19Ethereum Signed Message:\n" + len(message) + message)))
      val test = "TEST"
      val message = "\u0019Ethereum Signed Message:\n32" + test.length + test
      val hash = Hash.sha3(message)
      val signature = Sign.signMessage(hash.getBytes, credentials.getEcKeyPair)
      val r = Numeric.toHexString(signature.getR)
      val s = Numeric.toHexString(signature.getS)
      val v = "%02X".format(signature.getV)
      val signed = s"${Numeric.toHexString(signature.getR)}${Numeric.toHexString(signature.getS).substring(2)}${"%02X".format(signature.getV).toLowerCase()}"
      println(signed)
      //0x5d46da45a1af583990eeb142199e593480d0b1e5f88903e287ff67209140dcc646384d275b500ded85ca16dea5ff369532a27fc92721189cef68d652ab18580a1c

    //      val sData = new org.web3j.crypto.Sign.SignatureData(
//
//          new BigInteger(v, 16).toByteArray,
//          Hex.decode(r),
//        Hex.decode(s))
//      val extractedPublicKey = org.web3j.crypto.Sign.signedMessageToKey(signed.getBytes, sData).toString(16)
//      println(extractedPublicKey)
  }
}
