package com.cdlexample.contracts

import com.cdlexample.states.AgreementState
import com.cdlexample.states.AgreementStatus.*
import com.cdlexample.states.DummyState
import com.cdlexample.states.Status
import com.cdlexample.states.StatusState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class AgreementContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.cdlexample.contracts.AgreementContract"
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Propose : Commands
        class Repropose: Commands
        class Reject: Commands
        class Agree: Commands
        class Complete: Commands
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {

        verifyPathConstraints<AgreementState>(tx)
        verifyPathConstraints(tx, AgreementState::class.java)
        verifyUniversalConstraints(tx)
        verifyStatusConstraints(tx)
        verifyLinearIDConstraints(tx)
        verifySigningConstraints(tx)
        verifyCommandConstraints(tx)

    }

    // Kotlin version
    inline fun <reified T: StatusState> verifyPathConstraints(tx: LedgerTransaction) = verifyPathConstraints(tx, T::class.java)

    // Java version
    fun <T: StatusState> verifyPathConstraints(tx: LedgerTransaction, clazz: Class<T>){

        val txPath = getPath(tx, clazz)

        // todo: build txPath builder in Contract utils - including the additional states builder (needed before can merge back into master)
        // todo: how much of verify path constraints can be moved to ContractUtils


        val pathMap = mapOf<Status?, List<PathConstraint<T>>>(
            null to listOf(
                    PathConstraint(Commands.Propose(), PROPOSED, MultiplicityConstraint(0))
            ),
            PROPOSED to listOf(
                    PathConstraint(Commands.Reject(), REJECTED),
                    PathConstraint(Commands.Agree(), AGREED)
            ),
            REJECTED to listOf(
                    PathConstraint(Commands.Repropose(), PROPOSED)
            ),
            AGREED to listOf(
                    PathConstraint(Commands.Complete(), null, outputMultiplicityConstraint = MultiplicityConstraint(0))
            )
        )
        val inputStatus = requireSingleInputStatus(tx, clazz)
        val allowedPaths = pathMap[inputStatus]

        requireThat {
            "Input status must have a list of PathConstraints defined." using (allowedPaths != null)
            "txPath must be allowed by PathConstraints for inputStatus $inputStatus" using verifyPath(txPath, allowedPaths!!)
        }
    }

    fun verifyUniversalConstraints(tx: LedgerTransaction){

        val allStates = tx.inputsOfType<AgreementState>() + tx.outputsOfType<AgreementState>()

        for (s in allStates) {
            requireThat {
                "The buyer and seller must be different Parties." using (s.buyer != s.seller)
                "The proposer must be either the buyer or the seller." using (listOf(s.buyer, s.seller).contains(s.proposer))
                "The consenter must be either the buyer or the seller." using (listOf(s.buyer, s.seller).contains(s.consenter))
                "The consenter and proposer must be different Parties." using (s.consenter != s.proposer)
            }
        }
    }

    fun verifyStatusConstraints(tx: LedgerTransaction){
        val allStates = tx.inputsOfType<AgreementState>() + tx.outputsOfType<AgreementState>()

        // Note, in kotlin non-nullable properties must be populated, hence only need to check the nullable properties of the AgreementState
        for (s in allStates) {

            when(s.status){
                PROPOSED -> {
                    requireThat {
                        "When status is Proposed rejectionReason must be null" using (s.rejectionReason == null)
                        "When status is Rejected rejectedBy must be null" using (s.rejectedBy == null)
                    }
                }
                REJECTED -> {
                    requireThat {
                        "When status is Rejected rejectionReason must not be null" using (s.rejectionReason != null)
                        "When status is Rejected rejectedBy must not be null" using (s.rejectedBy != null)
                    }
                }
                AGREED -> {}
            }
        }
    }

    fun verifyLinearIDConstraints(tx: LedgerTransaction){}

    fun verifySigningConstraints(tx: LedgerTransaction){}

    fun verifyCommandConstraints(tx: LedgerTransaction){}








}