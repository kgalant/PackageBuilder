/** ========================================================================= *
 * Copyright (C)  2017, 2019 Salesforce Inc ( http://www.salesforce.com/      *
 *                            All rights reserved.                            *
 *                                                                            *
 *  @author     Stephan H. Wissel (stw) <swissel@salesforce.com>              *
 *                                       @notessensei                         *
 * @version     1.0                                                           *
 * ========================================================================== *
 *                                                                            *
 * Licensed under the  Apache License, Version 2.0  (the "License").  You may *
 * not use this file except in compliance with the License.  You may obtain a *
 * copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>.       *
 *                                                                            *
 * Unless  required  by applicable  law or  agreed  to  in writing,  software *
 * distributed under the License is distributed on an  "AS IS" BASIS, WITHOUT *
 * WARRANTIES OR  CONDITIONS OF ANY KIND, either express or implied.  See the *
 * License for the  specific language  governing permissions  and limitations *
 * under the License.                                                         *
 *                                                                            *
 * ========================================================================== *
 */
package com.kgal.packagebuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

/**
 * Extracts a given zip file to a target and adjusts the file date to value
 * given in a map
 *
 * @author swissel
 *
 */
public class ZipAndFileFixer {

    /**
     * @param args
     * @throws IOException
     */
    public static void main(final String[] args) throws IOException {
        final File test = new File("/Users/swissel/code/daimler/sit/package.1.zip");
        final ZipAndFileFixer zff = new ZipAndFileFixer(test, null);
        zff.extractAndAdjust("/Users/swissel/code/daimler/sit/test/");

    }

    private final File                  zip;
    private final Map<String, Calendar> fileDates;
    private int filesFixedCount = 0;


    public ZipAndFileFixer(final File zip, final Map<String, Calendar> fileDates) {
        this.zip = zip;
        this.fileDates = fileDates;
    }

    public int extractAndAdjust(final String targetDirName) throws IOException {
        final File targetDir = new File((targetDirName == null) ? "." : targetDirName);
        Files.createParentDirs(targetDir);
        if (!targetDir.exists()) {
            targetDir.mkdir();
        }
        if (!targetDir.isDirectory()) {
            throw new IOException("Target dir is not a directory:" + targetDirName);
        }
        final ZipInputStream zis = new ZipInputStream(new FileInputStream(this.zip));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            final File outFile = this.newFile(targetDir, zipEntry);
            if (!"package.xml".equalsIgnoreCase(outFile.getName())) {
                final FileOutputStream out = new FileOutputStream(outFile);
                ByteStreams.copy(zis, out);
                out.close();
                this.fixFileDate(targetDir, outFile.getAbsoluteFile());

            }
            zipEntry = zis.getNextEntry();
        }

        zis.close();
        return this.filesFixedCount;
    }

    private void fixFileDate(final File targetDir, final File outFile) {
        final String key = this.nameWithPathWithOutExtension(targetDir, outFile);
        if (outFile.exists() && (this.fileDates != null) && this.fileDates.containsKey(key)) {
            try {
                final Calendar newDate = this.fileDates.get(key);
                long theTime = newDate.getTimeInMillis();
                if (!outFile.setLastModified(theTime)) {
                    System.err.println("Couldn't update file date of "+outFile.getAbsolutePath());
                }
                this.filesFixedCount++;
            } catch (final Exception e) {
                e.printStackTrace();
            }
        } else if (this.fileDates != null) {
            // Quite a lot - not worth it
           // System.err.println("No file date found for "+key);
        }

    }

    /**
     * Extract the file name including the relative path (below target)
     * minus the extension to assign the right user
     */
    private String nameWithPathWithOutExtension(final File rootDir, final File rawFile) {
        final String fullString = rawFile.getAbsolutePath();
        final String rootString = rootDir.getAbsolutePath();
        final String rawName = fullString.substring(rootString.length()+1).toLowerCase();
        final String candidate = rawName.endsWith("-meta.xml") ? rawName.substring(0,rawName.length()-9) : rawName;       
        final String candidate2 = candidate.contains(".") ? candidate.substring(0, candidate.lastIndexOf(".")) : candidate;
        return (candidate2.startsWith("aura") || candidate2.startsWith("lwc")) ? candidate2.substring(0,candidate2.lastIndexOf("/")) : candidate2;
    }

    private File newFile(final File destinationDir, final ZipEntry zipEntry) throws IOException {
        final File destFile = new File(destinationDir, zipEntry.getName());

        final String destDirPath = destinationDir.getCanonicalPath();
        final String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }
        Files.createParentDirs(destFile);
        return destFile;
    }

}