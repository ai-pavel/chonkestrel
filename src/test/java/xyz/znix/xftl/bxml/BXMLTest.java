package xyz.znix.xftl.bxml;

import org.jdom2.*;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.junit.Assert;
import org.junit.Test;
import xyz.znix.xftl.VanillaDatafile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class BXMLTest {
    private final VanillaDatafile df = VanillaDatafile.createWithDefaultPath();

    @Test
    public void verifyKestral() {
        Document original = loadXML("data/kestral.xml");
        verifyDocument(original);
    }

    @Test
    public void verifyAll() {
        // Go through all the XML files in ftl.dat and make sure we can
        // serialise and deserialise them and end up with the same thing.
        for (VanillaDatafile.Entry entry : df.getAllFiles()) {
            if (!entry.getName().endsWith(".xml"))
                continue;

            Document original = loadXML(entry.getName());
            verifyDocument(original);
        }
    }

    private void verifyDocument(Document original) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BXMLWriter.write(original, out);

        byte[] data = out.toByteArray();

        ByteArrayInputStream input = new ByteArrayInputStream(data);
        Document restored = BXMLReader.read(input);

        // Delete all the comments from the original, since we don't
        // store them, and they'd otherwise break our comparison.
        recursivelyDeleteComments(original.getContent());

        // Check the XML matches the original
        XMLOutputter xmlOutput = new XMLOutputter(Format.getPrettyFormat());
        String originalString = xmlOutput.outputString(original);
        String restoredString = xmlOutput.outputString(restored);

        Assert.assertEquals(originalString, restoredString);
    }

    private void recursivelyDeleteComments(List<Content> contentList) {
        // Walk backwards, as we're removing stuff
        for (int i = contentList.size() - 1; i >= 0; i--) {
            Content content = contentList.get(i);

            if (content instanceof Comment) {
                contentList.remove(i);
                continue;
            }

            if (content instanceof Element child) {
                recursivelyDeleteComments(child.getContent());
            }
        }
    }

    private Document loadXML(String path) {
        byte[] data = df.read(df.get(path));
        SAXBuilder builder = new SAXBuilder();
        try {
            return builder.build(new ByteArrayInputStream(data));
        } catch (JDOMException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
