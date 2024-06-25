package org.codehaus.mojo.license.xray;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.maven.model.License;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.license.model.LicenseMap;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class XrayLicenseProcessor {

    private final String baseUrl;
    private final Log log;
    private final String accessToken;

    public XrayLicenseProcessor(Log log, String artifactRepositoryUrl, String artifactRepositoryAccessToken) {
        this.log = log;
        this.baseUrl = artifactRepositoryUrl + "/xray/api/v1";
        this.accessToken = artifactRepositoryAccessToken;
    }

    public List<License> getLicensesByProjectPaths(MavenProject project, List<String> paths) {
        try {
            String url = baseUrl + "/summary/artifact";
            log.info("Xray scan results for project: " + toString(project));

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> payload = new HashMap<>();
            payload.put("paths", paths);
            String jsonPayload = objectMapper.writeValueAsString(payload);
            StringEntity requestBody = new StringEntity(jsonPayload, ContentType.APPLICATION_JSON);

            Request request = Request.Post(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .body(requestBody);
            Response response = request.execute();
            HttpResponse httpResponse = response.returnResponse();

            int statusCode = httpResponse.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_OK) {
                String responseStr = EntityUtils.toString(httpResponse.getEntity());
                List<License> licenses = getLicenseFromJson(responseStr);

                if (licenses.isEmpty()) {
                    log.info("No license found in Xray for " + toString(project));
                } else {
                    log.info("Licenses found:" + toString(project));
                    for (License license: licenses) {
                        log.debug("\t" + license.getName() + " " + license.getUrl());
                    }
                }

                return licenses;
            } else {
                log.info("Unknown status code for " + toString(project) + " : " + statusCode);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return Collections.emptyList();
    }

    private List<License> getLicenseFromJson(String responseStr) throws IOException {
        ComponentInfo componentInfo = parseJSON(responseStr);

        return componentInfo.getArtifacts().stream()
                .flatMap(artifact -> artifact.getLicenses().stream())
                .filter(licenseData -> !Objects.equals(licenseData.getFullName(), LicenseMap.UNKNOWN_LICENSE_MESSAGE))
                .map(licenseData -> {
                    License license = new License();
                    license.setName(licenseData.getFullName());
                    if (licenseData.getMoreInfoUrl() != null && !licenseData.getMoreInfoUrl().isEmpty()) {
                        license.setUrl(licenseData.getMoreInfoUrl().get(0));
                    }
                    return license;
                })
                .collect(Collectors.toList());
    }

    private ComponentInfo parseJSON(String data) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(data, ComponentInfo.class);
    }

    private String toString(MavenProject project) {
        return project.getGroupId() + ":" + project.getArtifact();
    }
}
