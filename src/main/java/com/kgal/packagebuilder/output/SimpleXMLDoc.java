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
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * 
 * Simplified creation of XML documents using SAX Parser
 * Does not support mixing text and elements as childs
 * 
 * @author swissel
 *
 */
public class SimpleXMLDoc {

    private TransformerHandler  hd              = null;
    private boolean             documentStarted = false;
    private boolean             documentClosed  = false;
    private final DOMResult     domresult       = new DOMResult();
    private final Stack<String> xmlTagStack     = new Stack<String>();

    public void addEmptyTag(final String tagName, final Map<String, String> attributes)
            throws TransformerConfigurationException, SAXException {
        this.openTag(tagName, attributes);
        this.closeTag(1);
    }

    /**
     * SHortcut to add a tag that contains only a string value
     *
     * @param tagName
     * @param tagValue
     * @throws SAXException
     * @throws TransformerConfigurationException
     */
    public void addTag(final String tagName, final String tagValue)
            throws TransformerConfigurationException, SAXException {
        this.addTag(tagName, tagValue, null);

    }

    /**
     * SHortcut to add a tag that contains only a string value
     *
     * @param tagName
     * @param tagValue
     * @param attributes
     * @throws SAXException
     * @throws TransformerConfigurationException
     */
    public void addTag(final String tagName, final String tagValue, final Map<String, String> attributes)
            throws TransformerConfigurationException, SAXException {
        this.openTag(tagName, attributes);
        this.addText(tagValue);
        this.closeTag(1);

    }

    public void addText(final String tagValue) throws SAXException {
        if (!this.documentStarted) {
            throw new SAXException("Document has no root tag, can't add text");
        }
        final char[] payload = tagValue.toCharArray();
        // TODO: Do we need CDATA here?
        this.hd.startCDATA();
        this.hd.characters(payload, 0, payload.length);
        this.hd.endCDATA();
    }

    public Node closeDocument() throws SAXException {
        if (!this.documentClosed) {
            this.closeTag(-1); // Make sure all tages are closes
            // Closing of the document,
            this.hd.endDocument();
            this.documentClosed = true;
        }
        return this.domresult.getNode();
    }

    public void closeTag(final int howMany) throws SAXException {
        if (howMany < 0) {
            while (!this.xmlTagStack.empty()) {
                final String closeTag = this.xmlTagStack.pop();
                this.hd.endElement("", "", closeTag);
            }
        } else {
            for (int i = 0; i < howMany; i++) {
                if (!this.xmlTagStack.empty()) {
                    final String closeTag = this.xmlTagStack.pop();
                    this.hd.endElement("", "", closeTag);
                } else {
                    break; // No point looping
                }
            }
        }
    }

    public void openTag(final String tagName) throws SAXException, TransformerConfigurationException {
        this.openTag(tagName, null, null);
    }

    public void openTag(final String tagName, final Map<String, String> attributes)
            throws SAXException, TransformerConfigurationException {

        AttributesImpl atts = null;
        String nameSpace = "";
        if (!this.documentStarted) {
            this.initializeDoc();
        }
        // This creates attributes that go inside the element, all
        // encoding is taken care of
        if ((attributes != null) && !attributes.isEmpty()) {
            atts = new AttributesImpl();
            for (final Entry<String, String> curAtt : attributes.entrySet()) {
                if (curAtt.getKey().equals("xmlns")) {
                    nameSpace = curAtt.getValue();
                } else {
                    atts.addAttribute("", "", curAtt.getKey(), "CDATA", curAtt.getValue());
                }
            }
        }
        // This creates the element with the previously defined
        // attributes
        this.hd.startElement(nameSpace, "", tagName, atts);
        this.xmlTagStack.push(tagName); // Memorize that we opened it!
    }

    public void openTag(final String tagName, final String attributeName, final String attributValue)
            throws SAXException, TransformerConfigurationException {
        final Map<String, String> attributes = new HashMap<>();
        if ((attributeName != null) && (attributValue != null)) {
            attributes.put(attributeName, attributValue);
        }
        this.openTag(tagName, attributes);
    }

    /**
     * Pretty print the XML document to an output stream Will close the XML
     * document and not allow additions
     *
     * @param out
     * @throws SAXException
     * @throws TransformerException
     */
    public void printToStream(final OutputStream out) throws SAXException, TransformerException {
        final Source xmlInput = new DOMSource(this.closeDocument());
        final StreamResult xmlOutput = new StreamResult(out);
        final Transformer serializer = this.hd.getTransformer();
        serializer.transform(xmlInput, xmlOutput);
    }

    @Override
    public String toString() {
        final OutputStream out = new ByteArrayOutputStream();
        try {
            this.printToStream(out);
            return out.toString();
        } catch (SAXException | TransformerException e) {
            return e.getMessage();
        }

    }

    private void initializeDoc() throws TransformerConfigurationException, SAXException {
        // Factory pattern at work
        final SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory.newInstance();
        this.hd = tf.newTransformerHandler();
        final Transformer serializer = this.hd.getTransformer();
        serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        serializer.setOutputProperty(OutputKeys.METHOD, "xml");
        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        this.hd.setResult(this.domresult);
        this.hd.startDocument();
        this.documentStarted = true;
    }

}
