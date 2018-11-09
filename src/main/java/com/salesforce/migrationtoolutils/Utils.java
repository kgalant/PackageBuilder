package com.salesforce.migrationtoolutils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Utils {

	private static HashMap<String,String> dirToObjectMap = null;
	private static HashMap<String,String> objToDirMap = null;
	private static HashMap<String,String> objToSuffixMap = null;
	
	private static final boolean debug = "true".equalsIgnoreCase(System.getenv("debug"));
	
	public static String checkPathSlash(String p) {
		if (p != null && p.length() > 0 && !p.endsWith(File.separator))
			p += File.separator;
		return p;
	}

	public static void checkDir(String dir) {
		File f = new File(dir);
		if (!f.exists()) {
			f.mkdir();
		}
	}
	
	public static String readResource(String name) throws IOException {
		InputStream is = Utils.class.getClassLoader().getResourceAsStream(name);
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		return readFile(reader);
	}

	public static String readFile(File f) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(f));
		return readFile(reader);
	}

	public static String readFile(String path, String filename) throws IOException {
		if (!path.endsWith(File.separator))
			path += File.separator;
		BufferedReader reader = new BufferedReader(new FileReader(path + filename));
		return readFile(reader);
	}

	private static String readFile(BufferedReader reader) throws IOException {
		String line = null;
		StringBuilder stringBuilder = new StringBuilder();
		String ls = System.getProperty("line.separator");

		while ((line = reader.readLine()) != null) {
			stringBuilder.append(line);
			stringBuilder.append(ls);
		}
		reader.close();
		return stringBuilder.toString();
	}

	public static boolean writeFile(String filename, String toWrite) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(filename), "UTF8"));
			writer.write(toWrite);

		} catch (IOException e) {
		} finally {
			try {
				if (writer != null)
					writer.close();
			} catch (IOException e) {
				return false;
			}
		}

		return true;
	}

	public static boolean writeFile(String filename, ArrayList<String> toWrite) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(filename), StandardCharsets.ISO_8859_1));
			for (String s : toWrite) {
				writer.write(s);
				writer.newLine();
			}

		} catch (IOException e) {
		} finally {
			try {
				if (writer != null)
					writer.close();
			} catch (IOException e) {
				return false;
			}
		}

		return true;
	}

	public static HashMap<String, String> getSplitFileIntoMap(File f) throws IOException {
		HashMap<String, String> retVal = new HashMap<String, String>();
		String fileString = readFile(f);

		String lines[] = fileString.split("\\r?\\n");

		for (String line : lines) {
			line = line.trim();
			if (line.length() > 0) {
				// System.out.println("Line: " + line);
				String[] tokens = line.split(":");
				if (tokens.length == 2)
					retVal.put(tokens[0].trim().toLowerCase(), tokens[1].trim().length() > 0 ? tokens[1].trim() : "");
			}
		}
		return retVal;
	}

	public static void splitIntoChunksFiles(String file, String outputPath, String filenameParam) {

		String[] splits = file
				.split("\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*");

		for (String s : splits) {
			if (s.length() > 2) {
				// System.out.println("Split length:" + s.length());
				// System.out.println("Split:" + s);

				HashMap<String, String> h = new HashMap<String, String>();

				String lines[] = s.split("\\r?\\n");

				for (String line : lines) {
					line = line.trim();
					if (line.length() > 0) {
						// System.out.println("Line: " + line);
						String[] tokens = line.split(":");
						if (tokens.length == 2)
							h.put(tokens[0].trim().toLowerCase(), tokens[1].trim().length() > 0 ? tokens[1].trim() : "");
					}
				}
				Utils.writeFile(outputPath + h.get(filenameParam).substring(h.get(filenameParam).indexOf("/") < 0 ? 0 : h.get(filenameParam).indexOf("/")),
						s.trim());
			}
		}
	}

	public static HashMap<String, HashMap<String, String>> getChunks(String file, String indexName) {
		String[] splits = file
				.split("\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*");

		HashMap<String, HashMap<String, String>> h = new HashMap<String, HashMap<String, String>>();

		for (String s : splits) {
			if (s.length() > 2) {
				// System.out.println("Split length:" + s.length());
				// System.out.println("Split:" + s);

				HashMap<String, String> splitMap = new HashMap<String, String>();

				String lines[] = s.split("\\r?\\n");

				for (String line : lines) {
					line = line.trim();
					if (line.length() > 0) {
						// System.out.println("Line: " + line);
						String[] tokens = line.split(":");
						if (tokens.length == 2) {
							splitMap.put(tokens[0].trim().toLowerCase(), tokens[1].trim().length() > 0 ? tokens[1].trim() : "");
						}
					}
				}
				h.put(splitMap.get(indexName), splitMap);
			}
		}
		return h;
	}

	public static String join(Collection<?> s, String delimiter) {
		StringBuilder builder = new StringBuilder();
		Iterator<?> iter = s.iterator();
		while (iter.hasNext()) {
			builder.append(iter.next());
			if (!iter.hasNext()) {
				break;
			}
			builder.append(delimiter);
		}
		return builder.toString();
	}

	public static void logToFile(String path, String filename, String text, boolean append) throws IOException {
		if (!path.endsWith("\\"))
			path += "\\";
		checkDir(path);
		FileWriter fileWriter = new FileWriter(path + filename, append);
		BufferedWriter bufferWriter = new BufferedWriter(fileWriter);
		Date d = new Date();
		SimpleDateFormat df = (SimpleDateFormat) DateFormat.getDateTimeInstance();
		df.applyPattern("dd-MM-yy HH:mm:ss.SSS");
		text = df.format(d) + ": " + text;
		bufferWriter.write(text);
		bufferWriter.newLine();
		bufferWriter.close();
	}

	public static void unzip(String zipFile, String outputFolder) {

		byte[] buffer = new byte[1024];

		try {

			// create output directory if it doesn't exist

			File folder = new File(outputFolder);
			if (!folder.exists()) {
				folder.mkdir();
			}

			// get the zip file content
			ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
			// get the zipped file list entry
			ZipEntry ze = zis.getNextEntry();

			while (ze != null) {

				String fileName = ze.getName();
				File newFile = new File(outputFolder + File.separator + fileName);

				
				if (debug) {
					System.out.println("file unzip : " + newFile.getAbsoluteFile());
				}
				// create all non existent folders
				// else you will hit FileNotFoundException for compressed folder

				new File(newFile.getParent()).mkdirs();

				FileOutputStream fos = new FileOutputStream(newFile);

				int len;
				while ((len = zis.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}

				fos.close();
				ze = zis.getNextEntry();
			}

			zis.closeEntry();
			zis.close();

			System.out.println("Done unzipping " + zipFile + " to folder " + outputFolder);

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public static void callAnt(String target, String[] params, String buildXmlDir, String pathToAnt) throws IOException, InterruptedException {
		long start = System.currentTimeMillis();

		if (buildXmlDir == null) {
			buildXmlDir = ".";
		}
		if (pathToAnt == null) {
			pathToAnt = "C:\\Tools\\apache-ant-1.9.3\\bin\\ant.bat";
		}
		
		if (debug) {

			System.out.println("Called ant with parameters: ");
			for (String s : params) {
				System.out.println("Param: " + s);
			}
		}
		File pathToExecutable = new File(pathToAnt);

		Collection<String> collection = new ArrayList<String>();
		collection.add(pathToExecutable.getAbsolutePath());
		if (params == null) {
			return;
		} else {
			collection.addAll(Arrays.asList(params));
		}

		collection.add(target);
		params = collection.toArray(new String[] {});

		ProcessBuilder builder = new ProcessBuilder(params);
		builder.directory(new File(buildXmlDir).getAbsoluteFile());
		builder.redirectErrorStream(true);
		Process process = builder.start();

		Scanner s = new Scanner(process.getInputStream());
		StringBuilder text = new StringBuilder();
		while (s.hasNextLine()) {
			text.append(s.nextLine());
			text.append("\n");
		}
		s.close();

		int result = process.waitFor();

		long end = System.currentTimeMillis();
		double diff = ((double) (end - start)) / 1000;

		String operation = "ant ";

		for (String p : params) {
			if (p.contains("ant.bat"))
				continue;
			operation += p + " ";
		}
		operation = operation.trim();

	}

	public static void purgeDirectory(File dir) {
		if (dir.exists()) {
			for (File file : dir.listFiles()) {
				if (file.isDirectory())
					purgeDirectory(file);
				file.delete();
			}
			dir.delete();
		}
	}

	public static void zipIt(String zipFile, String sourceFolder) {

		byte[] buffer = new byte[1024];

		try {

			FileOutputStream fos = new FileOutputStream(zipFile);
			ZipOutputStream zos = new ZipOutputStream(fos);

			System.out.println("Output to Zip : " + zipFile);
			
			if (sourceFolder.endsWith(File.separator)) {
				sourceFolder = sourceFolder.substring(0, sourceFolder.length()-1);
			}
			
			Vector<String> fileList = generateFileList(new File(sourceFolder), sourceFolder); 

			for (String file : fileList) {

				if (debug) {
					System.out.println("File Added : " + file);
				}
				ZipEntry ze = new ZipEntry(file);
				zos.putNextEntry(ze);

				FileInputStream in = new FileInputStream(sourceFolder + File.separator + file);

				int len;
				while ((len = in.read(buffer)) > 0) {
					zos.write(buffer, 0, len);
				}

				in.close();
			}

			zos.closeEntry();
			// remember close it
			zos.close();

//			System.out.println("Done");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Traverse a directory and get all files, and add the file into fileList
	 * 
	 * @param node
	 *            file or directory
	 */
	
	private static Vector<String> generateFileList(File node, String baseDir) {

		Vector<String> retval = new Vector<String>();
		// add file only
		if (node.isFile()) {
			retval.add(generateZipEntry(node.getAbsoluteFile().toString(), baseDir));
//			retval.add(node.getName()); 
		} else if (node.isDirectory()) {
			String[] subNote = node.list();
			for (String filename : subNote) {
				retval.addAll(generateFileList(new File(node, filename), baseDir));
			}
		}
		return retval;
	}

	/**
	 * Format the file path for zip
	 * 
	 * @param file
	 *            file path
	 * @return Formatted file path
	 */
	private static String generateZipEntry(String file, String sourceFolder) {
		int indexOfSourceFolder = file.lastIndexOf(sourceFolder);
		return file.substring(indexOfSourceFolder + sourceFolder.length() + 1, file.length()); 
	}
	
	public static void copyFile(String source, String target) {
		try{
			 
    	    File afile =new File(source);
    	    File bfile =new File(target);
 
    	    InputStream inStream = new FileInputStream(afile);
    	    OutputStream outStream = new FileOutputStream(bfile);
 
    	    byte[] buffer = new byte[1024];
 
    	    int length;
    	    //copy the file content in bytes 
    	    while ((length = inStream.read(buffer)) > 0){
 
    	    	outStream.write(buffer, 0, length);
 
    	    }
 
    	    inStream.close();
    	    outStream.close();
 
    	    System.out.println("Copied " + afile.getAbsoluteFile() + " to " + bfile.getAbsoluteFile());
 
    	}catch(IOException e){
    		e.printStackTrace();
    	}
	}
	
	private static void initMaps() throws IOException {
		objToDirMap = new HashMap<String, String>();
		dirToObjectMap = new HashMap<String, String>();
		objToSuffixMap = new HashMap<String, String>();
		String mappings = readResource("DirToObjectMappings.txt");
		String[] lines = mappings.split("\n");
		for (String line : lines) {
			String dir = line.substring(0, line.indexOf("="));
			String metadata = line.substring(line.indexOf("=") + 1);
			objToDirMap.put(metadata, dir);
			dirToObjectMap.put(dir,  metadata);
		}
		
//		mappings = readFile("resources", "ObjectToSuffixMappings.txt");
//		lines = mappings.split("\n");
//		for (String line : lines) {
//			String metadata = line.substring(0, line.indexOf("="));
//			String suffix = line.substring(line.indexOf("=") + 1);
//			objToSuffixMap.put(metadata, suffix);
//		}
		
	}
	
	public static String getDirForMetadataType(String metadataType) throws IOException {
		if (objToDirMap == null || dirToObjectMap == null) {
			initMaps();
		}
		return objToDirMap.get(metadataType);
	}
	
	public static String getMetadataTypeForDir(String dir) throws IOException {
		if (objToDirMap == null || dirToObjectMap == null) {
			initMaps();
		}
		return dirToObjectMap.get(dir);
	}
	
	public static String getSuffixForMetadataType(String metadataType) throws IOException {
		if (objToDirMap == null || dirToObjectMap == null || objToSuffixMap == null) {
			initMaps();
		}
		return objToSuffixMap.get(metadataType).trim();
	}
	
	public static<T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
	  List<T> list = new ArrayList<T>(c);
	  java.util.Collections.sort(list);
	  return list;
	}
	
	public static void moveDirContent (String currentDir, String newDir) throws IOException {
		for (File f : FileUtils.listFilesAndDirs(new File(currentDir), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE)) {
			FileUtils.moveToDirectory(f, new File(newDir), true);
		}
	}
	
	public static void copyDirContent(String currentDir, String newDir) throws IOException {
		FileUtils.copyDirectory(new File (currentDir), new File(newDir), true);
	}

	public static void appendLines (Collection<String> lines, String toFile) throws IOException {
		FileUtils.writeLines(new File(toFile), "UTF-8", lines, true);
	}

	private static HashSet<String> initSets(NodeList source) {
		HashSet<String> retval = new HashSet<String>();
		

		for (int i = 0; i < source.getLength(); i++) {
			Node n = source.item(i);
			if (n.getNodeName().equals("members")) {
				retval.add(n.getTextContent());
			}
		}
		return retval;
	}
	
	public static Properties initProps(String propFilename) {
		Properties prop = null;
		try {
			// load a properties file for reading
			prop = new Properties();
			prop.load(new FileInputStream(propFilename));
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		return prop;
	}
	
	public static Properties initProps(File f) {
		Properties prop = null;
		try {
			// load a properties file for reading
			prop = new Properties();
			prop.load(new FileInputStream(f));
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		return prop;
	}
	
	public static void mergeTwoDirectories(File target, File source){
		String targetDirPath = target.getAbsolutePath();
		Utils.checkDir(targetDirPath);
		File[] files = source.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				mergeTwoDirectories(new File(Utils.checkPathSlash(targetDirPath) + file.getName()) , file);
			} else {
				Path oldPath = Paths.get(file.getAbsolutePath());
				Path newPath = Paths.get(targetDirPath+File.separator+file.getName());
				try {
					Files.move(oldPath, newPath);
				}  catch (FileAlreadyExistsException e1) {
					if (!file.getName().startsWith(".")) {
						System.out.println("Package merge conflict: file: " + file.getName() + " exists in more than one package.");
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				/*
				File newName = new File(targetDirPath+File.separator+file.getName()); 
				file.renameTo(newName);
//				System.out.println(file.getAbsolutePath() + " is moved to " + newName.getAbsolutePath());
 
 */
			}
		}
	}
	
	public static boolean checkIsDirectory(String filename) {
		File file = new File(filename);
		return file.isDirectory(); // Check if it's a directory

	}
	
	public static boolean checkIfFileExists(String filename) {
		File file = new File(filename);

		return file.exists();      // Check if the file exists
	}
	
}
