package org.jdom2.input;

import net.vhati.modmanager.core.SloppyXMLParser;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.sax.SAXHandlerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * The browser version of JDOM's SAXBuilder (in place of JDOM's own, which
 * needs the JDK's SAX parsers, which TeaVM hasn't got), with the bits of it
 * the game and Slipstream use: it parses with Slipstream's own parser,
 * which the game already reads FTL's XML with.
 */
public class SAXBuilder {
    public SAXBuilder() {
    }

    public void setExpandEntities(boolean expand) {
    }

    public void setSAXHandlerFactory(SAXHandlerFactory factory) {
    }

    public Document build(InputStream in) throws JDOMException, IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0)
            bytes.write(buffer, 0, read);
        return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    public Document build(Reader reader) throws JDOMException, IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) > 0)
            text.append(buffer, 0, read);
        return parse(text.toString());
    }

    private static Document parse(String text) throws JDOMException {
        // (without a byte order mark)
        if (text.startsWith("﻿"))
            text = text.substring(1);
        return new SloppyXMLParser().build(text);
    }
}
