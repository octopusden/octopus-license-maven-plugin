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
import org.codehaus.mojo.license.ILicenseProcessor;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class XrayLicenseProcessor implements ILicenseProcessor {

    private final String baseUrl;
    private final Log log;
    private final String accessToken;

    public XrayLicenseProcessor(Log log, String artifactRepositoryUrl, String artifactRepositoryAccessToken) {
        this.log = log;
        this.baseUrl = artifactRepositoryUrl;
        this.accessToken = artifactRepositoryAccessToken;
    }

    List<License> getLicenseFromJson(String responseStr) throws IOException {
        ComponentInfo componentInfo = parseJSON(responseStr);

        if (componentInfo.getData().isEmpty()) {
            log.debug("\tCan't find any licenses");
        } else {
            log.debug("\tFound licenses:");
        }

        return componentInfo.getData().stream()
                .map(componentData -> {
                    License license = new License();

                    license.setName(componentData.getLicenses());
                    log.debug("\t\t- " + license.getName());

                    return license;
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

    @Override
    public List<License> getLicensesByProject(MavenProject project) {
        try {
            String projectGAV = "gav:%2F%2F" + toString(project);
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
                log.info("Unknown status code for " + toString(project) + " : " + statusCode);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return Collections.emptyList();
    }
}
