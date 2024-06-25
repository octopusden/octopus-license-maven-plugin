package org.codehaus.mojo.license.xray;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComponentInfo {
    private List<ArtifactData> artifacts;

    public List<ArtifactData> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<ArtifactData> artifacts) {
        this.artifacts = artifacts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ArtifactData {
        private List<LicenseData> licenses;
        private GeneralData general;

        public List<LicenseData> getLicenses() {
            return licenses;
        }
        public GeneralData getGeneral() {
            return general;
        }

        public void setLicenses(List<LicenseData> licenses) {
            this.licenses = licenses;
        }
        public void setGeneral(GeneralData general) {
            this.general = general;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeneralData {
        private String path;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LicenseData {
        private String name;
        @JsonProperty("full_name")
        private String fullName;
        @JsonProperty("more_info_url")
        private List<String> moreInfoUrl;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public List<String> getMoreInfoUrl() {
            return moreInfoUrl;
        }

        public void setMoreInfoUrl(List<String> moreInfoUrl) {
            this.moreInfoUrl = moreInfoUrl;
        }
    }
}
