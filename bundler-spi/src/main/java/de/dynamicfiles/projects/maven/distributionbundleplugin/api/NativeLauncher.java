package de.dynamicfiles.projects.maven.distributionbundleplugin.api;

/**
 *
 * @author FibreFoX
 */
public class NativeLauncher {

    /**
     * The filename of the native launcher file (without file extension).
     */
    private String filename;

    /**
     * The file extension of the native launcher file (e.g. ".exe", ".scr", ...).
     */
    private String extension;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

}
