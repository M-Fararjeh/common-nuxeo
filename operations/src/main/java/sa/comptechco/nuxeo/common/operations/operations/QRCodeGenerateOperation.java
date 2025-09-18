package sa.comptechco.nuxeo.common.operations.operations;

import com.google.zxing.WriterException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import sa.comptechco.nuxeo.common.operations.service.QRCodeService;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Operation for generating QR codes and barcodes for documents.
 * 
 * This operation can generate:
 * - QR codes containing document URLs for easy access
 * - Various barcode formats for document identification
 * 
 * The generated codes can be customized with different sizes, formats, and margins.
 */
@Operation(id = QRCodeGenerateOperation.ID, category = Constants.CAT_DOCUMENT, label = "Generate QRcode", description = "Generate QRcode for document.")
public class QRCodeGenerateOperation {

    public static final String ID = "Document.GenerateQrcode";

    // Default values
    private static final int DEFAULT_WIDTH = 250;
    private static final int DEFAULT_HEIGHT = 250;
    private static final String DEFAULT_FORMAT = "png";
    private static final int DEFAULT_MARGIN = 4;
    private static final String DEFAULT_BARCODE_TYPE = "qrcode";
    private static final String DEFAULT_BARCODE_TEXT = "01";
    private static final String DEFAULT_TENANT_ID = "default-domain";
    
    // Configuration properties
    private static final String CLIENT_SERVER_URL_PROPERTY = "org.nuxeo.cts.server.url";
    private static final String CLIENT_SERVER_URL_DEFAULT = "http://localhost:4200/";

    @Param(name = "width", required = false)
    protected Integer width = DEFAULT_WIDTH;

    @Param(name = "height", required = false)
    protected Integer height = DEFAULT_HEIGHT;

    @Param(name = "format", required = false)
    protected String format = DEFAULT_FORMAT;

    @Param(name = "margin", required = false)
    protected Integer margin = DEFAULT_MARGIN;

    @Param(name = "barcodeType", required = false)
    protected String barcodeType = DEFAULT_BARCODE_TYPE;

    @Param(name = "barcodeText", required = false)
    protected String barcodeText = DEFAULT_BARCODE_TEXT;

    @Param(name = "tenantId", required = false)
    protected String tenantId = DEFAULT_TENANT_ID;

    @Context
    protected CoreSession session;

    @OperationMethod
    public Blob run(DocumentModel input) throws IOException, WriterException {
        try {
            if (isQRCodeType()) {
                return generateQRCode(input);
            } else {
                return generateBarcode();
            }
        } catch (Exception e) {
            String errorMessage = String.format("Failed to generate %s: %s", 
                                               barcodeType, e.getMessage());
            throw new NuxeoException(errorMessage, e);
        }
    }

    /**
     * Checks if the requested type is a QR code.
     */
    private boolean isQRCodeType() {
        return "qrcode".equals(barcodeType);
    }

    /**
     * Generates a QR code containing the document URL.
     */
    private Blob generateQRCode(DocumentModel input) throws IOException, WriterException {
        String documentUrl = buildDocumentUrl(input);
        return createCodeBlob(documentUrl, true);
    }

    /**
     * Generates a barcode with the specified text.
     */
    private Blob generateBarcode() throws IOException, WriterException {
        return createCodeBlob(barcodeText, false);
    }

    /**
     * Builds the document URL for QR code generation.
     */
    private String buildDocumentUrl(DocumentModel input) {
        String documentId = input.getId();
        String baseUrl = getClientBaseUrl();
        return baseUrl.replace("{id}", documentId);
    }

    /**
     * Retrieves the client base URL from system configuration.
     */
    private String getClientBaseUrl() {
        DocumentModel systemConfig = getSystemConfiguration();
        Object clientUrl = systemConfig.getProperty("sys_config:clientUrl").getValue();
        
        if (clientUrl == null) {
            return Framework.getProperty(CLIENT_SERVER_URL_PROPERTY, CLIENT_SERVER_URL_DEFAULT);
        }
        
        return clientUrl.toString();
    }

    /**
     * Gets the system configuration document.
     */
    private DocumentModel getSystemConfiguration() {
        String configPath = "/" + tenantId + "/workspaces/CTS/System Configurations";
        DocumentModel configFile = session.createDocumentModel(configPath, "system configuration", "SystemConfiguration");
        
        if (!session.exists(configFile.getRef())) {
            throw new NuxeoException("System configuration not found for tenant: " + tenantId);
        }
        
        return session.getDocument(configFile.getRef());
    }

    /**
     * Creates the actual code blob (QR code or barcode).
     */
    private Blob createCodeBlob(String content, boolean isQRCode) throws IOException, WriterException {
        FileBlob tempBlob = new FileBlob("." + format);
        
        try (FileOutputStream outputStream = new FileOutputStream(tempBlob.getFile())) {
            if (isQRCode) {
                QRCodeService.encodeAsQRStream(content, outputStream, width, height, format, margin);
            } else {
                QRCodeService.generateBarcodeImage(content, outputStream, format);
            }
        }
        
        return new FileBlob(tempBlob.getFile(), "image/png");
    }
}