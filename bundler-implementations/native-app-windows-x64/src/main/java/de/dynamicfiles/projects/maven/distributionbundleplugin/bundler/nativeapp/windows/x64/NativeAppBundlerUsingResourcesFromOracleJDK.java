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
package de.dynamicfiles.projects.maven.distributionbundleplugin.bundler.nativeapp.windows.x64;

import de.dynamicfiles.projects.maven.distributionbundleplugin.api.NativeLauncher;
import de.dynamicfiles.projects.maven.distributionbundleplugin.api.OS;
import de.dynamicfiles.projects.maven.distributionbundleplugin.api.SharedInternalTools;
import de.dynamicfiles.projects.maven.distributionbundleplugin.spi.NativeAppBundler;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author FibreFoX
 */
public class NativeAppBundlerUsingResourcesFromOracleJDK implements NativeAppBundler {

    private final String jmodWithLauncherBinaries = "jdk.packager.jmod";

    @Override
    public File bundleApp(String jdkPath, String jrePath, SharedInternalTools internalUtils, File outputBaseFolder, File sourceFolder, File tempWorkfolder, MavenProject project, List<NativeLauncher> nativeLaunchers) throws MojoFailureException, MojoExecutionException {
        // as since JDK 9 the resource-files are inside a jmod-file (non opened), as a workaround I'm using
        // the "jmod"-binary to extract the whole "jdk.packager.jmod"-file temporary
        // "native binaries" (bootstrapping launchers) are expecting the application sitting inside some "app"-folder aside of the native launcher
        // .\jmod.exe extract --dir C:\Users\FibreFoX\Desktop\blub ..\jmods\jdk.packager.jmod
        File outputFolderForJavaApp = outputBaseFolder.toPath().resolve("windows-x64").resolve("app").toFile();
        if( !outputFolderForJavaApp.exists() && !outputFolderForJavaApp.mkdirs() ){
            throw new MojoFailureException("Not possible to create output folder: " + outputFolderForJavaApp.getAbsolutePath());
        }

        // copy source bundle to nested app-folder (requirement of the native launcher from the jdk-executable)
        try{
            internalUtils.copyRecursive(sourceFolder.toPath(), outputFolderForJavaApp.toPath());
        } catch(IOException ex){
            throw new MojoFailureException(null, ex);
        }

        boolean isUsingJmodFiles = Files.exists(new File(jdkPath).toPath().resolve("jmods"), LinkOption.NOFOLLOW_LINKS);
        if( isUsingJmodFiles ){
            // check if required jmod file is existing
            if( !new File(jdkPath).toPath().resolve("jmods").resolve(jmodWithLauncherBinaries).toFile().exists() ){
                throw new MojoExecutionException("Missing JMOD-file: " + jmodWithLauncherBinaries);
            }

            List<String> command = new ArrayList<>();
            command.add("jmod");
            command.add("extract");
            command.add("--dir");
            command.add(tempWorkfolder.getAbsolutePath());
            command.add(jdkPath + File.separator + "jmods" + File.separator + jmodWithLauncherBinaries);

            ProcessBuilder extractionProcess = new ProcessBuilder()
                    .inheritIO()
                    // use the jmod-command from the provided JDK-path (might be a different version, where something has changed
                    .directory(new File(jdkPath))
                    .command(command);
            try{
                Process p = extractionProcess.start();
                p.waitFor();
                if( p.exitValue() != 0 ){
                    throw new MojoExecutionException("Could not extract JMOD " + jmodWithLauncherBinaries);
                }
            } catch(IOException | InterruptedException ex){
                throw new MojoExecutionException(null, ex);
            }
            Path windowsBinariesSource = tempWorkfolder.toPath().resolve("classes").resolve("com").resolve("oracle").resolve("tools").resolve("packager").resolve("windows");

            // copy all DLL-files
            try{
                Path targetFolder = outputBaseFolder.toPath().resolve("windows-x64");
                // TODO refactor this into utils-method
                Files.walkFileTree(windowsBinariesSource, new FileVisitor<Path>() {

                    boolean enteredFolder = false;

                    @Override
                    public FileVisitResult preVisitDirectory(Path subfolder, BasicFileAttributes attrs) throws IOException {
                        if( enteredFolder ){
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        enteredFolder = true;
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
                        // do copy, and replace, as the resource might already be existing
                        if( sourceFile.toFile().getName().endsWith(".dll") ){
                            Files.copy(sourceFile, targetFolder.resolve(windowsBinariesSource.relativize(sourceFile)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path source, IOException ioe) throws IOException {
                        // fail fast
                        return FileVisitResult.TERMINATE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path source, IOException ioe) throws IOException {
                        // nothing to do here
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch(IOException ex){
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
            }

            // copy launcher files
            Path windowsLauncherBinary = windowsBinariesSource.resolve("WinLauncher.exe");

            // check architecutre matching
            boolean bitCheckOfLauncherMatching = internalUtils.isWindowsExecutable64bit(windowsLauncherBinary);
            if( !bitCheckOfLauncherMatching ){
                // 32 bit -> quit work, wrong configured jdk
                throw new MojoExecutionException("Provided JDK did not contain correct bit architecture, please provide some 64bit JDK");
            }

            nativeLaunchers.forEach(nativeLauncher -> {
                try{
                    String fileExtension = nativeLauncher.getExtension();
                    if( fileExtension == null || fileExtension.trim().isEmpty() ){
                        if( internalUtils.isPlatformWindows() ){
                            fileExtension = "exe";
                        }
                    }
                    Files.copy(windowsLauncherBinary, outputBaseFolder.toPath().resolve("windows-x64").resolve(nativeLauncher.getFilename() + "." + fileExtension), StandardCopyOption.REPLACE_EXISTING);
                } catch(IOException ex){
                    Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
                }
            });
        } else {
            // assume we are using JDK prior JDK 9 (8,7,...)
            // on JDK prior JDK 9, java.home points to the JRE inside the JDK
            Path antJfxJar = new File(jdkPath).toPath().resolve("lib").resolve("ant-javafx.jar");
            Path antJfxJarinParentFolder = new File(jdkPath).toPath().getParent().resolve("lib").resolve("ant-javafx.jar");
//            System.out.println("Checking: " + antJfxJar.toFile().getAbsolutePath());
//            System.out.println("Checking: " + antJfxJarinParentFolder.toFile().getAbsolutePath());
            if( !antJfxJar.toFile().exists() && !antJfxJarinParentFolder.toFile().exists() ){
                // javafx files are not yet installed
                throw new MojoExecutionException("Required JavaFX file was not found: ant-javafx.jar, please make sure to have some javafx installed");
            }
            System.out.println("Would work on JDK 8 file schema");
            // TODO
        }

        AtomicReference<MojoExecutionException> configurationCreationException = new AtomicReference<>();

        // TODO create launcher cfg-files
        nativeLaunchers.forEach(nativeLauncher -> {
            if( configurationCreationException.get() != null ){
                return;
            }

            // check if configuration was provided as normal string
            AtomicReference<String> configurationContent = new AtomicReference<>();

            // first we check configuration file template being set
            AtomicReference<IOException> configurationSourceFileException = new AtomicReference<>();
            File configurationSourceFile = nativeLauncher.getConfigurationFile();
            Optional.ofNullable(nativeLauncher.getConfigurationFile()).ifPresent(configFileTemplate -> {
                System.out.println(String.format("Reading configuration file for launcher: '%s'", nativeLauncher.getFilename()));
                try{
                    if( !Files.exists(configFileTemplate.toPath(), LinkOption.NOFOLLOW_LINKS) ){
                        throw new IOException("Configuration file not found at: " + configFileTemplate.getAbsolutePath());
                    }
                    configurationContent.set(String.join("\n", Files.readAllLines(configFileTemplate.toPath())));
                } catch(IOException ex){
                    configurationSourceFileException.set(ex);
                }
            });

            if( configurationSourceFileException.get() != null ){
                configurationCreationException.set(new MojoExecutionException(null, configurationSourceFileException.get()));
                return;
            }

            // when no configuration file template was set, check "configuration" parameter
            // TODO find better namen for "configuration" xD
            if( configurationContent.get() == null ){
                String xmlConfiguration = nativeLauncher.getConfiguration();

                // if configuration template still is empty, use internal default one
                if( xmlConfiguration == null ){
                    System.out.println(String.format("Using default configuration for launcher: '%s'", nativeLauncher.getFilename()));
                    // TODO implement cfg-format too
                    StringBuilder sb = new StringBuilder();
                    try(InputStream resourceAsStream = this.getClass().getResourceAsStream("configurationTemplate.ini")){
                        try(BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream))){
                            reader.lines().forEachOrdered(line -> sb.append(line).append("\n"));
                        }
                    } catch(IOException ioex){
                        configurationCreationException.set(new MojoExecutionException("Could not read from default configuration file.", ioex));
                        return;
                    }
                    configurationContent.set(sb.toString());
                } else {
                    System.out.println(String.format("Using inline configuration for launcher: '%s'", nativeLauncher.getFilename()));
                    // sanitize ugly line-spacings when being specified via xml
                    String[] xmlConfigurationLines = xmlConfiguration.split("(\r\n)|(\r)|(\n)");
                    List<String> sanitizedLines = Arrays.asList(xmlConfigurationLines)
                            .stream()
                            .map(line -> line.trim())
                            .collect(Collectors.toList());
                    configurationContent.set(String.join("\n", sanitizedLines));
                }
            }

            System.out.println("Working with TEMPLATE:\n" + configurationContent.get());
        });

        // TODO copy JRE/runtime
        Path runtimeTargetFolder = outputBaseFolder.toPath().resolve("windows-x64").resolve("runtime");
        try{
            Files.createDirectories(runtimeTargetFolder);
            internalUtils.copyRecursive(new File(jrePath).toPath(), runtimeTargetFolder);
        } catch(IOException ex){
            Logger.getLogger(NativeAppBundlerUsingResourcesFromOracleJDK.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public boolean checkRequirements(SharedInternalTools internalUtils, String jdkPath, String jrePath) {
        Path javaBinary = new File(jrePath).toPath().resolve("bin").resolve("java.exe");
        boolean bitCheckOfLauncherMatching = internalUtils.isWindowsExecutable64bit(javaBinary);
        return bitCheckOfLauncherMatching == true;
    }

    @Override
    public boolean creatableOnBuildsystem(OS os) {
        return os == OS.WINDOWS;
    }

    @Override
    public String getBundlerIdentifier() {
        return "oracle-native-launcher";
    }

    @Override
    public OS getDistributionTargetOS() {
        return OS.WINDOWS;
    }

    @Override
    public void printHelp() {
        return;
    }

}
