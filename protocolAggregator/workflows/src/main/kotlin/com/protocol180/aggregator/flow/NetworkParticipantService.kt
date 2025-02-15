package com.protocol180.aggregator.flow

import com.protocol180.aggregator.states.RoleType
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor

/**
 * A simple service used to retrieve the node's role based on its identity. Role is configured on the node CordApp configuration
 */
@CordaService
class NetworkParticipantService(private val hub: AppServiceHub) : SingletonSerializeAsToken() {

    companion object {
        const val PARTICIPANT_ROLE_CONFIG_KEY = "participantRole"
        private val log = loggerFor<NetworkParticipantService>()
    }

    private val _role: RoleType?

    val role: RoleType
    get() = _role!!

    init {
        val config = hub.getAppContext().config

        _role = if (config.exists(PARTICIPANT_ROLE_CONFIG_KEY)) {
            RoleType.valueOf(config.getString(PARTICIPANT_ROLE_CONFIG_KEY))
        } else {
            log.warn("Participant's role is not set, error will be raised if accessed in a flow")
            null
        }
    }
}
