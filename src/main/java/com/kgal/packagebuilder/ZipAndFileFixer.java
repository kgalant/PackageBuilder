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


    public ZipAndFileFixer(final File zip, final Map<String, Calendar> fileDates) {
        this.zip = zip;
        this.fileDates = fileDates;
    }

    public void extractAndAdjust(final String targetDirName) throws IOException {
        final File targetDir = new File((targetDirName == null) ? "." : targetDirName);
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
                this.fixFileDate(outFile);

            }
            zipEntry = zis.getNextEntry();
        }

        zis.close();
    }

    private void fixFileDate(final File outFile) {
        final String key = outFile.getName().toLowerCase();
        if (outFile.exists() && (this.fileDates != null) && this.fileDates.containsKey(key)) {
            try {
                final Calendar newDate = this.fileDates.get(key);
                outFile.setLastModified(newDate.getTimeInMillis());
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }

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
