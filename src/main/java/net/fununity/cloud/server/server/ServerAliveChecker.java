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
 * <p>
 * This class checks if the server instance is still alive.
 * If the server does not respond for a long period, the server will be removed.</p>
 * This sends periodically an CLIENT_ALIVE_REQUEST event.
 * @see Server
 * @author Niko
 */
public class ServerAliveChecker extends TimerTask {

    private static final int PERIOD_TIME = 3000;
    private static final String LOG_MESSAGE = "[AliveChecker] {0} did not response in {1}ms. ";
    private static final String LAST_RESPONSE = "Sending last response request...";
    private static final String FLUSH_RESPONSE = "Flushing server!";

    private final String serverLogWarn;
    private final Server server;
    private final Timer timer;
    private int aliveOrdinal;

    /**
     * Instantiates the class and starts the repeation.
     * @param server Server - the server instance.
     * @since 0.0.1
     */
    protected ServerAliveChecker(Server server) {
        this.server = server;
        this.aliveOrdinal = ServerAliveInfo.SEND.ordinal() - 1;
        this.serverLogWarn = LOG_MESSAGE.replace("{0}", server.getServerId());
        timer = new Timer();
        timer.schedule(this, 40000, PERIOD_TIME);
    }

    /**
     * The action to be performed by this timer task.
     */
    @Override
    public void run() {
        if (server == null || server.getServerState() == ServerState.STOPPED) {
            timer.cancel();
            return;
        }

        if (this.aliveOrdinal < ServerAliveInfo.REMOVE.ordinal())
            this.aliveOrdinal++;

        switch (ServerAliveInfo.values()[aliveOrdinal]) {
            case SEND -> ClientHandler.getInstance().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_ALIVE_REQUEST).setEventPriority(EventPriority.LOW));
            case NO_RESPONSE -> {
                CloudServer.getLogger().warn(serverLogWarn.replace("{1}", String.valueOf(PERIOD_TIME * aliveOrdinal)) + LAST_RESPONSE);
                ClientHandler.getInstance().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_ALIVE_REQUEST).setEventPriority(EventPriority.LOW));
            }
            case REMOVE -> {
                CloudServer.getLogger().warn(serverLogWarn.replace("{1}", String.valueOf(PERIOD_TIME * aliveOrdinal)) + FLUSH_RESPONSE);
                ServerHandler.getInstance().flushServer(server);
            }
        }
    }

    protected void stopTimer() {
        timer.cancel();
    }

    public void receivedEvent() {
        aliveOrdinal = 0;
    }

    private enum ServerAliveInfo {
        ALIVE,
        WAIT,
        WAIT_2,
        SEND,
        NO_RESPONSE,
        REMOVE
    }
}
