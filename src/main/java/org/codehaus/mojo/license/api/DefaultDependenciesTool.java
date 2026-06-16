package org.codehaus.mojo.license.api;

/*
 * #%L
 * License Maven Plugin
 * %%
 * Copyright (C) 2011 CodeLutin, Codehaus, Tony Chemit
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.codehaus.mojo.license.model.Dependency;
import org.codehaus.mojo.license.utils.FileUtil;
import org.codehaus.mojo.license.utils.MojoHelper;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.logging.Logger;

/**
 * Default implementation of the {@link DependenciesTool}.
 *
 * @author tchemit dev@tchemit.fr
 * @version $Id$
 * @since 1.0
 */
@Component( role = DependenciesTool.class, hint = "default" )
public class DefaultDependenciesTool
    extends AbstractLogEnabled
    implements DependenciesTool
{

    public static final String INVALID_PATTERN_MESSAGE =
        "The pattern specified by expression <%s> seems to be invalid.";
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @Requirement
    private ProjectBuilder projectBuilder;

    @Requirement
    private ProjectDependenciesResolver dependenciesResolver;

    /** Provides MavenSession access from within a Plexus component. */
    @Requirement
    private LegacySupport legacySupport;

    /**
     * {@inheritDoc}
     */
    public SortedMap<String, MavenProject> loadProjectDependencies( MavenProject project,
                                                                    MavenProjectDependenciesConfigurator configuration,
                                                                    ArtifactRepository localRepository,
                                                                    List<ArtifactRepository> remoteRepositories,
                                                                    SortedMap<String, MavenProject> cache )
    {

        boolean haveNoIncludedGroups = StringUtils.isEmpty( configuration.getIncludedGroups() );
        boolean haveNoIncludedArtifacts = StringUtils.isEmpty( configuration.getIncludedArtifacts() );
        boolean excludeTransitiveDependencies = configuration.isExcludeTransitiveDependencies();

        boolean haveExcludedGroups = StringUtils.isNotEmpty( configuration.getExcludedGroups() );
        boolean haveExcludedArtifacts = StringUtils.isNotEmpty( configuration.getExcludedArtifacts() );
        boolean haveExclusions = haveExcludedGroups || haveExcludedArtifacts;

        Pattern includedGroupPattern = null;
        Pattern includedArtifactPattern = null;
        Pattern excludedGroupPattern = null;
        Pattern excludedArtifactPattern = null;

        if ( !haveNoIncludedGroups )
        {
            includedGroupPattern = Pattern.compile( configuration.getIncludedGroups() );
        }
        if ( !haveNoIncludedArtifacts )
        {
            includedArtifactPattern = Pattern.compile( configuration.getIncludedArtifacts() );
        }
        if ( haveExcludedGroups )
        {
            excludedGroupPattern = Pattern.compile( configuration.getExcludedGroups() );
        }
        if ( haveExcludedArtifacts )
        {
            excludedArtifactPattern = Pattern.compile( configuration.getExcludedArtifacts() );
        }

        Set<?> depArtifacts;

        if ( configuration.isIncludeTransitiveDependencies() )
        {
            depArtifacts = project.getArtifacts();
        }
        else
        {
            depArtifacts = project.getDependencyArtifacts();
        }

        List<String> includedScopes = configuration.getIncludedScopes();
        List<String> excludeScopes = configuration.getExcludedScopes();

        boolean verbose = configuration.isVerbose();

        SortedMap<String, MavenProject> result = new TreeMap<>();

        Map<String, Artifact> excludeArtifacts = new HashMap<>();
        Map<String, Artifact> includeArtifacts = new HashMap<>();

        SortedMap<String, MavenProject> localCache = new TreeMap<>();
        if (cache != null)
        {
            localCache.putAll(cache);
        }

        for ( Object o : depArtifacts )
        {
            Artifact artifact = (Artifact) o;

            excludeArtifacts.put(artifact.getId(), artifact);

            if ( DefaultThirdPartyTool.LICENSE_DB_TYPE.equals( artifact.getType() ) )
            {
                continue;
            }

            String scope = artifact.getScope();
            if ( CollectionUtils.isNotEmpty( includedScopes ) && !includedScopes.contains( scope ) )
            {
                continue;
            }

            if ( excludeScopes.contains( scope ) )
            {
                continue;
            }

            Logger log = getLogger();

            String id = MojoHelper.getArtifactId( artifact );

            if ( verbose )
            {
                log.info( "detected artifact " + id );
            }

            boolean isToInclude = haveNoIncludedArtifacts && haveNoIncludedGroups ||
                isIncludable( artifact, includedGroupPattern, includedArtifactPattern );

            boolean isToExclude = isToInclude && haveExclusions &&
                isExcludable( artifact, excludedGroupPattern, excludedArtifactPattern );

            if ( !isToInclude || isToExclude )
            {
                if ( verbose )
                {
                    log.info( "skip artifact " + id );
                }
                continue;
            }

            MavenProject depMavenProject = localCache.get( id );

            if ( depMavenProject != null )
            {
                if ( verbose )
                {
                    log.info( "add dependency [" + id + "] (from cache)" );
                }
            }
            else
            {
                try
                {
                    MavenSession session = legacySupport.getSession();
                    ProjectBuildingRequest request =
                        new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );
                    request.setRemoteRepositories( remoteRepositories );
                    request.setResolveDependencies( false );
                    depMavenProject = projectBuilder.build( artifact, request ).getProject();
                    depMavenProject.getArtifact().setScope( artifact.getScope() );
                }
                catch ( ProjectBuildingException e )
                {
                    log.warn( "Unable to obtain POM for artifact : " + artifact, e );
                    continue;
                }

                if ( verbose )
                {
                    log.info( "add dependency [" + id + "]" );
                }

                localCache.put(id, depMavenProject);
            }

            result.put(id, depMavenProject);

            excludeArtifacts.remove(artifact.getId());
            includeArtifacts.put(artifact.getId(), artifact);
        }

        if (excludeTransitiveDependencies) {
            for (Map.Entry<String, Artifact> entry : includeArtifacts.entrySet()) {
                List<String> dependencyTrail = entry.getValue().getDependencyTrail();
                boolean remove = false;
                for (int i = 1; i < dependencyTrail.size() - 1; i++) {
                    if (excludeArtifacts.containsKey(dependencyTrail.get(i))) {
                        remove = true;
                        break;
                    }
                }
                if (remove) {
                    result.remove(MojoHelper.getArtifactId(entry.getValue()));
                }
            }
        }

        if (cache != null) {
            cache.putAll(result);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    public void loadProjectArtifacts( ArtifactRepository localRepository, List remoteRepositories,
                                      MavenProject project,
                                      Map<String, List<org.apache.maven.model.Dependency>> reactorProjectDependencies )
        throws DependenciesToolException
    {
        if ( CollectionUtils.isEmpty( project.getDependencyArtifacts() ) )
        {
            MavenSession session = legacySupport.getSession();
            DefaultDependencyResolutionRequest request =
                new DefaultDependencyResolutionRequest( project, session.getRepositorySession() );
            try
            {
                DependencyResolutionResult resolved = dependenciesResolver.resolve( request );
                Set<Artifact> artifacts = resolved.getResolvedDependencies().stream()
                    .map( dep -> RepositoryUtils.toArtifact( dep.getArtifact() ) )
                    .collect( Collectors.toSet() );
                project.setDependencyArtifacts( artifacts );
                project.setArtifacts( artifacts );
            }
            catch ( DependencyResolutionException e )
            {
                throw new DependenciesToolException( e.getCause() );
            }
        }
    }

    @Override
    public void writeThirdPartyDependenciesFile( File outputDirectory, String listedDependenciesFilePath,
                                                 Set<Dependency> listedDependencies ) throws IOException
    {
        final File thirdPartyDepsFile = FileUtil.getFile(outputDirectory, listedDependenciesFilePath);

        if (listedDependencies.isEmpty()) {
            getLogger().warn("There is no dependencies for write to " + thirdPartyDepsFile);
            return;
        }

        getLogger().info( "Writing third-party dependencies file to " + thirdPartyDepsFile );
        MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(thirdPartyDepsFile, listedDependencies);
    }

    protected boolean isIncludable( Artifact project, Pattern includedGroupPattern, Pattern includedArtifactPattern )
    {
        Logger log = getLogger();

        if ( includedGroupPattern != null )
        {
            try
            {
                Matcher matchGroupId = includedGroupPattern.matcher( project.getGroupId() );
                if ( matchGroupId.find() )
                {
                    if ( log.isDebugEnabled() )
                    {
                        log.debug( "Include " + project.getGroupId() );
                    }
                    return true;
                }
            }
            catch ( PatternSyntaxException e )
            {
                log.warn( String.format( INVALID_PATTERN_MESSAGE, includedGroupPattern.pattern() ) );
            }
        }

        if ( includedArtifactPattern != null )
        {
            try
            {
                Matcher matchGroupId = includedArtifactPattern.matcher( project.getArtifactId() );
                if ( matchGroupId.find() )
                {
                    if ( log.isDebugEnabled() )
                    {
                        log.debug( "Include " + project.getArtifactId() );
                    }
                    return true;
                }
            }
            catch ( PatternSyntaxException e )
            {
                log.warn( String.format( INVALID_PATTERN_MESSAGE, includedArtifactPattern.pattern() ) );
            }
        }
        return false;
    }

    protected boolean isExcludable( Artifact project, Pattern excludedGroupPattern, Pattern excludedArtifactPattern )
    {
        Logger log = getLogger();

        if ( excludedGroupPattern != null )
        {
            try
            {
                Matcher matchGroupId = excludedGroupPattern.matcher( project.getGroupId() );
                if ( matchGroupId.find() )
                {
                    if ( log.isDebugEnabled() )
                    {
                        log.debug( "Exclude " + project.getGroupId() );
                    }
                    return true;
                }
            }
            catch ( PatternSyntaxException e )
            {
                log.warn( String.format( INVALID_PATTERN_MESSAGE, excludedGroupPattern.pattern() ) );
            }
        }

        if ( excludedArtifactPattern != null )
        {
            try
            {
                Matcher matchGroupId = excludedArtifactPattern.matcher( project.getArtifactId() );
                if ( matchGroupId.find() )
                {
                    if ( log.isDebugEnabled() )
                    {
                        log.debug( "Exclude " + project.getArtifactId() );
                    }
                    return true;
                }
            }
            catch ( PatternSyntaxException e )
            {
                log.warn( String.format( INVALID_PATTERN_MESSAGE, excludedArtifactPattern.pattern() ) );
            }
        }
        return false;
    }
}
