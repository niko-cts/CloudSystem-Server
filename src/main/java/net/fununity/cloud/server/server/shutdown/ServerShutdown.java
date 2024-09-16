package net.fununity.cloud.server.server.shutdown;

import net.fununity.cloud.server.misc.MinigameHandler;

/**
 * Interface for the server shutdown,
 * as BungeeCord needs to send a confirmation, that the server was stopped
 * @author Niko
 * @since 0.0.1
 */
public interface ServerShutdown {

    /**
     * Will be called if the server completely stops.
     * @since 0.0.1
     */
    void serverStopped();

    /**
     * A minigame check needs to happen in the stopping process.
     * @see MinigameHandler
     * @return boolean - check if a new minigame lobby should be instantiated.
     * @since 0.0.1
     */
    default boolean needsMinigameCheck() {
        return false;
    }
}
