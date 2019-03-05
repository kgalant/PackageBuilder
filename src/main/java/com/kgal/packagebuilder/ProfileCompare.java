package com.kgal.packagebuilder;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.kgal.packagebuilder.profilecompare.TagComparer;
import com.kgal.packagebuilder.profilecompare.UserPermissionsComparer;
import com.salesforce.migrationtoolutils.Utils;

public class ProfileCompare {
	
	
	
	private static final HashMap<String,String> TAGSTOBECOMPARED = new HashMap<String,String>() {
		{
			put("userPermissions","com.kgal.packagebuilder.profilecompare.UserPermissionsComparer");
		}
	};

	public static void main(String[] args) 
			throws InstantiationException, IllegalAccessException, ClassNotFoundException, SAXException, IOException, ParserConfigurationException, TransformerFactoryConfigurationError, TransformerException, XPathExpressionException {
		// TODO Auto-generated method stub
		ProfileCompare pc = new ProfileCompare();
		pc.diffTwoProfileFiles(new File("profilecompare/clean/Sysadmclean.profile"), 
				new File("profilecompare/dirty/Sysadmdirty.profile"), 
				"profilecompare", "result.xml");

	}
	
	/*
	 * This function will take two XML Documents (which are expected to be the same profile)
	 * And remove all permissions that appear in both and are identical
	 * 
	 * This is intended to remove all the boilerplate stuff from profiles and leave only the actual relevant 
	 * content (i.e. field permissions, etc.) so no changes that weren't expected are moved
	 * inadvertently
	 */

	public Document diffTwoProfiles (Document clean, Document dirty) 
			throws ParserConfigurationException, InstantiationException, IllegalAccessException, ClassNotFoundException, XPathExpressionException {
        
        // we're going to try to remove all the userPermission tags from the profile if they match across clean and dirty
        //    <userPermissions>
		//        <enabled>true</enabled>
		//        <name>ActivateOrder</name>
		//    </userPermissions>
        
        // go through clean, for each tag configured to be compared, run the comparer 
        // and remove the tag from the final document if matching
        
        for (String t : TAGSTOBECOMPARED.keySet()) {
        	
        	// initialize comparer
        	
        	TagComparer c = (TagComparer) Class.forName(TAGSTOBECOMPARED.get(t)).newInstance();
        	
  
			NodeList tagsDirty = dirty.getDocumentElement().getElementsByTagName(t);
			
			// remove all these tags from the dirty document for now,
			// we'll add back all the ones which are not identical later
			

			
			NodeList tagsClean = clean.getDocumentElement().getElementsByTagName(t);
        	for (int i = 0; i < tagsDirty.getLength(); i++) {
        		
        		for (int j = 0; j < tagsClean.getLength(); j++) {
        			if (c.isIdentical(tagsDirty.item(i), tagsClean.item(j))) {
        				// remove it from the target
        				dirty.getDocumentElement().removeChild(tagsDirty.item(i));
        			}
        		}
        	}
        }
        
        // now clean up any leftover whitespace in the dirty document after removing all differences
        
        dirty.normalize();
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.evaluate("//text()[normalize-space()='']",
                                                      dirty,
                                                      XPathConstants.NODESET);

        for (int i = 0; i < nodeList.getLength(); ++i) {
            Node node = nodeList.item(i);
            node.getParentNode().removeChild(node);
        }
        return dirty;
	}
	
	/*
	 * This function will pick up two files (which are expected to the the same profile, with different
	 * content, convert them into XML Documents and run the comparison
	 */
	
	public void diffTwoProfileFiles (File clean, File dirty, String targetDir, String filename) 
			throws SAXException, IOException, ParserConfigurationException, TransformerFactoryConfigurationError, TransformerException, InstantiationException, IllegalAccessException, ClassNotFoundException, XPathExpressionException {
		DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
		documentFactory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
        Document diff = diffTwoProfiles(documentBuilder.parse(clean), documentBuilder.parse(dirty));
        
        Transformer tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        tf.setOutputProperty(OutputKeys.METHOD, "xml");	
        tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        Writer out = new StringWriter();
        tf.transform(new DOMSource(diff), new StreamResult(out));
        
        Utils.writeFile(targetDir + File.separator + filename, out.toString());
        
	}
}
