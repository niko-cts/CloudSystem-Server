package net.fununity.cloud.server.misc;

import net.fununity.cloud.common.server.ServerType;

/**
 * This class holds utility methods for server creation.
 * @see ServerHandler
 * @author Niko
 * @since 0.0.1
 */
public class ServerUtils {

    private ServerUtils() {
        throw new UnsupportedOperationException("ServerUtils is a utility class.");
    }

    /**
     * Get the server id of the type.
     * @param serverType {@link ServerType} - the server type.
     * @return String - the server id.
     * @since 0.0.1
     */
    public static String getServerIdOfServerType(ServerType serverType) {
        switch(serverType) {
            case BUNGEECORD:
                return "BungeeCord";
            case LOBBY:
                return "Lobby";
            case CAVEHUNT:
                return "CH";
            case FLOWERWARS2x1:
                return "FWTxO";
            case FLOWERWARS2x2:
                return "FWTxT";
            case FLOWERWARS4x2:
                return "FWFxT";
            case BEATINGPIRATES:
                return "BP";
            case PAINTTHESHEEP:
                return "PTS";
            case LANDSCAPES:
                return "LandScapes";
            case FREEBUILD:
                return "FreeBuild";
            case COCBASE:
                return "CoCBase";
            case COCATTACK:
                return "CoCAttack";
        }
        return "";
    }

    /**
     * Get the ram for a specified type.
     * @param serverType {@link ServerType} - The server type.
     * @return int - the ram in megabyte.
     * @since 0.0.1
     */
    public static int getRamFromType(ServerType serverType) {
        switch (serverType) {
            case LANDSCAPES:
            case FREEBUILD:
            case COCBASE:
                return 4096;
            case FLOWERWARS2x1:
                return 256;
            default:
                return 512;
        }
    }

    /**
     * Gets the max amount of players of a server type.
     * @param serverType {@link ServerType} - the server type.
     * @return int - the maximum amount of players on the server.
     * @since 0.0.1
     */
    public static int getMaxPlayersOfServerType(ServerType serverType) {
        switch(serverType) {
            case BUNGEECORD:
                return 120;
            case LANDSCAPES:
            case FREEBUILD:
            case COCBASE:
                return 50;
            default:
                return 30;
        }
    }

}
