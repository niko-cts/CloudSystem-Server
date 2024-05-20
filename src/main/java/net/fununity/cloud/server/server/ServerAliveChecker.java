package net.fununity.cloud.server.server;

import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.utils.CloudLogger;
import net.fununity.cloud.server.client.ClientHandler;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * <p>
 * This class checks if the server instance is still alive.
 * If the server does not respond for a long period, the server will be removed.</p>
 * This sends periodically an CLIENT_ALIVE_REQUEST event.
 *
 * @author Niko
 * @see Server
 */
public class ServerAliveChecker extends TimerTask {

    private static final CloudLogger LOG = CloudLogger.getLogger(ServerAliveChecker.class.getSimpleName());
    private static final int PERIOD_TIME = 10000;

    private final Server server;
    private final Timer timer;
    private int aliveOrdinal;

    /**
     * Instantiates the class and starts the repeation.
     *
     * @param server Server - the server instance.
     * @since 0.0.1
     */
    protected ServerAliveChecker(Server server) {
        this.server = Objects.requireNonNull(server);
        this.aliveOrdinal = 0;
        timer = new Timer();
        timer.schedule(this, 30000, PERIOD_TIME);
    }

    /**
     * The action to be performed by this timer task.
     */
    @Override
    public void run() {
        if (server.isStopped()) {
            LOG.debug("Server seems to be stopped... Stopping timer.");
            stopTimer();
            return;
        }
        if (!server.isRunning()) {
            LOG.warn("Process of server '%s' could not be found. Assuming is not running anymore", server.getServerId());
            server.clientDisconnected();
            return;
        }
        switch (aliveOrdinal) {
            case 30 -> ClientHandler.getInstance().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_ALIVE_REQUEST));
            case 31 -> {
                LOG.warn("%s did not response in %s s. Sending last response request...", server.getServerId(), PERIOD_TIME * aliveOrdinal / 1000);
                ClientHandler.getInstance().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_ALIVE_REQUEST));
            }
            case 32 -> {
                LOG.warn("%s did not response in %s s. Flushing server...", server.getServerId(), PERIOD_TIME * aliveOrdinal / 1000);
                server.flushServer();
                stopTimer();
            }
        }
        this.aliveOrdinal++;
    }

    protected void stopTimer() {
        timer.cancel();
    }

    public void receivedEvent() {
        aliveOrdinal = 0; // wird erst nach * DELAY anfangen zu senden
    }
}
