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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.PersonIdent;

import com.kgal.packagebuilder.PackageBuilder;
import com.kgal.packagebuilder.PackageBuilderCommandLine;
import com.kgal.packagebuilder.inventory.GitInventoryItem;
import com.kgal.packagebuilder.inventory.InventoryItem;

/**
 * @author swissel
 *
 */
public class GitOutputManager {

	private final Map<String, String> 	parameters;
	private final File                	gitPath;
	private final File                	sourceDirPath;
	private final Logger 				logger;
	private Status						status;
	
	private static String				sourceFolder = "src";

	public GitOutputManager(final Map<String, String> parameters, Logger l) {
		this.parameters = parameters;
		//        this.gitPath = new File(this.getParam(PackageBuilderCommandLine.METADATATARGETDIR_LONGNAME, "src"));
		this.gitPath = findGitRoot(this.getParam(PackageBuilderCommandLine.METADATATARGETDIR_LONGNAME, sourceFolder));
		this.sourceDirPath = new File(this.getParam(PackageBuilderCommandLine.METADATATARGETDIR_LONGNAME, sourceFolder));
		logger = l;
	}

	public void commitToGit(final HashMap<String, InventoryItem> inventoryLookup)
			throws IOException, NoFilepatternException, GitAPIException {

		final Git git = Git.open(gitPath.getAbsoluteFile());

		if (!this.sourceDirPath.isDirectory()) {
			throw new IOException("MetaData source isn't a directory:" + sourceDirPath.getCanonicalPath());
		}

		// Group entries by who has changed them and add them to a list
		Map<String, Collection<GitInventoryItem>> itemsByContributor = this.getFilesByContributor(this.sourceDirPath.getPath(), this.sourceDirPath,
				inventoryLookup, new HashMap<String, Collection<GitInventoryItem>>());

		final Collection<String> filesOfInterest = this.getFilesToCommit(git);

		for (String entry : itemsByContributor.keySet()) {
			final Collection<String> actualToBeCommitted = new ArrayList<>();
			String user = entry;//entry.getKey();
			PersonIdent author = this.getIdentity(user);
			Collection<GitInventoryItem> allFiles = itemsByContributor.get(entry); //entry.getValue();
			logger.log(Level.FINE, author.getName() + ": " + allFiles.size());
			for (final GitInventoryItem gii : allFiles) {
				File file = gii.file;
				String pattern = file.getCanonicalPath().replace(sourceDirPath.getCanonicalPath(), this.sourceDirPath.getName());
				if (filesOfInterest.contains(pattern.toLowerCase())) {
					try {
						git.add().addFilepattern(pattern).call();
					} catch (GitAPIException e) {
						e.printStackTrace();
					}
					actualToBeCommitted.add(pattern);
				}
			}

			if (!actualToBeCommitted.isEmpty()) {
				String commitMessage = "Changes by " + author.getName();
				logger.log(Level.INFO, "Committing " + commitMessage);
				try {
					git.commit().setMessage(commitMessage).setAuthor(author).call();
				} catch (GitAPIException e) {
					e.printStackTrace();
				}
			}
		}
		
		// now do files that have been removed
		// git.status = missing
		
		try {
			
			final Collection<String> actualToBeCommitted = new ArrayList<>();
			
			Set<String> deletes = new HashSet<>();

			deletes.addAll(status.getMissing());
			deletes.addAll(status.getRemoved());
			
			String baseDirectory = this.getParam(PackageBuilderCommandLine.METADATATARGETDIR_LONGNAME,"").replace(sourceFolder, "");
			
			for (final String filename : deletes) {
				File file = new File(baseDirectory + filename);
				String pattern = file.getCanonicalPath().replace(sourceDirPath.getCanonicalPath(), this.sourceDirPath.getName());
				
				try {
					git.rm().addFilepattern(pattern).call();
					actualToBeCommitted.add(pattern);
				} catch (GitAPIException e) {
					e.printStackTrace();
				}
			}
			
			if (!actualToBeCommitted.isEmpty()) {
				
				PersonIdent author = getIdentity("Not tracked|noreply@salesforce.com");
				
				String commitMessage = "Deletes from org. Origin not tracked in API.";
				logger.log(Level.INFO, "Committing " + commitMessage);
				try {
					git.commit().setMessage(commitMessage).setAuthor(author).call();
				} catch (GitAPIException e) {
					e.printStackTrace();
				}
			}
			
			

		} catch (NoWorkTreeException e) {
			e.printStackTrace();
		}

	}

	private PersonIdent getIdentity(String user) {
		String[] identParts = user.split("\\|");
		String userName = identParts[0].equals("null") ? "John Doe" : identParts[0];
		String eMail = identParts[1].contains("@") ? identParts[1] : "john.doe@noreply.com";
		return new PersonIdent(userName, eMail);
	}

