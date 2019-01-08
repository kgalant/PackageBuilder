package com.kgal.packagebuilder.output;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.TransformerConfigurationException;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

public class SimpleXMLDocTest {
    
    private SimpleXMLDoc doc;
    private String expectedResult;

    @Before
    public void setUp() throws Exception {
        this.doc = new SimpleXMLDoc();
        this.expectedResult = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"+
        "<root xmlns=\"http://demo.org/\">\n"+
        "  <empty/>\n"+
        "  <simple>value</simple>\n"+
        "  <empty2 color=\"red\" shape=\"circle\"/>\n"+
        "  <simple2 color=\"red\" shape=\"circle\">value2</simple2>\n"+
        "  <inner color=\"red\" shape=\"circle\">\n"+
        "    <innerEmpty/>\n"+
        "  </inner>\n"+
        "</root>\n";

    }

    @Test
    public final void testFullDocument() {
        try {
            Map<String,String> attr = new HashMap<>();
            attr.put("color", "red");
            attr.put("shape", "circle");
            this.doc.openTag("root", "xmlns", "http://demo.org/");
            this.doc.addEmptyTag("empty", null);
            this.doc.addTag("simple", "value");
            this.doc.addEmptyTag("empty2", attr);
            this.doc.addTag("simple2", "value2", attr);
            this.doc.openTag("inner",attr);
            this.doc.addEmptyTag("innerEmpty", null);
            this.doc.closeDocument();
            System.out.println(this.doc.toString());
            assertEquals(this.expectedResult, this.doc.toString());
            
        } catch (TransformerConfigurationException | SAXException e) {
            fail(e.getMessage());
        }
    }

}
