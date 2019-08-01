package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.IOUContract
import com.template.states.IOUState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC

class IOUFlow(val iouValue: Int,
              val otherParty: Party) : FlowLogic<Unit>() {

    /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
    override val progressTracker = ProgressTracker()

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    override fun call() {
        // We retrieve the notary identity from the network map.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // We create the transaction components.
        val outputState = IOUState(iouValue, ourIdentity, otherParty)
        val command = Command(IOUContract.Create(), listOf(ourIdentity.owningKey, otherParty.owningKey))

        // We create a transaction builder and add the components.
        val txBuilder = TransactionBuilder(notary = notary)
                .addOutputState(outputState, IOUContract.ID)
                .addCommand(command)

        txBuilder.verify(serviceHub)

        // We sign the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Creating a session with the other party.
        val otherPartySession = initiateFlow(otherParty)

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherPartySession), CollectSignaturesFlow.tracker()))

        // We finalise the transaction and then send it to the counterparty.
        subFlow(FinalityFlow(fullySignedTx, otherPartySession))
    }
}

@InitiatedBy(IOUFlow::class)
class IOUFlowResponder(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object :  SignTransactionFlow(otherPartySession){
            // checkTransaction method is already there in SignTransactionFlow but we need to modify as per our requirement
            override fun checkTransaction(stx: SignedTransaction) = requireThat{
                val output = stx.tx.outputs.single().data
                "This must be a iou transaction" using (output is IOUState)

                val iou = output as IOUState
                "value must be less than 100" using (iou.value < 100)

            }
        }

        val expectedTxId = subFlow(signTransactionFlow).id

        subFlow(ReceiveFinalityFlow(otherPartySession , expectedTxId))
    }
}
