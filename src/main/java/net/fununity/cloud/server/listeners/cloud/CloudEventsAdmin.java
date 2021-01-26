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
import net.fununity.cloud.server.server.Server;

import java.util.List;

public class CloudEventsAdmin implements CloudEventListener {

    private final CloudServer cloudServer;
    private final ClientHandler clientHandler;
    private final ServerHandler serverHandler;

    public CloudEventsAdmin(){
        cloudServer = CloudServer.getInstance();
        clientHandler = ClientHandler.getInstance();
        serverHandler = ServerHandler.getInstance();
    }

    @Override
    public void newCloudEvent(CloudEvent cloudEvent) {
        ServerType serverType = null;
        String serverId = "";
        ChannelHandlerContext ctx = null;

        switch(cloudEvent.getId()){
            case CloudEvent.ADMIN_REQ_CLOUD_SHUTDOWN:
                cloudServer.shutdownEverything();
                break;
            case CloudEvent.ADMIN_REQ_SERVER_SHUTDOWN:
                Server server = serverHandler.getServerByIdentifier(cloudEvent.getData().get(0).toString());
                serverHandler.shutdownServer(server);
                break;
            case CloudEvent.ADMIN_REQ_SERVER_CREATE:
                serverType = (ServerType)cloudEvent.getData().get(0);
                serverHandler.createServerByServerType(serverType);
                break;
            case CloudEvent.ADMIN_REQ_SERVER_LIST:
                ctx = (ChannelHandlerContext)cloudEvent.getData().get(0);
                List<ServerDefinition> serverDefinitions = serverHandler.generateServerDefinitions();
                ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(
                        new CloudEvent(CloudEvent.ADMIN_RES_SERVER_LIST).addData(serverDefinitions)).toByteArray()));
                break;
            case CloudEvent.ADMIN_REQ_SERVER_TYPE_SHUTDOWN:
                serverType = (ServerType)cloudEvent.getData().get(0);
                serverHandler.shutdownAllServersOfType(serverType);
                break;
            case CloudEvent.ADMIN_REQ_SERVER_RESTART:
                serverId = cloudEvent.getData().get(0).toString();
                clientHandler.sendDisconnect(serverId);
                serverHandler.getServerByIdentifier(serverId).restart();
                break;
            case CloudEvent.ADMIN_REQ_SERVER_TYPE_RESTART:
                serverType = (ServerType)cloudEvent.getData().get(0);
                serverHandler.restartAllServersOfType(serverType);
                break;
            case CloudEvent.ADMIN_REQ_SERVER_SHUTDOWN_ALL:
                serverHandler.shutdownAllServers();
                break;
            case CloudEvent.ADMIN_REQ_SERVER_START_DEFAULT:
                serverHandler.startDefaultServers();
                break;
        }
    }
}
