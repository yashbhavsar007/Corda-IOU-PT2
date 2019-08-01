package com.template.contracts

import com.template.states.IOUState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class IOUContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.contracts.IOUContract"

    }

    class Create: CommandData

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Create>()

        requireThat {
            "No inputs consumed when issuing an IOU." using (tx.inputs.isEmpty())
            "There is one output state only " using  (tx.outputs.size == 1)

            val output = tx.outputsOfType<IOUState>().single()
            "The output value must be non-negative and non zero" using (output.value > 0)
            "The lender and borrower cannot be same" using (output.lender != output.borrower)

            val expectedSigners = listOf(output.borrower.owningKey, output.lender.owningKey)
            "Require two signers" using (command.signers.toSet().size == 2)
            "Signers must be lender and borrower" using (command.signers.containsAll(expectedSigners))

        }

    }

}