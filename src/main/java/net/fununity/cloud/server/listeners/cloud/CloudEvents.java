package net.fununity.cloud.server.listeners.cloud;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

import java.util.List;
import java.util.Vector;

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
            case CloudEvent.SERVER_CREATE:
                Vector data = cloudEvent.getData();
                serverHandler.addServer(new Server(data.get(0).toString(), data.get(1).toString(), data.get(3).toString(), data.get(4).toString(), Integer.parseInt(data.get(5).toString()), (ServerType) data.get(6)));
                break;
            case CloudEvent.SERVER_CREATE_WITHOUT_PORT:
                data = cloudEvent.getData();
                serverHandler.addServer(new Server(data.get(0).toString(), data.get(1).toString(), data.get(2).toString(), data.get(3).toString(), Integer.parseInt(data.get(4).toString()), (ServerType) data.get(5)));
                break;
            case CloudEvent.CLIENT_REGISTER:
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(cloudEvent.getData().size() - 1);
                clientHandler.saveClient(cloudEvent.getData().get(0).toString(), ctx);

                if (cloudEvent.getData().size() == 2) {
                    clientHandler.setClientIdToEventSender(ctx, "Main");
                    break;
                }

                int port = Integer.parseInt(cloudEvent.getData().get(1).toString());
                clientHandler.remapChannelHandlerContext(ctx, port);
                ServerDefinition def = serverHandler.getServerDefinitionByPort(port);

                if (def != null)
                    CloudServer.getLogger().info("Client registered: " + def.getServerId());

                if (def == null || def.getServerType() == ServerType.BUNGEECORD)
                    break;

                clientHandler.sendEvent(ctx, new CloudEvent(CloudEvent.RES_SERVER_INFO).addData(def));

                if (def.getServerType() == ServerType.LOBBY) {
                    clientHandler.sendLobbyInformationToLobbies();
                }

                serverHandler.checkStartQueue(def);
                break;
            case CloudEvent.CLIENT_DISCONNECT_GRACEFULLY:
                String serverId = cloudEvent.getData().get(0).toString();
                Server server = serverHandler.getServerByIdentifier(serverId);
                if (server != null)
                    serverHandler.addStopQueue(server);
                break;
            case CloudEvent.BUNGEE_REMOVED_SERVER:
                ctx = clientHandler.getClientContext(cloudEvent.getData().get(0).toString());
                if (ctx != null)
                    clientHandler.sendEvent(ctx, new CloudEvent(CloudEvent.CLIENT_SHUTDOWN_REQUEST));
                break;
            case CloudEvent.CLIENT_SHUTDOWN_RESPONSE:
                serverId = cloudEvent.getData().get(0).toString();
                server = serverHandler.getServerByIdentifier(serverId);
                serverHandler.addStopQueue(server);
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
            case CloudEvent.FORWARD_TO_LOBBIES:
                CloudEvent toForward = (CloudEvent) cloudEvent.getData().get(0);
                if (toForward.getId() == CloudEvent.REQ_FOLLOW_ME) {
                    toForward = new CloudEvent(CloudEvent.RES_FOLLOW_ME);
                    for (int i = 0; i < ((CloudEvent) cloudEvent.getData().get(0)).getData().size(); i++)
                        toForward.addData(((CloudEvent) cloudEvent.getData().get(0)).getData().get(i));
                }
                List<Server> lobbyServers = serverHandler.getLobbyServers();

                if (toForward.getId() == CloudEvent.STATUS_MINIGAME) {
                    MinigameHandler.getInstance().receivedStatusUpdate(toForward);
                    if (toForward.getData().size() == 7) {
                        CloudEvent finalToForward = toForward;
                        lobbyServers.removeIf(s -> !s.getServerId().equals(finalToForward.getData().get(6).toString()));
                    }
                }

                for (Server s : lobbyServers) {
                    ChannelHandlerContext lobbyContext = clientHandler.getClientContext(s.getServerId());
                    if (lobbyContext != null)
                        clientHandler.sendEvent(lobbyContext, toForward);
                }
                break;
        }
    }
}
