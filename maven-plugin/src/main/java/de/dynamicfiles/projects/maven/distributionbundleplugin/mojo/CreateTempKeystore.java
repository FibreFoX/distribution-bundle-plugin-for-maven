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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * While developing it sometimes is required to have a valid keystore.
 *
 * @author Danny Althoff
 */
@Mojo(name = "temp-keystore", requiresDependencyResolution = ResolutionScope.NONE)
public class CreateTempKeystore extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * Enable to see some status messages.
     */
    @Parameter(defaultValue = "false")
    private boolean verbose;

    /**
     * In case you have a special JDK to be used for creating, please specify this here. Expects some extracted/installed JDK.
     */
    @Parameter(defaultValue = "${java.home}")
    private String jdkPath;

    /**
     * Specify the target location of the generated keystore.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/distbundle/java-app/keystore.jks")
    private File keystore;

    /**
     * To protect from accidentally overwriting some existing keystore, overwriting has to be enabled.
     */
    @Parameter(defaultValue = "false")
    private boolean overwriteKeystore;

    /**
     * For creating a keystore the tool "keytool" of the used JDK gets used. Here you have to specify the used parameters for the
     * keystore creation process. As the position of the jks-file might vary, just use "{KEYSTORE}" as placeholder, which gets replaced by the filename being created.
     *
     * For further details about the available parameters, please consult the keytool documentation.
     *
     * Example-configuration:
     * <code>
     * &lt;createParameters&gt;
     *     &lt;parameter&gt;-genkeypair&lt;/parameter&gt;
     *     &lt;parameter&gt;-keystore&lt;/parameter&gt;
     *     &lt;parameter&gt;{KEYSTORE}&lt;/parameter&gt;
     *     &lt;parameter&gt;-alias&lt;/parameter&gt;
     *     &lt;parameter&gt;myalias&lt;/parameter&gt;
     *     &lt;parameter&gt;-storepass&lt;/parameter&gt;
     *     &lt;parameter&gt;changeit&lt;/parameter&gt;
     *     &lt;parameter&gt;-keypass&lt;/parameter&gt;
     *     &lt;parameter&gt;changeit&lt;/parameter&gt;
     *     &lt;parameter&gt;-dname&lt;/parameter&gt;
     *     &lt;parameter&gt;cn=YourCompany, ou=none, o=YourOrg, st=YourState, c=YourCountry&lt;/parameter&gt;
     *     &lt;parameter&gt;-sigalg&lt;/parameter&gt;
     *     &lt;parameter&gt;SHA256withRSA&lt;/parameter&gt;
     *     &lt;parameter&gt;-validity&lt;/parameter&gt;
     *     &lt;parameter&gt;100&lt;/parameter&gt;
     *     &lt;parameter&gt;-keyalg&lt;/parameter&gt;
     *     &lt;parameter&gt;RSA&lt;/parameter&gt;
     *     &lt;parameter&gt;-keysize&lt;/parameter&gt;
     *     &lt;parameter&gt;4096&lt;/parameter&gt;
     * &lt;/createParameters&gt;
     * </code>
     *
     * Note: When having verbose set to "true", the parameter "-v" gets added to this parameter-list too.
     *
     */
    @Parameter
    private List<String> createParameters;

    private final InternalUtils internalUtils = new InternalUtils();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if( keystore != null && keystore.exists() && !overwriteKeystore ){
            throw new MojoFailureException("Keystore is already existing, but plugin-configuration is not allowing to overwrite.");
        }
        if( keystore != null && keystore.exists() && overwriteKeystore ){
            if( verbose ){
                getLog().info("Overwriting keystore at: " + keystore.getAbsolutePath());
            }
        }

        if( createParameters == null || createParameters.isEmpty() ){
            throw new MojoFailureException("Missing configuration of 'createParameters'-parameter. Please check your plugin-configuration.");
        }

        // find keytool
        Path jdkLocationPath = new File(jdkPath).toPath();
        // speculation: in case of older JDKs, the "java.home" property is the JRE inside the JDK, so if we can detect the "java"-binary inside
        // parent folder-structure, we are using JDK prior JDK 9
        // otherwise we are using that folder and are looking for keytool inside the "bin"-folder, which is valid for normal JDK 9+ and when
        // having customized jdkHome being set manually inside plugin-configuration
        String platformExecutableFileExtension = internalUtils.isPlatformWindows() ? ".exe" : "";
        boolean isJreInsideJdk = Files.exists(jdkLocationPath.getParent().resolve("bin").resolve("java" + platformExecutableFileExtension), LinkOption.NOFOLLOW_LINKS);
        AtomicReference<String> pathToKeytool = new AtomicReference<>();
        if( isJreInsideJdk ){
            // look inside parent folder
            Path jarSignerPath = jdkLocationPath.getParent().resolve("bin").resolve("keytool" + platformExecutableFileExtension).toAbsolutePath();
            if( Files.exists(jarSignerPath, LinkOption.NOFOLLOW_LINKS) ){
                pathToKeytool.set(jarSignerPath.toString());
            }
        } else {
            // look inside given folder, should be the default-case since JDK 9+
            Path keytoolPath = jdkLocationPath.resolve("bin").resolve("keytool" + platformExecutableFileExtension).toAbsolutePath();
            if( Files.exists(keytoolPath, LinkOption.NOFOLLOW_LINKS) ){
                pathToKeytool.set(keytoolPath.toString());
            }
        }

        if( pathToKeytool.get() == null ){
            throw new MojoFailureException("Could not find keytool-executable, please check your JDK-installation of the configured JDK for this plugin-configuration.");
        }

        List<String> createCommand = new ArrayList<>();

        createCommand.add(pathToKeytool.get());

        if( verbose ){
            boolean alreadyContainsVerboseFlag = createParameters.stream().filter(parameter -> parameter.trim().equalsIgnoreCase("-v")).count() > 0;
            if( !alreadyContainsVerboseFlag ){
                createCommand.add("-v");
            }
        }

        createCommand.addAll(createParameters);

        // replace {KEYTOOL}-template with real filename
        List<String> createCommandToUse = createCommand.stream().map(creatingParameter -> {
            if( "{KEYTOOL}".equalsIgnoreCase(creatingParameter) ){
                return keystore.getAbsolutePath();
            }
            return creatingParameter;
        }).collect(Collectors.toList());

        try{
            ProcessBuilder pb = new ProcessBuilder()
                    .inheritIO()
                    .directory(project.getBasedir())
                    .command(createCommandToUse);

            if( verbose ){
                getLog().info("Running command: " + String.join(" ", createCommandToUse));
            }

            Process p = pb.start();
            p.waitFor();
            if( p.exitValue() != 0 ){
                throw new MojoExecutionException("Creating temporary keystore wasn't successful! Please check build-log.");
            }
        } catch(IOException | InterruptedException ex){
            throw new MojoExecutionException("There was an exception while signing jar-file: " + keystore.getAbsolutePath(), ex);
        }
    }

}
