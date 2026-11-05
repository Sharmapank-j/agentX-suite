package com.agentx.admin

import android.app.Application
import com.agentx.admin.server.AgentXAdminServer
import com.agentx.admin.server.AgentXNsdHelper
import com.agentx.admin.server.ClientManager
import com.agentx.admin.server.CloudGeminiGateway
import com.agentx.admin.server.LocalModelServerEngine
import com.agentx.admin.server.LocalTlsManager
import com.agentx.admin.server.ThermalBatteryManager
import com.agentx.admin.server.TunnelManager

class AgentXAdminApplication : Application() {

    lateinit var clientManager: ClientManager
        private set

    lateinit var cloudGateway: CloudGeminiGateway
        private set

    lateinit var localEngine: LocalModelServerEngine
        private set

    lateinit var thermalManager: ThermalBatteryManager
        private set

    lateinit var tlsManager: LocalTlsManager
        private set

    lateinit var tunnelManager: TunnelManager
        private set

    lateinit var nsdHelper: AgentXNsdHelper
        private set

    lateinit var database: com.agentx.admin.data.local.AdminDatabase
        private set

    lateinit var server: AgentXAdminServer
        private set

    override fun onCreate() {
        super.onCreate()
        database = com.agentx.admin.data.local.AdminDatabase.getInstance(this)
        thermalManager = ThermalBatteryManager(this)
        tlsManager = LocalTlsManager(this)
        tunnelManager = TunnelManager(this)
        nsdHelper = AgentXNsdHelper(this)
        clientManager = ClientManager(database)
        cloudGateway = CloudGeminiGateway()
        localEngine = LocalModelServerEngine(thermalManager)
        server = AgentXAdminServer(clientManager, cloudGateway, localEngine, thermalManager, tlsManager, tunnelManager)
    }
}
