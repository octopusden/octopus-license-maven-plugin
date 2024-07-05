package org.codehaus.mojo.license.xray;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComponentInfo {
    private List<ComponentData> data;

    public List<ComponentData> getData() {
        return data == null ? Collections.emptyList() : data;
    }

    public void setData(List<ComponentData> data) {
        this.data = data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComponentData {
        @JsonProperty("component_id")
        private String componentId;

        @JsonProperty("component_name")
        private String componentName;

        @JsonProperty("package_id")
        private String packageId;

        @JsonProperty("package_type")
        private String packageType;

        private String version;

        private String licenses;

        public String getComponentId() {
            return componentId;
        }

        public void setComponentId(String componentId) {
            this.componentId = componentId;
        }

        public String getComponentName() {
            return componentName;
        }

        public void setComponentName(String componentName) {
            this.componentName = componentName;
        }

        public String getPackageId() {
            return packageId;
        }

        public void setPackageId(String packageId) {
            this.packageId = packageId;
        }

        public String getPackageType() {
            return packageType;
        }

        public void setPackageType(String packageType) {
            this.packageType = packageType;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getLicenses() {
            return licenses;
        }

        public void setLicenses(String licenses) {
            this.licenses = licenses;
        }
    }
}
