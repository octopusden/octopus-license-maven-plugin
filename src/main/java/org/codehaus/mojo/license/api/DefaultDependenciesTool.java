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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

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
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.codehaus.mojo.license.model.Dependency;
import org.codehaus.mojo.license.utils.FileUtil;
import org.codehaus.mojo.license.utils.MojoHelper;

/**
 * Default implementation of the {@link DependenciesTool}.
 *
 * @author tchemit dev@tchemit.fr
 * @version $Id$
 * @since 1.0
 */
@org.codehaus.plexus.component.annotations.Component( role = DependenciesTool.class, hint = "default" )
@Named( "default" )
@Singleton
public class DefaultDependenciesTool
    implements DependenciesTool
{
    private static final Logger log = LoggerFactory.getLogger( DefaultDependenciesTool.class );

    /**
     * Message used when an invalid expression pattern is found.
     */
    public static final String INVALID_PATTERN_MESSAGE =
        "The pattern specified by expression <%s> seems to be invalid.";
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    private ProjectBuilder projectBuilder;

    @Inject
    private ProjectDependenciesResolver dependenciesResolver;

    /** Provides MavenSession access from within a Plexus component. */
    @Inject
    private LegacySupport legacySupport;

    @Inject
    private RepositorySystem repositorySystem;

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
            // All project dependencies
            depArtifacts = project.getArtifacts();
        }
        else
        {
            // Only direct project dependencies
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
            synchronized ( cache )
            {
                localCache.putAll(cache);
            }
        }

        MavenSession session = legacySupport.getSession();
        Set<String> reactorGavs = getReactorGavs();

        for ( Object o : depArtifacts )
        {
            Artifact artifact = (Artifact) o;

            excludeArtifacts.put(artifact.getId(), artifact);

            if ( DefaultThirdPartyTool.LICENSE_DB_TYPE.equals( artifact.getType() ) )
            {
                // the special dependencies for license databases don't count.
                // Note that this will still see transitive deps of a license db; so using the build helper inside of another project
                // to make them will be noisy.
                continue;
            }

            String scope = artifact.getScope();
            if ( CollectionUtils.isNotEmpty( includedScopes ) && !includedScopes.contains( scope ) )
            {
                // not in included scopes
                continue;
            }

            if ( excludeScopes.contains( scope ) )
            {
                // in excluded scopes
                continue;
            }

            String id = MojoHelper.getArtifactId( artifact );

            if ( reactorGavs.contains( gavOf( artifact ) ) )
            {
                // Reactor modules belong to the current build, not to third-party dependencies.
                if ( verbose )
                {
                    log.info( "skip reactor artifact " + id );
                }
                continue;
            }

            if ( verbose )
            {
                log.info( "detected artifact " + id );
            }

            // Check if the project should be included
            // If there is no specified artifacts and group to include, include all
            boolean isToInclude = haveNoIncludedArtifacts && haveNoIncludedGroups ||
                isIncludable( artifact, includedGroupPattern, includedArtifactPattern );

            // Check if the project should be excluded
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

            MavenProject depMavenProject;

            // try to get project from cache
            depMavenProject = localCache.get( id );

            if ( depMavenProject != null )
            {
                if ( verbose )
                {
                    log.info( "add dependency [" + id + "] (from cache)" );
                }
            }
            else
            {
                // Build a fresh request instead of reusing the session request: the latter carries
                // the current reactor project, so ProjectBuilder would return it for every artifact.
                ProjectBuildingRequest request = new DefaultProjectBuildingRequest();
                request.setLocalRepository( session.getLocalRepository() );
                request.setRemoteRepositories( remoteRepositories );
                request.setRepositorySession( session.getRepositorySession() );
                request.setResolveDependencies( false );
                request.setProcessPlugins( false );
                request.setValidationLevel( 0 ); // ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL
                try
                {
                    depMavenProject = projectBuilder.build( artifact, true, request ).getProject();
                    if ( depMavenProject.getArtifact() != null )
                    {
                        depMavenProject.getArtifact().setScope( artifact.getScope() );
                    }
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

                // store it also in cache
                localCache.put(id, depMavenProject);
            }

            // keep the project
            result.put(id, depMavenProject);

            excludeArtifacts.remove(artifact.getId());
            includeArtifacts.put(artifact.getId(), artifact);
        }

        // exclude artifacts from the result that contain excluded artifacts in the dependency trail
        if (excludeTransitiveDependencies) {
            for (Map.Entry<String, Artifact> entry : includeArtifacts.entrySet()) {
                List<String> dependencyTrail = entry.getValue().getDependencyTrail();
                boolean remove = false;
                if (dependencyTrail != null) {
                    for (int i = 1; i < dependencyTrail.size() - 1; i++) {
                        if (excludeArtifacts.containsKey(dependencyTrail.get(i))) {
                            remove = true;
                            break;
                        }
                    }
                }
                if (remove) {
                    result.remove(MojoHelper.getArtifactId(entry.getValue()));
                }
            }
        }

        if (cache != null) {
            synchronized ( cache )
            {
                cache.putAll(result);
            }
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
        if ( CollectionUtils.isEmpty( project.getDependencyArtifacts() )
             || CollectionUtils.isEmpty( project.getArtifacts() ) )
        {
            MavenSession session = legacySupport.getSession();
            // ProjectDependenciesResolver resolves reactor modules automatically via
            // MavenSession#getProjectDependencyGraph(), so the manual reactorProjectDependencies
            // substitution that was required by the old MavenMetadataSource API is no longer needed.
            DefaultDependencyResolutionRequest request =
                new DefaultDependencyResolutionRequest( project, session.getRepositorySession() );
            try
            {
                DependencyResolutionResult resolved = dependenciesResolver.resolve( request );
                Set<Artifact> artifacts;
                if ( resolved.getDependencies().isEmpty() && !project.getDependencies().isEmpty() )
                {
                    // ProjectDependenciesResolver returns empty when called on a reactor sub-module
                    // from an aggregator mojo before that module enters its own lifecycle.
                    // Fall back to collecting the dependency graph directly via Aether.
                    try
                    {
                        CollectRequest collectRequest = new CollectRequest();
                        for ( org.apache.maven.model.Dependency dep : project.getDependencies() )
                        {
                            collectRequest.addDependency( RepositoryUtils.toDependency(
                                dep, session.getRepositorySession().getArtifactTypeRegistry() ) );
                        }
                        collectRequest.setRepositories(
                            RepositoryUtils.toRepos( project.getRemoteArtifactRepositories() ) );
                        CollectResult collectResult = repositorySystem.collectDependencies(
                            session.getRepositorySession(), collectRequest );
                        Set<Artifact> collectedArtifacts = new HashSet<>();
                        Deque<String> trailStack = new ArrayDeque<>();
                        String rootId = project.getArtifact() != null
                            ? project.getArtifact().getId()
                            : project.getGroupId() + ":" + project.getArtifactId() + ":pom:" + project.getVersion();
                        trailStack.addLast( rootId );
                        collectResult.getRoot().accept( new DependencyVisitor()
                        {
                            @Override
                            public boolean visitEnter( DependencyNode node )
                            {
                                if ( node.getDependency() != null && node.getArtifact() != null )
                                {
                                    Artifact a = RepositoryUtils.toArtifact( node.getArtifact() );
                                    a.setScope( node.getDependency().getScope() );
                                    List<String> trail = new ArrayList<>( trailStack );
                                    trail.add( a.getId() );
                                    a.setDependencyTrail( trail );
                                    collectedArtifacts.add( a );
                                    trailStack.addLast( a.getId() );
                                }
                                return true;
                            }

                            @Override
                            public boolean visitLeave( DependencyNode node )
                            {
                                if ( node.getDependency() != null && node.getArtifact() != null )
                                {
                                    trailStack.removeLast();
                                }
                                return true;
                            }
                        } );
                        artifacts = collectedArtifacts;
                    }
                    catch ( DependencyCollectionException e )
                    {
                        log.warn( "Could not collect dependencies for " + project.getId()
                                              + ": " + e.getMessage() );
                        artifacts = new HashSet<>();
                    }
                }
                else
                {
                    // Walk the dependency graph tree to build proper dependency trails for
                    // excludeTransitiveDependencies filtering. Use the flat resolved list
                    // as the authoritative artifact set (non-conflicted), and apply trails
                    // from the tree onto them.
                    Map<String, List<String>> trailMap = new HashMap<>();
                    DependencyNode graphRoot = resolved.getDependencyGraph();
                    if ( graphRoot != null )
                    {
                        Deque<String> trailStack = new ArrayDeque<>();
                        String rootId = project.getArtifact() != null
                            ? project.getArtifact().getId()
                            : project.getGroupId() + ":" + project.getArtifactId() + ":pom:"
                                + project.getVersion();
                        trailStack.addLast( rootId );
                        graphRoot.accept( new DependencyVisitor()
                        {
                            @Override
                            public boolean visitEnter( DependencyNode node )
                            {
                                if ( node.getDependency() != null && node.getArtifact() != null )
                                {
                                    Artifact a = RepositoryUtils.toArtifact( node.getArtifact() );
                                    String id = a.getId();
                                    List<String> trail = new ArrayList<>( trailStack );
                                    trail.add( id );
                                    List<String> existing = trailMap.get( id );
                                    if ( existing == null || trail.size() < existing.size() )
                                    {
                                        trailMap.put( id, trail );
                                    }
                                    trailStack.addLast( id );
                                }
                                return true;
                            }
                            @Override
                            public boolean visitLeave( DependencyNode node )
                            {
                                if ( node.getDependency() != null && node.getArtifact() != null )
                                {
                                    trailStack.removeLast();
                                }
                                return true;
                            }
                        } );
                    }
                    artifacts = resolved.getDependencies().stream()
                        .map( dep -> {
                            Artifact a = RepositoryUtils.toArtifact( dep.getArtifact() );
                            a.setScope( dep.getScope() );
                            List<String> trail = trailMap.get( a.getId() );
                            if ( trail != null )
                            {
                                a.setDependencyTrail( trail );
                            }
                            return a;
                        } )
                        .collect( Collectors.toSet() );
                }
                // Exclude reactor modules from the artifact set: they belong to the current
                // build and must not be treated as third-party dependencies.
                Set<String> reactorGavs = getReactorGavs();
                artifacts = artifacts.stream()
                    .filter( a -> !reactorGavs.contains( gavOf( a ) ) )
                    .collect( Collectors.toSet() );

                Set<Artifact> directArtifacts = artifacts.stream()
                    .filter( a -> a.getDependencyTrail() != null && a.getDependencyTrail().size() == 2 )
                    .collect( Collectors.toSet() );
                project.setDependencyArtifacts( directArtifacts );
                project.setArtifacts( artifacts );
            }
            catch ( DependencyResolutionException e )
            {
                throw new DependenciesToolException( e );
            }
        }
    }

    /**
     * @return the {@code groupId:artifactId:version} coordinates of the artifacts belonging to the current reactor.
     */
    private Set<String> getReactorGavs()
    {
        Set<String> reactorGavs = new HashSet<>();
        MavenSession session = legacySupport.getSession();
        if ( session != null && session.getProjects() != null )
        {
            for ( MavenProject p : session.getProjects() )
            {
                reactorGavs.add( p.getGroupId() + ":" + p.getArtifactId() + ":" + p.getVersion() );
            }
        }
        return reactorGavs;
    }

    /**
     * @return the {@code groupId:artifactId:version} coordinates of the given artifact.
     */
    private String gavOf( Artifact artifact )
    {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    @Override
    public void writeThirdPartyDependenciesFile( File outputDirectory, String listedDependenciesFilePath,
                                                 Set<Dependency> listedDependencies ) throws IOException
    {
        final File thirdPartyDepsFile = FileUtil.getFile(outputDirectory, listedDependenciesFilePath);

        if (listedDependencies.isEmpty()) {
            log.warn("There is no dependencies for write to " + thirdPartyDepsFile);
            return;
        }

        log.info( "Writing third-party dependencies file to " + thirdPartyDepsFile );
        MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(thirdPartyDepsFile, listedDependencies);
    }

    /**
     * Tests if the given project is includeable against a groupdId pattern and a artifact pattern.
     *
     * @param project                 the project to test
     * @param includedGroupPattern    the include group pattern
     * @param includedArtifactPattern the include artifact pattenr
     * @return {@code true} if the project is includavble, {@code false} otherwise
     */
    protected boolean isIncludable( Artifact project, Pattern includedGroupPattern, Pattern includedArtifactPattern )
    {

        // check if the groupId of the project should be included
        if ( includedGroupPattern != null )
        {
            // we have some defined license filters
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

        // check if the artifactId of the project should be included
        if ( includedArtifactPattern != null )
        {
            // we have some defined license filters
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

    /**
     * Tests if the given project is excludable against a groupdId pattern and a artifact pattern.
     *
     * @param project                 the project to test
     * @param excludedGroupPattern    the exlcude group pattern
     * @param excludedArtifactPattern the exclude artifact pattenr
     * @return {@code true} if the project is excludable, {@code false} otherwise
     */
    protected boolean isExcludable( Artifact project, Pattern excludedGroupPattern, Pattern excludedArtifactPattern )
    {
        // check if the groupId of the project should be included
        if ( excludedGroupPattern != null )
        {
            // we have some defined license filters
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

        // check if the artifactId of the project should be included
        if ( excludedArtifactPattern != null )
        {
            // we have some defined license filters
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
