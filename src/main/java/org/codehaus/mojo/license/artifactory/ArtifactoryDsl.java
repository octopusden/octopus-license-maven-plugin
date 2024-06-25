package org.codehaus.mojo.license.artifactory;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.jfrog.artifactory.client.model.RepoPath;

import java.util.Collections;
import java.util.List;

public class ArtifactoryDsl {
    private final Log log;
    private final Artifactory artifactory;

    public ArtifactoryDsl(Log log, String artifactRepositoryUrl, String artifactRepositoryAccessToken){
        this.log = log;

        this.artifactory = ArtifactoryClientBuilder.create()
                .setUrl(artifactRepositoryUrl + "/artifactory")
                .setAccessToken(artifactRepositoryAccessToken)
                .build();
    }

    public List<RepoPath> getProjectRepositoryPaths(MavenProject project) {
        try {
            log.debug("Get repository path for project: " + project.toString());

            List<RepoPath> repoPaths = artifactory.searches()
                    .artifactsByGavc()
                    .groupId(project.getGroupId())
                    .artifactId(project.getArtifactId())
                    .version(project.getVersion())
                    .classifier("*.jar")
                    .doSearch();

            if (!repoPaths.isEmpty()) {
                log.debug("Found artifact at:");
                for (RepoPath repoPath : repoPaths) {
                    log.debug("\t" + "default/" + repoPath.getRepoKey() + "/" + repoPath.getItemPath());
                }
            } else {
                log.debug("Can't find any artifact for project " + project.toString());
            }

            return repoPaths;
        } catch (Exception e) {
            log.error("Error occurred while searching for artifacts ", e);
            return Collections.emptyList();
        }
    }

}
