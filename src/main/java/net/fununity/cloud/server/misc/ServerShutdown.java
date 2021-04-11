package net.fununity.cloud.server.misc;

/**
 * Interface for the server shutdown,
 * as BungeeCord needs to send a confirmation, that the server was stopped
 * @author Niko
 * @since 0.0.1
 */
public abstract class ServerShutdown {

    private final boolean needsMinigameCheck;

    public ServerShutdown(boolean needsMinigameCheck) {
        this.needsMinigameCheck = needsMinigameCheck;
    }

    /**
     * Server was stopped.
     * @since 0.0.1
     */
    public void serverStopped() {
        // nothing to do here
    }

    /**
     * A minigame check needs to happen in the stopping process.
     * @see MinigameHandler
     * @return boolean - check if new minigame lobby should be instantiated.
     * @since 0.0.1
     */
    public boolean needsMinigameCheck() {
        return this.needsMinigameCheck;
    }
}
