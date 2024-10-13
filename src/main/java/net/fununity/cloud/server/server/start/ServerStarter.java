package net.fununity.cloud.server.server.start;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.util.SystemConstants;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.config.PluginConfig;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.util.DockerUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is used to check for plugin updates and then start a docker container of a given template.
 *
 * @author Niko
 */
@Slf4j
public class ServerStarter {

    private static final int DEFAULT_PORT = 25565;

    private final Server server;
    private final String imageName;
    private final String containerName;
    private final int port;
    private final File templateDir;
    private final List<PluginConfig> plugins;
    private final boolean backup;

    private DockerClient dockerClient;

    public ServerStarter(Server server) {
        this.server = server;
        this.imageName = server.getConfig().getServerType().name();
        this.containerName = server.getServerId();
        this.port = server.getServerPort();
        this.templateDir = new File(server.getConfig().getDirectory()).getAbsoluteFile();
        this.backup = server.getConfig().isBackup();
        this.plugins = new ArrayList<>(CloudServer.getInstance().getConfigHandler().getByNames(server.getConfig().getPlugins()));

        Preconditions.checkArgument(templateDir.exists(), "Server template directory does not exists %s", templateDir.getAbsolutePath());
        Preconditions.checkArgument(templateDir.isDirectory(), "Server template is not a directory %s", templateDir.getAbsolutePath());
    }


    public CompletableFuture<Boolean> loadPluginsAndStartServerDockerized() {
        this.dockerClient = DockerUtil.createDockerClient();
        logDockerVersion();
        return updatePlugins().thenRun(this::checkPluginsAndPort)
                .thenRun(this::checkOrCreateNetwork)
                .thenRun(this::checkOrCreateImage)
                .thenApply(unused -> checkOrCreateContainer())
                .thenAccept(this::startContainer)
                .handle(this::onComplete);
    }

    private void logDockerVersion() {
        log.debug("Docker version: {}", dockerClient.versionCmd().exec().getVersion());
    }

    private boolean onComplete(Void ignored, Throwable throwable) {
        try {
            if (throwable != null) {
                log.error("Error while creating Server %s on port %s".formatted(containerName, port), throwable);
                try {
                    removeContainerOnFailedStart();
                } catch (Exception e) {
                    log.warn("Error while removing container on failed start", e);
                }
                CloudServer.getInstance().getServerManager().startFailed(server);
                return false;
            } else {
                log.info("Server started: {}", server);
                return true;
            }
        } finally {
            if (dockerClient != null) {
                try {
                    dockerClient.close();
                } catch (IOException e) {
                    log.warn("Could not close docker client", e);
                }
            }
        }
    }

    private void removeContainerOnFailedStart() {
        if (getContainerIdFromName().isPresent()) {
            log.info("Removing Docker container {} because server did not start correctly.", server.getServerId());
            dockerClient.removeContainerCmd(server.getServerId()).exec();
        }
    }

    private CompletableFuture<Void> updatePlugins() {
        if (CloudServer.getInstance().getNetworkConfig().map(cfg -> !cfg.isEnableRepositoryManager()).orElse(true)) {
            log.debug("Skipping plugin update checking, because repository manager is disabled.");
            return CompletableFuture.completedFuture(null);
        }

        List<PluginUpdater> jarUpdaters = plugins.stream()
                .filter(plugin -> plugin.getRepository() != null && !plugin.getRepository().getBaseUrl().equals("null"))
                .map(plugin -> new PluginUpdater(new File(plugin.getLocalPath()),
                        plugin.getRepository().getBaseUrl(),
                        plugin.getRepository().getRepositoryId(),
                        plugin.getRepository().getGroupId(),
                        plugin.getRepository().getArtifactId(),
                        plugin.getRepository().getVersion())).toList();
        CompletableFuture<?>[] futures = new CompletableFuture[jarUpdaters.size()];

        for (int i = 0; i < jarUpdaters.size(); i++) {
            PluginUpdater updater = jarUpdaters.get(i);

            futures[i] = CompletableFuture.runAsync(updater::checkAndUpdateJar).exceptionally(ex -> {
                log.warn("Error updating jar: {}", updater.pluginFile, ex);
                return null;
            });
        }

        return CompletableFuture.allOf(futures);
    }


    private void checkPluginsAndPort() {
        plugins.removeIf(plugin -> {
            if (new File(plugin.getLocalPath()).exists()) {
                return false;
            }
            log.warn("Plugin '{}' does not exist and cannot be mounted to server type {}! Removing from list and will start server without it.", plugin, imageName);
            return true;
        });

        if (plugins.isEmpty()) {
            throw new RuntimeException(String.format("No plugin loaded for server type %s! Needed at least CloudClient to work.", imageName));
        }

        if (this.port < DEFAULT_PORT)
            throw new RuntimeException("Given port for server %s is below default port 25565: %d".formatted(containerName, this.port));
    }


