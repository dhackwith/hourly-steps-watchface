import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/**
 * Checks a watch face against the Watch Face Format XSD files with nothing but the JDK.
 *
 * <p>The official wff-validator needs an XSD 1.1 processor and the JDK only has XSD 1.0. The only
 * 1.1 feature the WFF schemas use is repeatable elements inside xs:all, so this rewrites each
 * xs:all to a repeating xs:choice as it loads. Every element, attribute and enumeration is still
 * checked; only the "must appear" and "at most once" rules inside xs:all are not.
 *
 * <p>Usage: java WffSchemaCheck.java path/to/watchface.xsd path/to/watchface.xml
 */
public class WffSchemaCheck {

    public static void main(String[] args) throws Exception {
        File xsd = new File(args[0]);
        File face = new File(args[1]);
        if (!xsd.exists()) {
            System.err.println("WFF schema not found: " + xsd);
            System.err.println("Clone google/watchface first (see README).");
            System.exit(2);
        }

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setResourceResolver((type, ns, publicId, systemId, baseUri) ->
            systemId == null ? null : relaxedInput(URI.create(baseUri).resolve(systemId).toString()));

        StreamSource root = new StreamSource(relaxed(xsd.toPath()));
        root.setSystemId(xsd.toURI().toString());
        var validator = factory.newSchema(root).newValidator();

        int[] errors = {0};
        validator.setErrorHandler(new ErrorHandler() {
            @Override public void warning(SAXParseException e) { report("warning", e); }
            @Override public void error(SAXParseException e) { errors[0]++; report("error", e); }
            @Override public void fatalError(SAXParseException e) { errors[0]++; report("fatal", e); }
        });
        validator.validate(new StreamSource(face));

        if (errors[0] > 0) {
            System.err.println(errors[0] + " schema problem(s) in " + face);
            System.exit(1);
        }
        System.out.println("WFF schema check passed (JDK check, not the official validator): " + face);
    }

    private static void report(String level, SAXParseException e) {
        System.err.printf("%s [%d:%d] %s%n", level, e.getLineNumber(), e.getColumnNumber(), e.getMessage());
    }

    private static InputStream relaxed(Path xsd) throws Exception {
        String relaxed = Files.readString(xsd)
            .replace("<xs:all>", "<xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">")
            .replace("</xs:all>", "</xs:choice>");
        return new ByteArrayInputStream(relaxed.getBytes(StandardCharsets.UTF_8));
    }

    private static LSInput relaxedInput(String uri) {
        try {
            return new SimpleLsInput(uri, relaxed(Path.of(URI.create(uri))));
        } catch (Exception e) {
            throw new IllegalStateException("Can't read " + uri, e);
        }
    }

    private record SimpleLsInput(String uri, InputStream stream) implements LSInput {
        @Override public java.io.Reader getCharacterStream() { return null; }
        @Override public void setCharacterStream(java.io.Reader r) { }
        @Override public InputStream getByteStream() { return stream; }
        @Override public void setByteStream(InputStream b) { }
        @Override public String getStringData() { return null; }
        @Override public void setStringData(String s) { }
        @Override public String getSystemId() { return uri; }
        @Override public void setSystemId(String s) { }
        @Override public String getPublicId() { return null; }
        @Override public void setPublicId(String s) { }
        @Override public String getBaseURI() { return uri; }
        @Override public void setBaseURI(String s) { }
        @Override public String getEncoding() { return "UTF-8"; }
        @Override public void setEncoding(String s) { }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCertifiedText(boolean b) { }
    }
}
