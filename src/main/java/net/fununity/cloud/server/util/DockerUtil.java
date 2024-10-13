package net.fununity.cloud.server.util;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;

import java.time.Duration;

@Slf4j
public class DockerUtil {

    private DockerUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static DockerClient createDockerClient() {
        DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        if (SystemUtils.IS_OS_WINDOWS) {
            builder.withDockerHost("tcp://localhost:2375");
            log.debug("Detected windows OS. Using TCP for Docker host on localhost.");
        } else {
            log.debug("Detected non-windows OS. Using Unix socket for Docker host.");
            builder.withDockerHost("unix:///var/run/docker.sock");
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
