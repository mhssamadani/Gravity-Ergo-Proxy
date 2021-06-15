package helpers

import java.io.{PrintWriter, StringWriter}
import java.math.BigInteger

import javax.inject.{Inject, Singleton}
import org.ergoplatform.{ErgoAddress, ErgoAddressEncoder}
import org.ergoplatform.appkit.{Address, JavaHelpers, NetworkType}
import scorex.util.encode.Base16
import sigmastate.basics.DLogProtocol.DLogProverInput
import special.sigma.GroupElement



@Singleton
class Utils @Inject()() {
  private val secureRandom = new java.security.SecureRandom

  def getStackTraceStr(e: Throwable): String = {
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    e.printStackTrace(pw)
    sw.toString
  }

  def toHexString(array: Array[Byte]): String = Base16.encode(array)

  def toByteArray(s: String): Array[Byte] = Base16.decode(s).get

  /**
   * convert hex represent to GroupElement
   * @param data String
   * @return a GroupElement object
   */
  def hexToGroupElement(data: String): GroupElement = {
    JavaHelpers.decodeStringToGE(data)
  }

  /**
   * Generate a secure random bigint
   * @return BigInt
   */
  def randBigInt: BigInt = new BigInteger(256, secureRandom)

  /**
  *
   * @param sk: Secret
   * @return Address
   */
  def getAddressFromSk(sk: BigInteger) = new Address(JavaHelpers.createP2PKAddress(DLogProverInput(sk).publicImage, Configs.addressEncoder.networkPrefix))


  val addressEncoder = new ErgoAddressEncoder(Configs.networkType.networkPrefix)
  def getAddress(address: String): ErgoAddress = addressEncoder.fromString(address).get

}
