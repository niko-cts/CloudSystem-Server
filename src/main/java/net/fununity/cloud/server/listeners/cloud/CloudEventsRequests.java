package net.fununity.cloud.server.listeners.cloud;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.EventPriority;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

public class CloudEventsRequests implements CloudEventListener {

    private final ClientHandler clientHandler;
    private final ServerHandler serverHandler;

    public CloudEventsRequests() {
        clientHandler = ClientHandler.getInstance();
        serverHandler = ServerHandler.getInstance();
    }

    @Override
    public void newCloudEvent(CloudEvent cloudEvent) {
        switch (cloudEvent.getId()) {
            case CloudEvent.REQ_LOBBY_COUNT:
                CloudEvent event = new CloudEvent(CloudEvent.RES_LOBBY_INFOS);
                event.addData(serverHandler.getLobbyCount());
                ChannelHandlerContext ctx = (ChannelHandlerContext) cloudEvent.getData().get(0);
                ClientHandler.getInstance().sendEvent(ctx, event);
                break;
            case CloudEvent.REQ_SERVER_INFO:
                int port = Integer.parseInt(cloudEvent.getData().get(0).toString());
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(1);
                clientHandler.remapChannelHandlerContext(ctx, port);
                ServerDefinition def = serverHandler.getServerDefinitionByPort(port);
                if (def == null) {
                    CloudServer.getLogger().warn("Server info was requested but definition is null (port: " + port + ")");
                    break;
                }

                event = new CloudEvent(CloudEvent.RES_SERVER_INFO).addData(def);
                ClientHandler.getInstance().sendEvent(ctx, event);
                CloudServer.getLogger().info("Server requested info: " + def.getServerId());
                break;
            case CloudEvent.REQ_SERVER_SHUTDOWN:
                if (cloudEvent.getData().size() == 1) {
                    ctx = (ChannelHandlerContext) cloudEvent.getData().get(0);
                    clientHandler.removeClient(ctx);
                    ClientHandler.getInstance().sendEvent(ctx, new CloudEvent(CloudEvent.CLIENT_DISCONNECT_GRACEFULLY));
                } else {
                    String serverId = cloudEvent.getData().get(0).toString();
                    serverHandler.shutdownServer(serverHandler.getServerByIdentifier(serverId));
                }
                break;
            case CloudEvent.REQ_SERVER_TYPE:
                ServerType serverType = serverHandler.getServerByIdentifier(cloudEvent.getData().get(0).toString()).getServerType();
                event = new CloudEvent(CloudEvent.RES_SERVER_TYPE);
                event.addData(serverType);
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(1);
                ClientHandler.getInstance().sendEvent(ctx, event);
                break;
            case CloudEvent.REQ_SEND_PLAYER_TO_LOBBY:
                String serverId = serverHandler.getServerIdOfSuitableLobby();
                if (serverId.isEmpty()) {
                    CloudServer.getLogger().warn("No Lobby registered!!");
                    break;
                }

                event = new CloudEvent(CloudEvent.BUNGEE_SEND_PLAYER);
                event.addData(serverId);
                for (int i = 0; i < cloudEvent.getData().size() - 1; i++)
                    event.addData(cloudEvent.getData().get(i));

                serverHandler.sendToBungeeCord(event);
                break;
            case CloudEvent.REQ_PLAYER_COUNT_SERVER:
                event = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_SERVER);
                event.addData(serverHandler.getPlayerCountOfServer(cloudEvent.getData().get(0).toString()));
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(1);
                ClientHandler.getInstance().sendEvent(ctx, event);
                break;
            case CloudEvent.REQ_PLAYER_COUNT_TYPE:
                event = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_TYPE);
                event.addData(serverHandler.getPlayerCountOfServerType((ServerType) cloudEvent.getData().get(0)));
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(1);
                ClientHandler.getInstance().sendEvent(ctx, event);
                break;
            case CloudEvent.REQ_PLAYER_COUNT_NETWORK:
                event = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_NETWORK);
                event.addData(serverHandler.getPlayerCountOfNetwork());
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(0);
                ClientHandler.getInstance().sendEvent(ctx, event);
                break;
            case CloudEvent.NOTIFY_NETWORK_PLAYER_COUNT:
                serverHandler.setPlayerCountOfNetwork(Integer.parseInt(cloudEvent.getData().get(0).toString()));
                event = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_NETWORK);
                event.setEventPriority(EventPriority.LOW);
                event.addData(serverHandler.getPlayerCountOfNetwork());

                for (Server server : serverHandler.getLobbyServers()) {
                    ChannelHandlerContext lobbyContext = clientHandler.getClientContext(server.getServerId());
                    if (lobbyContext != null)
                        ClientHandler.getInstance().sendEvent(lobbyContext, event);
                }
                break;
        }
    }
}
