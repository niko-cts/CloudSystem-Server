package net.fununity.cloud.server.server.shutdown;

import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.netty.ClientHandler;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
public class ServerStopper {

    private final Server server;
    private final Set<ServerStoppingState> finishedStages;

    private Timer timer;

    public ServerStopper(Server server) {
        this.server = server;
        this.finishedStages = new HashSet<>();
        this.server.stopAliveChecker();
    }

    /**
     * Finds the first state which was not executed yet
     */
    public void nextState() {
        Arrays.stream(ServerStoppingState.values())
                .filter(s -> !finishedStages.contains(s))
                .findFirst().ifPresent(this::executeState);
    }

    /**
     * Flushes server without trying to shut down server
     */
    protected void flushServer() {
        cancelTimer();
        finishedStages.clear();
        finishedStages.addAll(Set.of(ServerStoppingState.REQ_CLIENT_SHUTDOWN, ServerStoppingState.RES_CLIENT_DISCONNECTED));
        nextState();
    }

    protected void executeState(ServerStoppingState state) {
        if (finishedStages.contains(state)) {
            LOG.debug("Tried to triggered stage for {} which was already fired: {}", server.getServerId(), state);
            return;
        }

        finishedStages.add(state);
        cancelTimer();
        LOG.debug("Executing state {}", state);

        switch (state) {
            case REQ_BUNGEECORD_REMOVE -> sendBungeeRemoveRequest();
            case REQ_CLIENT_SHUTDOWN -> sendClientShutdownRequest();
            case RES_CLIENT_DISCONNECTED -> clientDisconnected();
            case EXECUTE_KILL -> killClient();
            case EXECUTE_DELETE_AND_CLEANUP -> deleteServer();
        }
    }

    private void sendBungeeRemoveRequest() {
        if (ServerHandler.getInstance().getBungeeServers().isEmpty()) {
            LOG.debug("Skip bungeeremove. No bungeeservers found");
            nextState();
        } else {
            ServerHandler.getInstance().sendToBungeeCord(new CloudEvent(CloudEvent.BUNGEE_SERVER_REMOVE_REQUEST).addData(server.getServerId()));
            runTimer(200);
        }
    }


    private void sendClientShutdownRequest() {
        if (finishedStages.contains(ServerStoppingState.REQ_BUNGEECORD_REMOVE)) {
            ClientHandler.getInstance().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_SHUTDOWN_REQUEST));
            runTimer(1500);
        } else {
            finishedStages.remove(ServerStoppingState.REQ_CLIENT_SHUTDOWN);
            nextState();
        }
    }


    private void clientDisconnected() {
        LOG.info("Client disconnected {}", server.getServerId());
        finishedStages.add(ServerStoppingState.REQ_CLIENT_SHUTDOWN); // no need for another shutdown, if server disconnected unnormally
        server.serverStopped();
        runTimer(1500);
    }

    private void killClient() {
        if (server.kill())
            runTimer(150);
        else
            nextState();
    }

    private void deleteServer() {
        if (server.getLogFilePrefix() != null)
            server.saveLogFile();

        LOG.debug("Deleting server {}...", server.getServerId());
        ServerType serverType = server.getServerType();
        try {
            if (ServerUtils.needsServerBackup(serverType)) {
                server.moveToBackup(false);
            } else {
                FileUtils.deleteQuietly(new File(server.serverPath));
                LOG.debug("Server deleted: {}", server.serverPath);
            }
        } catch (IOException exception) {
            LOG.error("Error while deleting server {}: {}", server.getServerId(), exception.getMessage());
        }

        LOG.info("Server {} was deleted completely.", server.getServerId());
        removeServer();
    }

    protected void removeServer() {
        LOG.debug("Removing server {}...", server.getServerId());
        ServerHandler.getInstance().removeServer(server);
        ServerHandler.getInstance().actionWhenServerTypeShutdowns(server);

        ServerHandler.getInstance().removeStopQueue(server);

        if (server.getShutdownProcess() != null) {
            LOG.debug("Executing shutdown process for {}", server.getServerId());
            server.getShutdownProcess().serverStopped(); // Did this already in server manager
        }
    }

    private void cancelTimer() {
        if (timer == null) return;
        try {
            LOG.debug("Cancel timer for {}", server.getServerId());
            timer.cancel();
        } catch (IllegalStateException ignored) {}
    }

    private void runTimer(long delay) {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                LOG.debug("Scheduled timer finished for {}! Executing next states... Tasks done: {}", server.getServerId(), finishedStages);
                nextState();
            }
        }, delay);
    }

    public enum ServerStoppingState {
        REQ_BUNGEECORD_REMOVE, // send a bungee remove server req and wait for res
        REQ_CLIENT_SHUTDOWN, // send shutdown req to Client
        RES_CLIENT_DISCONNECTED, // call flush
        EXECUTE_KILL, // execute kill do nothing and then delete
        EXECUTE_DELETE_AND_CLEANUP // delete server
    }

}
