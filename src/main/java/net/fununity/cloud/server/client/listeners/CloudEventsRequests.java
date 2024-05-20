package net.fununity.cloud.server.client.listeners;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.EventPriority;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.CloudLogger;
import net.fununity.cloud.server.client.ClientHandler;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;

import java.util.*;

public class CloudEventsRequests implements CloudEventListener {

    private static final CloudLogger LOG = ClientHandler.getLogger();
    private final ClientHandler clientHandler;
    private final ServerHandler serverHandler;

    public CloudEventsRequests() {
        this.clientHandler = ClientHandler.getInstance();
        this.serverHandler = ServerHandler.getInstance();
    }

    @Override
    public void newCloudEvent(CloudEvent cloudEvent) {
        ChannelHandlerContext ctx;
        CloudEvent event;
        ServerType serverType;
        switch (cloudEvent.getId()) {
            case CloudEvent.REQ_LOBBY_COUNT -> {
                event = new CloudEvent(CloudEvent.RES_LOBBY_INFOS).addData(serverHandler.getLobbyServers().size());
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(0);
                ClientHandler.getInstance().sendEvent(ctx, event);
            }
            case CloudEvent.REQ_SERVER_INFO -> {
                int port = Integer.parseInt(cloudEvent.getData().get(0).toString());
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(1);
                clientHandler.remapChannelHandlerContext(ctx, port);
                ServerDefinition def = serverHandler.getServerDefinitionByPort(port);
                if (def == null) {
                    LOG.warn("Server info was requested but definition is null (port: " + port + ")");
                    break;
                }

                event = new CloudEvent(CloudEvent.RES_SERVER_INFO).addData(def);
                ClientHandler.getInstance().sendEvent(ctx, event);
                LOG.info("Server requested info: " + def.getServerId());
            }
            case CloudEvent.REQ_SERVER_SHUTDOWN -> {
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(cloudEvent.getData().size() - 1);
                String serverId = cloudEvent.getData().size() == 1 ? 
                                clientHandler.getClientId(ctx) :
                                (String) cloudEvent.getData().get(0);
                Server server = serverHandler.getServerByIdentifier(serverId);
                if (serverId == null || server == null) {
                    LOG.warn("Received REQ_SERVER_SHUTDOWN without additional serverId and could not map CTX to any registered Client! CTX was %s", ctx.channel());
                    ClientHandler.getInstance().sendEvent(ctx, new CloudEvent(CloudEvent.CLIENT_DISCONNECT_GRACEFULLY));
                    ctx.close();
                } else {
                    serverHandler.shutdownServer(server, true);
                }
            }
            case CloudEvent.REQ_SERVER_RESTART -> {
                String serverId = cloudEvent.getData().get(0).toString();
                serverHandler.restartServer(serverHandler.getServerByIdentifier(serverId));
            }
            case CloudEvent.REQ_SERVER_TYPE -> {
                serverType = serverHandler.getServerByIdentifier(cloudEvent.getData().get(0).toString()).getServerType();
                event = new CloudEvent(CloudEvent.RES_SERVER_TYPE);
                event.addData(serverType);
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(1);
                ClientHandler.getInstance().sendEvent(ctx, event);
            }
            case CloudEvent.REQ_SEND_PLAYER_DIFFERENT_LOBBY -> {
                Server blacklistedLobby = ServerHandler.getInstance().getServerByIdentifier(cloudEvent.getData().get(0).toString());
                if (blacklistedLobby != null) {
                    Queue<UUID> queue = new LinkedList<>();
                    for (int i = 1; i < cloudEvent.getData().size() - 1; i++)
                        queue.add((UUID) cloudEvent.getData().get(i));
                    ServerHandler.getInstance().sendPlayerToLobby(new ArrayList<>(Collections.singletonList(blacklistedLobby)), queue);
                }
            }
            case CloudEvent.REQ_SEND_PLAYER_TO_LOBBY -> {
                Queue<UUID> queue = new LinkedList<>();
                for (int i = 0; i < cloudEvent.getData().size() - 1; i++)
                    queue.add((UUID) cloudEvent.getData().get(i));
                ServerHandler.getInstance().sendPlayerToLobby(new ArrayList<>(), queue);
            }
            case CloudEvent.REQ_MINIGAME_RESEND_STATUS ->
                    clientHandler.sendMinigameInformationToLobby(cloudEvent.getData().get(0).toString());
            case CloudEvent.REQ_PLAYER_COUNT_SERVER -> {
                event = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_SERVER);
                event.addData(serverHandler.getPlayerCountOfServer(cloudEvent.getData().get(0).toString()));
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(1);
                ClientHandler.getInstance().sendEvent(ctx, event);
            }
            case CloudEvent.REQ_PLAYER_COUNT_TYPE -> {
                event = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_TYPE);
                event.addData(serverHandler.getPlayerCountOfServerType((ServerType) cloudEvent.getData().get(0)));
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(1);
                ClientHandler.getInstance().sendEvent(ctx, event);
            }
            case CloudEvent.REQ_PLAYER_COUNT_NETWORK -> {
                event = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_NETWORK);
                event.addData(serverHandler.getPlayerCountOfNetwork());
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(0);
                ClientHandler.getInstance().sendEvent(ctx, event);
            }
            case CloudEvent.REQ_MINIGAME_LOBBY_SEND -> {
                serverType = (ServerType) cloudEvent.getData().get(0);
                UUID uuid = (UUID) cloudEvent.getData().get(1);
                MinigameHandler.getInstance().sendPlayerToMinigameLobby(serverType, uuid);
            }
            case CloudEvent.NOTIFY_NETWORK_PLAYER_COUNT -> {
                serverHandler.setPlayerCountOfNetwork(Integer.parseInt(cloudEvent.getData().get(0).toString()));
                event = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_NETWORK);
                event.setEventPriority(EventPriority.LOW);
                event.addData(serverHandler.getPlayerCountOfNetwork());

                for (Server server : serverHandler.getLobbyServers()) {
                    ChannelHandlerContext lobbyContext = clientHandler.getClientContext(server.getServerId());
                    if (lobbyContext != null)
                        ClientHandler.getInstance().sendEvent(lobbyContext, event);
                }
            }
        }
    }
}
