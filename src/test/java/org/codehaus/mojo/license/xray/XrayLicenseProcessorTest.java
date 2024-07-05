package org.codehaus.mojo.license.xray;

import org.apache.maven.model.License;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class XrayLicenseProcessorTest {

    private final Log log = new SystemStreamLog();
    private String artifactUrl;
    private String accessToken;
    private XrayLicenseProcessor licenseProcessor;

    @Before
    public void setUp() {
        artifactUrl = System.getProperty("artifactoryUrl");
        accessToken = System.getProperty("artifactoryAccessToken");
        licenseProcessor = new XrayLicenseProcessor(log, artifactUrl, accessToken);
    }

    @Test
    public void testParseJSON() throws IOException {
        String data = loadToString("xrayLicenseInfo.json");
        ComponentInfo componentInfo = licenseProcessor.parseJSON(data);

        Assert.assertNotNull(componentInfo);
        Assert.assertEquals(2, componentInfo.getData().size());

        ComponentInfo.ComponentData data1 = componentInfo.getData().get(0);
        ComponentInfo.ComponentData data2 = componentInfo.getData().get(1);

        Assert.assertEquals("component id 1", data1.getComponentId());
        Assert.assertEquals("component name 1", data1.getComponentName());
        Assert.assertEquals("package id 1", data1.getPackageId());
        Assert.assertEquals("v1", data1.getVersion());
        Assert.assertEquals("license 1", data1.getLicenses());

        Assert.assertEquals("component id 2", data2.getComponentId());
        Assert.assertEquals("component name 2", data2.getComponentName());
        Assert.assertEquals("package id 2", data2.getPackageId());
        Assert.assertEquals("v2", data2.getVersion());
        Assert.assertEquals("license 2", data2.getLicenses());
    }

    @Test
    public void testGetLicenseFromJson() throws IOException {
        String data = loadToString("xrayLicenseInfo.json");

        List<License> licenses = licenseProcessor.getLicenseFromJson(data);

        Assert.assertEquals(2, licenses.size());
        Assert.assertEquals("license 1", licenses.get(0).getName());
        Assert.assertEquals("license 2", licenses.get(1).getName());
    }

    @Test
    public void testGetLicensesByProject() {
        if (artifactUrl == null || accessToken == null) return;

        MavenProject mavenProject = new MavenProject();
        mavenProject.setGroupId("com.fasterxml.jackson.core");
        mavenProject.setArtifactId("jackson-core");
        mavenProject.setVersion("2.8.6");

        List<License> licenses = licenseProcessor.getLicensesByProject(mavenProject);

        Assert.assertNotNull(licenses);
        Assert.assertEquals(1, licenses.size());
        Assert.assertEquals("Apache-2.0", licenses.get(0).getName());
    }


    private String loadToString(String fileName) throws IOException {
        InputStream resourceAsStream = XrayLicenseProcessorTest.class.getResourceAsStream("/" + fileName);
        return IOUtil.toString(resourceAsStream);
    }

}
