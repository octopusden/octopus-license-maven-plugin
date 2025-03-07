package org.codehaus.mojo.license.api;

/*
 * #%L
 * License Maven Plugin
 * %%
 * Copyright (C) 2012 CodeLutin, Codehaus, Tony Chemit
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
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.License;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.mojo.license.LicenseProcessor;
import org.codehaus.mojo.license.model.LicenseMap;
import org.codehaus.mojo.license.nexus.SonatypeServiceLicenseProcessor;
import org.codehaus.mojo.license.utils.LicenseRegistryClient;
import org.codehaus.mojo.license.utils.SortedProperties;
import org.codehaus.mojo.license.xray.XrayLicenseProcessor;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.codehaus.mojo.license.model.LicenseMap.UNKNOWN_LICENSE_MESSAGE;

/**
 * Default implementation of the {@link org.codehaus.mojo.license.api.ThirdPartyHelper}.
 *
 * @author tchemit dev@tchemit.fr
 * @since 1.1
 */
public class DefaultThirdPartyHelper
        implements ThirdPartyHelper
{

    /**
     * DependenciesTool to load dependencies.
     *
     * @see DependenciesTool
     */
    private final DependenciesTool dependenciesTool;

    /**
     * ThirdPartyTool to load third-parties descriptors.
     *
     * @see ThirdPartyTool
     */
    private final ThirdPartyTool thirdPartyTool;

    /**
     * Local repository used.
     */
    private final ArtifactRepository localRepository;

    /**
     * List of remote repositories.
     */
    private final List<ArtifactRepository> remoteRepositories;

    /**
     * Current maven project.
     */
    private final MavenProject project;

    /**
     * Encoding used to read and write files.
     */
    private final String encoding;

    /**
     * Verbose flag.
     */
    private final boolean verbose;

    /**
     * Instance logger.
     */
    private final Log log;

    /**
     * Cache of dependencies (as maven project) loaded.
     */
    private static SortedMap<String, MavenProject> artifactCache;

    /**
     * Artifactory URL for Xray license info.
     */
    private final String artifactoryUrl;

    /**
     * Artifactory access token for Xray.
     */
    private final String artifactoryAccessToken;

    /**
     * Flag to use Sonatype Processor for license info.
     */
    private final Boolean isUseSonatypeProcessor;

    /**
     * Flag to use Xray Processor for license info.
     */
    private final Boolean isUseXrayProcessor;

    /**
     * Constructor of the helper.
     *
     * @param project            Current maven project
     * @param encoding           Encoding used to read and write files
     * @param verbose            Verbose flag
     * @param dependenciesTool   tool to load dependencies
     * @param thirdPartyTool     tool to load third-parties descriptors
     * @param localRepository    maven local repository
     * @param remoteRepositories maven remote repositories
     * @param log                logger
     */

    public DefaultThirdPartyHelper( MavenProject project, String encoding, boolean verbose,
                                    DependenciesTool dependenciesTool, ThirdPartyTool thirdPartyTool,
                                    ArtifactRepository localRepository, List<ArtifactRepository> remoteRepositories,
                                    Log log, String artifactoryUrl, String artifactoryAccessToken,
                                    Boolean isUseSonatypeProcessor, Boolean isUseXrayProcessor)
    {
        this.project = project;
        this.encoding = encoding;
        this.verbose = verbose;
        this.dependenciesTool = dependenciesTool;
        this.thirdPartyTool = thirdPartyTool;
        this.localRepository = localRepository;
        this.remoteRepositories = remoteRepositories;
        this.log = log;
        this.thirdPartyTool.setVerbose( verbose );
        this.artifactoryUrl = artifactoryUrl;
        this.artifactoryAccessToken = artifactoryAccessToken;
        this.isUseSonatypeProcessor = isUseSonatypeProcessor;
        this.isUseXrayProcessor = isUseXrayProcessor;
    }

    /**
     * {@inheritDoc}
     */
    public SortedMap<String, MavenProject> getArtifactCache()
    {
        if ( artifactCache == null )
        {
            artifactCache = new TreeMap<String, MavenProject>();
        }
        return artifactCache;
    }

    /**
     * {@inheritDoc}
     */
    public SortedMap<String, MavenProject> loadDependencies( MavenProjectDependenciesConfigurator configuration )
    {
        return dependenciesTool.loadProjectDependencies( project, configuration, localRepository, remoteRepositories,
                                                         getArtifactCache() );
    }

    /**
     * {@inheritDoc}
     */
    public SortedProperties loadThirdPartyDescriptorForUnsafeMapping( Set<Artifact> topLevelDependencies,
                                                                      SortedSet<MavenProject> unsafeDependencies,
                                                                      Collection<MavenProject> projects,
                                                                      LicenseMap licenseMap )
            throws ThirdPartyToolException, IOException
    {
        return thirdPartyTool.loadThirdPartyDescriptorsForUnsafeMapping( topLevelDependencies, encoding, projects,
                                                                         unsafeDependencies, licenseMap,
                                                                         localRepository, remoteRepositories );
    }

    /**
     * {@inheritDoc}
     */
    public SortedProperties loadUnsafeMapping( LicenseMap licenseMap, File missingFile,
                                               SortedMap<String, MavenProject> projectDependencies )
            throws IOException
    {
        return thirdPartyTool.loadUnsafeMapping( licenseMap, projectDependencies, encoding, missingFile);
    }

    /**
     * {@inheritDoc}
     */
    public LicenseMap createLicenseMap( SortedMap<String, MavenProject> dependencies, String proxyUrl)
    {
        Collection<MavenProject> values = dependencies.values();
        return createLicenseMap(values, proxyUrl);
    }


    public LicenseMap createLicenseMap(Collection<MavenProject> dependencies, String proxyUrl) {
        LicenseMap licenseMap = new LicenseMap();
        for ( MavenProject project : dependencies)
        {
            thirdPartyTool.addLicense( licenseMap, project, project.getLicenses() );
        }

        log.info("license.useSonatypeProcessor=" + isUseSonatypeProcessor + ", license.useXrayProcessor=" + isUseXrayProcessor);

        if (isUseSonatypeProcessor) {
            updateLicensesWithInfoFromNexus(licenseMap, proxyUrl);
        }

        if (isUseXrayProcessor) {
            if (artifactoryUrl == null || artifactoryAccessToken == null) {
                throw new IllegalArgumentException("Either Environment variable or JVM argument for set 'artifactoryUrl' and 'artifactoryAccessToken' must be provided");
            }

            try {
                new URL(artifactoryUrl);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("The provided Artifactory URL is not valid: " + artifactoryUrl);
            }

            if (StringUtils.isBlank(artifactoryAccessToken)) {
                throw new IllegalArgumentException("The Artifactory access token cannot be blank or null");
            }

            updateLicensesWithInfoFromXRay(licenseMap);
        }

        return licenseMap;
    }

    private void updateLicensesWithInfoFromXRay(LicenseMap licenseMap) {
        log.info("Update licenses with info from XRay");
        SortedSet<MavenProject> mavenProjects = licenseMap.get(UNKNOWN_LICENSE_MESSAGE);

        if(mavenProjects != null) {
            log.info("Projects with unknown license:");
            log.info(mavenProjects.toString());
            log.debug("License map before update:");
            log.debug(licenseMap.toString());

            Set<MavenProject> projectsToIterate = new TreeSet<>(mavenProjects);

            LicenseProcessor licenseProcessor = new XrayLicenseProcessor(log, artifactoryUrl, artifactoryAccessToken);

            for (MavenProject mavenProject: projectsToIterate) {
                List<License> licenses = licenseProcessor.getLicensesByProject(mavenProject);

                if (!licenses.isEmpty()) {
                    mavenProjects.remove(mavenProject);
                    thirdPartyTool.addLicense(licenseMap, mavenProject, licenses);
                }
            }

            log.debug("License map after update:");
            log.debug(licenseMap.toString());
        } else {
            log.info("No project with unknown license");
        }
    }

    private void updateLicensesWithInfoFromNexus(LicenseMap licenseMap, String proxyUrl) {
        log.info("Update licenses with info from Sonatype");
        SortedSet<MavenProject> mavenProjects = licenseMap.get(UNKNOWN_LICENSE_MESSAGE);
        if (mavenProjects != null) {
            Set<MavenProject> projectsToIterate = new TreeSet<>(mavenProjects);
            for (MavenProject mavenProject : projectsToIterate) {
                SonatypeServiceLicenseProcessor licenseProcessor = new SonatypeServiceLicenseProcessor(log, proxyUrl);
                List<License> licences = licenseProcessor.getLicencesByProject(mavenProject);
                if (!licences.isEmpty()) {
                    mavenProjects.remove(mavenProject);
                    thirdPartyTool.addLicense(licenseMap, mavenProject, licences);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void attachThirdPartyDescriptor( File file )
    {

        thirdPartyTool.attachThirdPartyDescriptor( project, file );
    }


    /**
     * {@inheritDoc}
     */
    public SortedSet<MavenProject> getProjectsWithNoLicense( LicenseMap licenseMap )
    {
        return thirdPartyTool.getProjectsWithNoLicense( licenseMap, verbose );
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings( "unchecked" ) // project.getArtifacts()
    public SortedProperties createUnsafeMapping( LicenseMap licenseMap, File missingFile,
                                                 boolean useRepositoryMissingFiles,
                                                 SortedSet<MavenProject> unsafeDependencies,
                                                 SortedMap<String, MavenProject> projectDependencies )
            throws ProjectBuildingException, IOException, ThirdPartyToolException
    {

        SortedProperties unsafeMappings = loadUnsafeMapping( licenseMap, missingFile, projectDependencies );

        if ( CollectionUtils.isNotEmpty( unsafeDependencies ) )
        {

            // there is some unresolved license

            if ( useRepositoryMissingFiles )
            {

                // try to load missing third party files from dependencies

                Collection<MavenProject> projects = new ArrayList<MavenProject>( projectDependencies.values() );
                projects.remove( project );
                projects.removeAll( unsafeDependencies );

                SortedProperties resolvedUnsafeMapping =
                        loadThirdPartyDescriptorForUnsafeMapping( project.getArtifacts(), unsafeDependencies, projects,
                                                                  licenseMap );

                // push back resolved unsafe mappings (only for project dependencies)
                for ( Object coord : resolvedUnsafeMapping.keySet() )
                {
                    String s = (String) coord;
                    if ( projectDependencies.containsKey( s ) )
                    {
                        unsafeMappings.put( s, resolvedUnsafeMapping.get( s ) );
                    }
                }
            }
        }

        return unsafeMappings;
    }


    /**
     * {@inheritDoc}
     */
    public void mergeLicenses( List<String> licenseMerges, LicenseMap licenseMap )
            throws MojoFailureException
    {
        log.info("Loading merges from merges.txt");
        Scanner mergesLines = new Scanner(LicenseRegistryClient.getInstance().getFileContent("merges.txt")).useDelimiter("\\n");
        while (mergesLines.hasNext()) {
            licenseMerges.add(mergesLines.next());
        }

        Set<String> licenseFound = new HashSet<String>();

        if ( !CollectionUtils.isEmpty( licenseMerges ) )
        {

            // check where is not multi licenses merged main licenses (see MLICENSE-23)
            Map<String, Set<String>> mergedLicenses = new HashMap<String, Set<String>>();

            for ( String merge : licenseMerges )
            {
                merge = merge.trim();
                String[] split = merge.split( "\\s*\\|\\s*" );

                String mainLicense = split[0];

                Set<String> mergeList;

                if ( mergedLicenses.containsKey( mainLicense ) )
                {

                    mergeList = mergedLicenses.get( mainLicense );
                }
                else
                {
                    mergeList = new HashSet<String>();
                }

                for ( int i = 0; i < split.length; i++ )
                {
                    String licenseToAdd = split[i];
                    if ( i == 0 )
                    {
                        // mainLicense will not be merged (to itself)
                        continue;
                    }

                    // check license not already described to be merged
                    if ( mergeList.contains( licenseToAdd ) || licenseFound.contains( licenseToAdd ) )
                    {

                        // this license to merge was already described, fail the build...

                        throw new MojoFailureException(
                                "The license " + licenseToAdd + " was already registred in the " +
                                        "configuration, please use only one such entry as describe in example " +
                                        "http://mojo.codehaus.org/license-maven-plugin/examples/example-thirdparty.html#Merge_licenses." );
                    }

                    // can add this license for merge
                    mergeList.add( licenseToAdd );
                    licenseFound.add( licenseToAdd );
                }

                // push back licenses to merge for this main license
                mergedLicenses.put( mainLicense, mergeList );
            }

            // merge licenses in license map

            for ( Map.Entry<String, Set<String>> entry : mergedLicenses.entrySet() )
            {
                String mainLicense = entry.getKey();
                Set<String> mergedLicense = entry.getValue();
                if ( verbose )
                {
                    log.info( "Will merge to *" + mainLicense + "*, licenses: " + mergedLicense );
                }

                thirdPartyTool.mergeLicenses( licenseMap, mainLicense, mergedLicense );
            }
        }
    }

}
