package org.ergoplatform.appkit;

import org.ergoplatform.P2PKAddress;
import org.ergoplatform.wallet.protocol.context.ErgoLikeParameters;
import special.sigma.BigInt;

/**
 * Interface of the provers that can be used to sign {@link UnsignedTransaction}s.
 */
public interface ErgoProver {
    /**
     * Returns Pay-To-Public-Key {@link org.ergoplatform.ErgoAddress Ergo address} of this prover.
     */
    P2PKAddress getP2PKAddress();

    /**
     * Returns Pay-To-Public-Key address of this prover (represented as {@link Address}).
     */
    Address getAddress();

    /**
     * Returns a master secret key of this prover.
     *
     * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP-32</a>
     */
    BigInt getSecretKey();

    /**
     * Signs unsigned transaction by using configured secrets.
     * This method delegate to {@link ErgoProver#sign(UnsignedTransaction, int)} with
     * baseCost = 0
     *
     * @param tx transaction to be signed
     * @return new instance of {@link SignedTransaction} which contains necessary
     * proofs
     */
    SignedTransaction sign(UnsignedTransaction tx);

    /**
     * Signs unsigned transaction by using configured secrets.
     * The prover can attach signatures (aka `proofs of knowledge`) to the inputs
     * spent by the given {@link UnsignedTransaction transaction}.
     *
     * @param tx       transaction to be signed
     * @param baseCost computational cost before this transaction validation,
     *                 the validation starts with this value and shouldn't exceed the
     *                 total block limit known to the prover (see
     *                 {@link AppkitProvingInterpreter} and
     *                 {@link ErgoLikeParameters#maxBlockCost()})
     * @return new instance of {@link SignedTransaction} which contains necessary
     * proofs
     */
    SignedTransaction sign(UnsignedTransaction tx, int baseCost);
}

