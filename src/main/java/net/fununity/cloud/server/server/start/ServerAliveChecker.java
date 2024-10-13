package net.fununity.cloud.server.server.start;

import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.config.NetworkConfig;
import net.fununity.cloud.server.server.Server;

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
@Slf4j
public class ServerAliveChecker extends TimerTask {

	private final long periodInMillis;
	private final int eventOnTimerRuns;

	private final Server server;
	private final Timer timer;
	private int aliveOrdinal;

	/**
	 * Instantiates the class and starts the repeation.
	 *
	 * @param server Server - the server instance.
	 * @since 0.0.1
	 */
	public ServerAliveChecker(Server server) {
		this.periodInMillis = CloudServer.getInstance().getNetworkConfig().map(NetworkConfig::getAliveEventPeriodInMillis).orElse(10000L);
		this.eventOnTimerRuns = CloudServer.getInstance().getNetworkConfig().map(NetworkConfig::getStartSendingAliveEventOnTimerRuns).orElse(30);
		this.server = Objects.requireNonNull(server);
		this.aliveOrdinal = 0;
		log.debug("Starting alive checker for server '{}' with period of {} ms.", server.getServerId(), periodInMillis);
		timer = new Timer();
		timer.schedule(this, Math.max(20000, periodInMillis * 2), periodInMillis);
	}

	/**
	 * The action to be performed by this timer task.
	 */
	@Override
	public void run() {
		if (server.isStopped()) {
			log.warn("Server seems to be stopped, but alive checker was not removed... Stopping timer.");
			stopTimer();
			return;
		}
		if (!server.isRunning()) {
			log.warn("Process of server '{}' could not be found. Assuming is not running anymore", server.getServerId());
			server.clientDisconnected();
			return;
		}

		if (aliveOrdinal == eventOnTimerRuns) {
			log.debug("Sending normal alive request to {} after {} s.", server.getServerId(), periodInMillis * aliveOrdinal / 1000);
			CloudServer.getInstance().getClientHandler().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_ALIVE_REQUEST));
		} else if (aliveOrdinal == eventOnTimerRuns + 1) {
			log.warn("{} did not response in {} s. Sending last response request...", server.getServerId(), periodInMillis * aliveOrdinal / 1000);
			CloudServer.getInstance().getClientHandler().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_ALIVE_REQUEST));
		} else {
			log.warn("{} did not response in {} s. Flushing server...", server.getServerId(), periodInMillis * aliveOrdinal / 1000);
			timer.cancel();
			server.stop();
		}

		this.aliveOrdinal++;
	}

	public void stopTimer() {
		timer.cancel();
	}

	public void receivedEvent() {
		aliveOrdinal = 0;
	}
}
