package net.fununity.cloud.server.listeners.cloud;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

import java.util.Vector;

public class CloudEvents implements CloudEventListener {

    private final ClientHandler clientHandler;
    private final ServerHandler serverHandler;

    public CloudEvents(){
        clientHandler = ClientHandler.getInstance();
        serverHandler = ServerHandler.getInstance();
    }

    @Override
    public void newCloudEvent(CloudEvent cloudEvent) {
        ChannelHandlerContext ctx = null;
        switch(cloudEvent.getId()){
            case CloudEvent.SERVER_CREATE:
                Vector data = cloudEvent.getData();
                serverHandler.addServer(new Server(data.get(0).toString(), data.get(1).toString(), Integer.parseInt(data.get(2).toString()), data.get(3).toString(), data.get(4).toString(), (ServerType)data.get(5)));
                break;
            case CloudEvent.SERVER_CREATE_WITHOUT_PORT:
                data = cloudEvent.getData();
                serverHandler.addServer(new Server(data.get(0).toString(), data.get(1).toString(), data.get(2).toString(), data.get(3).toString(), (ServerType)data.get(4)));
                break;
            case CloudEvent.CLIENT_REGISTER:
                ctx = (ChannelHandlerContext)cloudEvent.getData().get(1);
                clientHandler.saveClient(cloudEvent.getData().get(0).toString(), ctx);
                break;
            case CloudEvent.FORWARD_TO_BUNGEE:
                ChannelHandlerContext bungee = clientHandler.getClientContext("Main");
                if(bungee == null){
                    CloudServer.getLogger().warn("BUNGEECORD IS NOT REGISTERED!!!");
                    break;
                }
                bungee.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream((CloudEvent)cloudEvent.getData().get(0)).toByteArray()));
                break;
            case CloudEvent.NOTIFY_IDLE:
                String serverId = cloudEvent.getData().get(0).toString();
                serverHandler.setServerIdle(serverId);
                break;
            case CloudEvent.NOTIFY_PLAYER_JOIN:
                serverId = cloudEvent.getData().get(0).toString();
                serverHandler.increasePlayerCountFromServer(serverId);
                break;
            case CloudEvent.NOTIFY_PLAYER_QUIT:
                serverId = cloudEvent.getData().get(0).toString();
                serverHandler.reducePlayerCountFromServer(serverId);
                break;
            case CloudEvent.FORWARD_TO_LOBBIES:
                CloudEvent toForward = (CloudEvent)cloudEvent.getData().get(0);
                if(toForward.getId() == CloudEvent.REQ_FOLLOW_ME){
                    Server server = serverHandler.getServerByIdentifier(toForward.getData().get(0).toString());
                    toForward = new CloudEvent(CloudEvent.RES_FOLLOW_ME);
                    toForward.addData(serverHandler.getServerDefinitionByPort(server.getServerPort()));
                }
                for(Server server : serverHandler.getLobbyServers()){
                    ChannelHandlerContext lobbyContext = clientHandler.getClientContext(server.getServerId());
                    if(lobbyContext != null) lobbyContext.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(toForward).toByteArray()));
                }
                break;
        }
    }
}
