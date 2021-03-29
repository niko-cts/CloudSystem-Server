package net.fununity.cloud.server.misc;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.server.Server;

import java.util.Iterator;

/**
 * A helper class for restarting multiple server.
 * @author Niko
 * @since 0.0.1
 */
public class ServerRestartingIterator {

    private final ServerType serverType;
    private final Iterator<Server> iterator;
    private final int size;

    /**
     * Instantiates the class.
     * @param serverType ServerType - the server type.
     * @param iterator Iterator<Server> - The iterator for restarting the servers.
     * @param size int - the size of servers to restart.
     * @since 0.0.1
     */
    public ServerRestartingIterator(ServerType serverType, Iterator<Server> iterator, int size) {
        this.serverType = serverType;
        this.iterator = iterator;
        this.size = size;
    }

    /**
     * The type of the server.
     * @return ServerType - the type of the server to restart.
     * @since 0.0.1
     */
    public ServerType getServerType() {
        return serverType;
    }

    /**
     * The iterator for restarting servers.
     * @return Iterator<Server> - The iterator for server restarting.
     * @since 0.0.1
     */
    public Iterator<Server> getIterator() {
        return iterator;
    }

    /**
     * Get the size of the iterator.
     * @return int - the size of the servers to restart.
     * @since 0.0.1
     */
    public int getSize() {
        return size;
    }
}
