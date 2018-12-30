/** ========================================================================= *
 * Copyright (C)  2017, 2018 Salesforce Inc ( http://www.salesforce.com/      *
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
package com.kgal.packagebuilder.output;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import com.google.common.io.Closeables;
import com.google.common.io.Files;

/**
 * @author swissel
 *
 */
public class MetaDataOutput extends OutputStream {

    private static final int            OUT_SIZE  = 102400;
    private final ByteArrayOutputStream out;
    private final String                location;
    private boolean                     fileSaved = false;

    public MetaDataOutput(final String location) {
        this.location = location;
        this.out = new ByteArrayOutputStream(MetaDataOutput.OUT_SIZE);
    }

    @Override
    public void close() throws IOException {
        this.out.close();
        this.save();
    }

    @Override
    public void flush() throws IOException {
        this.out.flush();
    }

    public boolean isFileSaved() {
        return this.fileSaved;
    }

    @Override
    public void write(final byte[] b) throws IOException {
        this.out.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        this.out.write(b, off, len);
    }

    @Override
    public void write(final int b) throws IOException {
        this.out.write(b);

    }

    /**
     * Saves the output stream if it has been modified
     *
     * @return true if it has been saved - false if not
     */
    private boolean save() {
        final File targetFile = new File(this.location);
        if (targetFile.isDirectory()) {
            System.err.println("Directory encountered!" + this.location);
            return false;
        }

        boolean saveThis = true;

        if (targetFile.exists()) {
            byte[] existingByte;
            try {
                existingByte = Files.asByteSource(targetFile).read();
                saveThis = !Arrays.equals(existingByte, this.out.toByteArray());
            } catch (final IOException e) {
                saveThis = true;
            }

            if (saveThis) {
                targetFile.delete();
            }

        } else {
            saveThis = true;
        }

        if (saveThis) {
            OutputStream finalOut = null;

            try {
                // Ensure the directory structure exists
                Files.createParentDirs(targetFile);
                finalOut = new FileOutputStream(targetFile);
                finalOut.write(this.out.toByteArray());
                finalOut.flush();
                Closeables.close(finalOut, true);
            } catch (final FileNotFoundException e) {
                e.printStackTrace();
            } catch (final IOException e) {
                e.printStackTrace();
            }
            System.out.println("\n+" + targetFile);
        }

        this.fileSaved = saveThis;
        return saveThis;
    }

}
