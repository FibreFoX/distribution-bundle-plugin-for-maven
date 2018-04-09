package de.dynamicfiles.projects.maven.distributionbundleplugin.mojo;

import java.io.File;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author FibreFoX
 */
@Mojo(name = "native-installer")
public class CreateNativeAppInstaller extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/bundles/native-app")
    private File sourceFolder;

    /**
     * As the used bundler for creating the native app is not known by this MOJO, the created subfolder must be set here.
     */
    @Parameter(required = true)
    private String sourceSubFolder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
