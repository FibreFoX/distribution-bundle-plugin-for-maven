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
package de.dynamicfiles.projects.maven.distributionbundleplugin;

import de.dynamicfiles.projects.maven.distributionbundleplugin.api.SharedInternalTools;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Some methods which are used internally.
 *
 * @author FibreFoX
 */
public class InternalUtils implements SharedInternalTools {

    @Override
    public boolean copyRecursive(Path sourceFolder, Path targetFolder) throws IOException {
        AtomicBoolean failed = new AtomicBoolean(false);
        Files.walkFileTree(sourceFolder, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path subfolder, BasicFileAttributes attrs) throws IOException {
                // do create subfolder (if needed)
                Files.createDirectories(targetFolder.resolve(sourceFolder.relativize(subfolder)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
                // do copy, and replace, as the resource might already be existing
                Files.copy(sourceFile, targetFolder.resolve(sourceFolder.relativize(sourceFile)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path source, IOException ioe) throws IOException {
                // don't fail, just inform user
                failed.set(true);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path source, IOException ioe) throws IOException {
                // nothing to do here
                if( ioe != null ){
                    failed.set(true);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return failed.get();
    }

    @Override
    public boolean deleteRecursive(Path sourceFolder) throws IOException {
        AtomicBoolean failed = new AtomicBoolean(false);
        Files.walkFileTree(sourceFolder, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path subfolder, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(sourceFile);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path source, IOException ioe) throws IOException {
                failed.set(true);
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path source, IOException ioe) throws IOException {
                if( ioe != null ){
                    failed.set(true);
                    return FileVisitResult.TERMINATE;
                }
                Files.deleteIfExists(source);
                return FileVisitResult.CONTINUE;
            }
        });
        return failed.get();
    }

    @Override
    public void pack(final Path folder, final Path zipFilePath) throws IOException {
        // source of inspiration: http://stackoverflow.com/a/35158142/1961102
        try(FileOutputStream fos = new FileOutputStream(zipFilePath.toFile()); ZipOutputStream zos = new ZipOutputStream(fos)){
            Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // sanitize windows path parts, because ZIP only allows normal slash
                    zos.putNextEntry(new ZipEntry(folder.relativize(file).toString().replace("\\", "/")));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Override
    public boolean isClassInsideJarFile(String classname, String locationPrefix, File jarFile) {
        String requestedJarEntryName = locationPrefix + classname.replace(".", "/") + ".class";
        try{
            JarFile jarFileToSearchIn = new JarFile(jarFile, false, JarFile.OPEN_READ);
            return jarFileToSearchIn.stream().parallel().filter(jarEntry -> {
                return jarEntry.getName().equals(requestedJarEntryName);
            }).findAny().isPresent();
        } catch(IOException ex){
            // NO-OP
        }
        return false;
    }

    @Override
    public boolean isPlatformWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    @Override
    public boolean isPlatformLinux() {
        return System.getProperty("os.name").toLowerCase().contains("nix") || System.getProperty("os.name").toLowerCase().contains("nux");
    }

    @Override
    public boolean isPlatformMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    @Override
    public boolean isLinuxExecutable64bit(Path linuxBinary) {
        if( !(Files.isRegularFile(linuxBinary) || Files.isSymbolicLink(linuxBinary)) || !Files.isReadable(linuxBinary) ){
            return false;
        }

        try(FileChannel openFileChannel = FileChannel.open(linuxBinary, StandardOpenOption.READ)){
            MappedByteBuffer startMarker = openFileChannel.map(FileChannel.MapMode.READ_ONLY, 0, 4);
            startMarker.load();
            if( startMarker.get(0) == 0x7F && startMarker.get(1) == 'E' && startMarker.get(2) == 'L' && startMarker.get(3) == 'F' ){
                // e_machine inside Elf*_Ehdr
                MappedByteBuffer architectureBytes = openFileChannel.map(FileChannel.MapMode.READ_ONLY, 18, 2);
                architectureBytes.load();
                short architectureShort = architectureBytes.getShort();
                // https://stackoverflow.com/a/7932774/1961102
                int architectureAsInt = architectureShort >= 0 ? architectureShort : 0x10000 + architectureShort;

                // http://www.sco.com/developers/gabi/1998-04-29/ch4.eheader.html
                // https://en.wikipedia.org/wiki/Executable_and_Linkable_Format#File_header
                // 32 bit
                if( architectureAsInt == 0x03 ){
                    return false;
                }

                // 64 bit (x86-64)
                if( architectureAsInt == 0x3E ){
                    return true;
                }
            }

        } catch(IOException ex){
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    public boolean isWindowsExecutable64bit(Path windowsBinary) {
        if( !(Files.isRegularFile(windowsBinary) || Files.isSymbolicLink(windowsBinary)) || !Files.isReadable(windowsBinary) ){
            return false;
        }

        // try to find PE-header offset inside MS-DOS header and check architecture
        try(FileChannel openFileChannel = FileChannel.open(windowsBinary, StandardOpenOption.READ)){
            MappedByteBuffer startMarker = openFileChannel.map(FileChannel.MapMode.READ_ONLY, 0, 2);
            startMarker.load();
            if( startMarker.get(0) == 'M' && startMarker.get(1) == 'Z' ){
                // found MS-DOS header magic bytes

                // MS-DOS header contains 4 byte PE-offset at 0x60 (e_lfanew in MZ structure)
                MappedByteBuffer offsetMarker = openFileChannel.map(FileChannel.MapMode.READ_ONLY, 60, 4);
                offsetMarker.load();
                offsetMarker.order(ByteOrder.LITTLE_ENDIAN);
                int foundOffset = offsetMarker.getInt();
                // make it positive in case of huge offset-gap 
                foundOffset = foundOffset >= 0 ? foundOffset : 0x10000 + foundOffset;
                // TODO put into log on verbose
//                System.out.println("Found PE-Header offset: " + foundOffset);

                if( foundOffset > 0 ){
                    // found PE header offset, reading the next 6 bytes should be enough to verify
                    MappedByteBuffer peHeader = openFileChannel.map(FileChannel.MapMode.READ_ONLY, foundOffset, 6);
                    peHeader.load();
                    peHeader.order(ByteOrder.LITTLE_ENDIAN);
                    if( peHeader.get(0) == 'P' && peHeader.get(1) == 'E' && peHeader.get(2) == '\0' && peHeader.get(3) == '\0' ){
                        // TODO put into log on verbose
                        // found legal PE header, now reading architecture (first bytes from COFF header)
                        short architectureShort = peHeader.getShort(4);
                        // https://stackoverflow.com/a/7932774/1961102
                        int architectureAsInt = architectureShort >= 0 ? architectureShort : 0x10000 + architectureShort;

                        // https://msdn.microsoft.com/library/windows/desktop/ms680547(v=vs.85).aspx?id=19509#machine_types
                        // 32bit
                        if( architectureAsInt == 0x014c ){
                            return false;
                        }
                        // 64bit
                        if( architectureAsInt == 0x8664 ){
                            return true;
                        }
                        // TODO put into log on verbose: different/unsupported architecture
                    }
                }
            }
        } catch(IOException ex){
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        }

        return false;
    }
}
