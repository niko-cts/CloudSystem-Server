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
    public static String getTemplatePath(ServerType serverType) {
        StringBuilder path = new StringBuilder();
        path.append("./Servers/Templates/");
        switch (serverType) {
            case BUNGEECORD -> path.append("BungeeCord/");
            case LOBBY -> path.append("Lobby/");
            case CAVEHUNT -> path.append("CaveHunt/");
            case FLOWERWARS2x1 -> path.append("FlowerWars2x1/");
            case FLOWERWARS2x2 -> path.append("FlowerWars2x2/");
            case FLOWERWARS4x2 -> path.append("FlowerWars4x2/");
            case BEATINGPIRATES -> path.append("BeatingPirates/");
            case PAINTTHESHEEP -> path.append("PaintTheSheep/");
            case LANDSCAPES -> path.append("Landscapes/");
            case FREEBUILD -> path.append("FreeBuild/");
            case COCBASE -> path.append("ClashOfClubs-Base/");
            case COCATTACK -> path.append("ClashOfClubs-Attack/");
            case TTT -> path.append("TTT/");
            case SKYDILATION -> path.append("SkyDilation/");
            default -> path.append("undefined/");
        }
        return path.toString();
    }

    /**
     * Get the ram for a specified type.
     * @param serverType {@link ServerType} - The server type.
     * @return int - the ram in megabyte.
     * @since 0.0.1
     */
    public static int getRamFromType(ServerType serverType) {
        return switch (serverType) {
            case LOBBY -> 1536;
            case LANDSCAPES, FREEBUILD, COCBASE -> 4096;
            case BUNGEECORD -> 512;
            default -> 1024;
        };
    }

    /**
     * Gets the max amount of players of a server type.
     * @param serverType {@link ServerType} - the server type.
     * @return int - the maximum amount of players on the server.
     * @since 0.0.1
     */
    public static int getMaxPlayersOfServerType(ServerType serverType) {
        return switch (serverType) {
            case BUNGEECORD -> 120;
            case LANDSCAPES, FREEBUILD -> 50;
            case COCBASE -> 10;
            default -> 20;
        };
    }

    /**
     * Gets the max amount of players of a server type.
     * @param serverType {@link ServerType} - the server type.
     * @return int - the maximum amount of players on the server.
     * @since 0.0.1
     */
    public static int getDefaultPortForServerType(ServerType serverType) {
        return switch (serverType) {
            case BUNGEECORD -> 25565;
            case LOBBY -> 25566;
            case BEATINGPIRATES -> 26000;
            case PAINTTHESHEEP -> 26250;
            case CAVEHUNT -> 26500;
            case FLOWERWARS2x1 -> 26750;
            case FLOWERWARS2x2 -> 27000;
            case FLOWERWARS4x2 -> 27250;
            case FREEBUILD -> 27500;
            case TTT -> 27750;
            case SKYDILATION -> 28000;
            default -> 28250;
        };
    }

}
