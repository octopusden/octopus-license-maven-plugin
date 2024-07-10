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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class DefaultThirdPartyToolTest {

    private DefaultThirdPartyTool thirdPartyTool;
    private SortedMap<String, MavenProject> artifactCacheScalarVersionFormat;
    private String[] projectArtifactsScalarVersionFormat;
    private SortedMap<String, MavenProject> artifactCacheMvnVersionRangeFormat;
    private String[] projectArtifactsMvnVersionRangeFormat;

    @Before
    public void setUp() throws InvalidVersionSpecificationException {
        thirdPartyTool = new DefaultThirdPartyTool();
        Logger logger = new ConsoleLogger(ConsoleLogger.LEVEL_INFO, "test");
        thirdPartyTool.enableLogging(logger);

        initializeArtifactCache();
    }

    @Test
    public void testOverrideLicensesWithScalarVersionFormat() throws URISyntaxException, IOException {
        URL resource = getClass().getClassLoader().getResource("license-scalar-version-format.properties");

        if (resource != null) {
            LicenseMap licenseMap = new LicenseMap();
            File propertiesFile = new File(resource.toURI());

            thirdPartyTool.overrideLicenses(licenseMap, artifactCacheScalarVersionFormat, "UTF-8", propertiesFile);

            Assert.assertEquals(3, licenseMap.size());
            Assert.assertEquals(1, licenseMap.get("License 1").size());
            Assert.assertTrue(licenseMap.get("License 1").contains(artifactCacheScalarVersionFormat.get("group1--artifact1--1.0")));
            Assert.assertEquals(1, licenseMap.get("License 2").size());
            Assert.assertTrue(licenseMap.get("License 2").contains(artifactCacheScalarVersionFormat.get("group2--artifact2--2.0.RELEASE")));
            Assert.assertEquals(1, licenseMap.get("License 3").size());
            Assert.assertTrue(licenseMap.get("License 3").contains(artifactCacheScalarVersionFormat.get("group3--artifact3--3.2.1")));
        }
    }

    @Test
    public void testOverrideLicensesWithMvnVersionRangeFormat() throws URISyntaxException, IOException {
        URL resource = getClass().getClassLoader().getResource("license-mvn-version-range-format.properties");

        if (resource != null) {
            LicenseMap licenseMap = new LicenseMap();
            File propertiesFile = new File(resource.toURI());

            thirdPartyTool.overrideLicenses(licenseMap, artifactCacheMvnVersionRangeFormat, "UTF-8", propertiesFile);

            Assert.assertEquals(4, licenseMap.size());

            Assert.assertEquals(11, licenseMap.get("License 1").size());
            SortedSet<MavenProject> projectsLicense1 = licenseMap.get("License 1");
            for (int i = 0; i < 11; i++) {
                Assert.assertTrue(projectsLicense1.contains(artifactCacheMvnVersionRangeFormat.get(projectArtifactsMvnVersionRangeFormat[i])));
            }

            Assert.assertEquals(4, licenseMap.get("License 2").size());
            SortedSet<MavenProject> projectsLicense2 = licenseMap.get("License 2");
            for (int i = 11; i < 15; i++) {
                Assert.assertTrue(projectsLicense2.contains(artifactCacheMvnVersionRangeFormat.get(projectArtifactsMvnVersionRangeFormat[i])));
            }

            Assert.assertEquals(7, licenseMap.get("License 3").size());
            SortedSet<MavenProject> projectsLicense3 = licenseMap.get("License 3");
            for (int i = 15; i < 22; i++) {
                Assert.assertTrue(projectsLicense3.contains(artifactCacheMvnVersionRangeFormat.get(projectArtifactsMvnVersionRangeFormat[i])));
            }

            Assert.assertEquals(8, licenseMap.get("License 4").size());
            SortedSet<MavenProject> projectsLicense4 = licenseMap.get("License 4");
            for (int i = 22; i < 30; i++) {
                Assert.assertTrue(projectsLicense4.contains(artifactCacheMvnVersionRangeFormat.get(projectArtifactsMvnVersionRangeFormat[i])));
            }
        }
    }

    private void initializeArtifactCache() throws InvalidVersionSpecificationException {
        projectArtifactsScalarVersionFormat = new String[]{
                "group1--artifact1--1.0",
                "group2--artifact2--2.0.RELEASE",
                "group3--artifact3--3.2.1",

                // Versions not covered by the licenses properties file will not be included in the license map
                "group1--artifact1--3.0"
        };
        artifactCacheScalarVersionFormat = new TreeMap<>();
        addProjectToArtifact(artifactCacheScalarVersionFormat, projectArtifactsScalarVersionFormat);

        projectArtifactsMvnVersionRangeFormat = new String[]{
                "group1--artifact1--8.1",
                "group2--artifact2--7.7.1-966",
                "group3--artifact3--2.9",
                "group4--artifact4--7.7.1-965",
                "group5--artifact5--1.5",
                "group6--artifact6--1.5",
                "group7--artifact7--4.84.5",
                "group8--artifact8--0.6.160",
                "group9--artifact9--4.84",
                "group10--artifact10--1.40",
                "group11--artifact11--2.4.46.84",
                "group12--artifact12--3.5.0",
                "group13--artifact13--3.0.22-0000",
                "group14--artifact14--0.7.100",
                "group15--artifact15--2.4.20",
                "group16--artifact16--5.5",
                "group17--artifact17--8.1",
                "group18--artifact18--1.2.1",
                "group19--artifact19--2.0",
                "group20--artifact20--38.50",
                "group21--artifact21--1.1.5",
                "group22--artifact22--2.4.53-250",
                "group23--artifact23--1.0.13",
                "group24--artifact24--3.44.29",
                "group25--artifact25--1.2.336",
                "group26--artifact26--1.0.35-0009",
                "group27--artifact27--2.250",
                "group28--artifact28--2.313",
                "group29--artifact29--52.0.1-5",
                "group30--artifact30--1.2.200",

                // Versions not covered by the licenses properties file will not be included in the license map
                "group1--artifact1--8",
                "group13--artifact13--3.0.21",
                "group21--artifact21--1.2.0",
                "group29--artifact29--52.0.1.6",

        };
        artifactCacheMvnVersionRangeFormat = new TreeMap<>();
        addProjectToArtifact(artifactCacheMvnVersionRangeFormat, projectArtifactsMvnVersionRangeFormat);
    }

    private void addProjectToArtifact(SortedMap<String, MavenProject> cache, String[] projectArtifacts) throws InvalidVersionSpecificationException {
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

                cache.put(artifact, project);
            }
        }
    }
}
