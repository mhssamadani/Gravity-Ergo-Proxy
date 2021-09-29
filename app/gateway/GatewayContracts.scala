package gateway

import org.ergoplatform.appkit._
import scorex.crypto.hash.Digest32
import sigmastate.Values.ErgoTree
import helpers.Configs


class GatewayContracts(ctx: BlockchainContext) {
  var oracleAddress: String = _
  var pulseAddress: String = _
  var gravityAddress: String = _
  var tokenRepoAddress: String = _
  var signalAddress: String = _

  lazy val gravityScript =
    s"""{
       |  val newConsuls = OUTPUTS(0).R5[Coll[Coll[Byte]]].get
       |  // make Coll[GroupElement] for sign validation from input's consuls witch are in [Coll[Coll[Byte]]] format
       |  val consuls: Coll[GroupElement] = SELF.R5[Coll[Coll[Byte]]].get.map({(consul: Coll[Byte]) => decodePoint(consul)})
       |
       |  // each sign made two part a (a groupelemet) and z(a bigint)
       |  val signs_a = OUTPUTS(0).R6[Coll[GroupElement]].get
       |  val signs_z = OUTPUTS(0).R7[Coll[BigInt]].get
       |
       |  // get round and lastRound value
       |  val round = OUTPUTS(0).R8[Long].get
       |  val lastRound = SELF.R8[Long].get
       |  // making the message by concatenation of newConsoles
       |  val msg = newConsuls(0) ++ newConsuls(1) ++ newConsuls(2) ++ newConsuls(3) ++ newConsuls(4) ++ longToByteArray(round)
       |
       | // Verify sign base on schnorr protocol
       |  val validateSign = {(v: ((Coll[Byte], GroupElement), (GroupElement, BigInt))) => {
       |     val e: Coll[Byte] = blake2b256(v._1._1) // weak Fiat-Shamir
       |     val eInt = byteArrayToBigInt(e) // challenge as big integer
       |     val g: GroupElement = groupGenerator
       |     val l = g.exp(v._2._2)
       |     val r = v._2._1.multiply(v._1._2.exp(eInt))
       |     if (l == r) 1 else 0
       |  }}
       |
       |  // validate each sign and consul
       |  val count = validateSign( ( (msg, consuls(0)), (signs_a(0), signs_z(0)) ) ) +
       |              validateSign( ( (msg, consuls(1)), (signs_a(1), signs_z(1)) ) ) +
       |              validateSign( ( (msg, consuls(2)), (signs_a(2), signs_z(2)) ) ) +
       |              validateSign( ( (msg, consuls(3)), (signs_a(3), signs_z(3)) ) ) +
       |              validateSign( ( (msg, consuls(4)), (signs_a(4), signs_z(4)) ) )
       |
       |  val bftValueIn = SELF.R4[Int].get
       |  val bftValueOut = OUTPUTS(0).R4[Int].get
       |  sigmaProp (
       |    allOf(Coll(
       |      round > lastRound,
       |
       |      // check output's bftvalue be valid
       |      bftValueIn == bftValueOut,
       |      OUTPUTS(0).propositionBytes == SELF.propositionBytes,
       |      OUTPUTS(0).value >= SELF.value,     // value of output should be bigger or equal to input's
       |      OUTPUTS(0).tokens(0)._1 == tokenId, // Build-time assignment, it's the NFT tocken
       |      OUTPUTS(0).tokens(0)._2 == 1,       // check NFT count
       |
       |       // check count be bigger than input's bftvalue. to change the consuls,
       |       // it's important to sign at least equal to input's bftvalue
       |      count >= bftValueIn
       |
       |  )))
       |}""".stripMargin

  lazy val signalScript: String =
    s"""{
       | sigmaProp(allOf(Coll(
       |  // To prevent placing two signal boxes in one transaction
       |  SELF.id == INPUTS(0).id,
       |
       |  // Expect pulseId to be in R4 of the signal box
       |  SELF.R4[Long].isDefined,
       |  // There must be data in the R5 of the signal box
       |  // TODO: this data must be equal to msgHash in pulseId
       |  SELF.R5[Coll[Byte]].isDefined,
       |
       |  // Id of first token in signal box must be equal to tokenRepoId with value 1
       |  SELF.tokens(0)._1 == tokenRepoId,
       |  SELF.tokens(0)._2 == 1,
       |
       |  // Contract of second INPUT must be equal to tokenRepoContractHash
       |  blake2b256(INPUTS(1).propositionBytes) == tokenRepoContractHash,
       |  // Id of first token in token repo box must be equal to tokenRepoId
       |  INPUTS(1).tokens(0)._1 == tokenRepoId,
       |
       |  // Contract of first OUTPUT must be equal to tokenRepoContractHash
       |  blake2b256(OUTPUTS(0).propositionBytes) == tokenRepoContractHash
       | )))
       |}""".stripMargin

