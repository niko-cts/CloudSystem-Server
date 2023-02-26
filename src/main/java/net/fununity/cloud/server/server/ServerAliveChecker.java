package net.fununity.cloud.server.server;

import net.fununity.cloud.common.events.EventPriority;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;
import net.fununity.cloud.server.misc.ServerHandler;

import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * <b>DISABLED</b> - Enable in {@link Server}
 * <p>
 * This class checks if the server instance is still alive.
 * If the server does not respond for a long period, the server will be removed.</p>
 * @see Server
 * @author Niko
 */
public class ServerAliveChecker extends TimerTask {

    private static final int PERIOD_TIME = 120000;

    private final Server server;
    private final Timer timer;
    private ServerAliveInfo aliveInfo;

    /**
     * Instantiates the class and starts the repeation.
     * @param server Server - the server instance.
     * @since 0.0.1
     */
    public ServerAliveChecker(Server server) {
        this.server = server;
        this.aliveInfo = ServerAliveInfo.ALIVE;
        timer = new Timer();
        timer.schedule(this, 20000, PERIOD_TIME);
    }

    /**
     * The action to be performed by this timer task.
     */
    @Override
    public void run() {
        if (server == null || server.getServerState() != ServerState.RUNNING) {
            timer.cancel();
            return;
        }

        if (this.aliveInfo != ServerAliveInfo.REMOVE)
            this.aliveInfo = ServerAliveInfo.values()[this.aliveInfo.ordinal() + 1];
        switch (this.aliveInfo) {
            case NO_RESPONSE:
                ClientHandler.getInstance().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_ALIVE_REQUEST).setEventPriority(EventPriority.LOW));
                break;
            case NO_RESPONSE_2:
                CloudServer.getLogger().warn(server.getServerId() + " did not response in " + (PERIOD_TIME * this.aliveInfo.ordinal()) + "ms. Sending last response request");
                ClientHandler.getInstance().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_ALIVE_REQUEST).setEventPriority(EventPriority.LOW));
                break;
            case RESTART_REQUEST:
                CloudServer.getLogger().warn(server.getServerId() + " did not response in " + (PERIOD_TIME * this.aliveInfo.ordinal()) + "ms. Try to restart server.");
                ServerHandler.getInstance().restartServer(server);
                break;
            case REMOVE:
                CloudServer.getLogger().warn(server.getServerId() + " did not response in " + (PERIOD_TIME * this.aliveInfo.ordinal()) + "ms. Flushing server.");
                ServerHandler.getInstance().flushServer(server);
                break;
        }
    }

    protected void stopTimer() {
        timer.cancel();
    }

    public void receivedEvent() {
        this.aliveInfo = ServerAliveInfo.ALIVE;
    }

    private enum ServerAliveInfo {
        ALIVE,
        NO_RESPONSE,
        NO_RESPONSE_2,
        RESTART_REQUEST,
        REMOVE
    }
}
