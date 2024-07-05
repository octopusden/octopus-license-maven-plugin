package org.codehaus.mojo.license.xray;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.apache.maven.model.License;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.license.LicenseProcessor;
import org.codehaus.mojo.license.model.LicenseMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class XrayLicenseProcessor implements LicenseProcessor {

    private final String baseUrl;
    private final Log log;
    private final String accessToken;
    private static final String UNKNOWN_LICENSE_MESSAGE = "Unknown";

    public XrayLicenseProcessor(Log log, String artifactoryUrl, String artifactoryAccessToken) {
        this.log = log;
        this.baseUrl = artifactoryUrl;
        this.accessToken = artifactoryAccessToken;
    }

    List<License> getLicenseFromJson(String responseStr) throws IOException {
        ComponentInfo componentInfo = parseJSON(responseStr);

        if (componentInfo.getData().isEmpty()) {
            log.debug("\tCan't find any licenses");
        } else {
            log.debug("\tFound licenses:");
        }

        return componentInfo.getData().stream()
                .flatMap(componentData -> {
                    // Split the licenses string by comma and trim each resulting string
                    return Arrays.stream(componentData.getLicenses().split(","))
                            .map(String::trim)
                            .map(this::createLicense);
                })
                .collect(Collectors.toList());
    }

    ComponentInfo parseJSON(String data) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(data, ComponentInfo.class);
    }

    private String toString(MavenProject project) {
        return project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
    }

    private License createLicense(String licenseName) {
        License license = new License();

//        Change the Unknown license name to the default Unknown License Message
        if (licenseName.equals(UNKNOWN_LICENSE_MESSAGE)) {
            license.setName(LicenseMap.UNKNOWN_LICENSE_MESSAGE);
        } else {
            license.setName(licenseName);
        }

        log.debug("\t\t- " + license.getName());
        return license;
    }

    @Override
    public List<License> getLicensesByProject(MavenProject project) {
        try {
            String projectGAV = "gav://" + toString(project);

            // Note: This is not an official Xray API as documented.
            // Alternatively, consider using the official API:
            // https://jfrog.com/help/r/xray-rest-apis/find-component-by-name
            String url = baseUrl + "/ui/api/v1/xray/ui/scans_list/components?comp_id=" + projectGAV;

            log.info("Execute: " + url);

            Request request = Request.Get(url)
                    .addHeader("Authorization", "Bearer " + accessToken);
            Response response = request.execute();

            HttpResponse httpResponse = response.returnResponse();

            int statusCode = httpResponse.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_OK) {
                String responseStr = EntityUtils.toString(httpResponse.getEntity());
                return getLicenseFromJson(responseStr);
            } else {
                log.error("Unknown status code for " + toString(project) + " : " + statusCode);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return Collections.emptyList();
    }
}
