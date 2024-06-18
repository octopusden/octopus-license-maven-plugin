package org.codehaus.mojo.license.xray;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.model.License;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.mojo.license.model.LicenseMap;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class XrayLicenseProcessor {

    private static final String SUMMARY_ARTIFACT_URL = "https://artifactory/xray/api/v1/summary/artifact";

    private final Log log;
    private final String username;
    private final String password;
    private final Gson gson;

    public XrayLicenseProcessor(Log log, String username, String password) {
        this.log = log;
        this.username = username;
        this.password = password;

        this.gson = new Gson();
    }

    public List<License> getLicensesByProject(List<String> paths) {
        log.info("Executing load available scan results");

        try (CloseableHttpClient httpClient = createHttpClient()) {
            HttpPost request = createHttpPostRequest(paths);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                log.info("Response Status: " + response.getStatusLine());

                if (response.getEntity() != null) {
                    return parseLicenses(response.getEntity());
                } else {
                    log.info("Empty scan results");
                }
            } catch (IOException e) {
                log.error("Error executing HTTP request: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            log.error("Error creating HTTP client: " + e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    private CloseableHttpClient createHttpClient() {
        BasicCredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(
                org.apache.http.auth.AuthScope.ANY,
                new org.apache.http.auth.UsernamePasswordCredentials(username, password)
        );
        return HttpClients.custom()
                .setDefaultCredentialsProvider(provider)
                .build();
    }

    private HttpPost createHttpPostRequest(List<String> paths) {
        HttpPost request = new HttpPost(SUMMARY_ARTIFACT_URL);
        request.setEntity(createJsonEntity(paths));
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Authorization", createAuthHeader());

        return request;
    }

    private StringEntity createJsonEntity(List<String> paths) {
        JsonObject payload = new JsonObject();
        payload.add("paths", gson.toJsonTree(paths));
        return new StringEntity(gson.toJson(payload), ContentType.APPLICATION_JSON);
    }

    private String createAuthHeader() {
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes());
        return "Basic " + new String(encodedAuth);
    }

    private List<License> parseLicenses(HttpEntity responseEntity) throws IOException {
        String responseBody = EntityUtils.toString(responseEntity);

        Type responseType = new TypeToken<ResponseData>() {}.getType();
        ResponseData responseData = gson.fromJson(responseBody, responseType);

        return responseData.getArtifacts().stream()
                .flatMap(artifact -> artifact.getLicenses().stream())
                .filter(licenseData -> !Objects.equals(licenseData.getFullName(), LicenseMap.UNKNOWN_LICENSE_MESSAGE))
                .map(licenseData -> {
                    License license = new License();
                    license.setName(licenseData.getName());
                    if (licenseData.getMoreInfoUrl() != null && !licenseData.getMoreInfoUrl().isEmpty()) {
                        license.setUrl(licenseData.getMoreInfoUrl().get(0));
                    }
                    return license;
                })
                .collect(Collectors.toList());
    }

    static class ArtifactData {
        private List<LicenseData> licenses;

        public List<LicenseData> getLicenses() {
            return licenses;
        }

        public void setLicenses(List<LicenseData> licenses) {
            this.licenses = licenses;
        }
    }

    static class LicenseData {
        private String name;
        private String full_name;
        private List<String> more_info_url;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFullName() {
            return full_name;
        }

        public void setFullName(String full_name) {
            this.full_name = full_name;
        }

        public List<String> getMoreInfoUrl() {
            return more_info_url;
        }

        public void setMoreInfoUrl(List<String> more_info_url) {
            this.more_info_url = more_info_url;
        }
    }

    static class ResponseData {
        private List<ArtifactData> artifacts;

        public List<ArtifactData> getArtifacts() {
            return artifacts;
        }

        public void setArtifacts(List<ArtifactData> artifacts) {
            this.artifacts = artifacts;
        }
    }
}
