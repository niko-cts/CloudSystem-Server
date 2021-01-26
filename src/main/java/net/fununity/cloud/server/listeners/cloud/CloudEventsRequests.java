package net.fununity.cloud.server.listeners.cloud;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;
import net.fununity.cloud.server.misc.ServerHandler;

public class CloudEventsRequests implements CloudEventListener {

    private final ClientHandler clientHandler;
    private final ServerHandler serverHandler;

    public CloudEventsRequests(){
        clientHandler = ClientHandler.getInstance();
        serverHandler = ServerHandler.getInstance();
    }

    @Override
    public void newCloudEvent(CloudEvent cloudEvent) {
        switch(cloudEvent.getId()) {
            case CloudEvent.REQ_LOBBY_COUNT:
                CloudEvent event = new CloudEvent(CloudEvent.RES_LOBBY_COUNT);
                event.addData(serverHandler.getLobbyCount());
                ChannelHandlerContext ctx = (ChannelHandlerContext) cloudEvent.getData().get(0);
                ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(event).toByteArray()));
                break;
            case CloudEvent.REQ_SERVER_INFO:
                int port = Integer.parseInt(cloudEvent.getData().get(0).toString());
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(1);
                clientHandler.remapChannelHandlerContext(ctx, port);
                ServerDefinition def = serverHandler.getServerDefinitionByPort(port);
                if(def == null) {
                    CloudServer.getLogger().warn("Server info was requested but definition is null (port: " + port + ")");
                    break;
                }
                if(def.getServerType() == ServerType.LOBBY)
                    serverHandler.sendLobbyQueue();

                event = new CloudEvent(CloudEvent.RES_SERVER_INFO).addData(def);
                ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(event).toByteArray()));
                CloudServer.getLogger().info("Server requested info: " + def.getServerId());
                break;
            case CloudEvent.REQ_SERVER_SHUTDOWN:
                if(cloudEvent.getData().size() == 1) {
                    ctx = (ChannelHandlerContext) cloudEvent.getData().get(0);
                    clientHandler.removeClient(ctx);
                } else {
                    String serverId = cloudEvent.getData().get(0).toString();
                    ctx = (ChannelHandlerContext)cloudEvent.getData().get(1);
                    serverHandler.shutdownServer(serverHandler.getServerByIdentifier(serverId));
                }
                ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(new CloudEvent(CloudEvent.CLIENT_DISCONNECT_GRACEFULLY)).toByteArray()));
                break;
            case CloudEvent.REQ_SERVER_TYPE:
                ServerType serverType = serverHandler.getServerByIdentifier(cloudEvent.getData().get(0).toString()).getServerType();
                CloudEvent res = new CloudEvent(CloudEvent.RES_SERVER_TYPE);
                res.addData(serverType);
                ctx = (ChannelHandlerContext)cloudEvent.getData().get(1);
                ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(res).toByteArray()));
                break;
            case CloudEvent.REQ_SEND_PLAYER_TO_LOBBY:
                String serverId = serverHandler.getServerIdOfSuitableLobby();
                if (serverId.isEmpty()) {
                    CloudServer.getLogger().warn("No Lobby registered!!");
                    break;
                }
                ChannelHandlerContext bungee = clientHandler.getClientContext("Main");
                event = new CloudEvent(CloudEvent.BUNGEE_SEND_PLAYER);
                event.addData(cloudEvent.getData().get(0));
                event.addData(serverId);
                bungee.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(event).toByteArray()));
                break;
            case CloudEvent.REQ_PLAYER_COUNT_SERVER:
                res = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_SERVER);
                res.addData(serverHandler.getPlayerCountOfServer(cloudEvent.getData().get(0).toString()));
                ctx = (ChannelHandlerContext)cloudEvent.getData().get(1);
                ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(res).toByteArray()));
                break;
            case CloudEvent.REQ_PLAYER_COUNT_TYPE:
                res = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_TYPE);
                res.addData(serverHandler.getPlayerCountOfServerType((ServerType) cloudEvent.getData().get(0)));
                ctx = (ChannelHandlerContext)cloudEvent.getData().get(1);
                ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(res).toByteArray()));
                break;
            case CloudEvent.REQ_PLAYER_COUNT_NETWORK:
                res = new CloudEvent(CloudEvent.RES_PLAYER_COUNT_NETWORK);
                res.addData(serverHandler.getPlayerCountOfNetwork());
                ctx = (ChannelHandlerContext) cloudEvent.getData().get(0);
                ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(res).toByteArray()));
                break;
        }
    }
}
