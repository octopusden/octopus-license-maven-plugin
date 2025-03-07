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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.License;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.mojo.license.model.LicenseMap;
import org.codehaus.mojo.license.nexus.SonatypeServiceLicenseProcessor;
import org.codehaus.mojo.license.utils.FileUtil;
import org.codehaus.mojo.license.utils.LicenseRegistryClient;
import org.codehaus.mojo.license.utils.MojoHelper;
import org.codehaus.mojo.license.utils.SortedProperties;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.octopusden.releng.versions.NumericVersionFactory;
import org.octopusden.releng.versions.VersionNames;
import org.octopusden.releng.versions.VersionRangeFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.codehaus.mojo.license.api.FreeMarkerHelper.TEMPLATE;

/**
 * Default implementation of the third party tool.
 *
 * @author <a href="mailto:tchemit@codelutin.com">Tony Chemit</a>
 * @version $Id$
 */
@Component( role = ThirdPartyTool.class, hint = "default" )
public class DefaultThirdPartyTool
        extends AbstractLogEnabled
        implements ThirdPartyTool
{
    /**
     * Classifier of the third-parties descriptor attached to a maven module.
     */
    private static final String DESCRIPTOR_CLASSIFIER = "third-party";

    /**
     * Type of the the third-parties descriptor attached to a maven module.
     */
    private static final String DESCRIPTOR_TYPE = "properties";

    /**
     * Pattern of a GAV plus a type.
     */
    private static final Pattern GAV_PLUS_TYPE_PATTERN = Pattern.compile( "(.+)--(.+)--(.+)--(.+)" );

    /**
     * Pattern of a GAV plus a type plus a classifier.
     */
    private static final Pattern GAV_PLUS_TYPE_AND_CLASSIFIER_PATTERN =
            Pattern.compile( "(.+)--(.+)--(.+)--(.+)--(.+)" );

    static final String LICENSE_DB_TYPE = "license.properties";

    // ----------------------------------------------------------------------
    // Components
    // ----------------------------------------------------------------------

    /**
     * The component that is used to resolve additional artifacts required.
     */
    @Requirement
    private ArtifactResolver artifactResolver;

    /**
     * The component used for creating artifact instances.
     */
    @Requirement
    private ArtifactFactory artifactFactory;

    /**
     * Maven ProjectHelper.
     */
    @Requirement
    private MavenProjectHelper projectHelper;

    /**
     * freeMarker helper.
     */
    private FreeMarkerHelper freeMarkerHelper = FreeMarkerHelper.newDefaultHelper();

    /**
     * Maven project comparator.
     */
    private final Comparator<MavenProject> projectComparator = MojoHelper.newMavenProjectComparator();

    /**
     * Version Range.
     */
    private final VersionNames versionNames = new VersionNames("", "", "");
    private final VersionRangeFactory versionRangeFactory = new VersionRangeFactory(versionNames);
    private final NumericVersionFactory numericVersionFactory = new NumericVersionFactory(versionNames);

    private boolean verbose;

    /**
     * {@inheritDoc}
     */
    public boolean isVerbose()
    {
        return verbose;
    }

    /**
     * {@inheritDoc}
     */
    public void setVerbose( boolean verbose )
    {
        this.verbose = verbose;
    }

    /**
     * {@inheritDoc}
     */
    public void attachThirdPartyDescriptor( MavenProject project, File file )
    {
        projectHelper.attachArtifact( project, DESCRIPTOR_TYPE, DESCRIPTOR_CLASSIFIER, file );
    }

    /**
     * {@inheritDoc}
     */
    public SortedSet<MavenProject> getProjectsWithNoLicense( LicenseMap licenseMap, boolean doLog )
    {

        Logger log = getLogger();

        // get unsafe dependencies (says with no license)
        SortedSet<MavenProject> unsafeDependencies = licenseMap.get( LicenseMap.UNKNOWN_LICENSE_MESSAGE );

        if ( doLog )
        {
            if ( CollectionUtils.isEmpty( unsafeDependencies ) )
            {
                log.debug( "There is no dependency with no license from poms." );
            }
            else
            {
                log.debug( "There is " + unsafeDependencies.size() + " dependencies with no license from poms : " );
                for ( MavenProject dep : unsafeDependencies )
                {

                    // no license found for the dependency
                    log.debug( " - " + MojoHelper.getArtifactId( dep.getArtifact() ) );
                }
            }
        }

        return unsafeDependencies;
    }

    /**
     * {@inheritDoc}
     */
    public SortedProperties loadThirdPartyDescriptorsForUnsafeMapping( Set<Artifact> topLevelDependencies,
                                                                       String encoding,
                                                                       Collection<MavenProject> projects,
                                                                       SortedSet<MavenProject> unsafeDependencies,
                                                                       LicenseMap licenseMap,
                                                                       ArtifactRepository localRepository,
                                                                       List<ArtifactRepository> remoteRepositories )
            throws ThirdPartyToolException, IOException
    {

        SortedProperties result = new SortedProperties( encoding );
        Map<String, MavenProject> unsafeProjects = new HashMap<String, MavenProject>();
        for ( MavenProject unsafeDependency : unsafeDependencies )
        {
            String id = MojoHelper.getArtifactId( unsafeDependency.getArtifact() );
            unsafeProjects.put( id, unsafeDependency );
        }

        for ( MavenProject mavenProject : projects )
        {

            if ( CollectionUtils.isEmpty( unsafeDependencies ) )
            {

                // no more unsafe dependencies to find
                break;
            }

            File thirdPartyDescriptor = resolvThirdPartyDescriptor( mavenProject, localRepository, remoteRepositories );

            if ( thirdPartyDescriptor != null && thirdPartyDescriptor.exists() && thirdPartyDescriptor.length() > 0 )
            {

                if ( getLogger().isInfoEnabled() )
                {
                    getLogger().info( "Detects third party descriptor " + thirdPartyDescriptor );
                }

                // there is a third party file detected form the given dependency
                SortedProperties unsafeMappings = new SortedProperties( encoding );

                if ( thirdPartyDescriptor.exists() )
                {

                    getLogger().info( "Load missing file " + thirdPartyDescriptor );

                    // load the missing file
                    unsafeMappings.load( thirdPartyDescriptor );
                }
                resolveUnsafe( unsafeDependencies, licenseMap, unsafeProjects, unsafeMappings, result );
            }

        }
        try
        {
            loadGlobalLicenses( topLevelDependencies, localRepository, remoteRepositories, unsafeDependencies,
                                licenseMap, unsafeProjects, result );
        }
        catch ( ArtifactNotFoundException e )
        {
            throw new ThirdPartyToolException( "Failed to load global licenses", e );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new ThirdPartyToolException( "Failed to load global licenses", e );
        }
        return result;
    }

    private void resolveUnsafe( SortedSet<MavenProject> unsafeDependencies, LicenseMap licenseMap,
                                Map<String, MavenProject> unsafeProjects, SortedProperties unsafeMappings,
                                SortedProperties result )
    {
        for ( String id : unsafeProjects.keySet() )
        {

            if ( unsafeMappings.containsKey( id ) )
            {

                String license = (String) unsafeMappings.get( id );
                if ( StringUtils.isEmpty( license ) )
                {

                    // empty license means not fill, skip it
                    continue;
                }

                // found a resolved unsafe dependency in the missing third party file
                MavenProject resolvedProject = unsafeProjects.get( id );
                unsafeDependencies.remove( resolvedProject );

                // push back to
                result.put( id, license.trim() );

                addLicense( licenseMap, resolvedProject, license );
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public File resolvThirdPartyDescriptor( MavenProject project, ArtifactRepository localRepository,
                                            List<ArtifactRepository> repositories )
            throws ThirdPartyToolException
    {
        if ( project == null )
        {
            throw new IllegalArgumentException( "The parameter 'project' can not be null" );
        }
        if ( localRepository == null )
        {
            throw new IllegalArgumentException( "The parameter 'localRepository' can not be null" );
        }
        if ( repositories == null )
        {
            throw new IllegalArgumentException( "The parameter 'remoteArtifactRepositories' can not be null" );
        }

        try
        {
            return resolveThirdPartyDescriptor( project, localRepository, repositories );
        }
        catch ( ArtifactNotFoundException e )
        {
            getLogger().debug( "ArtifactNotFoundException: Unable to locate third party descriptor: " + e );
            return null;
        }
        catch ( ArtifactResolutionException e )
        {
            throw new ThirdPartyToolException(
                    "ArtifactResolutionException: Unable to locate third party descriptor: " + e.getMessage(), e );
        }
        catch ( IOException e )
        {
            throw new ThirdPartyToolException(
                    "IOException: Unable to locate third party descriptor: " + e.getMessage(), e );
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addLicense( LicenseMap licenseMap, MavenProject project, String... licenseNames )
    {
        List<License> licenses = new ArrayList<License>();
        for ( String licenseName : licenseNames )
        {
            License license = new License();
            license.setName( licenseName.trim() );
            license.setUrl( licenseName.trim() );
            licenses.add( license );
        }
        addLicense( licenseMap, project, licenses );
    }

    /**
     * {@inheritDoc}
     */
    public void addLicense( LicenseMap licenseMap, MavenProject project, License license )
    {
        addLicense( licenseMap, project, Collections.singletonList( license ) );
    }

    /**
     * {@inheritDoc}
     */
    public void addLicense( LicenseMap licenseMap, MavenProject project, List<?> licenses )
    {
        getLogger().debug("Processing " + project.toString());
        if ( Artifact.SCOPE_SYSTEM.equals( project.getArtifact().getScope() ) )
        {
            getLogger().info("Ignoring " + project.toString() + " as SYSTEM");
            // do NOT treat system dependency
            return;
        }

        if ( CollectionUtils.isEmpty( licenses ) )
        {

            // no license found for the dependency
            getLogger().debug("Unknown license for:" + project.toString()) ;
            licenseMap.put( LicenseMap.UNKNOWN_LICENSE_MESSAGE, project );
            return;
        }

        for ( Object o : licenses )
        {
            String id = MojoHelper.getArtifactId( project.getArtifact() );
            if ( o == null )
            {
                getLogger().warn( "could not acquire the license for " + id + " " + project.toString());
                continue;
            }
            License license = (License) o;
            String licenseKey = license.getName();

            // tchemit 2010-08-29 Ano #816 Check if the License object is well formed

            if ( StringUtils.isEmpty( license.getName() ) )
            {
                getLogger().warn( "The license for " + id + " has no name (but exist)" );
                licenseKey = license.getUrl();
            }

            if ( StringUtils.isEmpty( licenseKey ) )
            {
                getLogger().warn( "No license url defined for " + id );
                licenseKey = LicenseMap.UNKNOWN_LICENSE_MESSAGE;
            }
            SonatypeServiceLicenseProcessor licenseProcessor = new SonatypeServiceLicenseProcessor(null, null);
            List<String> licenseList = licenseProcessor.parseLicense(licenseKey);
            for (String licenseId : licenseList) {
                getLogger().debug(licenseId + " -> " + project);
                licenseMap.put(licenseId, project);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void mergeLicenses( LicenseMap licenseMap, String mainLicense, Set<String> licenses )
    {

        if ( licenses.isEmpty() )
        {

            // nothing to merge, is this can really happen ?
            return;
        }

        SortedSet<MavenProject> mainSet = licenseMap.get( mainLicense );
        if ( mainSet == null )
        {
            if ( isVerbose() )
            {
                getLogger().warn( "No license [" + mainLicense + "] found, will create it." );
            }
            mainSet = new TreeSet<MavenProject>( projectComparator );
        }
        for ( String license : licenses )
        {
            SortedSet<MavenProject> set = licenseMap.get( license );
            if ( set == null )
            {
                if ( isVerbose() )
                {
                    getLogger().warn( "No license [" + license + "] found, skip the merge to [" + mainLicense + "]" );
                }
                continue;
            }
            if ( isVerbose() )
            {
                getLogger().info(
                        "Merge license [" + license + "] to [" + mainLicense + "] (" + set.size() + " dependencies)." );
            }
            mainSet.addAll( set );
            set.clear();
            licenseMap.remove( license );
        }
        if (!mainSet.isEmpty()) {
            licenseMap.put( mainLicense, mainSet );
        } else {
            getLogger().debug("No artifacts for " + mainLicense + " are found at merge");
        }

    }

    /**
     * {@inheritDoc}
     */
    public SortedProperties loadUnsafeMapping( LicenseMap licenseMap, SortedMap<String, MavenProject> artifactCache,
                                               String encoding, File missingFile ) throws IOException
    {
        Map<String, MavenProject> snapshots = new HashMap<String, MavenProject>();

        //find snapshot dependencies
        for ( Map.Entry<String, MavenProject> entry : artifactCache.entrySet() )
        {
            MavenProject mavenProject = entry.getValue();
            if ( mavenProject.getVersion().endsWith( Artifact.SNAPSHOT_VERSION ) )
            {
                snapshots.put( entry.getKey(), mavenProject );
            }
        }

        for ( Map.Entry<String, MavenProject> snapshot : snapshots.entrySet() )
        {
            // remove invalid entries, which contain timestamps in key
            artifactCache.remove( snapshot.getKey() );
            // put corrected keys/entries into artifact cache
            MavenProject mavenProject = snapshot.getValue();

            String id = MojoHelper.getArtifactId( mavenProject.getArtifact() );
            artifactCache.put( id, mavenProject );

        }
        SortedSet<MavenProject> unsafeDependencies = getProjectsWithNoLicense( licenseMap, false );

        SortedProperties unsafeMappings = new SortedProperties( encoding );

        if ( missingFile.exists() )
        {
            // there is some unsafe dependencies

            getLogger().info( "Load missing file " + missingFile );

            // load the missing file
            unsafeMappings.load( missingFile );
        }

        // get from the missing file, all unknown dependencies
        List<String> unknownDependenciesId = new ArrayList<String>();

        // coming from maven-license-plugin, we used the full g/a/v/c/t. Now we remove classifier and type
        // since GAV is good enough to qualify a license of any artifact of it...
        Map<String, String> migrateKeys = migrateMissingFileKeys( unsafeMappings.keySet() );

        for ( Object o : migrateKeys.keySet() )
        {
            String id = (String) o;
            String migratedId = migrateKeys.get( id );

            MavenProject project = artifactCache.get( migratedId );
            if ( project == null )
            {
                // now we are sure this is a unknown dependency
                unknownDependenciesId.add( id );
            }
            else
            {
                if ( !id.equals( migratedId ) )
                {

                    // migrates id to migratedId
                    getLogger().info( "Migrates [" + id + "] to [" + migratedId + "] in the missing file." );
                    Object value = unsafeMappings.get( id );
                    unsafeMappings.remove( id );
                    unsafeMappings.put( migratedId, value );
                }
            }
        }

        if ( !unknownDependenciesId.isEmpty() )
        {

            // there is some unknown dependencies in the missing file, remove them
            for ( String id : unknownDependenciesId )
            {
                getLogger().debug(
                        "dependency [" + id + "] does not exist in project, remove it from the missing file." );
                unsafeMappings.remove( id );
            }

            unknownDependenciesId.clear();
        }

        // push back loaded dependencies
        for ( Object o : unsafeMappings.keySet() )
        {
            String id = (String) o;

            MavenProject project = artifactCache.get( id );
            if ( project == null )
            {
                getLogger().debug( "dependency [" + id + "] does not exist in project." );
                continue;
            }

            String license = (String) unsafeMappings.get( id );

            String[] licenses = StringUtils.split( license, '|' );

            if ( ArrayUtils.isEmpty( licenses ) )
            {

                // empty license means not fill, skip it
                continue;
            }

            // add license in map
            addLicense( licenseMap, project, licenses );

            // remove unknown license
            unsafeDependencies.remove( project );
        }

        if ( unsafeDependencies.isEmpty() )
        {

            // no more unknown license in map
            licenseMap.remove( LicenseMap.UNKNOWN_LICENSE_MESSAGE );
        }
        else
        {

            // add a "with no value license" for missing dependencies
            for ( MavenProject project : unsafeDependencies )
            {
                String id = MojoHelper.getArtifactId( project.getArtifact() );
                if ( getLogger().isDebugEnabled() )
                {
                    getLogger().debug( "dependency [" + id + "] has no license, add it in the missing file." );
                }
                unsafeMappings.setProperty( id, "" );
            }
        }
        return unsafeMappings;
    }

    /**
     * {@inheritDoc}
     */
    public void overrideLicenses( LicenseMap licenseMap, SortedMap<String, MavenProject> artifactCache, String encoding, File overrideFile ) throws IOException
    {

        SortedProperties overrideMappings = new SortedProperties( encoding );

        if ( overrideFile!=null && overrideFile.exists() )
        {
            // there is some unsafe dependencies

            getLogger().info( "Load override file " + overrideFile );

            // load the missing file
            overrideMappings.load( overrideFile );
        }

        for ( Object o : overrideMappings.keySet() )
        {
            String id = (String) o;

            List<MavenProject> projects = getProjectFromCustomOverrideFile(id, artifactCache);

            if (projects.isEmpty()) {
                getLogger().warn( "dependency [" + id + "] does not exist in project." );
                continue;
            }

            String license = (String) overrideMappings.get( id );

            String[] licenses = StringUtils.split( license, '|' );

            if ( ArrayUtils.isEmpty( licenses ) )
            {

                // empty license means not fill, skip it
                continue;
            }

            projects.forEach(project -> {
                licenseMap.removeProject( project );

                // add license in map
                addLicense( licenseMap, project, licenses );
            });
        }
        licenseMap.removeEmptyLicenses();
    }

    @Override
    public void overrideLicenses(LicenseMap licenseMap, SortedMap<String, MavenProject> artifactCache, String encoding, String customOverrideFile) {
        SortedProperties overrideMappings = new SortedProperties( encoding );

        // there is some unsafe dependencies
        getLogger().info( "Load overrides from " + customOverrideFile);
        getLogger().info("Artifact cache " + artifactCache);
        // load the missing file
        try {
            overrideMappings.load(new StringReader(LicenseRegistryClient.getInstance().getFileContent(customOverrideFile)));
        } catch (final IOException ioException) {
            throw new IllegalStateException(ioException);
        }

        for ( Object o : overrideMappings.keySet() )
        {
            String id = (String) o;

            List<MavenProject> projects = getProjectFromCustomOverrideFile(id, artifactCache);

            if ( projects.isEmpty() )
            {
                getLogger().debug( "dependency [" + id + "] not found in project" );
                continue;
            }

            String license = (String) overrideMappings.get( id );

            String[] licenses = StringUtils.split( license, '|' );

            if ( ArrayUtils.isEmpty( licenses ) )
            {

                // empty license means not fill, skip it
                continue;
            }

            projects.forEach(project -> {
                getLogger().info("overriding for " + project + ", " + Arrays.toString(licenses));
                licenseMap.removeProject( project );

                // add license in map
                addLicense( licenseMap, project, licenses );
            });

        }
        licenseMap.removeEmptyLicenses();
    }

    public List<MavenProject> getProjectFromCustomOverrideFile(String id, SortedMap<String, MavenProject> artifactCache) {
        getLogger().debug("get project for dependency [" + id + "]");
        String[] overrideProjectGAV = id.split("--");

        if (overrideProjectGAV.length != 3) {
            getLogger().warn("id [" + id + "] format is invalid, no project found");
            return Collections.emptyList();
        }

        String overrideGroupId = overrideProjectGAV[0];
        String overrideArtifactId = overrideProjectGAV[1];
        String overrideVersionId = overrideProjectGAV[2];

        // Ensure versionIdRanges is in the correct format ( must start and end with [ or ( and ) or ] )
        String versionIdRanges = overrideVersionId.matches("^[\\[(].*[\\])]$") ? overrideVersionId : "[" + overrideVersionId + "]";

        return artifactCache.entrySet().stream()
                .filter(entry -> entry.getValue().getGroupId().equals(overrideGroupId) && entry.getValue().getArtifactId().equals(overrideArtifactId))
                .filter(entry -> {
                    getLogger().debug("Version check for " + entry.getKey());
                    getLogger().debug("\tVersion ranges: " + versionIdRanges);
                    getLogger().debug("\tProject version: " + entry.getValue().getVersion());

                    boolean isVersionValid = versionRangeFactory.create(versionIdRanges).containsVersion(
                            numericVersionFactory.create(entry.getValue().getVersion())
                    );

                    getLogger().debug("\tIs version valid: " + isVersionValid);

                    return isVersionValid;
                })
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }


    /**
     * {@inheritDoc}
     */
    public void writeThirdPartyFile( LicenseMap licenseMap, File thirdPartyFile, boolean verbose, String encoding, String lineFormat, boolean custom)
            throws IOException
    {
        Logger log = getLogger();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put( "licenseMap", licenseMap.entrySet() );
        properties.put( "dependencyMap", licenseMap.toDependencyMap().entrySet() );
        final String content;
        if (custom) {
            getLogger().info("Get template from " +  lineFormat);
            freeMarkerHelper = FreeMarkerHelper.newHelperFromContent(LicenseRegistryClient.getInstance().getFileContent(lineFormat));
            content = freeMarkerHelper.renderTemplate(TEMPLATE, properties);
        } else {
            content = freeMarkerHelper.renderTemplate(lineFormat, properties);
        }

        log.info( "Writing third-party file to " + thirdPartyFile );
        if ( verbose )
        {
            log.info( content );
        }

        FileUtil.printString( thirdPartyFile, content, encoding );

    }

    /**
     * {@inheritDoc}
     */
    public void writeBundleThirdPartyFile( File thirdPartyFile, File outputDirectory, String bundleThirdPartyPath )
            throws IOException
    {

        // creates the bundled license file
        File bundleTarget = FileUtil.getFile( outputDirectory, bundleThirdPartyPath );
        getLogger().info( "Writing bundled third-party file to " + bundleTarget );
        FileUtil.copyFile( thirdPartyFile, bundleTarget );
    }

    private void loadGlobalLicenses( Set<Artifact> dependencies, ArtifactRepository localRepository,
                                     List<ArtifactRepository> repositories, SortedSet<MavenProject> unsafeDependencies,
                                     LicenseMap licenseMap, Map<String, MavenProject> unsafeProjects,
                                     SortedProperties result )
            throws IOException, ArtifactNotFoundException, ArtifactResolutionException
    {
        for ( Artifact dep : dependencies )
        {
            if ( LICENSE_DB_TYPE.equals( dep.getType() ) )
            {
                loadOneGlobalSet( unsafeDependencies, licenseMap, unsafeProjects, dep, localRepository, repositories,
                                  result );
            }
        }
    }

    private void loadOneGlobalSet( SortedSet<MavenProject> unsafeDependencies, LicenseMap licenseMap,
                                   Map<String, MavenProject> unsafeProjects, Artifact dep,
                                   ArtifactRepository localRepository, List<ArtifactRepository> repositories,
                                   SortedProperties result )
            throws IOException, ArtifactNotFoundException, ArtifactResolutionException
    {
        artifactResolver.resolve( dep, repositories, localRepository );
        File propFile = dep.getFile();
        getLogger().info(
                String.format( "Loading global license map from %s: %s", dep.toString(), propFile.getAbsolutePath() ) );
        SortedProperties props = new SortedProperties( "utf-8" );
        InputStream propStream = null;

        try
        {
            propStream = new FileInputStream( propFile );
            props.load( propStream );
        }
        finally
        {
            IOUtils.closeQuietly( propStream );
        }

        for ( Object keyObj : props.keySet() )
        {
            String key = (String) keyObj;
            String val = (String) props.get( key );
            result.put( key, val );
        }

        resolveUnsafe( unsafeDependencies, licenseMap, unsafeProjects, props, result );
    }

    // ----------------------------------------------------------------------
    // Private methods
    // ----------------------------------------------------------------------

    /**
     * @param project         not null
     * @param localRepository not null
     * @param repositories    not null
     * @return the resolved site descriptor
     * @throws IOException                 if any
     * @throws ArtifactResolutionException if any
     * @throws ArtifactNotFoundException   if any
     */
    private File resolveThirdPartyDescriptor( MavenProject project, ArtifactRepository localRepository,
                                              List<ArtifactRepository> repositories )
            throws IOException, ArtifactResolutionException, ArtifactNotFoundException
    {
        File result;
        try
        {
            result = resolveArtifact( project.getGroupId(), project.getArtifactId(), project.getVersion(),
                                      DESCRIPTOR_TYPE, DESCRIPTOR_CLASSIFIER, localRepository, repositories );

            // we use zero length files to avoid re-resolution (see below)
            if ( result.length() == 0 )
            {
                getLogger().debug( "Skipped third party descriptor" );
            }
        }
        catch ( ArtifactNotFoundException e )
        {
            getLogger().debug( "Unable to locate third party files descriptor : " + e );

            Artifact artifact = e.getArtifact() == null
                    ? artifactFactory.createArtifactWithClassifier(
                    project.getGroupId(), project.getArtifactId(), project.getVersion(),
                    DESCRIPTOR_TYPE, DESCRIPTOR_CLASSIFIER)
                    : e.getArtifact();

            // we can afford to write an empty descriptor here as we don't expect it to turn up later in the remote
            // repository, because the parent was already released (and snapshots are updated automatically if changed)
            result = new File(localRepository.getBasedir(), localRepository.pathOf(artifact));
        }

        return result;
    }

    public File resolveMissingLicensesDescriptor( String groupId, String artifactId, String version,
                                                  ArtifactRepository localRepository, List<ArtifactRepository> repositories )
            throws IOException, ArtifactResolutionException, ArtifactNotFoundException
    {
        return resolveArtifact( groupId, artifactId, version, DESCRIPTOR_TYPE, DESCRIPTOR_CLASSIFIER, localRepository, repositories );
    }

    private File resolveArtifact( String groupId, String artifactId, String version,
                                  String type, String classifier, ArtifactRepository localRepository, List<ArtifactRepository> repositories ) throws ArtifactResolutionException, IOException, ArtifactNotFoundException
    {
        // TODO: this is a bit crude - proper type, or proper handling as metadata rather than an artifact in 2.1?
        Artifact artifact = artifactFactory.createArtifactWithClassifier( groupId, artifactId, version, type,
                                                                          classifier );

        artifactResolver.resolve( artifact, repositories, localRepository );

        return artifact.getFile();
    }

    private Map<String, String> migrateMissingFileKeys( Set<Object> missingFileKeys )
    {
        Map<String, String> migrateKeys = new HashMap<String, String>();
        for ( Object object : missingFileKeys )
        {
            String id = (String) object;
            Matcher matcher;

            String newId = id;
            matcher = GAV_PLUS_TYPE_AND_CLASSIFIER_PATTERN.matcher( id );
            if ( matcher.matches() )
            {
                newId = matcher.group( 1 ) + "--" + matcher.group( 2 ) + "--" + matcher.group( 3 );

            }
            else
            {
                matcher = GAV_PLUS_TYPE_PATTERN.matcher( id );
                if ( matcher.matches() )
                {
                    newId = matcher.group( 1 ) + "--" + matcher.group( 2 ) + "--" + matcher.group( 3 );

                }
            }
            migrateKeys.put( id, newId );
        }
        return migrateKeys;
    }
}
