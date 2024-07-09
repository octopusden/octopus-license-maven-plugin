package org.codehaus.mojo.license.api;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.license.model.LicenseMap;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.octopusden.releng.versions.NumericVersionFactory;
import org.octopusden.releng.versions.VersionNames;
import org.octopusden.releng.versions.VersionRangeFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class DefaultThirdPartyToolTest {

    private DefaultThirdPartyTool thirdPartyTool;
    private SortedMap<String, MavenProject> artifactCache;
    private final VersionNames versionNames = new VersionNames("", "", "");
    private final VersionRangeFactory versionRangeFactory = new VersionRangeFactory(versionNames);
    private final NumericVersionFactory numericVersionFactory = new NumericVersionFactory(versionNames);

    @Before
    public void setUp() throws InvalidVersionSpecificationException {
        thirdPartyTool = new DefaultThirdPartyTool();
        Logger logger = new ConsoleLogger(ConsoleLogger.LEVEL_INFO, "test");
        thirdPartyTool.enableLogging(logger);

        initializeArtifactCache();
    }

    @Test
    public void testExactVersionMatch() {
        String id = "group--artifact--1.0";

        List<MavenProject> projects = thirdPartyTool.getProjectFromCustomOverrideFile(id, artifactCache, versionRangeFactory, numericVersionFactory);

        Assert.assertEquals(1, projects.size());
        Assert.assertEquals(id, toString(projects.get(0)));
    }

    @Test
    public void testSingleVersionRangeWithExactMatch() {
        String id = "group--artifact--[1.0]";

        List<MavenProject> projects = thirdPartyTool.getProjectFromCustomOverrideFile(id, artifactCache, versionRangeFactory, numericVersionFactory);

        Assert.assertEquals(1, projects.size());
        Assert.assertEquals("group--artifact--1.0", toString(projects.get(0)));
    }

    @Test
    public void testVersionRangeInclusive() {
        String id = "group2--artifact2--[1-0,1-2]";

        List<MavenProject> projects = thirdPartyTool.getProjectFromCustomOverrideFile(id, artifactCache, versionRangeFactory, numericVersionFactory);

        Assert.assertEquals(1, projects.size());
        Assert.assertEquals("group2--artifact2--1.2", toString(projects.get(0)));
    }

    @Test
    public void testVersionRangeExclusiveEnd() {
        String id = "group3--artifact3--[1-3,1-5)";

        List<MavenProject> projects = thirdPartyTool.getProjectFromCustomOverrideFile(id, artifactCache, versionRangeFactory, numericVersionFactory);

        Assert.assertEquals(1, projects.size());
        Assert.assertEquals("group3--artifact3--1.3", toString(projects.get(0)));
    }

    @Test
    public void testVersionRangeExclusiveStartAndEnd() {
        String id = "group4--artifact4--(1-3,1-5)";

        List<MavenProject> projects = thirdPartyTool.getProjectFromCustomOverrideFile(id, artifactCache, versionRangeFactory, numericVersionFactory);

        Assert.assertEquals(1, projects.size());
        Assert.assertEquals("group4--artifact4--1.4", toString(projects.get(0)));
    }

    @Test
    public void testVersionRangeExclusiveStartInclusiveEnd() {
        String id = "group5--artifact5--(1-3,1-5]";

        List<MavenProject> projects = thirdPartyTool.getProjectFromCustomOverrideFile(id, artifactCache, versionRangeFactory, numericVersionFactory);

        Assert.assertEquals(1, projects.size());
        Assert.assertEquals("group5--artifact5--1.5", toString(projects.get(0)));
    }

    @Test
    public void testMultipleRangesMatch() {
        String id = "group5--artifact5--[1-0,1-2),(1-3,1-5]";

        List<MavenProject> projects = thirdPartyTool.getProjectFromCustomOverrideFile(id, artifactCache, versionRangeFactory, numericVersionFactory);

        Assert.assertEquals(1, projects.size());
        Assert.assertEquals("group5--artifact5--1.5", toString(projects.get(0)));
    }

    @Test
    public void testVersionRangeNoMatchingVersion() {
        String id = "group--artifact--[1-3,1-5]";

        List<MavenProject> projects = thirdPartyTool.getProjectFromCustomOverrideFile(id, artifactCache, versionRangeFactory, numericVersionFactory);

        Assert.assertEquals(0, projects.size());
    }

    @Test
    public void testMultipleRangesNoMatchingVersion() {
        String id = "group2--artifact2--[1-0,1-2),(1-3,1-5]";

        List<MavenProject> projects = thirdPartyTool.getProjectFromCustomOverrideFile(id, artifactCache, versionRangeFactory, numericVersionFactory);

        Assert.assertEquals(0, projects.size());
    }

    @Test
    public void testOverrideLicenses() throws URISyntaxException, IOException {
        URL resource = getClass().getClassLoader().getResource("license-version-range.properties");

        if (resource != null) {
            LicenseMap licenseMap = new LicenseMap();
            File propertiesFile = new File(resource.toURI());

            thirdPartyTool.overrideLicenses(licenseMap, artifactCache, "UTF-8", propertiesFile);

            Assert.assertEquals(5, licenseMap.size());
            Assert.assertEquals(1, licenseMap.get("License 1").size());
            Assert.assertTrue(licenseMap.get("License 1").contains(artifactCache.get("group--artifact--1.0")));
            Assert.assertEquals(1, licenseMap.get("License 2").size());
            Assert.assertTrue(licenseMap.get("License 2").contains(artifactCache.get("group2--artifact2--1.2")));
            Assert.assertEquals(1, licenseMap.get("License 3").size());
            Assert.assertTrue(licenseMap.get("License 3").contains(artifactCache.get("group3--artifact3--1.3")));
            Assert.assertEquals(1, licenseMap.get("License 4").size());
            Assert.assertTrue(licenseMap.get("License 4").contains(artifactCache.get("group4--artifact4--1.4")));
            Assert.assertEquals(1, licenseMap.get("License 5").size());
            Assert.assertTrue(licenseMap.get("License 5").contains(artifactCache.get("group5--artifact5--1.5")));
        }
    }

    private void initializeArtifactCache() throws InvalidVersionSpecificationException {
        String[] projectArtifacts = {"group--artifact--1.0", "group2--artifact2--1.2", "group3--artifact3--1.3", "group4--artifact4--1.4", "group5--artifact5--1.5"};

        artifactCache = new TreeMap<>();

        for (String artifact : projectArtifacts) {
            String[] parts = artifact.split("--");

            if (parts.length == 3) {
                String groupId = parts[0];
                String artifactId = parts[1];
                String version = parts[2];

                VersionRange versionRange = VersionRange.createFromVersionSpec(version);

                Artifact projectArtifact = new DefaultArtifact(
                        groupId,
                        artifactId,
                        versionRange,
                        Artifact.SCOPE_TEST,
                        "jar",
                        null,
                        new org.apache.maven.artifact.handler.DefaultArtifactHandler("jar")
                );

                MavenProject project = new MavenProject();
                project.setGroupId(groupId);
                project.setArtifactId(artifactId);
                project.setVersion(version);
                project.setArtifact(projectArtifact);

                artifactCache.put(artifact, project);
            }
        }
    }

    private String toString(MavenProject project) {
        return project.getGroupId() + "--" + project.getArtifactId() + "--" + project.getVersion();
    }

}
