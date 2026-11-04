package com.agentx.admin

import android.app.Application
import com.agentx.admin.server.AgentXAdminServer
import com.agentx.admin.server.ClientManager
import com.agentx.admin.server.CloudGeminiGateway

class AgentXAdminApplication : Application() {

    lateinit var clientManager: ClientManager
        private set

    lateinit var cloudGateway: CloudGeminiGateway
        private set

    lateinit var server: AgentXAdminServer
        private set

    override fun onCreate() {
        super.onCreate()
        clientManager = ClientManager()
        cloudGateway = CloudGeminiGateway()
        server = AgentXAdminServer(clientManager, cloudGateway)
    }
}
