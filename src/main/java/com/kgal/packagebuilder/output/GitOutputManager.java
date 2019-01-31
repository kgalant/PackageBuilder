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

import com.kgal.packagebuilder.PackageBuilderCommandLine;
import com.kgal.packagebuilder.inventory.InventoryItem;
import com.salesforce.migrationtoolutils.Utils;

/**
 * @author swissel
 *
 */
public class GitOutputManager {

    private final Map<String, String> parameters;
    private final File              gitPath;
    private final File              sourceDirPath;

    public GitOutputManager(final Map<String, String> parameters) {
        this.parameters = parameters;
        this.gitPath = new File(this.getParam(PackageBuilderCommandLine.BASEDIRECTORY_LONGNAME, "."));
        this.sourceDirPath = new File(this.getParam(PackageBuilderCommandLine.METADATATARGETDIR_LONGNAME, "src"));

    }

    public void commitToGit(final HashMap<String, ArrayList<InventoryItem>>[] actualInventory)
            throws IOException, NoFilepatternException, GitAPIException {

        final Git git = Git.open(gitPath.getAbsoluteFile());
        Map<String, InventoryItem> inventoryLookup = this.flattenInventoryMap(actualInventory);

        if (!this.sourceDirPath.isDirectory()) {
            throw new IOException("MetaData source isn't a directory:" + sourceDirPath.getAbsolutePath());
        }

        // Group entries by who has changed them and add them to a list
        Map<String, Collection<InventoryItem>> itemsByContributor = this.getFilesByContributor(this.sourceDirPath,
                inventoryLookup, new HashMap<String, Collection<InventoryItem>>());

        itemsByContributor.entrySet().forEach(entry -> {
            String user = entry.getKey();
            PersonIdent author = this.getIdenity(user);
            Collection<InventoryItem> allFiles = entry.getValue();
            System.out.print(author.getName());
            System.out.print(": ");
            System.out.println(allFiles.size());
            allFiles.forEach(inv -> {
                String pattern = this.sourceDirPath.getName()+"/"+inv.localFileName;
                try {
                    git.add().addFilepattern(pattern).call();
                } catch (GitAPIException e) {
                    e.printStackTrace();
                }
            });
            
            String commitMessage = "Changes by " + author.getName();
            System.out.println("Committing "+commitMessage);
            try {
                git.commit().setMessage(commitMessage).setAuthor(author).call();
            } catch (GitAPIException e) {
                e.printStackTrace();
            }
        });

    }

    private PersonIdent getIdenity(String user) {
        String[] identParts = user.split("\\|");
        String userName = identParts[0].equals("null") ? "John Doe" : identParts[0];
        String eMail = identParts[1].contains("@") ? identParts[1] : "john.doe@noreply.com";
        return new PersonIdent(userName, eMail);
    }

    private Map<String, Collection<InventoryItem>> getFilesByContributor(File curFile,
            Map<String, InventoryItem> inventoryLookup,
            HashMap<String, Collection<InventoryItem>> itemsByContributor) {
        if (curFile.isDirectory()) {
            for (File f : curFile.listFiles()) {
                this.getFilesByContributor(f, inventoryLookup, itemsByContributor);
            }
        } else {
            // Process the actual file - if it can be found in the list
            String localfile = curFile.getAbsolutePath().substring(this.sourceDirPath.getAbsolutePath().length()+1);
            String key = localfile.substring(0, localfile.lastIndexOf("."));
            if (inventoryLookup.containsKey(key)) {
                InventoryItem ii = inventoryLookup.get(key);
                // TODO: Fix filename to be relative ?
                ii.localFileName = localfile;
                this.addInventoryToUser(itemsByContributor, ii);
            }

        }
        return itemsByContributor;
    }

    /**
     * Adds a inventory item to a collection by last updater
     * 
     * @param itemsByContributor
     * @param ii
     */
    private void addInventoryToUser(HashMap<String, Collection<InventoryItem>> itemsByContributor, InventoryItem ii) {
        String userKey = String.valueOf(ii.lastModifiedByUsername) + "|" + String.valueOf(ii.lastModifiedByEmail);
        Collection<InventoryItem> curColl = itemsByContributor.containsKey(userKey)
                ? itemsByContributor.get(userKey)
                : new ArrayList<>();
        curColl.add(ii);
        itemsByContributor.put(userKey, curColl);

    }

    private String getParam(final String paramName, final String defaultValue) {
        return (this.parameters.containsKey(paramName) && this.parameters.get(paramName) != null)
                ? this.parameters.get(paramName)
                : defaultValue;
    }

    /**
     * Flatten the array - map - list of inventory items into one flat list with
     * the file name as key
     * 
     * @param actualInventory
     *            Array of Maps
     * @return flat map
     */
    private Map<String, InventoryItem> flattenInventoryMap(
            final HashMap<String, ArrayList<InventoryItem>>[] actualInventory) {
        Map<String, InventoryItem> result = new HashMap<>();
        for (int i = 0; i < actualInventory.length; i++) {
            HashMap<String, ArrayList<InventoryItem>> curInv = actualInventory[i];
            curInv.entrySet().forEach(item -> {
                try {
                    String dirPrefix = Utils.getDirForMetadataType(item.getKey());
                    item.getValue().forEach(inv -> {
                        String newKey = dirPrefix + "/" + inv.itemName;
                        result.put(newKey, inv);
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
        return result;
    }

}
