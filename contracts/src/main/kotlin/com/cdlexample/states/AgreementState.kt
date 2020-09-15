package com.cdlexample.states

import com.cdlexample.contracts.AgreementContract
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.util.*

// *********
// * State *
// *********
@BelongsToContract(AgreementContract::class)
data class AgreementState(val status: Status,
        val buyer: Party,
                          val seller: Party,
                          val goods: String,
                          val price: Amount<Currency>,
                          val proposer: Party,
                          val consenter: Party,
                          val rejectionReason: String? = null,
                          val rejectedBy: Party?= null,
                          override val participants: List<AbstractParty> = listOf(buyer, seller),
                          override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {


}


interface AgreementStatus {
    class Proposed: Status
    class Rejected: Status
    class Agreed: Status

}

@CordaSerializable
interface Status


//data class Status<T: Status>(val value: T)