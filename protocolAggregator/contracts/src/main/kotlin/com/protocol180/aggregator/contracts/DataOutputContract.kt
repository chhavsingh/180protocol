package com.protocol180.aggregator.contracts

import com.protocol180.aggregator.states.DataOutputState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

class DataOutputContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.protocol180.aggregator.contracts.DataOutputContract"
    }

    /**
     * Contract to verify Data Output States
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Create -> requireThat {
                "No inputs should be consumed when issuing a data output state." using (tx.inputs.isEmpty())
                "Only one output state should be created when issuing a data output state." using (tx.outputs.size == 1)
                val dataOutputState = tx.outputsOfType<DataOutputState>().single()
                "A newly issued data output must have a consumer & host" using
                        (dataOutputState.consumer != null && dataOutputState.host != null)
                "The enclave attestation used to create the data output must not be null" using
                        (dataOutputState.enclaveAttestation != null)
                "The flow topic used to create the data output must not be null" using
                        (dataOutputState.flowTopic != null)
                "Only the consumer and host may sign the Data Output State Transaction" using
                        (command.signers.toSet() == dataOutputState.participants.map { it.owningKey }.toSet())
            }

        }
    }

    /**
     * Add any commands required for this contract as classes within this interface.
     * It is useful to encapsulate your commands inside an interface, so you can use the [requireSingleCommand]
     * function to check for a number of commands which implement this interface.
     */
    interface Commands : CommandData {

        class Create : TypeOnlyCommandData(), Commands
    }
}