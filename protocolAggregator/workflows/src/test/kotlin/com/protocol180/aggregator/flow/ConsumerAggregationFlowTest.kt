package com.protocol180.aggregator.flow

import com.protocol180.aggregator.states.*
import net.corda.core.internal.readFully
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Practical exercise instructions Flows part 1.
 * Uncomment the unit tests and use the hints + unit test body to complete the Flows such that the unit tests pass.
 * Note! These tests rely on Quasar to be loaded, set your run configuration to "-ea -javaagent:lib/quasar.jar"
 * Run configuration can be edited in IntelliJ under Run -> Edit Configurations -> VM options
 * On some machines/configurations you may have to provide a full path to the quasar.jar file.
 * On some machines/configurations you may have to use the "JAR manifest" option for shortening the command line.
 */
class ConsumerAggregationFlowTest {
    lateinit var network: MockNetwork
    lateinit var consumer: StartedMockNode
    lateinit var host: StartedMockNode
    lateinit var provider1: StartedMockNode
    lateinit var provider2: StartedMockNode
    lateinit var coalitionConfigurationState: CoalitionConfigurationState

    @Before
    fun setup() {
        network = MockNetwork(
                MockNetworkParameters(
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.protocol180.aggregator.contracts"),
                                TestCordapp.findCordapp("com.protocol180.aggregator.flow")
                        )
                )
        )
        consumer = prepareNodeForRole(RoleType.DATA_CONSUMER)
        host = prepareNodeForRole(RoleType.COALITION_HOST)
        provider1 = prepareNodeForRole(RoleType.DATA_PROVIDER)
        provider2 = prepareNodeForRole(RoleType.DATA_PROVIDER)
        network.runNetwork()
        createConfigurationState()
    }


    @After
    fun tearDown() {
        network.stopNodes()
    }

    private fun prepareNodeForRole(role: RoleType) : StartedMockNode {
        return network.createNode(
            parameters = MockNodeParameters(
                additionalCordapps = listOf(
                    TestCordapp.findCordapp("com.protocol180.aggregator.flow")
                        .withConfig(mapOf(Pair(NetworkParticipantService.PARTICIPANT_ROLE_CONFIG_KEY, role.name)))
                )
            )
        )
    }

    private fun createConfigurationState(){
        var coalitionPartyToRole = mapOf(host.info.chooseIdentityAndCert().party to RoleType.COALITION_HOST,
            consumer.info.chooseIdentityAndCert().party to RoleType.DATA_CONSUMER,
            provider1.info.chooseIdentityAndCert().party to RoleType.DATA_PROVIDER,
            provider2.info.chooseIdentityAndCert().party to RoleType.DATA_PROVIDER)

        val dataTypes = listOf(
            CoalitionDataType("testDataType1", "Test Data Type 1",
            ClassLoader.getSystemClassLoader().getResourceAsStream("testSchema1.avsc").readFully()),
            CoalitionDataType("testDataType2", "Test Data Type 2",
                ClassLoader.getSystemClassLoader().getResourceAsStream("testSchema2.avsc").readFully())
        )

        val flow1 = CoalitionConfigurationUpdateFlow(coalitionPartyToRole, dataTypes)
        val future1 = host.startFlow(flow1)
        network.runNetwork()
        val signedTransaction = future1.get()
        assertEquals(1, signedTransaction.tx.outputStates.size)
        coalitionConfigurationState = signedTransaction.tx.getOutput(0) as CoalitionConfigurationState
    }

    @Test
    fun consumerAggregationFlowAndRetrievalTest() {
        val flow = ConsumerAggregationFlow("testDataType2")
        val future = consumer.startFlow(flow)
        network.runNetwork()
        val signedTransaction = future.get()
        assertEquals(1, signedTransaction.tx.outputStates.size)
        val output = signedTransaction.tx.getOutput(0) as DataOutputState
        assertEquals(host.info.legalIdentities[0], output.host)
        assertEquals(consumer.info.legalIdentities.first(), output.consumer)

        //check data output and rewards transaction for host
        host.transaction {
            val dataOutputState : DataOutputState = host.services.vaultService.queryBy<DataOutputState>(
                VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states.single().state.data
            val rewardsStates = provider1.services.vaultService.queryBy<RewardsState>(
                VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states

            assertEquals(output.flowTopic, dataOutputState.flowTopic)
            assertEquals(host.info.legalIdentities[0], dataOutputState.host)
            assertEquals(consumer.info.legalIdentities.first(), dataOutputState.consumer)
            assertTrue { dataOutputState.participants.containsAll(
                listOf(host.info.legalIdentities[0], consumer.info.legalIdentities.first())) }

            val rewardsState1 = rewardsStates[0].state.data
            val rewardsState2 = rewardsStates[1].state.data

            assertEquals(host.info.legalIdentities[0], rewardsState1.host)
            assertEquals(provider1.info.legalIdentities.first(), rewardsState1.provider)
            assertTrue { rewardsState1.participants.containsAll(
                listOf(host.info.legalIdentities[0], provider1.info.legalIdentities.first())) }

            assertEquals(host.info.legalIdentities[0], rewardsState2.host)
            assertEquals(provider2.info.legalIdentities.first(), rewardsState2.provider)
            assertTrue { rewardsState2.participants.containsAll(
                listOf(host.info.legalIdentities[0], provider2.info.legalIdentities.first())) }
        }

        //check rewards transaction and retrieval for consumer

        val dataOutputState : DataOutputState = consumer.services.vaultService.queryBy<DataOutputState>(
            VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states.single().state.data

        assertEquals(output.flowTopic, dataOutputState.flowTopic)
        assertEquals(host.info.legalIdentities[0], dataOutputState.host)
        assertEquals(consumer.info.legalIdentities.first(), dataOutputState.consumer)
        assertTrue { dataOutputState.participants.containsAll(
            listOf(host.info.legalIdentities[0], consumer.info.legalIdentities.first())) }

        val consumerDataOutputRetrievalFlow = ConsumerDataOutputRetrievalFlow(dataOutputState.flowTopic)
        val dataOutputFuture = consumer.startFlow(consumerDataOutputRetrievalFlow)
        val dataOutputRecords = dataOutputFuture.get()
        assert(dataOutputRecords.size > 1)
        println("Consumer Data Output Retrieval : $dataOutputRecords")

        //check rewards transaction and retrieval for provider 1
        val rewardsState1 : RewardsState = provider1.services.vaultService.queryBy<RewardsState>(
            VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states.single().state.data

        assertEquals(host.info.legalIdentities[0], rewardsState1.host)
        assertEquals(provider1.info.legalIdentities.first(), rewardsState1.provider)
        assertTrue { rewardsState1.participants.containsAll(
            listOf(host.info.legalIdentities[0], provider1.info.legalIdentities.first())) }

        var providerRewardOutputRetrievalFlow = ProviderRewardOutputRetrievalFlow(rewardsState1.flowTopic)
        val rewardOutputFuture1 = provider1.startFlow(providerRewardOutputRetrievalFlow)
        val provider1RewardOutput = rewardOutputFuture1.get()
        println("Provider 1 Rewards Retrieval : $provider1RewardOutput")
        assertEquals(provider1RewardOutput.size, 1)


        //check rewards transaction and retrieval for provider 2

        val rewardsState2 : RewardsState = provider2.services.vaultService.queryBy<RewardsState>(
            VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states.single().state.data

        assertEquals(host.info.legalIdentities[0], rewardsState2.host)
        assertEquals(provider2.info.legalIdentities.first(), rewardsState2.provider)
        assertTrue { rewardsState2.participants.containsAll(
            listOf(host.info.legalIdentities[0], provider2.info.legalIdentities.first())) }

        providerRewardOutputRetrievalFlow = ProviderRewardOutputRetrievalFlow(rewardsState2.flowTopic)
        val rewardOutputFuture2 = provider2.startFlow(providerRewardOutputRetrievalFlow)
        val provider2RewardOutput = rewardOutputFuture2.get()
        println("Provider 2 Rewards Retrieval : $provider2RewardOutput")
        assertEquals(provider2RewardOutput.size, 1)

    }

    @Test
    fun consumerAggregationFlowFailTest() {
        //check unsupported data type
        val flow = ConsumerAggregationFlow("testDataType3")
        val future = consumer.startFlow(flow)
        network.runNetwork()
        assertFailsWith(ConsumerAggregationFlowException::class) { future.getOrThrow() }

        //check new consumer added to coalition without updating coalition configuration
        var consumer2: StartedMockNode = prepareNodeForRole(RoleType.DATA_CONSUMER)
        val flow2 = ConsumerAggregationFlow("testDataType2")
        val future2 = consumer2.startFlow(flow2)
        network.runNetwork()
        assertFailsWith(ConsumerAggregationFlowException::class) { future2.getOrThrow() }
    }
}