  lazy val tokenRepoScript: String =
    s"""{
       | val checkPulse = {allOf(Coll(
       |  // Contract of new tokenRepo box must be equal to contract of tokenRepo box in input
       |  SELF.propositionBytes == OUTPUTS(1).propositionBytes,
       |  // Id of first token in tokenRepo box must be equal to tokenRepoId
       |  SELF.tokens(0)._1 == tokenRepoId,
       |  // The transaction in which the tokenRepo box is located as the input box must contain the first input box containing the pulseNebulaNFT token
       |  INPUTS(0).tokens(0)._1 == pulseNebulaNFT,
       |  // OUTPUTS(1) is box of tokenRepo, OUTPUTS(2) is box of signal
       |  // In scenario send_value_to_subs, a token is transferred from the tokenRepo to the signal box, also the minValue value must be sent to the signal box.
       |  blake2b256(OUTPUTS(2).R5[Coll[Byte]].get) == OUTPUTS(0).R4[Coll[Byte]].get,
       |  OUTPUTS(1).tokens(0)._1 == tokenRepoId,
       |  OUTPUTS(1).tokens(0)._2 == SELF.tokens(0)._2 - 1,
       |  OUTPUTS(1).value == SELF.value - minValue,
       |  OUTPUTS(2).tokens(0)._1 == tokenRepoId,
       |  OUTPUTS(2).tokens(0)._2 == 1,
       |  OUTPUTS(2).value == minValue
       | ))}
       | // In scenario spend signal box in USER-SC, the token in the signal  box and its Erg must be returned to the tokenRepo.
       | val checkSignal = {allOf(Coll(
       |  OUTPUTS(0).value == SELF.value + minValue,
       |  OUTPUTS(0).tokens(0)._1 == tokenRepoId,
       |  OUTPUTS(0).tokens(0)._2 == INPUTS(1).tokens(0)._2 + 1,
       |  OUTPUTS(0).propositionBytes == SELF.propositionBytes
       | ))}
       | sigmaProp(checkPulse || checkSignal)
       |}""".stripMargin

