package me.mbcu.integrated.mmm.ops.common

import java.math.BigInteger
import java.security.MessageDigest

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

abstract class AbsRequest {

  private val md5: MessageDigest = MessageDigest.getInstance("MD5")

  def md5(secretKey : String, data: String): String = {
    import java.nio.charset.StandardCharsets
    md5.update(StandardCharsets.UTF_8.encode(data))
    String.format("%032x", new BigInteger(1, md5.digest)).toUpperCase
  }

 def signHmacSHA512(isCapital : Boolean, secret : String, data : String): String = {
    val signedSecret = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA512")   //Crypto Funs : 'SHA256' , 'HmacSHA1'
    val mac = Mac.getInstance("HmacSHA512")
    mac.init(signedSecret)
    val res = mac.doFinal(data.getBytes("UTF-8"))
    toHex(res, isCapital)
   }

  private def toHex(bytes: Array[Byte], isCapital : Boolean = true): String = {
    val x = if (isCapital) "X" else "x"
    val bi = new BigInteger(1, bytes)
    String.format("%0" + (bytes.length << 1) + x, bi)
  }

}
