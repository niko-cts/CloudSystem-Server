package net.fununity.cloud.server.server.start;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.util.SystemConstants;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Base64;

import static java.lang.String.format;

@Slf4j
@RequiredArgsConstructor
public class PluginUpdater {

    protected final File pluginFile;
    private final String baseUrl;
    private final String repoId;
    private final String groupId;
    private final String artifact;
    private final String version;

    private String pluginDownloadURL;

    public void checkAndUpdateJar() {
        File checksumFile = new File(pluginFile.getAbsolutePath() + ".sha1");
        try {
            log.debug("Checking if {} is up to date on {}", pluginFile.getName(), baseUrl);
            pluginDownloadURL = buildDownloadUrl();
            log.debug("Plugin {} download url is {}", pluginFile.getName(), pluginDownloadURL);
            byte[] remoteChecksum = getRemoteChecksum(pluginDownloadURL + ".sha1");

            if (!pluginFile.exists() || !checksumFile.exists()) {
                log.debug("Jar file not found or checksum missing. Downloading: {}", pluginFile);
                downloadJar(pluginFile);
                saveChecksum(checksumFile, remoteChecksum);
                log.info("Downloaded jar {}", pluginFile.getName());
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

    private String buildDownloadUrl() {
        if (version.toLowerCase().contains("snapshot")) {
            String latestSnapshotVersion = getLatestSnapshotVersion(this.baseUrl, this.repoId, this.groupId, this.artifact, this.version);
            log.debug("Latest sub-version of {}-{} is {}", artifact, version, latestSnapshotVersion);
            return format("%s/repository/%s/%s/%s/%s/%s-%s.jar",
                    this.baseUrl, this.repoId, this.groupId.replace('.', '/'),
                    this.artifact, this.version, this.artifact, latestSnapshotVersion);
        } else {
            return format("%s/repository/%s/%s/%s/%s/%s-%s.jar",
                    this.baseUrl, this.repoId, this.groupId.replace('.', '/'),
                    this.artifact, this.version, this.artifact, this.version);
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
            log.debug("Getting checksum for {} from: {}", pluginFile.getName(), checksumUrl);
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
            throw new RuntimeException("Error fetching remote checksum: " + checksumUrl, e);
        }
    }

    private void downloadJar(@NotNull File jarFile) {
        HttpURLConnection connection = null;

        try {
            log.debug("Downloading plugin from url: {}", pluginDownloadURL);
            connection = (HttpURLConnection) URI.create(pluginDownloadURL).toURL().openConnection();
            setConnectionAuthentication(connection);


            if (jarFile.mkdirs()) {
                log.debug("Created directories for jar file: {}", jarFile);
            }

            boolean exists = jarFile.exists();
            try {
                Files.copy(connection.getInputStream(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.debug("Copied jar file: {}", jarFile);
            } catch (IOException exception) {
                if (!exists && jarFile.delete()) {
                    log.debug("Deleting jar file because could not copy...");
                }
                throw new RuntimeException("Error saving jar file " + jarFile, exception);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error downloading jar file " + jarFile, e);
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
        String user = System.getProperty(SystemConstants.PROP_NEXUS_USER);
        String pw = System.getProperty(SystemConstants.PROP_NEXUS_PASSWORD);
        if (user != null && pw != null) {
            connection.setRequestProperty("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(format(
                            "%s:%s", user, pw
                    ).getBytes(StandardCharsets.UTF_8)));
        } else {
            log.warn("User and password properties are missing. Skipping connection authentication");
        }
    }

    private String getLatestSnapshotVersion(String baseUrl, String repoId, String groupId, String artifactId, String version) {
        String metadataUrl = format("%s/repository/%s/%s/%s/%s/maven-metadata.xml",
                baseUrl, repoId, groupId.replace(".", "/"), artifactId, version);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(metadataUrl).toURL().openConnection();
            setConnectionAuthentication(connection);

            InputStream stream = connection.getInputStream();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(stream);

            NodeList versioning = doc.getElementsByTagName("versioning");
            if (versioning.getLength() == 0) {
                throw new RuntimeException("Invalid maven-metadata.xml");
            }
            NodeList snapshotVersions = doc.getElementsByTagName("snapshotVersion");

            for (int i = 0; i < snapshotVersions.getLength(); i++) {
                Element versionElement = (Element) snapshotVersions.item(i);
                String extension = versionElement.getElementsByTagName("extension").item(0).getTextContent();
                if ("jar".equals(extension)) {
                    return versionElement.getElementsByTagName("value").item(0).getTextContent();
                }
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new RuntimeException(format("Could not get latest version of plugin %s. Artifact %s Version %s", pluginFile.getName(), artifactId, version), e);
        } finally {
            if (connection != null)
                connection.disconnect();
        }

        throw new RuntimeException("Snapshot version not found");
    }

}

