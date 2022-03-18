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
     * Creates the path for the template directories.
     * @return String - the path.
     * @since 0.0.1
     */
    public static String createTemplatePath(ServerType serverType) {
        StringBuilder path = new StringBuilder();
        path.append("./Servers/Templates/");
        switch(serverType) {
            case BUNGEECORD:
                path.append("BungeeCord/");
                break;
            case LOBBY:
                path.append("Lobby/");
                break;
            case MINIGAME:
                path.append("MiniGame/");
                break;
            case CAVEHUNT:
                path.append("CaveHunt/");
                break;
            case FLOWERWARS2x1:
                path.append("FlowerWars2x1/");
                break;
            case FLOWERWARS2x2:
                path.append("FlowerWars2x2/");
                break;
            case FLOWERWARS4x2:
                path.append("FlowerWars4x2/");
                break;
            case BEATINGPIRATES:
                path.append("BeatingPirates/");
                break;
            case PAINTTHESHEEP:
                path.append("PaintTheSheep/");
                break;
            case LANDSCAPES:
                path.append("Landscapes/");
                break;
            case FREEBUILD:
                path.append("FreeBuild/");
                break;
            case COCBASE:
                path.append("ClashOfClubs-Base/");
                break;
            case COCATTACK:
                path.append("ClashOfClubs-Attack/");
                break;
            case TTT:
                path.append("TTT/");
                break;
            default:
        }
        return path.toString();
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
            case TTT:
                return "TTT";
            default:
                return "";
        }
    }

    /**
     * Get the ram for a specified type.
     * @param serverType {@link ServerType} - The server type.
     * @return int - the ram in megabyte.
     * @since 0.0.1
     */
    public static int getRamFromType(ServerType serverType) {
        switch (serverType) {
            case LOBBY:
                return 1536;
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

    /**
     * Gets the max amount of players of a server type.
     * @param serverType {@link ServerType} - the server type.
     * @return int - the maximum amount of players on the server.
     * @since 0.0.1
     */
    public static int getDefaultPortForServerType(ServerType serverType) {
        switch(serverType) {
            case BUNGEECORD:
                return 25565;
            case LOBBY:
                return 25566;
            case BEATINGPIRATES:
                return 26000;
            case PAINTTHESHEEP:
                return 26500;
            case CAVEHUNT:
                return 27000;
            case FLOWERWARS2x1:
                return 27500;
            case FLOWERWARS2x2:
                return 27600;
            case FLOWERWARS4x2:
                return 27700;
            case FREEBUILD:
                return 28000;
            case TTT:
                return 28500;
            default:
                return 29000;
        }
    }

}
