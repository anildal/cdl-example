package com.cdlexample.contracts

import com.cdlexample.states.AgreementState
import com.cdlexample.states.AgreementStatus
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.util.*

class AgreementContractTests {
    private val ledgerServices = MockServices()

    val alice = TestIdentity(CordaX500Name("Alice Ltd", "London", "GB"))
    val bob = TestIdentity(CordaX500Name("Bob Inc", "New York", "US"))

    @Test
    fun `correctly formed Tx verifies`() {  // todo: modify as add more constraints

        val input = AgreementState(AgreementStatus.Proposed(),
                alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)
        val output = AgreementState(AgreementStatus.Agreed(),
                alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)

        ledgerServices.ledger {
            transaction {
                input(AgreementContract.ID, input)
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, output)
                verifies()
            }
        }
    }

    @Test
    fun `All inputs of type AgreementState have the same Status`() {

        val input1 = AgreementState(AgreementStatus.Proposed(),
                alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)
        val input2 = AgreementState(AgreementStatus.Proposed(), alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)
        val input3 = AgreementState(AgreementStatus.Agreed(), alice.party, bob.party, "yet more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)

        ledgerServices.ledger {
            transaction {
                input(AgreementContract.ID, input1)
                input(AgreementContract.ID, input2)
                input(AgreementContract.ID, input3)
                command(alice.publicKey, AgreementContract.Commands.Propose())
                failsWith("All inputs of type AgreementState have the same Status.")
            }
        }
    }


    @Test
    fun `All outputs of type AgreementState have the same Status`() {

        val output1 = AgreementState(AgreementStatus.Proposed(), alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)
        val output2 = AgreementState(AgreementStatus.Proposed(), alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)
        val output3 = AgreementState(AgreementStatus.Agreed(), alice.party, bob.party, "yet more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)

        ledgerServices.ledger {
            transaction {
                output(AgreementContract.ID, output1)
                output(AgreementContract.ID, output2)
                output(AgreementContract.ID, output3)
                command(alice.publicKey, AgreementContract.Commands.Propose())
                failsWith("All outputs of type AgreementState have the same Status.")
            }
        }
    }


    @Test
    fun `non happy paths fail by sample`() {
        /**
         * Note, for each input status:
         * - Test all first order errors, ie one Path property wrong
         * - Test a sample of combined errors, ie both Path properties wrong
         */

        val proposedState = AgreementState(AgreementStatus.Proposed(),
                alice.party, bob.party, "Some grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)
        val rejectedState = AgreementState(AgreementStatus.Rejected(),
                alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)
        val agreedState = AgreementState(AgreementStatus.Agreed(),
                alice.party, bob.party, "Some more grapes", Amount(10, Currency.getInstance("GBP")), alice.party, bob.party)

        ledgerServices.ledger {

            // from null status
            // Statuses
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, agreedState)
                `fails with`("When there is no input AgreementState the path must be Propose -> Proposed")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Propose())
                output(AgreementContract.ID, rejectedState)
                `fails with`("When there is no input AgreementState the path must be Propose -> Proposed")
            }
            // Commands
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, proposedState)
                `fails with`("When there is no input AgreementState the path must be Propose -> Proposed")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Reject())
                output(AgreementContract.ID, proposedState)
                `fails with`("When there is no input AgreementState the path must be Propose -> Proposed")
            }
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Repropose())
                output(AgreementContract.ID, proposedState)
                `fails with`("When there is no input AgreementState the path must be Propose -> Proposed")
            }
            // Combined
            transaction {
                command(alice.publicKey, AgreementContract.Commands.Agree())
                output(AgreementContract.ID, agreedState)
                `fails with`("When there is no input AgreementState the path must be Propose -> Proposed")
            }

            // from Proposed state
            // Statuses
            transaction {
                input(AgreementContract.ID, proposedState)
                command(alice.publicKey, AgreementContract.Commands.Agree())

//                fails() // todo: this looks like its failing with null pointer rather than error message
                `fails with`("When the input Status is Proposed, the path must be Reject -> Rejected, or Agree -> Agreed.")
            }
//            transaction {
//                input(AgreementContract.ID, proposedState)
//                command(alice.publicKey, AgreementContract.Commands.())
//                output(AgreementContract.ID, agreedState)
//                `fails with`("When the input Status is Proposed, the path must be Reject -> Rejected, or Agree -> Agreed.")
//            }


        }

    }


    @Test
    fun `check Path equality method`() {

        // test Command equality

        val path1 = AgreementContract.Path(AgreementContract.Commands.Agree(), AgreementStatus.Proposed())
        val path2 = AgreementContract.Path(AgreementContract.Commands.Agree(), AgreementStatus.Proposed())
        val path3 = AgreementContract.Path(AgreementContract.Commands.Propose(), AgreementStatus.Proposed())

        assert(path1 == path2)
        assert(path1 !== path3)
        assert(path2 == path1)
        assert(path3 != path1)

        // test Status Equality
        val path4 = AgreementContract.Path(AgreementContract.Commands.Agree(), AgreementStatus.Proposed())
        val path5 = AgreementContract.Path(AgreementContract.Commands.Agree(), AgreementStatus.Proposed())
        val path6  = AgreementContract.Path(AgreementContract.Commands.Agree(), AgreementStatus.Agreed())
        val path7 = AgreementContract.Path(AgreementContract.Commands.Agree(), null)
        val path8 = AgreementContract.Path(AgreementContract.Commands.Agree(), null)

        assert(path4 == path5)
        assert(path5 == path4)
        assert(path4 != path6)
        assert(path6 != path4)
        assert(path4 != path7)
        assert(path7 !== path4)
        assert(path7 == path8)
        assert(path8 == path7)

        // check both Command and Status mismatched

        assert(path3 != path7)
        assert(path7 != path3)

    }






}