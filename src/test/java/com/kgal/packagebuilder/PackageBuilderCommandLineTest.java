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
package com.kgal.packagebuilder;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

import com.kgal.packagebuilder.PackageBuilderCommandLine;

/**
 * @author swissel
 *
 */
public class PackageBuilderCommandLineTest {

    @Test
    public final void testParametersFromFile() {
        String workingDir = System.getProperty("user.dir");
        System.out.println("Current working directory : " + workingDir);
        PackageBuilderCommandLine pbc = new PackageBuilderCommandLine();
        String[] args = new String[1];
        args[0] = "-o properties/test.properties";
        pbc.parseCommandLine(args);
        Map<String,String> result = pbc.getParameters();
        assertTrue(result.containsKey(PackageBuilderCommandLine.SKIPPATTERNS_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.PASSWORD_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.BASEDIRECTORY_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.DESTINATION_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.SERVERURL_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.METADATAITEMS_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.INCLUDECHANGEDATA_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.APIVERSION_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.USERNAME_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.VERBOSE_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.FROMDATE_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.TODATE_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.DOWNLOAD_LONGNAME));
        assertTrue(result.containsKey(PackageBuilderCommandLine.METADATATARGETDIR_LONGNAME));
    }

    @Test
    public final void testMissingParamsAlert() {
        PackageBuilderCommandLine pbc = new PackageBuilderCommandLine();
        String[] args = new String[1];
        args[0] = "";
        boolean result = pbc.parseCommandLine(args);
        assertTrue(!result);        
    }
    
    @Test
    public final void testGitParam() {
        PackageBuilderCommandLine pbc = new PackageBuilderCommandLine();
        String[] args = new String[2];
        args[0] = "-o properties/test.properties";
        args[1] = "-g";
        pbc.parseCommandLine(args);
        Map<String,String> result = pbc.getParameters();
        assertEquals("true", result.get(PackageBuilderCommandLine.INCLUDECHANGEDATA_LONGNAME));
        assertEquals("true", result.get(PackageBuilderCommandLine.DOWNLOAD_LONGNAME));
        assertEquals("true", result.get(PackageBuilderCommandLine.GITCOMMIT_LONGNAME));        
    }
    
}
