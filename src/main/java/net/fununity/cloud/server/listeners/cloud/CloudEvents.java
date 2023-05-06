package net.fununity.cloud.server.listeners.cloud;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

import java.util.List;

public class CloudEvents implements CloudEventListener {

    private final ClientHandler clientHandler;
    private final ServerHandler serverHandler;

    public CloudEvents() {
        clientHandler = ClientHandler.getInstance();
        serverHandler = ServerHandler.getInstance();
    }

    @Override
    public void newCloudEvent(CloudEvent cloudEvent) {
        ChannelHandlerContext ctx;
        switch (cloudEvent.getId()) {
            case CloudEvent.SERVER_CREATE_BY_TYPE:
                serverHandler.createServerByServerType((ServerType) cloudEvent.getData().get(0));
                break;
            case CloudEvent.CLIENT_REGISTER:
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(cloudEvent.getData().size() - 1);
                String clientId = cloudEvent.getData().get(0).toString();
                clientHandler.saveClient(clientId, ctx);

                int port;

                if (cloudEvent.getData().size() == 2) { // BUNGEE CORD ONLY
                    clientHandler.setClientIdToEventSender(ctx, clientId);
                    List<Server> bungeeServers = ServerHandler.getInstance().getBungeeServers();
                    port = bungeeServers.get(bungeeServers.size() - 1).getServerPort();
                } else {
                    port = Integer.parseInt(cloudEvent.getData().get(1).toString());
                }

                clientHandler.remapChannelHandlerContext(ctx, port);
                ServerDefinition def = serverHandler.getServerDefinitionByPort(port);

                if (def == null) {
                    CloudServer.getLogger().warn("Could not create server definition of " + clientId + ":" + port);
                    break;
                }

                clientHandler.sendEvent(ctx, new CloudEvent(CloudEvent.RES_SERVER_INFO).addData(def));

                if (def.getServerType() == ServerType.LOBBY) {
                    clientHandler.sendLobbyInformationToLobbies();
                } else if (def.getServerType() == ServerType.COCATTACK) {
                    clientHandler.sendCocAttackServerAmount();
                }

                CloudServer.getLogger().info("Client registered: " + def.getServerId());
                serverHandler.checkStartQueue(def);
                break;
            case CloudEvent.CLIENT_DISCONNECT_GRACEFULLY:
                String serverId = cloudEvent.getData().get(0).toString();
                Server server = serverHandler.getServerByIdentifier(serverId);
                if (server != null && server.getServerState() != ServerState.STOPPED)
                    serverHandler.stopServerFinally(server);
                break;
            case CloudEvent.BUNGEE_SERVER_REMOVED_RESPONSE:
                ctx = clientHandler.getClientContext(cloudEvent.getData().get(0).toString());
                if (ctx != null)
                    clientHandler.sendEvent(ctx, new CloudEvent(CloudEvent.CLIENT_SHUTDOWN_REQUEST));
                break;
            case CloudEvent.CLIENT_SHUTDOWN_RESPONSE:
                serverId = cloudEvent.getData().get(0).toString();
                server = serverHandler.getServerByIdentifier(serverId);
                serverHandler.deleteServer(server);
                break;
            case CloudEvent.CLIENT_ALIVE_RESPONSE:
                serverId = cloudEvent.getData().get(0).toString();
                server = serverHandler.getServerByIdentifier(serverId);
                if (server != null)
                    server.receivedClientAliveResponse();
                break;
            case CloudEvent.FORWARD_TO_BUNGEE:
                serverHandler.sendToBungeeCord((CloudEvent) cloudEvent.getData().get(0));
                break;

            case CloudEvent.NOTIFY_IDLE:
                serverId = cloudEvent.getData().get(0).toString();
                serverHandler.setServerIdle(serverId);
                break;
            case CloudEvent.NOTIFY_SERVER_PLAYER_COUNT:
                serverId = cloudEvent.getData().get(0).toString();
                int playerCount = Integer.parseInt(cloudEvent.getData().get(1).toString());
                serverHandler.setPlayerCountFromServer(serverId, playerCount);
                break;
            case CloudEvent.FORWARD_TO_SERVER:
                ctx = clientHandler.getClientContext(cloudEvent.getData().get(0).toString());
                if (ctx != null)
                    clientHandler.sendEvent(ctx, (CloudEvent) cloudEvent.getData().get(1));
                break;
            case CloudEvent.FORWARD_TO_SERVERTYPE:
                ServerType serverType = (ServerType) cloudEvent.getData().get(0);
                List<Server> serversByType = serverHandler.getServersByType(serverType);

                if (serversByType.isEmpty())
                    break;

                CloudEvent forwardingEvent = (CloudEvent) cloudEvent.getData().get(1);

                if (forwardingEvent.getId() == CloudEvent.REQ_FOLLOW_ME) {
                    forwardingEvent = new CloudEvent(CloudEvent.RES_FOLLOW_ME);
                    for (int i = 0; i < ((CloudEvent) cloudEvent.getData().get(1)).getData().size(); i++)
                        forwardingEvent.addData(((CloudEvent) cloudEvent.getData().get(1)).getData().get(i));
                } else
                if (forwardingEvent.getId() == CloudEvent.STATUS_MINIGAME) {
                    MinigameHandler.getInstance().receivedStatusUpdate(forwardingEvent);
                    if (forwardingEvent.getData().size() == 7) {
                        CloudEvent finalToForward = forwardingEvent;
                        serversByType.removeIf(s -> !s.getServerId().equals(finalToForward.getData().get(6).toString()));
                    }
                }

                for (Server s : serversByType) {
                    ChannelHandlerContext lobbyContext = clientHandler.getClientContext(s.getServerId());
                    if (lobbyContext != null)
                        clientHandler.sendEvent(lobbyContext, forwardingEvent);
                }
                break;
        }
    }
}
