package net.fununity.cloud.server.server.start;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.VersionCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.util.EnvironmentVariables;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.config.PluginConfig;
import net.fununity.cloud.server.server.Server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

	private static final int INTERNAL_PORT = 25565;

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
		this.templateDir = new File(server.getConfig().getDirectory());
		this.backup = server.getConfig().isBackup();
		this.plugins = CloudServer.getInstance().getConfigHandler().getByNames(server.getConfig().getPlugins());

		Preconditions.checkArgument(templateDir.exists(), "Server template directory does not exists %s", templateDir.getAbsolutePath());
	}


	public void loadPluginsAndStartServerDockerized() {
		DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost("unix:///var/run/docker.sock")  // For Linux/macOS
				// .withDockerHost("tcp://localhost:2375")    // Uncomment for Windows Docker setup
				.build();
		ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).maxConnections(100).connectionTimeout(Duration.ofSeconds(30)).responseTimeout(Duration.ofSeconds(45)).build();

		this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
		VersionCmd versionCmd = dockerClient.versionCmd();
		log.debug("Docker version: {}", versionCmd.exec().getVersion());

		updatePlugins().thenRun(this::checkPluginsAndPort).thenRun(this::checkOrCreateNetwork).thenRun(this::checkOrCreateImage).thenApply(unused -> checkOrCreateContainer()).thenAccept(this::startContainer).whenComplete((ignored, throwable) -> {
			if (throwable != null) {
				log.error(String.format("Error while creating Server %s on port %s. Will not start, because:", containerName, port), throwable);
				server.flushServer();
			} else {
				log.info("Server started: {}", server);
			}
			try {
				if (dockerClient != null) this.dockerClient.close();
			} catch (IOException e) {
				log.error("Could not close docker client", e);
			}
		});
	}

	private CompletableFuture<Void> updatePlugins() {
		List<PluginUpdater> jarUpdaters = plugins.stream().map(plugin -> new PluginUpdater(new File(plugin.getLocalPath()), plugin.getNexusPluginUrl())).toList();
		CompletableFuture<?>[] futures = new CompletableFuture[jarUpdaters.size()];

		for (int i = 0; i < jarUpdaters.size(); i++) {
			PluginUpdater updater = jarUpdaters.get(i);

			futures[i] = CompletableFuture.runAsync(updater::checkAndUpdateJar).exceptionally(ex -> {
				log.error("Error updating jar: {}", updater.pluginFile, ex);
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
			throw new RuntimeException("No plugin loaded for server type " + imageName);
		}

		if (this.port < INTERNAL_PORT)
			throw new RuntimeException("Given port for server " + containerName + " is below default port 25565: " + this.port);
	}


	private String checkOrCreateContainer() {
		Optional<String> containerId = getContainerIdFromName();
		if (containerId.isPresent()) {
			log.debug("Container {} already exists. Skipping creation.", containerName);
			return containerId.get();
		}

		// Define the exposed port
		ExposedPort exposedPort = ExposedPort.tcp(port);

		// Bind files from host to the container
		List<Bind> binds = plugins.stream().map(plugin -> new Bind(new File(plugin.getLocalPath()).getAbsolutePath(), new Volume("/plugins/" + new File(plugin.getLocalPath()).getName()))).collect(Collectors.toList());

		binds.add(new Bind(templateDir.getPath() + "/../logs/" + imageName + "/" + containerName, new Volume("/logs/latest.txt")));
		if (backup) {
			Path backupPath = Path.of(templateDir.getAbsolutePath(), "..", "backups", imageName, server.getServerName());
			try (Stream<Path> paths = Files.walk(backupPath)) {
				binds.addAll(paths.filter(Files::isDirectory) // Only consider directories
						.filter(path -> path.getFileName().toString().startsWith("world")) // Filter names starting with "world"
						.map(path -> new Bind(path.toString(), new Volume("/" + path.getFileName()))).toList());
			} catch (IOException e) {
				log.error("Could not create backup directory for {}", templateDir, e);
			}
		}

		HostConfig hostConfig = HostConfig.newHostConfig()
				.withPortBindings(new PortBinding(Ports.Binding.bindPort(INTERNAL_PORT), exposedPort))
				.withNetworkMode(EnvironmentVariables.NETWORK_NAME)
				.withBinds(binds);


		// Create the container with network configuration and container name in environment variables
		CreateContainerResponse container = dockerClient
				.createContainerCmd(imageName)
				.withName(containerName)
				.withHostConfig(hostConfig)
				.withExposedPorts(exposedPort)
				.withEnv(String.format("%s=%s", EnvironmentVariables.CONTAINER_NAME, containerName))  // Set CONTAINER_NAME environment variable
				.exec();


		log.debug("Container {} created with ID {} on network {}.", containerName, container.getId(), EnvironmentVariables.NETWORK_NAME);
		return container.getId();

	}


	private void startContainer(String containerId) {
		dockerClient.startContainerCmd(containerId).exec();
	}


	private void checkOrCreateImage() {
		if (doesImageExist()) {
			log.debug("Image {} already exists. Skipping build.", imageName);
			return;
		}

		try {
			dockerClient.buildImageCmd(templateDir).withTags(Set.of(imageName)).start().awaitCompletion();

			log.debug("Image {} successfully built from {}", imageName, templateDir.getPath());
		} catch (InterruptedException e) {
			throw new RuntimeException("Could not create docker image of " + imageName, e);
		}
	}


	private Optional<String> getContainerIdFromName() {
		return dockerClient.listContainersCmd().withShowAll(true).exec().stream().filter(container -> container.getNames()[0].equals("/" + containerName)).map(Container::getId).findFirst();
	}


	private boolean doesImageExist() {
		return dockerClient.listImagesCmd().exec().stream().anyMatch(image -> image.getRepoTags() != null && image.getRepoTags()[0].equals(imageName));
	}


	private void checkOrCreateNetwork() {
		if (doesNetworkExist()) {
			log.debug("Network {} already exists.", EnvironmentVariables.NETWORK_NAME);
			return;
		}

		dockerClient.createNetworkCmd().withName(EnvironmentVariables.NETWORK_NAME).exec();
		log.debug("Network {} created.", EnvironmentVariables.NETWORK_NAME);
	}


	/**
	 * Check if the Docker network exists
	 */
	private boolean doesNetworkExist() {
		return dockerClient.listNetworksCmd().exec().stream().anyMatch(network -> network.getName().equals(EnvironmentVariables.NETWORK_NAME));
	}
}