  lazy val pulseScript: String =
    s"""{
       | // We expect msgHash to be in R4
       | val msgHash = OUTPUTS(0).R4[Coll[Byte]].get
       |
       | // We expect first option of signs to be in R6 [a, a, ..] TODO: after fix AOT in ergo this can be change to [(a, z), (a, z), ...]
       | val signs_a = OUTPUTS(0).R5[Coll[GroupElement]].get
       | // We expect second option of signs to be in R7 [z, z, ..]
       | val signs_z = OUTPUTS(0).R6[Coll[BigInt]].get
       |
       | val currentPulseId = SELF.R7[Long].get
       | val signalCreated: Int = SELF.R8[Int].get
       |
       | // Verify signs
       | val validateSign: Int = {(v: ((Coll[Byte], GroupElement), (GroupElement, BigInt))) => {
       |    val e: Coll[Byte] = blake2b256(v._1._1) // weak Fiat-Shamir
       |    val eInt = byteArrayToBigInt(e) // challenge as big integer
       |    val g: GroupElement = groupGenerator
       |    val l = g.exp(v._2._2)
       |    val r = v._2._1.multiply(v._1._2.exp(eInt))
       |    if (l == r) 1 else 0
       | }}
       |
       | val publicCheckOutBoxes: Boolean = {(box: (Box, Box)) => {
       |    allOf(Coll(
       |      // We expect one tokenNFT for pulse contract to be in token(0)
       |      box._2.tokens(0)._1 == pulseNebulaNFT,
       |      // Value of new pulse box must be greater than equal to value of pulse box in input
       |      box._2.value >= box._1.value,
       |      // Contract of new pulse box must be equal to contract of pulse box in input
       |      box._2.propositionBytes == box._1.propositionBytes
       |    ))
       | }}
       |
       | val verified = if (signalCreated == 1) {
       |    // should to be box of oracle contract
       |    val dataInput = CONTEXT.dataInputs(0)
       |    // We Expect number of oracles that verified msgHash of in pulseId bigger than bftValue
       |    val check_bftCoefficient = {
       |      // We expect one tokenNFT for oracle contract to be in token(0) of this box
       |      if (dataInput.tokens(0)._1 == oracleNebulaNFT) {
       |        // get BftCoefficient from R4 of oracleContract Box
       |        val bftValue = dataInput.R4[Int].get
       |        // Get oracles from R5 of oracleContract Box and convert to Coll[GroupElement]
       |        val oracles: Coll[GroupElement] = dataInput.R5[Coll[Coll[Byte]]].get.map({ (oracle: Coll[Byte]) =>
       |            decodePoint(oracle)
       |        })
       |        val count : Int= validateSign(((msgHash, oracles(0)),(signs_a(0), signs_z(0)))) + validateSign(((msgHash, oracles(1)),(signs_a(1), signs_z(1)))) + validateSign(((msgHash, oracles(2)),(signs_a(2), signs_z(2)))) + validateSign(((msgHash, oracles(3)),(signs_a(3), signs_z(3)))) + validateSign(((msgHash, oracles(4)),(signs_a(4), signs_z(4))))
       |        count >= bftValue
       |      }
       |     else false
       |    }
       |    val checkOUTPUTS = {
       |     if(SELF.tokens(0)._1 == pulseNebulaNFT) {
       |     val dataType = OUTPUTS(0).R9[Int].get
       |      allOf(Coll(
       |        publicCheckOutBoxes((SELF, OUTPUTS(0))),
       |        // We expect pulseId to be in R7 and increase pulseId in out box
       |        OUTPUTS(0).R7[Long].get == currentPulseId + 1,
       |        OUTPUTS(0).R8[Int].get == 0,
       |        dataType == SELF.R9[Int].get,
       |        dataType >= 0,
       |        dataType < 3
       |
       |      ))
       |     }
       |     else false
       |    }
       |    (check_bftCoefficient && checkOUTPUTS)
       |  } else {
       |     val checkRegisters = {
       |      val dataType = OUTPUTS(0).R9[Int].get
       |
       |      allOf(Coll(
       |         SELF.R4[Coll[Byte]].get == msgHash,
       |         SELF.R5[Coll[GroupElement]].get == signs_a,
       |         SELF.R6[Coll[BigInt]].get == signs_z,
       |         // We expect pulseId to be in R7 and increase pulseId in out box
       |         OUTPUTS(0).R7[Long].get == currentPulseId,
       |         OUTPUTS(0).R8[Int].get == 1,
       |         dataType == SELF.R9[Int].get,
       |         dataType >= 0,
       |         dataType < 3,
       |
       |         // Expect pulseId to be in R4 of the signal box
       |         OUTPUTS(2).R4[Long].get == currentPulseId,
       |         // There must be data in the R5 of the signal box
       |         // TODO: this data must be equal to msgHash
       |         OUTPUTS(2).R5[Coll[Byte]].isDefined
       |      ))
       |     }
       |     val checkOUTPUTS = {
       |       if(SELF.tokens(0)._1 == pulseNebulaNFT) {
       |        allOf(Coll(
       |          publicCheckOutBoxes((SELF, OUTPUTS(0))),
       |
       |          // Contract of second INPUT/OUTPUT must be equal to tokenRepoContractHash
       |          blake2b256(INPUTS(1).propositionBytes) == tokenRepoContractHash,
       |          blake2b256(OUTPUTS(1).propositionBytes) == tokenRepoContractHash,
       |
       |          // Contract of third OUTPUT must be equal to signalContractHash
       |          blake2b256(OUTPUTS(2).propositionBytes) == signalContractHash
       |        ))
       |       }
       |       else false
       |    }
       |    (checkRegisters && checkOUTPUTS)
       |  }
       |
       |
       | sigmaProp ( verified )
       |
       | }
    """.stripMargin

