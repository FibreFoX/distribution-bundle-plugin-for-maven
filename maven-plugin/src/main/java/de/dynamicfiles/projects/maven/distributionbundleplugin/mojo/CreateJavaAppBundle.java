package de.dynamicfiles.projects.maven.distributionbundleplugin.mojo;

import de.dynamicfiles.projects.maven.distributionbundleplugin.InternalUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URI;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * This MOJO creates an executable java application bundle which gets wrapped in some ZIP-file. As input-file the generated
 * project-artifact is used in order to process some work to make it executable.
 *
 * @author Danny Althoff
 */
@Mojo(name = "java-app", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class CreateJavaAppBundle extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Enable to see some status messages.
     */
    @Parameter(defaultValue = "false")
    private boolean verbose;

    /**
     * Specify where the bundle should be generated to.
     */
    @Parameter(defaultValue = "${project.build.directory}/bundles/java-app")
    private File outputFolder;

    /**
     * Set to true in case you want to clean the outputFolder on every execution.
     */
    @Parameter(defaultValue = "false")
    private boolean cleanupOutputFolder;

    /**
     * In case you have a special project configuration, which attaches the final artifact with
     * some classifier, you have to change this parameter. This happens e.g. in spring boot projects.
     */
    @Parameter(defaultValue = "")
    private String sourceClassifier;

    /**
     * For having some executable jar file, you might want to set this parameter, but only when the source
     * jar artifact does not already has this being set (e.g. via normal maven-jar-plugin or spring-boot).
     */
    @Parameter(defaultValue = "")
    private String mainClass;

    /**
     * In case the processed jar artifact already has some main-class registered, this plugin checks if this matches the custom set
     * main-class. To ignore this warning, just set this to "true".
     */
    @Parameter(defaultValue = "false")
    private boolean ignoreMainClassMismatch;

    /**
     * By default all required dependencies are copied into a separate folder for creating some executable application bundle.
     */
    @Parameter(defaultValue = "true")
    private boolean copyDependencies;

    /**
     * Specify the folder, where the dependencies are going to be placed.
     */
    @Parameter(defaultValue = "${project.build.directory}/bundles/java-app/lib")
    private File outputLibFolder;

    /**
     * By default system-scoped dependencies are not used for copying into the lib-folder. In case you have some special dependencies
     * (like packager.jar for having user defined JVM arguments, using JDK 8), set this to "true".
     */
    @Parameter(defaultValue = "false")
    private boolean copySystemDependencies;

    /**
     * When you need to add additional files to generated app-folder (e.g. README, license, third-party-tools, ...),
     * you can specify the source-folder here. All files will be copied recursively.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/distbundle/java-app-resources")
    private File additionalAppResources;

    /**
     * Same as additionalAppResources, but can used for defining multiple sources.
     */
    @Parameter
    private List<File> additionalAppResourcesList;

    /**
     * When having dependencies, to create some executable jar-file the corresponding classpath inside the JAR's manifest has to
     * point to the required files inside the lib-folder. In case you already have some classpath inside that JAR-file, set this to "false".
     * This is the case if you use some spring-boot project, where the created JAR-file already has the dependencies inside.
     */
    @Parameter(defaultValue = "true")
    private boolean generateClasspath;

    /**
     * Per defaut this is "false" and all known jar-files coming from the copied dependencies are used for the classpath manifest entry.
     * In case some additional application resources are containing some additional libraries, this should be set to "true".
     */
    @Parameter(defaultValue = "false")
    private boolean generateClasspathUsingLibFolder;

    /**
     * When using the lib-folder contents as classpath entries, you might want to specify what files are getting appended.
     * Using glob pattern matching for finding thse entries.
     */
    @Parameter(defaultValue = "*.jar")
    private String generateClasspathUsingLibFolderFileFilter;

    /**
     * If mainClass is defined, this mojo scans for the configured main-class being present in generated jar-file.
     * Set this to "false" in case the main-class is inside a different location (e.g. inside a nested jar-file).
     */
    @Parameter(defaultValue = "true")
    private boolean scanForMainClass;

    /**
     * If mainClass is defined and scanForMainClass is set to "true", the generated jar-file will be scanned. In case
     * the classes are in a different location (e.g. when using spring boot or other classpath-bootloaders) this can
     * be adjusted here.
     */
    @Parameter(defaultValue = "")
    private String scanForMainClassWithLocationPrefix;

    /**
     * To sign the jar-files inside your distribution bundle, set this to "true" and see "signParameters" for further details.
     */
    @Parameter(defaultValue = "false")
    private boolean signJars;

    /**
     * In case your have a special JDK to be used for signing, please specify this here. Expects some extracted/installed JDK.
     */
    @Parameter(defaultValue = "${java.home}")
    private String jdkPath;

    /**
     * For signing the tool "jarsigner" of the used JDK gets used. Here you have to specify the used parameters (including the keystore) for the
     * signing process. As the position of the jar-file might vary, just use "{JAR}" as placeholder, which gets replaced by the filename being signed.
     *
     * For further details about the available parameters, please consult the jarsigner documentation.
     *
     * Example-configuration:
     * <code>
     * &lt;signParameters&gt;
     *     &lt;parameter&gt;-strict&lt;/parameter&gt;
     *     &lt;parameter&gt;-keystore&lt;/parameter&gt;
     *     &lt;parameter&gt;${project.basedir}/src/main/distbundle/yourKeystore.jks&lt;/parameter&gt;
     *     &lt;parameter&gt;-storepass&lt;/parameter&gt;
     *     &lt;parameter&gt;changeit&lt;/parameter&gt;
     *     &lt;parameter&gt;-keypass&lt;/parameter&gt;
     *     &lt;parameter&gt;changeit&lt;/parameter&gt;
     *     &lt;parameter&gt;{JAR}&lt;/parameter&gt;
     *     &lt;parameter&gt;alias&lt;/parameter&gt;
     * &lt;/signParameters&gt;
     * </code>
     *
     * Note: When having verbose set to "true", the parameter "-verbose" gets added to this parameter-list too.
     *
     */
    @Parameter
    private List<String> signParameters;

    /**
     * While scanning for files to sign, this filter gets used for finding these files. Using glob pattern matching for finding thse files.
     */
    @Parameter(defaultValue = "*.jar")
    private String signJarsLibFilter;

    /**
     * To create some easy to share distribution bundle, the generated executable java application bundle is getting packed
     * into some ZIP-file inside the configured build-folder (normally inside the "target"-folder).
     */
    @Parameter(defaultValue = "true")
    private boolean createPackedBundle;

    /**
     * When some packed bundle was created, you can attach that file to the project artifacts, making it more easy to upload
     * your application to some repository.
     */
    @Parameter(defaultValue = "false")
    private boolean attachAsArtifact;

    /**
     * Attaching some artifact to the project can be fine-tuned by the artifact classifier. To adjust that classifier, just set this parameter.
     */
    @Parameter(defaultValue = "app-bundle")
    private String targetClassifier;

    private final InternalUtils internalUtils = new InternalUtils();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if( !"jar".equalsIgnoreCase(project.getPackaging()) ){
            throw new MojoExecutionException(String.format("Projects with packaging '%s' are not supported", project.getPackaging()));
        }
        // prepare target area
        if( verbose ){
            getLog().info("Prepare target area: " + outputFolder.toString());
        }
        if( outputFolder.exists() && cleanupOutputFolder ){
            try{
                if( verbose ){
                    getLog().info("Deleting recursively: " + outputFolder.toString());
                }
                internalUtils.deleteRecursive(outputFolder.toPath());
            } catch(IOException ex){
                throw new MojoFailureException("Not possible to cleanup output folder: " + outputFolder.getAbsolutePath(), ex);
            }
        }
        if( !outputFolder.exists() && !outputFolder.mkdirs() ){
            throw new MojoFailureException("Not possible to create output folder: " + outputFolder.getAbsolutePath());
        }

        if( verbose ){
            getLog().info("Finding artifact to work on...");
        }
        // find artifact to work on
        AtomicReference<File> sourceToCopy = new AtomicReference<>();
        // refactor this, making normal artefact as "fallback" when no classifier is found (might reduce branches)
        if( sourceClassifier == null || String.valueOf(sourceClassifier).trim().isEmpty() ){
            if( verbose ){
                getLog().info("Using default classifier");
            }
            if( project.getArtifact().getFile() == null || !project.getArtifact().getFile().exists() ){
                throw new MojoFailureException("There was no artifact to work on. Please check your build-log or reconfigure");
            }
            sourceToCopy.set(project.getArtifact().getFile());
        } else {
            if( verbose ){
                getLog().info("Using custom classifier: " + sourceClassifier);
            }
            project.getAttachedArtifacts().forEach(attachedArtifact -> {
                if( attachedArtifact.getClassifier().equalsIgnoreCase(sourceClassifier) ){
                    if( attachedArtifact.getFile().exists() ){
                        sourceToCopy.set(attachedArtifact.getFile());
                    }
                }
            });
            if( sourceToCopy.get() == null ){
                throw new MojoFailureException(String.format("There was no artifact with qualifier %s to work on. Please check your build-log or reconfigure", sourceClassifier));
            }
        }

        // copy artifact
        String artifactFileName = sourceToCopy.get().getName();
        if( verbose ){
            getLog().info("Copying artifact: " + artifactFileName);
        }
        Path targetAppArtifact = outputFolder.toPath().resolve(artifactFileName);
        try{
            Files.copy(sourceToCopy.get().toPath(), targetAppArtifact, StandardCopyOption.REPLACE_EXISTING);
        } catch(IOException ex){
            throw new MojoExecutionException(null, ex);
        }

        boolean hasCustomMainClass = mainClass != null && !mainClass.trim().isEmpty();

        if( verbose ){
            if( hasCustomMainClass ){
                getLog().info("Custom main-class was defined: " + mainClass);
            }
        }
        try{
            if( verbose ){
                getLog().info("Scanning JAR file for configured main-class");
            }
            // scan if is already executable jar, otherwise rework this
            AtomicBoolean hasRegisteredMainClass = new AtomicBoolean(false);
            AtomicBoolean registeredMainClassMatchesConfiguredMainClass = new AtomicBoolean(false);
            try(JarFile jarFile = new JarFile(targetAppArtifact.toFile())){
                Optional.ofNullable(jarFile.getManifest()).ifPresent(existingManifest -> {
                    Optional.ofNullable(existingManifest.getMainAttributes().get(Attributes.Name.MAIN_CLASS)).ifPresent(registeredMainClass -> {
                        if( !String.valueOf(registeredMainClass).trim().isEmpty() ){
                            if( verbose ){
                                getLog().info("Found registered main-class inside JAR file");
                            }
                            hasRegisteredMainClass.set(true);
                            registeredMainClassMatchesConfiguredMainClass.set(true);
                            // check if main-class is the correct one
                            if( hasCustomMainClass ){
                                if( !mainClass.equalsIgnoreCase(String.valueOf(registeredMainClass)) ){
                                    registeredMainClassMatchesConfiguredMainClass.set(false);
                                }
                            }
                        }
                    });
                });
            }

            if( !hasRegisteredMainClass.get() && !hasCustomMainClass ){
                throw new MojoFailureException("There is no main-class configured for the executable jar file. Please review your plugin-configuration.");
            }

            // warn about registeredMainClassMatchesConfiguredMainClass
            if( ignoreMainClassMismatch ){
                getLog().warn("The already existing main-class does not match the configured one. Please check if this is correct. You might want to set 'ignoreMainClassMismatch' to true to avoid this warning.");
            }
            // rework or create manifest file with new entries
            if( hasCustomMainClass ){
                if( verbose ){
                    getLog().info("Trying to change main-class in manifest of JAR file.");
                }
                // first check if manifest-file exists at all

                Map<String, String> env = new HashMap<>();
                URI uriToJarFile = targetAppArtifact.toAbsolutePath().toUri();
                // using explicit JAR URL syntax
                URI uriToFileSystem = URI.create("jar:" + uriToJarFile.toString());
                try(FileSystem zipFS = FileSystems.newFileSystem(uriToFileSystem, env, null)){
                    boolean hasManifestFile = false;
                    Manifest manifest = new Manifest();

                    Path manifestFile = zipFS.getPath("/META-INF/MANIFEST.MF");
                    if( Files.exists(manifestFile, LinkOption.NOFOLLOW_LINKS) ){
                        hasManifestFile = true;
                        // if it exists, load existing values
                        try(InputStream manifestAsStream = Files.newInputStream(manifestFile, StandardOpenOption.READ)){
                            manifest.read(manifestAsStream);
                        }
                    }

                    // adjust manifest-entries 
                    Attributes mainAttributes = manifest.getMainAttributes();
                    if( !hasManifestFile ){
                        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
                    }
                    mainAttributes.put(Attributes.Name.MAIN_CLASS, mainClass.trim());

                    if( verbose ){
                        getLog().info("Changing/Writing main-class in manifest of JAR file...");
                    }
                    // (over)write it back to artifact
                    try(OutputStream manifestOutput = Files.newOutputStream(manifestFile, StandardOpenOption.TRUNCATE_EXISTING)){
                        manifest.write(manifestOutput);
                    }
                }
            }
        } catch(IOException ex){
            throw new MojoExecutionException(null, ex);
        }

        Set<String> copiedDependencies = new LinkedHashSet<>();
        if( copyDependencies ){
            if( verbose ){
                getLog().info("Copying registered dependencies...");
            }
            if( !outputLibFolder.exists() && !outputLibFolder.mkdirs() ){
                throw new MojoFailureException("Not possible to create output library folder: " + outputLibFolder.getAbsolutePath());
            }

            if( copySystemDependencies ){
                if( verbose ){
                    getLog().info("Copying registered system-scoped dependencies...");
                }
                AtomicReference<MojoExecutionException> copyException = new AtomicReference<>();
                project.getDependencies().stream()
                        .filter(dependency -> "system".equalsIgnoreCase(dependency.getScope()))
                        .forEach(dependency -> {
                            // when having the first exception, skip all following tasks
                            if( copyException.get() == null ){
                                File dependencyFilePath = new File(dependency.getSystemPath());
                                File targetLibFile = outputLibFolder.toPath().resolve(dependencyFilePath.getName()).toFile();
                                try{
                                    Files.copy(dependencyFilePath.toPath(), targetLibFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    copiedDependencies.add(targetLibFile.toPath().toString());
                                } catch(IOException ex){
                                    copyException.set(new MojoExecutionException("Could not copy system-scoped dependency, please check your build log.", ex));
                                }
                            }
                        });
                if( copyException.get() != null ){
                    throw copyException.get();
                }
            }
            if( verbose ){
                getLog().info("Copying registered provided-scoped and runtime-scoped dependencies...");
            }

            AtomicReference<MojoExecutionException> copyException = new AtomicReference<>();
            project.getArtifacts().stream().filter(dependencyArtifact -> {
                // filter all unreadable, non-file artifacts
                File artifactFile = dependencyArtifact.getFile();
                return artifactFile.isFile() && artifactFile.canRead();
            }).forEach(dependencyArtifact -> {
                // when having the first exception, skip all following tasks
                if( copyException.get() == null ){
                    File dependencyArtifactFile = dependencyArtifact.getFile();
                    File targetLibFile = outputLibFolder.toPath().resolve(dependencyArtifactFile.getName()).toFile();
                    try{
                        Files.copy(dependencyArtifactFile.toPath(), targetLibFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        copiedDependencies.add(targetLibFile.toPath().toString());
                    } catch(IOException ex){
                        copyException.set(new MojoExecutionException("Could not copy provided-scoped or runtime-scoped dependency, please check your build log.", ex));
                    }
                }
            });

            if( copyException.get() != null ){
                throw copyException.get();
            }

            // cleanup
            if( outputLibFolder.list().length == 0 ){
                if( verbose ){
                    getLog().info("Removing lib-folder, as it was empty...");
                }

                // remove lib-folder, when nothing ended up there
                outputLibFolder.delete();
            }
        }

        // copy additional application resources (single source)
        if( additionalAppResources != null && additionalAppResources.exists() && additionalAppResources.list().length > 0 ){
            if( verbose ){
                getLog().info("Copying additional application resources, using source: " + additionalAppResources.toString());
            }
            try{
                internalUtils.copyRecursive(additionalAppResources.toPath(), outputFolder.toPath());
            } catch(IOException ex){
                throw new MojoExecutionException("Could not copy additional application resources, please check your build log.", ex);
            }
        }

        if( additionalAppResourcesList != null && !additionalAppResourcesList.isEmpty() ){
            AtomicReference<MojoExecutionException> copyException = new AtomicReference<>();
            additionalAppResourcesList.stream().filter(resourcesList -> {
                return resourcesList != null && resourcesList.exists() && resourcesList.canRead() && resourcesList.list().length > 0;
            }).forEach(additionalResources -> {
                if( verbose ){
                    getLog().info("Copying additional application resources, using source: " + additionalResources.toString());
                }
                // when having the first exception, skip all following tasks
                if( copyException.get() == null ){
                    try{
                        internalUtils.copyRecursive(additionalResources.toPath(), outputFolder.toPath());
                    } catch(IOException ex){
                        copyException.set(new MojoExecutionException("Could not copy additional application resources, please check your build log.", ex));
                    }
                }
            });
            if( copyException.get() != null ){
                throw copyException.get();
            }
        }

        // adjust classpath inside jar-file
        if( generateClasspath ){
            List<String> entriesForClasspath = new ArrayList<>();
            if( generateClasspathUsingLibFolder ){
                if( verbose ){
                    getLog().info("Generating classpath using entries inside lib-folder ...");
                }
                // prepare matcher
                FileSystem libFolderFilesystem = outputLibFolder.toPath().getFileSystem();
                PathMatcher pathMatcher = libFolderFilesystem.getPathMatcher("glob:" + generateClasspathUsingLibFolderFileFilter);
                try{
                    Files.walkFileTree(outputLibFolder.toPath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Path relativeFileOfLib = outputLibFolder.toPath().relativize(file);
                            if( pathMatcher.matches(relativeFileOfLib) ){
                                entriesForClasspath.add(relativeFileOfLib.toString().replace("\\", "/"));
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch(IOException ioex){
                    throw new MojoExecutionException(null, ioex);
                }
            } else {
                if( verbose ){
                    getLog().info("Generating classpath using registered and copied dependencies...");
                }
                // reusing recorded libs from normal dependencies
                entriesForClasspath.addAll(copiedDependencies);
            }

            StringBuilder relativeLibFolderLocation = new StringBuilder(outputFolder.toPath().relativize(outputLibFolder.toPath()).toString().replace("\\", "/"));
            // append slash when having some subfolder
            if( relativeLibFolderLocation.length() > 0 ){
                relativeLibFolderLocation.append("/");
            }
            final String relativeLibFolder = relativeLibFolderLocation.toString();

            Set<String> pathCorrectedClasspathEntries = entriesForClasspath.stream()
                    .map(entry -> relativeLibFolder + entry.replace("\\", "/"))
                    .collect(Collectors.toSet());

            // as app-folder and lib-folder might not be in the thought location, calculate relative location
            String resultingClasspath = String.join(" ", pathCorrectedClasspathEntries);

            Map<String, String> env = new HashMap<>();
            URI uriToJarFile = targetAppArtifact.toAbsolutePath().toUri();
            // using explicit JAR URL syntax
            URI uriToFileSystem = URI.create("jar:" + uriToJarFile.toString());
            try(FileSystem zipFS = FileSystems.newFileSystem(uriToFileSystem, env, null)){
                boolean hasManifestFile = false;
                Manifest manifest = new Manifest();

                Path manifestFile = zipFS.getPath("/META-INF/MANIFEST.MF");
                if( Files.exists(manifestFile, LinkOption.NOFOLLOW_LINKS) ){
                    hasManifestFile = true;
                    // if it exists, load existing values
                    try(InputStream manifestAsStream = Files.newInputStream(manifestFile, StandardOpenOption.READ)){
                        manifest.read(manifestAsStream);
                    }
                }

                // adjust manifest-entries 
                if( !hasManifestFile ){
                    // fail the build, might happen when multiple processes are working in the same file in parallel
                    throw new MojoFailureException("Could not find MANIFEST.MF inside generated jar-file.");
                }

                Attributes mainAttributes = manifest.getMainAttributes();
                mainAttributes.put(Attributes.Name.CLASS_PATH, resultingClasspath.trim());

                if( verbose ){
                    getLog().info("Changing/Writing classpath in manifest of JAR file...");
                }
                // (over)write it back to artifact
                try(OutputStream manifestOutput = Files.newOutputStream(manifestFile, StandardOpenOption.TRUNCATE_EXISTING)){
                    manifest.write(manifestOutput);
                }
            } catch(IOException ex){
                throw new MojoExecutionException(null, ex);
            }
        }

        // scan for main-class
        if( hasCustomMainClass && scanForMainClass ){
            if( verbose ){
                getLog().info("Scanning for custom main-class...");
            }
            String locationPrefix = scanForMainClassWithLocationPrefix;
            if( locationPrefix == null ){
                locationPrefix = "";
            }
            if( !internalUtils.isClassInsideJarFile(mainClass.trim(), locationPrefix, targetAppArtifact.toFile()) ){
                throw new MojoExecutionException(String.format("Configured main-class '%s' was not found inside generated jar-file. Please check the built artifact or plugin-configuration.", mainClass));
            }
        }

        // sign jar-files
        if( signJars ){
            if( verbose ){
                getLog().info("Signing JAR files...");
            }

            if( signParameters == null || signParameters.isEmpty() ){
                throw new MojoFailureException("Missing configuration of 'signParameters'-parameter. Please check your plugin-configuration.");
            }

            // find jarsigner
            Path jdkLocationPath = new File(jdkPath).toPath();
            // speculation: in case of older JDKs, the "java.home" property is the JRE inside the JDK, so if we can detect the "java"-binary inside
            // parent folder-structure, we are using JDK prior JDK 9
            // otherwise we are using that folder and are looking for jarsigner inside the "bin"-folder, which is valid for normal JDK 9+ and when
            // having customized jdkHome being set manually inside plugin-configuration
            String platformExecutableFileExtension = internalUtils.isPlatformWindows() ? ".exe" : "";
            boolean isJreInsideJdk = Files.exists(jdkLocationPath.getParent().resolve("bin").resolve("java" + platformExecutableFileExtension), LinkOption.NOFOLLOW_LINKS);
            AtomicReference<String> pathToJarsigner = new AtomicReference<>();
            if( isJreInsideJdk ){
                // look inside parent folder
                Path jarSignerPath = jdkLocationPath.getParent().resolve("bin").resolve("jarsigner" + platformExecutableFileExtension).toAbsolutePath();
                if( Files.exists(jarSignerPath, LinkOption.NOFOLLOW_LINKS) ){
                    pathToJarsigner.set(jarSignerPath.toString());
                }
            } else {
                // look inside given folder, should be the default-case since JDK 9+
                Path jarSignerPath = jdkLocationPath.resolve("bin").resolve("jarsigner" + platformExecutableFileExtension).toAbsolutePath();
                if( Files.exists(jarSignerPath, LinkOption.NOFOLLOW_LINKS) ){
                    pathToJarsigner.set(jarSignerPath.toString());
                }
            }

            if( pathToJarsigner.get() == null ){
                throw new MojoFailureException("Could not find jarsigner-executable, please check your JDK-installation of the configured JDK for this plugin-configuration.");
            }

            Set<String> filepathsToSign = new LinkedHashSet<>();
            // add generated artifact file
            filepathsToSign.add(targetAppArtifact.toAbsolutePath().toString());
            // add all jars in lib-folder
            FileSystem libFolderFilesystem = outputLibFolder.toPath().getFileSystem();
            PathMatcher pathMatcher = libFolderFilesystem.getPathMatcher("glob:" + signJarsLibFilter);

            try{
                Files.walkFileTree(outputLibFolder.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path relativeFileOfLib = outputLibFolder.toPath().relativize(file);
                        if( pathMatcher.matches(relativeFileOfLib) ){
                            // add to signing-list
                            filepathsToSign.add(file.toAbsolutePath().toString());
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch(IOException ioex){
                throw new MojoExecutionException(null, ioex);
            }

            // do signing stuff
            AtomicReference<MojoExecutionException> signingException = new AtomicReference<>();
            filepathsToSign.forEach(filepathToSign -> {
                if( signingException.get() != null ){
                    return;
                }
                Set<String> signingCommand = new LinkedHashSet<>();
                // command
                signingCommand.add(pathToJarsigner.get());

                // being verbose makes jarsigner verbose too ;)
                if( verbose ){
                    signingCommand.add("-verbose");
                }
                // parameters
                signingCommand.addAll(signParameters);

                // replace {JAR}-template with real filename
                signingCommand.stream().map(signingParameter -> {
                    if( "{JAR}".equalsIgnoreCase(signingParameter) ){
                        return filepathToSign;
                    }
                    return signingParameter;
                }).collect(Collectors.toList());

                // run jarsigner
                try{
                    ProcessBuilder pb = new ProcessBuilder()
                            .inheritIO()
                            .directory(project.getBasedir())
                            .command(new ArrayList<>(signingCommand));
                    if( verbose ){
                        getLog().info("Running command: " + String.join(" ", signingCommand));
                    }
                    Process p = pb.start();
                    p.waitFor();
                    if( p.exitValue() != 0 ){
                        signingException.set(new MojoExecutionException("Signing jar using jarsigner wasn't successful! Please check build-log."));
                    }
                } catch(IOException | InterruptedException ex){
                    signingException.set(new MojoExecutionException("There was an exception while signing jar-file: " + filepathToSign, ex));
                }
            });
            if( signingException.get() != null ){
                throw signingException.get();
            }
        }

        if( createPackedBundle ){
            if( verbose ){
                getLog().info("Creating packed bundle (ZIP-file)...");
            }
            // add to project artifacts, zipped
            File targetZippedArtifact = new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + "-" + targetClassifier + ".zip");
            if( targetZippedArtifact.exists() ){
                // always remove old file
                targetZippedArtifact.delete();
            }
            try{
                internalUtils.pack(outputFolder.toPath(), targetZippedArtifact.toPath());

                if( attachAsArtifact ){
                    if( verbose ){
                        getLog().info("Attaching packed bundle to project artifacts using classifier: " + targetClassifier);
                    }
                    projectHelper.attachArtifact(project, "zip", targetClassifier, targetZippedArtifact);
                }
            } catch(IOException ex){
                throw new MojoExecutionException("Could not create packed bundle, please check your build log.", ex);
            }
        }
    }
}