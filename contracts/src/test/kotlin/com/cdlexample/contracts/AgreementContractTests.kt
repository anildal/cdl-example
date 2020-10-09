package com.cdlexample.contracts

import com.cdlexample.states.AgreementState
import com.cdlexample.states.AgreementStatus
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.util.*

// todo: make sure error messages are consistent

class AgreementContractTests {
    private val ledgerServices = MockServices()

    val alice = TestIdentity(CordaX500Name("Alice Ltd", "London", "GB"))
    val bob = TestIdentity(CordaX500Name("Bob Inc", "New York", "US"))
    val charlie = TestIdentity(CordaX500Name("Charlie SA", "Paris", "FR"))

    @Test
    fun `check the happy path`() {

        val linearId = UniqueIdentifier()

        val proposed1 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "One bunch of Bananas", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)
        val rejected = AgreementState(AgreementStatus.REJECTED,
                alice.party, bob.party, "One bunch of Bananas", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, "Run out of Bananas", bob.party, linearId = linearId)
        val proposed2 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "One bag of grapes", Amount(8, Currency.getInstance("GBP")), bob.party, alice.party, linearId = linearId)
        val agreed = AgreementState(AgreementStatus.AGREED,
                alice.party, bob.party, "One bag of grapes", Amount(8, Currency.getInstance("GBP")), bob.party, alice.party, linearId = linearId)

        ledgerServices.ledger {
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, proposed1)
                verifies()
            }
            transaction {
                input(AgreementContract.ID, proposed1)
                command(bob.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, rejected)
                verifies()
            }
            transaction {
                input(AgreementContract.ID, rejected)
                command(bob.publicKey, AgreementContract.Commands.Repropose())
                output(AgreementContract.ID, proposed2)
                verifies()
            }
            transaction {
                input(AgreementContract.ID, proposed2)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, agreed)
                verifies()
            }
            transaction {
                input(AgreementContract.ID, agreed)
                command(bob.publicKey, AgreementContract.Commands.Complete())
                verifies()
            }
        }
    }


    // todo: should this test move to ContractUtilsTest as the functions called are now in Contractutils
    @Test
    fun `check all inputs of type AgreementState are in Proposed status when agreed on`() {


        val linearId1 = UniqueIdentifier()
        val linearId2 = UniqueIdentifier()
        val linearId3 = UniqueIdentifier()
        val input1 = AgreementState(AgreementStatus.PROPOSED, alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId1)
        val input2 = AgreementState(AgreementStatus.PROPOSED, alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId2)
        val input3 = AgreementState(AgreementStatus.AGREED, alice.party, bob.party, "yet more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId3)

        val output1 = AgreementState(AgreementStatus.AGREED, alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId1)
        val output2 = AgreementState(AgreementStatus.AGREED, alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId2)
        val output3 = AgreementState(AgreementStatus.AGREED, alice.party, bob.party, "yet more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId3)

        ledgerServices.ledger {
            transaction {
                input(AgreementContract.ID, input1)
                input(AgreementContract.ID, input2)
                input(AgreementContract.ID, input3)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, output1)
                output(AgreementContract.ID, output2)
                output(AgreementContract.ID, output3)
                failsWith("The status must be transitioning from Proposed to Agreed")
            }
        }
    }

    @Test
    fun `check all outputs in a tx representing a proposal must be in PROPOSED state`() {

        val linearId = UniqueIdentifier()
        val output1 = AgreementState(AgreementStatus.PROPOSED, alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)
        val output2 = AgreementState(AgreementStatus.PROPOSED, alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)
        val output3 = AgreementState(AgreementStatus.AGREED, alice.party, bob.party, "yet more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)

        ledgerServices.ledger {
            transaction {
                output(AgreementContract.ID, output1)
                output(AgreementContract.ID, output2)
                output(AgreementContract.ID, output3)
                command(alice.publicKey, AgreementContract.Commands.Propose())
                failsWith("The output state must be in status Proposed")
            }
        }
    }

    @Test
    fun `check paths constraints`() {

        val linearId = UniqueIdentifier()
        val proposedState = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)
        val rejectedState = AgreementState(AgreementStatus.REJECTED,
                alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, "I don't like grapes", alice.party, linearId = linearId)
        val agreedState = AgreementState(AgreementStatus.AGREED,
                alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)

        ledgerServices.ledger {

            // From null status
            // Incorrect Statuses
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, agreedState)
                `fails with`("The output state must be in status Proposed")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, rejectedState)
                `fails with`("The output state must be in status Proposed")
            }
            // Incorrect Commands
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, proposedState)
                `fails with`("Number of input states and output states must be the same")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, proposedState)
                `fails with`("Number of input states and output states must be the same")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Repropose())
                output(AgreementContract.ID, proposedState)
                `fails with`("Number of input states and output states must be the same")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Complete())
                output(AgreementContract.ID, proposedState)
                `fails with`("txPath must be allowed by PathConstraints for inputStatus null.")
            }

            // from Proposed state
            // Incorrect Statuses
            transaction {
                input(AgreementContract.ID, proposedState)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                `fails with`("Number of input states and output states must be the same")
            }
            transaction {
                input(AgreementContract.ID, proposedState)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, proposedState)
                `fails with`("The status must be transitioning from Proposed to Agreed")
            }
            transaction {
                input(AgreementContract.ID, proposedState)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, rejectedState)
                `fails with`("The status must be transitioning from Proposed to Agreed")
            }
            // Incorrect Commands
            transaction {
                input(AgreementContract.ID, proposedState)
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, agreedState)
                `fails with`("There must be at most 0 input state(s)")
            }
            transaction {
                input(AgreementContract.ID, proposedState)
                command(alice.publicKey, AgreementContract.Commands.Repropose())
                output(AgreementContract.ID, agreedState)
                `fails with`("The status must be transitioning from Rejected to Proposed")
            }
            transaction {
                input(AgreementContract.ID, proposedState)
                command(alice.publicKey, AgreementContract.Commands.Complete())
                output(AgreementContract.ID, agreedState)
                `fails with`("txPath must be allowed by PathConstraints for inputStatus PROPOSED.")
            }

            // From Rejected Status
            // Incorrect Statuses
            transaction {
                input(AgreementContract.ID, rejectedState)
                command(alice.publicKey, AgreementContract.Commands.Repropose())
                `fails with`("Number of input states and output states must be the same")
            }
            transaction {
                input(AgreementContract.ID, rejectedState)
                command(alice.publicKey, AgreementContract.Commands.Repropose())
                output(AgreementContract.ID, rejectedState)
                `fails with`("The status must be transitioning from Rejected to Proposed")
            }
            transaction {
                input(AgreementContract.ID, rejectedState)
                command(alice.publicKey, AgreementContract.Commands.Repropose())
                output(AgreementContract.ID, agreedState)
                `fails with`("The status must be transitioning from Rejected to Proposed")
            }
            // Incorrect Commands
            transaction {
                input(AgreementContract.ID, rejectedState)
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, proposedState)
                `fails with`("There must be at most 0 input state(s)")
            }
            transaction {
                input(AgreementContract.ID, rejectedState)
                command(alice.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, proposedState)
                `fails with`("The status must be transitioning from Proposed to Rejected")
            }
            transaction {
                input(AgreementContract.ID, rejectedState)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, proposedState)
                `fails with`("The status must be transitioning from Proposed to Agreed")
            }
            transaction {
                input(AgreementContract.ID, rejectedState)
                command(alice.publicKey, AgreementContract.Commands.Complete())
                output(AgreementContract.ID, proposedState)
                `fails with`("txPath must be allowed by PathConstraints for inputStatus REJECTED.")
            }

            // From Agreed
            // Incorrect Status
            transaction {
                input(AgreementContract.ID, agreedState)
                command(alice.publicKey, AgreementContract.Commands.Complete())
                output(AgreementContract.ID, proposedState)
                `fails with`("txPath must be allowed by PathConstraints for inputStatus AGREED.")
            }
            transaction {
                input(AgreementContract.ID, agreedState)
                command(alice.publicKey, AgreementContract.Commands.Complete())
                output(AgreementContract.ID, rejectedState)
                `fails with`("txPath must be allowed by PathConstraints for inputStatus AGREED.")
            }
            transaction {
                input(AgreementContract.ID, agreedState)
                command(alice.publicKey, AgreementContract.Commands.Complete())
                output(AgreementContract.ID, agreedState)
                `fails with`("txPath must be allowed by PathConstraints for inputStatus AGREED.")
            }
            // Incorrect Commands
            transaction {
                input(AgreementContract.ID, agreedState)
                command(alice.publicKey, AgreementContract.Commands.Propose())
                `fails with`("There must be at most 0 input state(s)")
            }
            transaction {
                input(AgreementContract.ID, agreedState)
                command(alice.publicKey, AgreementContract.Commands.Reject())
                `fails with`("Number of input states and output states must be the same")
            }
            transaction {
                input(AgreementContract.ID, agreedState)
                command(alice.publicKey, AgreementContract.Commands.Repropose())
                `fails with`("Number of input states and output states must be the same")
            }
            transaction {
                input(AgreementContract.ID, agreedState)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                `fails with`("Number of input states and output states must be the same")
            }
        }
    }

    @Test
    fun `check universal constraints`(){

        val linearId = UniqueIdentifier()
        val state1 = AgreementState(AgreementStatus.PROPOSED, alice.party, alice.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)
        val state2 = AgreementState(AgreementStatus.PROPOSED, alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), charlie.party, bob.party, linearId = linearId)
        val state3 = AgreementState(AgreementStatus.PROPOSED, alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, charlie.party, linearId = linearId)
        val state4 = AgreementState(AgreementStatus.PROPOSED, alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, alice.party, linearId = linearId)

        ledgerServices.ledger {

            // As output states
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, state1)
                `fails with`("The buyer and seller must be different Parties.")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, state2)
                `fails with`("The proposer must be either the buyer or the seller.")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, state3)
                `fails with`("The consenter must be either the buyer or the seller.")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, state4)
                `fails with`("The consenter and proposer must be different Parties.")
            }
        }
    }

    @Test
    fun `check status constraints`() {

        val linearId = UniqueIdentifier()
        val proposed1 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)
        val proposed2 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, "I don't like grapes", linearId = linearId)
        val proposed3 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, rejectedBy = alice.party, linearId = linearId)
        val proposed4 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, "I don't like grapes", alice.party, linearId = linearId)

        val rejected1 = AgreementState(AgreementStatus.REJECTED,
                alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, "I don't like grapes", linearId = linearId)
        val rejected2 = AgreementState(AgreementStatus.REJECTED,
                alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, rejectedBy = alice.party, linearId = linearId)
        val rejected3 = AgreementState(AgreementStatus.REJECTED,
                alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)


        ledgerServices.ledger {
            // Proposed
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, proposed2)
                `fails with`("When status is Proposed rejectionReason must be null.")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, proposed3)
                `fails with`("When status is Rejected rejectedBy must be null.")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, proposed4)
                `fails with`("When status is Proposed rejectionReason must be null.")
            }

            // Rejected
            transaction {
                input(AgreementContract.ID, proposed1)
                command(alice.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, rejected1)
                `fails with`("When status is Rejected rejectedBy must not be null.")
            }
            transaction {
                input(AgreementContract.ID, proposed1)
                command(alice.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, rejected2)
                `fails with`("When status is Rejected rejectionReason must not be null.")
            }
            transaction {
                input(AgreementContract.ID, proposed1)
                command(alice.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, rejected3)
                `fails with`("When status is Rejected rejectionReason must not be null.")
            }
        }
    }

    @Test
    fun `check the linear id constraints path`() {

        val linearId1 = UniqueIdentifier()
        val linearId2 = UniqueIdentifier()
        val linearId3 = UniqueIdentifier()
        val linearId4 = UniqueIdentifier()

        val proposed1 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "One bunch of Bananas", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId1)
        val rejected = AgreementState(AgreementStatus.REJECTED,
                alice.party, bob.party, "One bunch of Bananas", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, "Run out of Bananas", bob.party, linearId = linearId2)
        val proposed2 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "One bag of grapes", Amount(8, Currency.getInstance("GBP")), bob.party, alice.party, linearId = linearId3)
        val agreed = AgreementState(AgreementStatus.AGREED,
                alice.party, bob.party, "One bag of grapes", Amount(8, Currency.getInstance("GBP")), bob.party, alice.party, linearId = linearId4)

        ledgerServices.ledger {

            transaction {
                input(AgreementContract.ID, proposed1)
                command(bob.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, rejected)
                failsWith("Each input state must have a corresponding output state (with same linear id)")
            }
            transaction {
                input(AgreementContract.ID, rejected)
                command(bob.publicKey, AgreementContract.Commands.Repropose())
                output(AgreementContract.ID, proposed2)
                failsWith("Each input state must have a corresponding output state (with same linear id)")
            }
            transaction {
                input(AgreementContract.ID, proposed2)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, agreed)
                failsWith("Each input state must have a corresponding output state (with same linear id)")
            }
        }
    }

    @Test
    fun `check signing constraints`() {

        val linearId = UniqueIdentifier()
        val proposed1 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "One bunch of Bananas", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)
        val rejected = AgreementState(AgreementStatus.REJECTED,
                alice.party, bob.party, "One bunch of Bananas", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, "Run out of Bananas", bob.party, linearId = linearId)
        val proposed2 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "One bag of grapes", Amount(8, Currency.getInstance("GBP")), bob.party, alice.party, linearId = linearId)
        val agreed = AgreementState(AgreementStatus.AGREED,
                alice.party, bob.party, "One bag of grapes", Amount(8, Currency.getInstance("GBP")), bob.party, alice.party, linearId = linearId)

        // For each Command try the following signers:
        //  - other Participant (fails)
        //  - a non participant (fails)
        //  - correct participant and a non participant (verifies)

        ledgerServices.ledger {

            // Propose (alice should sign)
            transaction {
                command(bob.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, proposed1)
                failsWith("When the Command is Propose the output.proposer must sign.")
            }
            transaction {
                command(charlie.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, proposed1)
                failsWith("When the Command is Propose the output.proposer must sign.")
            }
            transaction {
                command(listOf(alice.publicKey, charlie.publicKey), AgreementContract.Commands.Propose())
                output(AgreementContract.ID, proposed1)
                verifies()
            }

            // Reject (Bob should sign)
            transaction {
                input(AgreementContract.ID, proposed1)
                command(alice.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, rejected)
                failsWith("When the Command is Reject the output.rejectedBy must sign.")
            }
            transaction {
                input(AgreementContract.ID, proposed1)
                command(charlie.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, rejected)
                failsWith("When the Command is Reject the output.rejectedBy must sign.")
            }
            transaction {
                input(AgreementContract.ID, proposed1)
                command(listOf(bob.publicKey, charlie.publicKey), AgreementContract.Commands.Reject())
                output(AgreementContract.ID, rejected)
                verifies()
            }

            // Repropose (Bob should sign)
            transaction {
                input(AgreementContract.ID, rejected)
                command(alice.publicKey, AgreementContract.Commands.Repropose())
                output(AgreementContract.ID, proposed2)
                failsWith("When the Command is Repropose the output.proposer must sign.")
            }
            transaction {
                input(AgreementContract.ID, rejected)
                command(charlie.publicKey, AgreementContract.Commands.Repropose())
                output(AgreementContract.ID, proposed2)
                failsWith("When the Command is Repropose the output.proposer must sign.")
            }
            transaction {
                input(AgreementContract.ID, rejected)
                command(listOf(bob.publicKey, charlie.publicKey), AgreementContract.Commands.Repropose())
                output(AgreementContract.ID, proposed2)
                verifies()
            }

            // Agree (Alice should sign)
            transaction {
                input(AgreementContract.ID, proposed2)
                command(bob.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, agreed)
                failsWith("When the Command is Agree the input.consenter must sign.")
            }
            transaction {
                input(AgreementContract.ID, proposed2)
                command(charlie.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, agreed)
                failsWith("When the Command is Agree the input.consenter must sign.")
            }
            transaction {
                input(AgreementContract.ID, proposed2)
                command(listOf(alice.publicKey, charlie.publicKey), AgreementContract.Commands.Agree())
                output(AgreementContract.ID, agreed)
                verifies()
            }

            // Complete (Bob should sign)
            transaction {
                input(AgreementContract.ID, agreed)
                command(alice.publicKey, AgreementContract.Commands.Complete())
                failsWith("When the command is Complete the input.seller must sign.")
            }
            transaction {
                input(AgreementContract.ID, agreed)
                command(charlie.publicKey, AgreementContract.Commands.Complete())
                failsWith("When the command is Complete the input.seller must sign.")
            }
            transaction {
                input(AgreementContract.ID, agreed)
                command(listOf(bob.publicKey, charlie.publicKey), AgreementContract.Commands.Complete())
                verifies()
            }
        }
    }

    @Test
    fun `check the command constraints`() {

        val linearId = UniqueIdentifier()

        val proposed1 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "One bunch of Bananas", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, linearId = linearId)
        val rejected = AgreementState(AgreementStatus.REJECTED,
                alice.party, bob.party, "Two bunches of Bananas", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party, "Run out of Bananas", bob.party, linearId = linearId)
        val proposed2 = AgreementState(AgreementStatus.PROPOSED,
                alice.party, bob.party, "One bag of grapes", Amount(8, Currency.getInstance("GBP")), bob.party, alice.party, linearId = linearId)
        val agreed1 = AgreementState(AgreementStatus.AGREED,
                alice.party, bob.party, "Two bags of grapes", Amount(8, Currency.getInstance("GBP")), bob.party, alice.party, linearId = linearId)
        val agreed2 = AgreementState(AgreementStatus.AGREED,
                alice.party, bob.party, "One bag of grapes", Amount(5, Currency.getInstance("GBP")), bob.party, alice.party, linearId = linearId)
        val agreed3 = AgreementState(AgreementStatus.AGREED,
                alice.party, bob.party, "One bag of grapes", Amount(5, Currency.getInstance("EUR")), bob.party, alice.party, linearId = linearId)
        val agreed4 = AgreementState(AgreementStatus.AGREED,
                alice.party, bob.party, "One bag of grapes", Amount(5, Currency.getInstance("EUR")), alice.party, bob.party, linearId = linearId)

        ledgerServices.ledger {

            // Propose
            // Note: null values are already checked for in status constraints, so can't check here because they have already errored
            transaction {
                input(AgreementContract.ID, proposed1)
                command(bob.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, rejected)
                failsWith("When the command is Reject no properties can change except status, rejectionReason and rejectedBy.")
            }

            // Agree
            transaction {
                input(AgreementContract.ID, proposed2)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, agreed1)
                failsWith("When the command is Agree no properties can change except status.")
            }
            transaction {
                input(AgreementContract.ID, proposed2)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, agreed2)
                failsWith("When the command is Agree no properties can change except status.")
            }
            transaction {
                input(AgreementContract.ID, proposed2)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, agreed3)
                failsWith("When the command is Agree no properties can change except status.")
            }
            transaction {
                input(AgreementContract.ID, proposed2)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, agreed4)
                failsWith("When the command is Agree no properties can change except status.")
            }
        }
    }
}