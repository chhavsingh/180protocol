package com.protocol180.aggregator.states

import com.protocol180.aggregator.contracts.DataOutputContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.time.Instant

@BelongsToContract(DataOutputContract::class)
data class DataOutputState(val consumer: Party,
                           val host: Party,
                           val dataOutput: ByteArray,
                           val dateCreated: Instant,
                           val enclaveAttestation: ByteArray,
                           val flowTopic: String
) : ContractState {

    /**
     *  This property holds a list of the nodes which can "use" this state in a valid transaction. In this case, the
     *  consumer or host.
     */
    override val participants: List<AbstractParty> get() = listOf(consumer, host)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataOutputState

        if (consumer != other.consumer) return false
        if (host != other.host) return false
        if (!dataOutput.contentEquals(other.dataOutput)) return false
        if (dateCreated != other.dateCreated) return false
        if (enclaveAttestation != other.enclaveAttestation) return false
        if (flowTopic != other.flowTopic) return false

        return true
    }

    override fun hashCode(): Int {
        var result = consumer.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + dataOutput.contentHashCode()
        result = 31 * result + dateCreated.hashCode()
        result = 31 * result + enclaveAttestation.hashCode()
        result = 31 * result + flowTopic.hashCode()
        return result
    }

}
