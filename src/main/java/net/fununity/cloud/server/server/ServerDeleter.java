package net.fununity.cloud.server.server;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.misc.ServerHandler;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Server deleter class delays the deletion or backup moving of the server in order to stop the server perfectly.
 * @author Niko
 * @since 0.0.1
 */
public class ServerDeleter extends TimerTask {

    private final Server server;

    /**
     * Instantiates the class and starts the delay.
     * @param server Server - the server instance.
     * @since 0.0.1
     */
    public ServerDeleter(Server server) {
        this.server = server;
        new Timer().schedule(this, 100);
    }

    /**
     * The action to be performed by this timer task.
     */
    @Override
    public void run() {
        ServerType serverType = server.getServerType();
        try {
            if (serverType == ServerType.LANDSCAPES || serverType == ServerType.FREEBUILD || serverType == ServerType.COCBASE)
                server.moveToBackup(false);
            else
                server.deleteServerContent(Paths.get(server.serverPath).toFile());
        } catch (IOException exception) {
            Server.LOG.warn("Error while deleting server: " + exception.getMessage());
        }

        ServerHandler.getInstance().getServers().remove(server);

        if (ServerHandler.getInstance().getClientHandler().getClientContext(server.getServerId()) != null)
            ServerHandler.getInstance().getClientHandler().removeClient(server.getServerId());

        if (serverType == ServerType.LOBBY)
            ServerHandler.getInstance().getClientHandler().sendLobbyInformationToLobbies();
        else
            MinigameHandler.getInstance().removeServer(server, server.getShutdownProcess() != null && server.getShutdownProcess().needsMinigameCheck());

        if (server.getShutdownProcess() != null)
            server.getShutdownProcess().serverStopped();

        Server.LOG.info(server.getServerId() + " fully stopped.");

        ServerHandler.getInstance().checkStopQueue(server);
    }
}