	private Map<String, Collection<GitInventoryItem>> getFilesByContributor(String baseDirectory, File curFile,
			Map<String, InventoryItem> inventoryLookup,
			HashMap<String, Collection<GitInventoryItem>> itemsByContributor) throws IOException {
		if (curFile.isDirectory()) {
			for (File f : curFile.listFiles()) {
				this.getFilesByContributor(baseDirectory, f, inventoryLookup, itemsByContributor);
			}
		} else {
			// Process the actual file - if it can be found in the list
			String localfile = curFile.getAbsolutePath().substring(this.sourceDirPath.getAbsolutePath().length() + 1);
			InventoryItem ii = PackageBuilder.getInventoryItemForFile(inventoryLookup, curFile, baseDirectory);
			// TODO: Fix filename to be relative ?
			if (ii != null) {
				ii.localFileName = localfile;
				this.addInventoryToUser(itemsByContributor, ii, curFile);
				logger.log(Level.FINE, "Adding to inventory by user: " + (ii.fp != null ? ii.fp.getFileName() : ii.getFullName()));			
			} else {
				logger.log(Level.INFO, "Couldn't map file for adding to GIT: " + curFile.getCanonicalPath());
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
	private void addInventoryToUser(HashMap<String, Collection<GitInventoryItem>> itemsByContributor, InventoryItem ii, File file) {
		
		// generate a unique key for adding to GIT
		// need to handle the fact that some items may be going in that don't have a lastmodifiedby (e.g. standard objects that haven't been touched by a user)
		String userName = 	String.valueOf(ii.lastModifiedByUsername) == null || 
							String.valueOf(ii.lastModifiedByUsername).equals("") || 
							String.valueOf(ii.lastModifiedByUsername).equals("null") ? "OOTB" : String.valueOf(ii.lastModifiedByUsername);
		String userEmail = 	String.valueOf(ii.lastModifiedByEmail) == null || 
							String.valueOf(ii.lastModifiedByEmail).equals("") ||
							String.valueOf(ii.lastModifiedByEmail).equals("null") ? "standard@salesforce.com" : String.valueOf(ii.lastModifiedByEmail);
		String userKey = userName + "|" + userEmail;
		GitInventoryItem gii = new GitInventoryItem(ii, file);
		if (itemsByContributor.containsKey(userKey)) {
			itemsByContributor.get(userKey).add(gii);
		} else {
			ArrayList<GitInventoryItem> newUserItems = new ArrayList<>();
			newUserItems.add(gii);
			itemsByContributor.put(userKey, newUserItems);
		}
	}

	private String getParam(final String paramName, final String defaultValue) {
		return (this.parameters.containsKey(paramName) && this.parameters.get(paramName) != null)
				? this.parameters.get(paramName)
						: defaultValue;
	}

	private Collection<String> getFilesToCommit(Git git) {
		final Collection<String> resultCandidate = new ArrayList<>();
		try {
			status = git.status().call();

			Set<String> conflicting = status.getConflicting();
			Set<String> added = status.getAdded();
			Set<String> changed = status.getChanged();
			Set<String> missing = status.getMissing();
			Set<String> modified = status.getModified();
			Set<String> removed = status.getRemoved();
			Set<String> uncommittedChanges = status.getUncommittedChanges();
			Set<String> untracked = status.getUntracked();
			Set<String> untrackedFolders = status.getUntrackedFolders();

			resultCandidate.addAll(conflicting);
			resultCandidate.addAll(added);
			resultCandidate.addAll(changed);
			resultCandidate.addAll(missing);
			resultCandidate.addAll(modified);
			resultCandidate.addAll(removed);
			resultCandidate.addAll(uncommittedChanges);
			resultCandidate.addAll(untracked);
			resultCandidate.addAll(untrackedFolders);

			// Remove this after stuff works as expected
			for (String conflict : conflicting) {
				logger.log(Level.FINER, "Conflicting: " + conflict);
			}

			for (String add : added) {
				logger.log(Level.FINER, "Added: " + add);
			}

			for (String change : changed) {
				logger.log(Level.FINER, "Change: " + change);
			}

			for (String miss : missing) {
				logger.log(Level.FINER, "Missing: " + miss);
			}

			for (String modify : modified) {
				logger.log(Level.FINER, "Modification: " + modify);
			}

			for (String remove : removed) {
				logger.log(Level.FINER, "Removed: " + remove);
			}

			for (String uncommitted : uncommittedChanges) {
				logger.log(Level.FINER, "Uncommitted: " + uncommitted);
			}

			for (String untrack : untracked) {
				logger.log(Level.FINER, "Untracked: " + untrack);
			}

			for (String untrack : untrackedFolders) {
				logger.log(Level.FINER, "Untracked Folder: " + untrack);
			}

		} catch (NoWorkTreeException | GitAPIException e) {
			e.printStackTrace();
		}

		// We use lowercase for better matches
		final Collection<String> result = new ArrayList<>();
		resultCandidate.forEach(r -> {
			result.add(r.toLowerCase());
		});
		return result;
	}

	private File findGitRoot (String myCurrentDirectory) {
		File startingDirectory = new File(myCurrentDirectory);
		if (hasGitSubdirectory(startingDirectory)) {
			return startingDirectory;
		} else {
			File parentToCurrentDirectory = startingDirectory.getParentFile();
			if (hasGitSubdirectory(parentToCurrentDirectory)) {
				return parentToCurrentDirectory;
			}
		}
		return null;
	}

	private boolean hasGitSubdirectory (File directory) {
		if (!directory.isDirectory()) {
			return false;
		} 
		File potentialGitDirectory = new File(directory.getAbsolutePath() + File.separator + ".git");
		return potentialGitDirectory.exists() && potentialGitDirectory.isDirectory();  

	}

}