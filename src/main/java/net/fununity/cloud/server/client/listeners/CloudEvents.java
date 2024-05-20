package net.fununity.cloud.server.client.listeners;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.client.ClientHandler;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;

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
        String serverId;
        Server server;
        switch (cloudEvent.getId()) {
            case CloudEvent.SERVER_CREATE_BY_TYPE ->
                    serverHandler.createServerByServerType((ServerType) cloudEvent.getData().get(0));
            case CloudEvent.CLIENT_REGISTER -> {
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(cloudEvent.getData().size() - 1);
                String clientId = cloudEvent.getData().get(0).toString();
                clientHandler.saveClient(clientId, ctx);

                int port;

                if (cloudEvent.getData().size() == 2) { // BUNGEE CORD ONLY
                    List<Server> bungeeServers = ServerHandler.getInstance().getServersByType(ServerType.BUNGEECORD);
                    if (bungeeServers.isEmpty()) {
                        ClientHandler.getLogger().error("There was a bungeecord server registration, but no bungeecord is registered in the list! Sending disconnect...");
                        clientHandler.sendEvent(ctx, new CloudEvent(CloudEvent.CLIENT_DISCONNECT_GRACEFULLY));
                        return;
                    }
                    port = bungeeServers.get(bungeeServers.size() - 1).getServerPort();
                } else {
                    port = Integer.parseInt(cloudEvent.getData().get(1).toString());
                }

                clientHandler.remapChannelHandlerContext(ctx, port);
                ServerDefinition def = serverHandler.getServerDefinitionByPort(port);

                if (def == null) {
                    ClientHandler.getLogger().error("Could not create server definition of %s:%s", clientId, port);
                    break;
                }

                clientHandler.sendEvent(ctx, new CloudEvent(CloudEvent.RES_SERVER_INFO).addData(def));

                if (def.getServerType() == ServerType.LOBBY) {
                    clientHandler.sendLobbyInformationToLobbies();
                } else if (def.getServerType() == ServerType.COCATTACK) {
                    clientHandler.sendCocAttackServerAmount();
                }

                ClientHandler.getLogger().info("Client registered: " + def.getServerId());
                serverHandler.checkStartQueue(ServerHandler.getInstance().getServerByIdentifier(def.getServerId()));
            }
            case CloudEvent.CLIENT_DISCONNECT_GRACEFULLY -> {
                serverId = cloudEvent.getData().get(0).toString();
                server = serverHandler.getServerByIdentifier(serverId);
                if (server != null)
                    server.clientDisconnected();
            }
//            case CloudEvent.BUNGEE_SERVER_REMOVED_RESPONSE -> {
//                server = serverHandler.getServerByIdentifier(cloudEvent.getData().get(0).toString());
//                if (server != null)
//                    server.bungeeRemovedServer();
//            }
            case CloudEvent.CLIENT_ALIVE_RESPONSE -> {
                serverId = cloudEvent.getData().get(0).toString();
                server = serverHandler.getServerByIdentifier(serverId);
                if (server != null)
                    server.receivedClientAliveResponse();
            }
            case CloudEvent.FORWARD_TO_BUNGEE ->
                    serverHandler.sendToBungeeCord((CloudEvent) cloudEvent.getData().get(0));
            case CloudEvent.NOTIFY_SERVER_PLAYER_COUNT -> {
                serverId = cloudEvent.getData().get(0).toString();
                int playerCount = Integer.parseInt(cloudEvent.getData().get(1).toString());
                serverHandler.setPlayerCountFromServer(serverId, playerCount);
            }
            case CloudEvent.FORWARD_TO_SERVER -> {
                ctx = clientHandler.getClientContext(cloudEvent.getData().get(0).toString());
                if (ctx != null)
                    clientHandler.sendEvent(ctx, (CloudEvent) cloudEvent.getData().get(1));
            }
            case CloudEvent.FORWARD_TO_SERVERTYPE -> {
                ServerType serverType = (ServerType) cloudEvent.getData().get(0);
                List<Server> serversByType = serverHandler.getActiveServersByType(serverType);

                if (serversByType.isEmpty())
                    break;

                CloudEvent forwardingEvent = (CloudEvent) cloudEvent.getData().get(1);

                if (forwardingEvent.getId() == CloudEvent.REQ_FOLLOW_ME) {
                    forwardingEvent = new CloudEvent(CloudEvent.RES_FOLLOW_ME);
                    for (int i = 0; i < ((CloudEvent) cloudEvent.getData().get(1)).getData().size(); i++)
                        forwardingEvent.addData(((CloudEvent) cloudEvent.getData().get(1)).getData().get(i));
                } else if (forwardingEvent.getId() == CloudEvent.STATUS_MINIGAME) {
                    MinigameHandler.getInstance().receivedStatusUpdate(forwardingEvent);
                    if (forwardingEvent.getData().size() == 7) {
                        CloudEvent finalToForward = forwardingEvent;
                        serversByType.removeIf(s -> !s.getServerId().equals(finalToForward.getData().get(6).toString()));
                    }
                }

                for (Server s : serversByType) {
                    clientHandler.sendEvent(s, forwardingEvent);
                }
            }
        }
    }
}
