package me.mbcu.integrated.mmm.ops.common

import java.math.BigInteger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

abstract class AbsRestRequest {

  private val md5: MessageDigest = MessageDigest.getInstance("MD5")

  def md5(secretKey: String, data: String): String = {
    import java.nio.charset.StandardCharsets
    md5.update(StandardCharsets.UTF_8.encode(data))
    String.format("%032x", new BigInteger(1, md5.digest)).toUpperCase
  }

  def signHmacSHA512(secret: String, data: String): Array[Byte] = signHmac(secret, data, "HmacSHA512")

  def signHmac(secret: String, data: String, fun: String): Array[Byte] = {
    val signedSecret = new SecretKeySpec(secret.getBytes("UTF-8"), fun)
    val mac = Mac.getInstance(fun)
    mac.init(signedSecret)
    mac.doFinal(data.getBytes("UTF-8"))
  }

   def toHex(bytes: Array[Byte], isCapital: Boolean = true): String = {
    val x = if (isCapital) "X" else "x"
    val bi = new BigInteger(1, bytes)
    String.format("%0" + (bytes.length << 1) + x, bi)
  }

  def signHmacSHA1(secret: String, data: String): Array[Byte] = signHmac(secret, data, "HmacSHA1")

  def signHmacSHA256(secret: String, data: String): Array[Byte] = signHmac(secret, data, "HmacSHA256")

  def sortToForm(params: Map[String, String]) :String = params.toSeq.sortBy(_._1).map(c => s"${c._1}=${urlEncode(c._2)}").mkString("&")

  def urlEncode(in: String): String = URLEncoder.encode(in, StandardCharsets.UTF_8.name())

}
