package net.fununity.cloud.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RepositoryConfig {

    @JsonProperty(value = "baseUrl", defaultValue = "null", required = true)
    private String baseUrl;

    @JsonProperty(value ="repositoryId", required = true)
    private String repositoryId;

    @JsonProperty(value ="groupId", required = true)
    private String groupId;

    @JsonProperty(value ="artifactId", required = true)
    private String artifactId;

    @JsonProperty(value ="version", required = true)
    private String version;

}
