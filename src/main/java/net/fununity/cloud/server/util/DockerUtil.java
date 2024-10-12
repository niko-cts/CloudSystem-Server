package net.fununity.cloud.server.util;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.time.Duration;

public class DockerUtil {

	private DockerUtil() {
		throw new IllegalStateException("Utility class");
	}

	public static DockerClient createDockerClient() {

		DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder();
		if (isLinux()) {
			builder.withDockerHost("unix:///var/run/docker.sock");
		} else {
			builder.withDockerHost("tcp://localhost:2375");
		}

		DockerClientConfig config = builder.build();

		ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient
				.Builder()
				.dockerHost(config.getDockerHost())
				.sslConfig(config.getSSLConfig())
				.maxConnections(100)
				.connectionTimeout(Duration.ofSeconds(30))
				.responseTimeout(Duration.ofSeconds(45)).build();

		return DockerClientImpl.getInstance(config, httpClient);
	}

	private static boolean isLinux() {
		return System.getProperty("os.name").toLowerCase().contains("nux");
	}
}
