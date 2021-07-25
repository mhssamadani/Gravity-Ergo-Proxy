package org.ergoplatform.appkit.impl

import org.ergoplatform.P2PKAddress
import org.ergoplatform.appkit._
import sigmastate.eval.CostingSigmaDslBuilder
import special.sigma.BigInt
import sigmastate.utils.Helpers._  // don't remove, required for Scala 2.11

class ErgoProverImpl(_ctx: BlockchainContextImpl,
                     _prover: AppkitProvingInterpreter) extends ErgoProver {
  override def getP2PKAddress: P2PKAddress = {
    val pk = _prover.pubKeys(0)
    JavaHelpers.createP2PKAddress(pk, _ctx.getNetworkType.networkPrefix)
  }

  override def getAddress = new Address(getP2PKAddress)

  override def getSecretKey: BigInt =
    CostingSigmaDslBuilder.BigInt(_prover.secretKeys.get(0).privateInput.w)

  override def sign(tx: UnsignedTransaction): SignedTransaction =
    sign(tx, baseCost = 0)

  override def sign(tx: UnsignedTransaction, baseCost: Int): SignedTransaction = {
    val txImpl = tx.asInstanceOf[UnsignedTransactionImpl]
    val boxesToSpend = JavaHelpers.toIndexedSeq(txImpl.getBoxesToSpend)
    val dataBoxes = JavaHelpers.toIndexedSeq(txImpl.getDataBoxes)
    val (signed, cost) = _prover.sign(txImpl.getTx, boxesToSpend, dataBoxes, txImpl.getStateContext, baseCost).getOrThrow
    new SignedTransactionImpl(_ctx, signed, cost)
  }
}