  lazy val oracleScript: String =
    s"""{
       | // We get oracles from R5
       | val newSortedOracles = OUTPUTS(0).R5[Coll[Coll[Byte]]].get
       |
       | // We expect first option of signs to be in R6 [a, a, ..] TODO: after fix AOT in ergo this can be change to [(a, z), (a, z), ...]
       | val signs_a = OUTPUTS(0).R6[Coll[GroupElement]].get
       | // We expect first option of signs to be in R7 [z, z, ..]
       | val signs_z = OUTPUTS(0).R7[Coll[BigInt]].get
       |
       | // should to be box of gravity contract
       | val dataInput = CONTEXT.dataInputs(0)
       |
       | // Verify signs
       | val validateSign = {(v: ((Coll[Byte], GroupElement), (GroupElement, BigInt))) => {
       |    val e: Coll[Byte] = blake2b256(v._1._1) // weak Fiat-Shamir
       |    val eInt = byteArrayToBigInt(e) // challenge as big integer
       |    val g: GroupElement = groupGenerator
       |    val l = g.exp(v._2._2)
       |    val r = v._2._1.multiply(v._1._2.exp(eInt))
       |    if (l == r) 1 else 0
       | }}
       |
       | val check_bftCoefficient = {
       |   // We expect in tokens of gravity contract there is NFT token of gravity also five oracles at R5 of OUTPUTS(0)
       |   if (dataInput.tokens(0)._1 == gravityNFT && newSortedOracles.size == 5) {
       |     // We get bftCoefficient from R4
       |     val bftValueIn = SELF.R4[Int].get
       |     val bftValueOut = OUTPUTS(0).R4[Int].get
       |     // We expect in R5 of gravity contract there are consuls
       |     val consuls: Coll[GroupElement] = dataInput.R5[Coll[Coll[Byte]]].get.map({ (consul: Coll[Byte]) =>
       |       decodePoint(consul)
       |     })
       |     // Concatenation all new oracles for create, newSortedOracles as a Coll[Byte] and verify signs.
       |     val newSortedOracles1 = newSortedOracles(0) ++ newSortedOracles(1) ++ newSortedOracles(2) ++ newSortedOracles(3) ++ newSortedOracles(4)
       |     val count = validateSign(((newSortedOracles1, consuls(0)),(signs_a(0), signs_z(0)))) + validateSign(((newSortedOracles1, consuls(1)),(signs_a(1), signs_z(1)))) + validateSign(((newSortedOracles1, consuls(2)),(signs_a(2), signs_z(2)))) + validateSign(((newSortedOracles1, consuls(3)),(signs_a(3), signs_z(3)))) + validateSign(((newSortedOracles1, consuls(4)),(signs_a(4), signs_z(4))))
       |     // We Expect the numbers of consuls that verified the new oracles list, to be more than three. TODO: in the future, with a change in the contract, this parameter can be dynamic.
       |     bftValueIn == bftValueOut && count >= bftValueIn
       |   }
       |  else false
       | }
       |
       | val checkOUTPUT = {
       |   if(SELF.tokens(0)._1 == oracleNebulaNFT) {
       |    allOf(Coll(
       |      // We expect a NFT token for oracle contract to be in tokens(0)
       |      OUTPUTS(0).tokens(0)._1 == oracleNebulaNFT,
       |
       |      // Value of new oracle box must be greater than equal to value of oracle box in input
       |      OUTPUTS(0).value >= SELF.value,
       |      // Contract of new oracle box must be equal to contract of oracle box in input
       |      OUTPUTS(0).propositionBytes == SELF.propositionBytes
       |    ))
       |   }
       |   else false
       | }
       |
       | sigmaProp ( checkOUTPUT && check_bftCoefficient )
       |
       | }
    """.stripMargin

  lazy val gravityContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create()
      .item("tokenId", ErgoId.create(Configs.gravityTokenId).getBytes)
      .build(),
    gravityScript
  )

  lazy val tokenRepoContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create()
      .item("tokenRepoId", ErgoId.create(Configs.tokenRepoTokenId).getBytes)
      .item("pulseNebulaNFT", ErgoId.create(Configs.pulseTokenId).getBytes)
      .item("minValue", Configs.signalBoxValue)
      .build(),
    tokenRepoScript
  )
  val tokenRepoErgoTree: ErgoTree = tokenRepoContract.getErgoTree
  val tokenRepoHash: Digest32 = scorex.crypto.hash.Blake2b256(tokenRepoErgoTree.bytes)

  lazy val signalContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create()
      .item("tokenRepoContractHash", tokenRepoHash)
      .item("tokenRepoId", ErgoId.create(Configs.tokenRepoTokenId).getBytes)
      .build(),
    signalScript
  )
  val signalErgoTree: ErgoTree = signalContract.getErgoTree
  val signalHash: Digest32 = scorex.crypto.hash.Blake2b256(signalErgoTree.bytes)

  lazy val pulseContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create()
      .item("oracleNebulaNFT", ErgoId.create(Configs.oracleTokenId).getBytes)
      .item("pulseNebulaNFT", ErgoId.create(Configs.pulseTokenId).getBytes)
      .item("tokenRepoContractHash", tokenRepoHash)
      .item("signalContractHash", signalHash)
      .build(),
    pulseScript
  )

  lazy val oracleContract: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create()
      .item("gravityNFT", ErgoId.create(Configs.gravityTokenId).getBytes)
      .item("oracleNebulaNFT", ErgoId.create(Configs.oracleTokenId).getBytes)
      .build(),
    oracleScript
  )

  gravityAddress = Configs.addressEncoder.fromProposition(gravityContract.getErgoTree).get.toString
  pulseAddress = Configs.addressEncoder.fromProposition(pulseContract.getErgoTree).get.toString
  oracleAddress = Configs.addressEncoder.fromProposition(oracleContract.getErgoTree).get.toString
  tokenRepoAddress = Configs.addressEncoder.fromProposition(tokenRepoErgoTree).get.toString
  signalAddress = Configs.addressEncoder.fromProposition(signalContract.getErgoTree).get.toString

}
