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
package com.kgal.packagebuilder.output;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.PersonIdent;

import com.kgal.packagebuilder.inventory.InventoryItem;

/**
 * @author swissel
 *
 */
public class GitOutputManager {
    
    private final Map<String, String> parameters;

    public GitOutputManager(final Map<String, String> parameters) {
        this.parameters = parameters;
    }
    
    public void commitToGit(final HashMap<String, ArrayList<InventoryItem>>[] actualInventory)
            throws IOException, NoFilepatternException, GitAPIException {
        // TODO: Read the correct repository path
       final Git git = Git.open(new File("."));

//        for (final String key : actualInventory.keySet()) {
//            PersonIdent author = null;
//            String commitMessage = null;
//            final Collection<InventoryItem> curSet = actualInventory.get(key);
//            for (final InventoryItem curItem : curSet) {
//                // TODO: check if local file name works
//                git.add().addFilepattern(curItem.localFileName).call();
//                if (author == null) {
//                    author = new PersonIdent(curItem.lastModifiedByUsername, curItem.lastModifiedByEmail);
//                }
//                if (commitMessage == null) {
//                    commitMessage = "Change by " + curItem.lastModifiedByEmail + " [AutoRetrieve]";
//                }
//            }
//
//            git.commit().setMessage(commitMessage).setAuthor(author).call();
//
//        }

    }

}
