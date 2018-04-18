package de.dynamicfiles.projects.maven.distributionbundleplugin.api;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 *
 * @author FibreFoX
 */
public class NativeAppOptions {

    private boolean verbose;

    private File sourceFolder;

    private File outputBaseFolder;

    private File tempWorkfolder;

    private String jdkPath;

    private boolean withJRE;

    private String jrePath;

    private List<NativeLauncher> nativeLaunchers;

    private Map<String, String> internalParameters;

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public File getSourceFolder() {
        return sourceFolder;
    }

    public void setSourceFolder(File sourceFolder) {
        this.sourceFolder = sourceFolder;
    }

    public File getOutputBaseFolder() {
        return outputBaseFolder;
    }

    public void setOutputBaseFolder(File outputBaseFolder) {
        this.outputBaseFolder = outputBaseFolder;
    }

    public File getTempWorkfolder() {
        return tempWorkfolder;
    }

    public void setTempWorkfolder(File tempWorkfolder) {
        this.tempWorkfolder = tempWorkfolder;
    }

    public String getJdkPath() {
        return jdkPath;
    }

    public void setJdkPath(String jdkPath) {
        this.jdkPath = jdkPath;
    }

    public boolean isWithJRE() {
        return withJRE;
    }

    public void setWithJRE(boolean withJRE) {
        this.withJRE = withJRE;
    }

    public String getJrePath() {
        return jrePath;
    }

    public void setJrePath(String jrePath) {
        this.jrePath = jrePath;
    }

    public List<NativeLauncher> getNativeLaunchers() {
        return nativeLaunchers;
    }

    public void setNativeLaunchers(List<NativeLauncher> nativeLaunchers) {
        this.nativeLaunchers = nativeLaunchers;
    }

    public Map<String, String> getInternalParameters() {
        return internalParameters;
    }

    public void setInternalParameters(Map<String, String> internalParameters) {
        this.internalParameters = internalParameters;
    }

}
