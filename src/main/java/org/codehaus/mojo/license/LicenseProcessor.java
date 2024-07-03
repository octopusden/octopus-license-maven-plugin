package org.codehaus.mojo.license;

import org.apache.maven.model.License;
import org.apache.maven.project.MavenProject;

import java.util.List;

public interface LicenseProcessor {
    List<License> getLicensesByProject(MavenProject project);
}
