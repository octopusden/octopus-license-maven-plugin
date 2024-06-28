package org.codehaus.mojo.license.artifactory;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.jfrog.artifactory.client.model.RepoPath;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ArtifactoryDsl {
    private final Log log;
    private final Artifactory artifactory;

    private static final List<String> EXCLUDED_CLASSIFIERS = Arrays.asList(
            "-sources", "-javadoc", "-tests", "-shaded", "-bundle", "-native"
    );

    public ArtifactoryDsl(Log log, String artifactRepositoryUrl, String artifactRepositoryAccessToken){
        this.log = log;

        this.artifactory = ArtifactoryClientBuilder.create()
                .setUrl(artifactRepositoryUrl + "/artifactory")
                .setAccessToken(artifactRepositoryAccessToken)
                .build();
    }

    public List<RepoPath> getProjectRepositoryPaths(MavenProject project) {
        try {
            log.debug("Execute repository path searches for project: " + project.toString());

            List<RepoPath> repoPaths = artifactory.searches()
                    .artifactsByGavc()
                    .groupId(project.getGroupId())
                    .artifactId(project.getArtifactId())
                    .version(project.getVersion())
                    .classifier("*.jar")
                    .doSearch();

            // Filter to get the main JAR file repo path
            List<RepoPath> mainRepoPaths = repoPaths.stream()
                    .filter(repoPath -> {
                        String itemPath = repoPath.getItemPath();
                        String fileName = itemPath.substring(itemPath.lastIndexOf('/') + 1);
                        return EXCLUDED_CLASSIFIERS.stream().noneMatch(fileName::contains);
                    })
                    .collect(Collectors.toList());

            if (!mainRepoPaths.isEmpty()) {
                log.debug("Found repo paths:");
                for (RepoPath repoPath : mainRepoPaths) {
                    log.debug("\t" + "default/" + repoPath.getRepoKey() + "/" + repoPath.getItemPath());
                }
            } else {
                log.debug("Can't find any artifact for project " + project.toString());
            }

            return mainRepoPaths;
        } catch (Exception e) {
            log.error("Error occurred while searching for artifacts ", e);
            return Collections.emptyList();
        }
    }

}
