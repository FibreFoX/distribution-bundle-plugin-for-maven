/*
 * Copyright 2018 Danny Althoff
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.dynamicfiles.projects.maven.distributionbundleplugin.mojo;

import de.dynamicfiles.projects.maven.distributionbundleplugin.InternalUtils;
import de.dynamicfiles.projects.maven.distributionbundleplugin.api.NativeAppOptions;
import de.dynamicfiles.projects.maven.distributionbundleplugin.api.NativeLauncher;
import de.dynamicfiles.projects.maven.distributionbundleplugin.spi.NativeAppBundler;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.AbstractMojoExecutionException;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;

/**
 * This MOJO creates an native executable java application bundle which can get wrapped in some ZIP-file. As input-file the previous generated
 * java-app bundle is used in order to it distributable using native launchers.
 * 
 * As a short term goal this re-uses the files provided by JDK8-10/OpenJFX.
 *
 * @author Danny Althoff
 */
@Mojo(name = "native-app")
public class CreateNativeAppBundle extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution mojoExecution;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    /**
     * Enable to see some status messages.
     */
    @Parameter(defaultValue = "false")
    private boolean verbose;

    /**
     * Source folder to pick up the application. It's not required that this application has to be created by this
     * plugin, but expects a special file/folder layout.
     */
    @Parameter(defaultValue = "${project.build.directory}/distbundle/java-app", property = "distbundle.nativeapp.sourceFolder")
    private File sourceFolder;

    @Parameter(defaultValue = "${project.build.directory}/distbundle/native-app", property = "distbundle.nativeapp.outputBaseFolder")
    private File outputBaseFolder;

    /**
     * Some tasks require a special space to extract or manipulate files. To change the location,
     * just set this parameter to your wanted location.
     */
    @Parameter(defaultValue = "${project.build.directory}/distbundle-tmp", property = "distbundle.nativeapp.tempWorkfolder")
    private File tempWorkfolder;

    /**
     * When creating a native bundle, it sometimes is needed to clean the output-folder first. To speedup the process, the output-folder
     * will not be cleaned, but it might result in files being present after it got deleted from the source-folder. Set this to true to
     * recursivly delete the output-folder on each execution.
     */
    @Parameter(defaultValue = "false", property = "distbundle.nativeapp.cleanupOutputFolder")
    private boolean cleanupOutputFolder;

    /**
     * In case your have a special JDK to be used for bundling, please specify this here. Expects some
     * extracted/installed JDK (whether its OracleJDK or OpenJDK+OpenJFX).
     */
    @Parameter(defaultValue = "${java.home}")
    private String jdkPath;

    /**
     * If you want no JRE being bundled with your application, just set this parameter to "false".
     */
    @Parameter(defaultValue = "true")
    private boolean withJRE;

    /**
     * To bundle your application with the JRE, you have to set this parameter. When having JDK9+ the location can
     * be set to "${java.home}/../jre-${java.version}", on JDK8 the JRE inside the JDK will get used (like the javapackager
     * and the javafx-maven-plugin before).
     */
    @Parameter
    private String jrePath;

    /**
     * In case you want to have multiple native launchers, please create them here, each with a different
     * appName (otherwise it'll be revoked to work on).
     * If nothing specified, the application will be build using the project artifact finalname as filename.
     */
    @Parameter
    private List<NativeLauncher> nativeLaunchers;

    /**
     * GAV
     * de.dynamicfiles.projects.maven.distributionbundleplugin.bundler:native-app-windows-x64:1.0.0-SNAPSHOT
     */
    @Parameter(defaultValue = "")
    private String bundlerSource;

    /**
     * Uses latest version of bundler per default. To avoid this, fixate the version here.
     *
     * 1.0.0-SNAPSHOT
     */
    @Parameter(defaultValue = "")
    private String overrideBundlerSourceVersion;

    /**
     * A bundler source might contain multiple bundlers, which are identified by some internal ID.
     *
     * oracle-native-launcher
     */
    @Parameter(defaultValue = "")
    private String bundlerFlavor;

    /**
     * Per default the OS of the build-system is used.
     *
     * linux
     * mac
     * windows
     */
    @Parameter(defaultValue = "")
    private String clientOS;

    /**
     * Per default the cpu architecture of the build-system is used.
     *
     * x32
     * x64
     */
    @Parameter(defaultValue = "")
    private String clientArch;

    /**
     * Some bundlers might have special configuration options, these are set via a simple <i>&lt;key&gt;value&lt;/key&gt;</i> string entry.
     * As these options are highly unique to each bundler, please look into the corresponding documentation.
     */
    @Parameter
    private Map<String, String> internalParameters = new HashMap<>();

    private final InternalUtils internalUtils = new InternalUtils();

    private final String HARDCODED_DEFAULT_BUNDLER_GROUPID = "de.dynamicfiles.projects.maven.distributionbundleplugin.bundler";
    private final String HARDCODED_DEFAULT_BUNDLER_ARTIFACTID_PREFIX = "native-app-";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // first check if there is something to process on
        if( !sourceFolder.exists() || sourceFolder.list().length == 0 ){
            throw new MojoExecutionException("No resources found to work on. Make sure to call 'distbundle:java-app' first.");
        }

        if( verbose ){
            getLog().info("Prepare target area: " + outputBaseFolder.toString());
        }

        if( outputBaseFolder.exists() && cleanupOutputFolder ){
            try{
                if( verbose ){
                    getLog().info("Deleting recursively: " + outputBaseFolder.toString());
                }
                internalUtils.deleteRecursive(outputBaseFolder.toPath());
            } catch(IOException ex){
                throw new MojoFailureException("Not possible to cleanup output folder: " + outputBaseFolder.getAbsolutePath(), ex);
            }
        }

        if( !outputBaseFolder.exists() && !outputBaseFolder.mkdirs() ){
            throw new MojoFailureException("Not possible to create output folder: " + outputBaseFolder.getAbsolutePath());
        }

        if( !tempWorkfolder.exists() && !tempWorkfolder.mkdirs() ){
            throw new MojoFailureException("Not possible to create temporary working folder: " + tempWorkfolder.getAbsolutePath());
        }

        if( !withJRE ){
            if( jrePath == null || jrePath.trim().isEmpty() ){
                if( verbose ){
                    getLog().info("JRE was not set, trying to autodetect...");
                }
                // detect jmods-folder in order to detect jdk9+
                boolean isUsingJmodFiles = Files.exists(new File(jdkPath).toPath().resolve("jmods"), LinkOption.NOFOLLOW_LINKS);
                if( isUsingJmodFiles ){
                    if( verbose ){
                        getLog().info("Found JDK9+ layout.");
                    }
                    // JDK9+
                    jrePath = System.getProperty("java.home") + "/../jre-" + System.getProperty("java.version");
                } else {
                    if( verbose ){
                        getLog().info("Found JDK8 layout.");
                    }
                    // JDK8
                    // please be aware that java.home does NOT equal to JAVA_HOME, this is an often
                    // misinterpreted system-property, it points to the JRE inside the JDK
                    jrePath = System.getProperty("java.home");
                }
            }
            File jre = new File(jrePath);
            if( !jre.exists() ){
                throw new MojoFailureException("Could not find JRE at location: " + jre.getAbsolutePath());
            }
        }

        if( verbose ){
            getLog().info("Checking and sanitizing native launcher configuration...");
        }

        // prepare native launchers list
        if( nativeLaunchers == null ){
            nativeLaunchers = new ArrayList<>();
        }
        if( nativeLaunchers.isEmpty() ){
            if( verbose ){
                getLog().info("Adding default native launcher entry...");
            }

            NativeLauncher defaultLauncher = new NativeLauncher();
            defaultLauncher.setFilename(project.getBuild().getFinalName());
            if( internalUtils.isPlatformWindows() ){
                defaultLauncher.setExtension("exe");
            }
            nativeLaunchers.add(defaultLauncher);
        }

        if( verbose ){
            getLog().info("Constructing bundler source maven coordinates...");
        }

        // download bundler-source-artifact (might contain multiple bundlers)
        // to get this artifact, we have to create our source-GAV
        AtomicReference<String> bundlerSourceToUse = new AtomicReference<>();
        Optional.ofNullable(bundlerSource).ifPresent(configuredBundlerSource -> {
            if( configuredBundlerSource.trim().isEmpty() ){
                return;
            }

            // check if it contains GAV
            if( configuredBundlerSource.split(":").length <= 2 ){
                getLog().warn("Provided bundler source did not contain GAV format, using default bundler source artifact. Please check your plugin-configuration.");
                return;
            }

            // developer did provide some GAV-reachable artifact
            bundlerSourceToUse.set(configuredBundlerSource.trim());
        });

        // used did not set custom source, so let's use the "default one"
        if( bundlerSourceToUse.get() == null ){
            if( verbose ){
                getLog().info("Using default bundler source artifact coordinates.");
            }

            // check targeted clientOS/-Arch for generating artifact-id
            AtomicReference<String> clientOSToUse = new AtomicReference<>();
            Optional.ofNullable(clientOS).ifPresent(configuredClientOS -> {
                if( configuredClientOS.trim().isEmpty() ){
                    return;
                }

                // developer selected clientOS
                clientOSToUse.set(configuredClientOS.trim());
            });

            // user did not set custom clientOS, so let's use the "default one"
            if( clientOSToUse.get() == null ){
                if( internalUtils.isPlatformWindows() ){
                    clientOSToUse.set("windows");
                }
                if( internalUtils.isPlatformLinux() ){
                    clientOSToUse.set("linux");
                }
                if( internalUtils.isPlatformMac() ){
                    clientOSToUse.set("mac");
                }
            }

            AtomicReference<String> clientArchToUse = new AtomicReference<>();
            Optional.ofNullable(clientArch).ifPresent(configuredClientArch -> {
                if( configuredClientArch.trim().isEmpty() ){
                    return;
                }

                // developer selected clientArch
                clientArchToUse.set(configuredClientArch.trim());
            });

            // user did not set custom clientArch, so let's use the "default one"
            if( clientArchToUse.get() == null ){
                // even when this might seem like "get bit of operating system", it seems to be the architecture of the running jdk
                if( System.getProperty("os.arch").contains("64") ){
                    clientArchToUse.set("x64");
                } else {
                    clientArchToUse.set("x86");
                }
            }

            bundlerSourceToUse.set(HARDCODED_DEFAULT_BUNDLER_GROUPID + ":" + HARDCODED_DEFAULT_BUNDLER_ARTIFACTID_PREFIX + clientOSToUse.get() + "-" + clientArchToUse.get() + ":" + mojoExecution.getPlugin().getVersion());
        }

        String bundlerSourceGAV = bundlerSourceToUse.get();
        String[] bundlerSourceParts = bundlerSourceGAV.split(":");

        Artifact bundlerSourceArtifact = null;
        if( bundlerSourceParts.length == 3 ){
            // GAV
            bundlerSourceArtifact = repositorySystem.createArtifact(bundlerSourceParts[0], bundlerSourceParts[1], Optional.ofNullable(overrideBundlerSourceVersion).orElse(bundlerSourceParts[2]), "jar");
        }
        if( bundlerSourceParts.length == 4 ){
            // GAV + classifier
            bundlerSourceArtifact = repositorySystem.createArtifact(bundlerSourceParts[0], bundlerSourceParts[1], Optional.ofNullable(overrideBundlerSourceVersion).orElse(bundlerSourceParts[2]), "jar", bundlerSourceParts[3]);
        }

        if( bundlerSourceArtifact == null ){
            throw new MojoExecutionException("Provided bundlerSource did not contain the requested GAV-format. Please check your configuration.");
        }

        if( verbose ){
            getLog().info(String.format("Using GAV for bundler source artifact: %s", bundlerSourceToUse.get()));
        }

        // try to resolve this artifact
        ArtifactResolutionRequest request = new ArtifactResolutionRequest();

        request.setArtifact(bundlerSourceArtifact);
        try{
            request.setLocalRepository(repositorySystem.createDefaultLocalRepository());
        } catch(InvalidRepositoryException ire){
            throw new MojoExecutionException("Got exception while creating local repository reference.", ire);
        }
        request.setRemoteRepositories(project.getRemoteArtifactRepositories());

        if( session.isOffline() ){
            if( verbose ){
                getLog().info("Activating OFFLINE search");
            }

            request.setOffline(true);
        }

        if( verbose ){
            getLog().info("Starting bundler source artifact resolution...");
        }

        ArtifactResolutionResult result = repositorySystem.resolve(request);

        if( !result.isSuccess() ){
            throw new MojoExecutionException("Could not resolve bundler source, got some exceptions (" + result.hasExceptions() + "), here is the last one:", result.getExceptions().get(result.getExceptions().size() - 1));
        }

        Set<URL> artifactUrls = result.getArtifacts()
                .stream()
                .map(artifact -> {
                    return artifact.getFile().toURI();
                })
                .map(uri -> {
                    try{
                        return uri.toURL();
                    } catch(MalformedURLException ex){
                        // NO-OP stupid java api
                    }
                    return null;
                })
                .filter(entry -> entry != null)
                .collect(Collectors.toSet());

        if( verbose ){
            getLog().info("Creating temporary classloader for found bundler source artifact...");
        }

        // create our own classloader for easier isolation
        URLClassLoader cl = new URLClassLoader(
                artifactUrls.toArray(new URL[0]),
                Thread.currentThread().getContextClassLoader()
        );

        AtomicBoolean didRun = new AtomicBoolean(false);
        AtomicReference<AbstractMojoExecutionException> innerException = new AtomicReference<>();

        AtomicBoolean hasSpecialFlavorRequest = new AtomicBoolean(false);
        Optional.ofNullable(bundlerFlavor).ifPresent(flavor -> {
            if( !flavor.trim().isEmpty() ){
                hasSpecialFlavorRequest.set(true);
            }
        });

        if( verbose ){
            getLog().info("Searching for NativeAppBundler service implementations...");
        }

        ServiceLoader<NativeAppBundler> nativeAppBundlerServices = ServiceLoader.load(NativeAppBundler.class, cl);

        nativeAppBundlerServices.iterator().forEachRemaining(appBundler -> {
            // first found native app bundler wins
            // skip if any exception was thrown
            if( didRun.get() || innerException.get() != null ){
                return;
            }

            if( hasSpecialFlavorRequest.get() ){
                // if special bundler flavor was configured, skip each non-matching
                if( !bundlerFlavor.trim().equalsIgnoreCase(appBundler.getBundlerIdentifier()) ){
                    if( verbose ){
                        getLog().info("Found bundler did not match requested id: " + appBundler.getBundlerIdentifier());
                    }

                    return;
                }
            }

            if( verbose ){
                getLog().info("Using bundler with id: " + appBundler.getBundlerIdentifier());
            }

            try{
                if( verbose ){
                    getLog().info("Running bundler requirements checks...");
                }

                NativeAppOptions nativeAppOptions = new NativeAppOptions();

                nativeAppOptions.setInternalParameters(internalParameters);
                nativeAppOptions.setJdkPath(jdkPath);
                nativeAppOptions.setJrePath(jrePath);
                nativeAppOptions.setNativeLaunchers(nativeLaunchers);
                nativeAppOptions.setOutputBaseFolder(outputBaseFolder);
                nativeAppOptions.setSourceFolder(sourceFolder);
                nativeAppOptions.setTempWorkfolder(tempWorkfolder);
                nativeAppOptions.setVerbose(verbose);
                nativeAppOptions.setWithJRE(withJRE);

                appBundler.checkRequirements(nativeAppOptions, internalUtils, project, repositorySystem, mojoExecution, session, getLog());

                if( verbose ){
                    getLog().info("Running creation of native app bundle...");
                }

                // here we have a "valid" bundler, so call it
                File bundlerOutput = appBundler.bundleApp(nativeAppOptions, internalUtils, project, repositorySystem, mojoExecution, session, getLog());
            } catch(MojoFailureException | MojoExecutionException ex){
                // pass exception to outer world
                innerException.set(ex);
            }
            didRun.set(true);
        });

        if( innerException.get() != null ){
            AbstractMojoExecutionException thrownInnerException = innerException.get();
            throw new MojoFailureException("There was a problem while creating the native app bundle, please check your build log.", thrownInnerException);
        }

        try{
            cl.close();
        } catch(IOException ex){
            // NO-OP
        }
        if( !didRun.get() ){
            throw new MojoFailureException("No bundler found to build with, please check your plugin-configuration.");
        }
    }
}
