package net.fununity.cloud.server.server.start;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.util.EnvironmentVariables;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@RequiredArgsConstructor
public class PluginUpdater {

	protected final File pluginFile;
	private final String pluginDownloadURL;

	public void checkAndUpdateJar() {
		File checksumFile = new File(pluginFile.getAbsolutePath() + ".sha1");
		log.debug("Checking if {} is up to date on {}", pluginFile.getName(), pluginDownloadURL);
		try {
			byte[] remoteChecksum = getRemoteChecksum(pluginDownloadURL + ".sha1");

			if (!pluginFile.exists() || !checksumFile.exists()) {
				log.debug("Jar file not found or checksum missing. Downloading: {}", pluginFile);
				downloadJar(pluginFile);
				saveChecksum(checksumFile, remoteChecksum);
				return;
			}

			byte[] localChecksum = readLocalChecksum(checksumFile);
			if (localChecksum.length == 0 || !Arrays.equals(localChecksum, remoteChecksum)) {
				log.debug("Checksum mismatch. Updating jar: {}", pluginFile);
				downloadJar(pluginFile);
				saveChecksum(checksumFile, remoteChecksum);
				log.info("Updated jar {}", pluginFile.getName());
			} else {
				log.debug("Jar is up to date: {}", pluginFile.getName());
			}
		} catch (Exception e) {
			log.error("Error while checking or downloading jar: {}", pluginFile, e);
		}
	}

	private byte[] readLocalChecksum(@NotNull File checksumFile) {
		try {
			return Files.readAllBytes(checksumFile.toPath());
		} catch (IOException e) {
			log.error("Error reading local checksum: {}", checksumFile, e);
		}
		return new byte[0];
	}

	private byte[] getRemoteChecksum(@NotNull String checksumUrl) {
		try {
			HttpURLConnection connection = (HttpURLConnection) URI.create(checksumUrl)
					.toURL().openConnection();
			setConnectionAuthentication(connection);

			try (BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream())) {
				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
				byte[] buffer = new byte[256];  // Buffer size of 256B can be adjusted
				int bytesRead;

				while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) != -1) {
					byteArrayOutputStream.write(buffer, 0, bytesRead);
				}

				return byteArrayOutputStream.toByteArray();
			}
		} catch (IOException e) {
			log.error("Error fetching remote checksum: {}", checksumUrl, e);
		}
		return new byte[0];
	}

	private void downloadJar(@NotNull File jarFile) {
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) URI.create(pluginDownloadURL).toURL().openConnection();
			setConnectionAuthentication(connection);

			if (jarFile.mkdirs()) {
				log.debug("Created directories for jar file: {}", jarFile);
			}

			try {
				Files.copy(connection.getInputStream(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				log.debug("Copied jar file: {}", jarFile);
			} catch (IOException exception) {
				log.error("Error saving jar file: {}", jarFile, exception);
			}
		} catch (IOException e) {
			log.error("Error downloading jar file: {}", jarFile, e);
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private void saveChecksum(@NotNull File checksumFile, byte[] checksum) {
		try {
			Files.write(checksumFile.toPath(), checksum,
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING);
			log.debug("Checksum saved: {}", checksumFile);
		} catch (IOException e) {
			log.error("Error saving checksum: {}", checksumFile, e);
		}
	}

	private void setConnectionAuthentication(@NotNull HttpURLConnection connection) {
		String user = System.getenv(EnvironmentVariables.NEXUS_USER_NAME);
		String pw = System.getenv(EnvironmentVariables.NEXUS_USER_PASSWORD);
		if (user != null && pw != null) {
			String encodedAuth = Base64.getEncoder().encodeToString(String.format(
					"%s:%s", user, pw
			).getBytes(StandardCharsets.UTF_8));
			connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
		} else {
			log.warn("User and password variables are missing. Skipping connection authentication");
		}
	}

}