    private String checkOrCreateContainer() {
        Optional<String> containerId = getContainerIdFromName();
        if (containerId.isPresent()) {
            log.debug("Container {} already exists. Skipping creation.", containerName);
            return containerId.get();
        }


        ExposedPort exposedPort = ExposedPort.tcp(port);

        List<Bind> binds = plugins.stream().map(plugin ->
                new Bind(new File(plugin.getLocalPath()).getAbsolutePath(),
                        new Volume("/plugins/" + new File(plugin.getLocalPath()).getName()))).collect(Collectors.toList());

        binds.add(new Bind(templateDir.toPath() + "/../logs/" + imageName + "/" + containerName, new Volume("/logs/latest.txt")));
        if (backup) {
            Path backupPath = Path.of(templateDir.getAbsolutePath(), "..", "backups", imageName, server.getServerName());
            try (Stream<Path> paths = Files.walk(backupPath)) {
                binds.addAll(paths.filter(Files::isDirectory) // Only consider directories
                        .filter(path -> path.getFileName().toString().startsWith("world")) // Filter names starting with "world"
                        .map(path -> new Bind(path.toString(), new Volume("/" + path.getFileName()))).toList());
            } catch (IOException e) {
                throw new RuntimeException("Could not create backup directory for %s".formatted(templateDir), e);
            }
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withPortBindings(new PortBinding(Ports.Binding.bindPort(DEFAULT_PORT), exposedPort))
                .withNetworkMode(SystemConstants.NETWORK_NAME)
                .withBinds(binds);


        log.debug("Attempting to create container {} with image {} on network {} with binds {}", containerName, imageName, SystemConstants.NETWORK_NAME, binds);
        // Create the container with network configuration and container name in environment variables
        CreateContainerResponse container = dockerClient
                .createContainerCmd(imageName)
                .withName(containerName)
                .withHostConfig(hostConfig)
                .withExposedPorts(exposedPort)
                .withEnv(
                        "%s=%s".formatted(SystemConstants.ENV_SERVER_ID, containerName),
                        "%s=%s".formatted(SystemConstants.ENV_SERVER_NAME, server.getServerName()),
                        "%s=%s".formatted(SystemConstants.ENV_RAM, server.getConfig().getRam())
                ).exec();


        log.debug("Container {} created with ID {} on network {}.", containerName, container.getId(), SystemConstants.NETWORK_NAME);
        return container.getId();
    }


    private void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }


    private void checkOrCreateImage() {
        try {
            if (doesImageExist()) {
                log.debug("Image {} already exists. Skipping build.", imageName);
                return;
            }
            log.debug("Image not found. Building image {} from {}", imageName, templateDir.getPath());
            dockerClient
                    .buildImageCmd(templateDir)
                    .withTags(Set.of(imageName))
                    .exec(new BuildImageResultCallback()).awaitCompletion();
            log.debug("Image {} successfully built from {}", imageName, templateDir.getPath());
        } catch (Exception e) {
            throw new RuntimeException("Could not create docker image of " + imageName, e);
        }
    }


    private Optional<String> getContainerIdFromName() {
        log.debug("Check if container id {} exists", containerName);
        return dockerClient.listContainersCmd().withShowAll(true).exec().stream().filter(container -> container.getNames()[0].equals("/" + containerName)).map(Container::getId).findFirst();
    }

    private boolean doesImageExist() {
        log.debug("Check if image {} exists...", imageName);
        List<Image> images = dockerClient.listImagesCmd().exec();
        log.debug("Available images: {}", images);
        return images.stream()
                .anyMatch(image -> image.getRepoTags() != null &&
                        image.getRepoTags().length > 0 &&
                        image.getRepoTags()[0].equals(imageName));
    }


    private void checkOrCreateNetwork() {
        if (doesNetworkExist()) {
            log.debug("Network {} already exists. Skipping creation.", SystemConstants.NETWORK_NAME);
            return;
        }

        dockerClient.createNetworkCmd().withName(SystemConstants.NETWORK_NAME).exec();
        log.debug("Network {} created.", SystemConstants.NETWORK_NAME);
    }


    /**
     * Check if the Docker network exists
     */
    private boolean doesNetworkExist() {
        return dockerClient.listNetworksCmd().exec().stream().anyMatch(network -> network.getName().equals(SystemConstants.NETWORK_NAME));
    }
}
