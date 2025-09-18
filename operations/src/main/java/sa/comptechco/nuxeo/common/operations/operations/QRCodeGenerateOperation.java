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
//import net.glxn.qrgen.javase.QRCode;

/**
 *
 */
@Operation(id = QRCodeGenerateOperation.ID, category = Constants.CAT_DOCUMENT, label = "Generate QRcode", description = "Generate QRcode for document.")
public class QRCodeGenerateOperation {

    public static final String ID = "Document.GenerateQrcode";

    @Param(name = "width", required = false)
    protected Integer width = 250;

    @Param(name = "height", required = false)
    protected Integer height = 250;

    @Param(name = "format", required = false)
    protected String format = "png";

    @Param(name = "margin", required = false)
    protected Integer margin = 4;

    @Param(name = "barcodeType", required = false)
    protected String barcodeType = "qrcode";

    @Param(name = "barcodeText", required = false)
    protected String barcodeText = "01";

    @Param(name = "tenantId", required = false)
    protected String tenantId = "default-domain";

    @Context
    protected CoreSession session;

    public static final String CLIENT_SERVER_URL = "org.nuxeo.cts.server.url";
    public static final String CLIENT_SERVER_URL_DEFAULT = "http://localhost:4200/";

    @OperationMethod
    public Blob run(DocumentModel input) throws IOException, WriterException {
        FileBlob fb;
        FileBlob blob;
        if (barcodeType.equals("qrcode")) {
            String id = input.getId();


            /// retrieve client url

            String parentPath = "/"+tenantId+"/workspaces/CTS/System Configurations";

            DocumentModel config = null;
            DocumentModel configFile = session.createDocumentModel(parentPath, "system configuration", "SystemConfiguration");
            if (session.exists(configFile.getRef())) {
                config = session.getDocument(configFile.getRef());
            } else {
                throw new NuxeoException("System configuration is not found");
            }
            String baseUrl = (String) config.getProperty("sys_config:clientUrl").getValue();//Framework.getProperty(CLIENT_SERVER_URL, CLIENT_SERVER_URL_DEFAULT);

            //String documentUrl= baseUrl.concat("dashboard(viewer:view/").concat(id).concat(")");
            String documentUrl = baseUrl.replace("{id}", id);

            try {

            /*OutputStream out = new ByteArrayOutputStream();
            QRCodeService.encodeAsQRStream(id, out, width, height, format);
            byte[] bytes = ((ByteArrayOutputStream) out).toByteArray();
            InputStream inputstream = new ByteArrayInputStream(bytes);
            Blob blob = new FileBlob(inputstream,"image/png");

            return blob;//, "image/png", null);*/
                blob = new FileBlob("." + format);
                FileOutputStream out = new FileOutputStream(blob.getFile());
                QRCodeService.encodeAsQRStream(documentUrl, out, width, height, format,margin);
                fb = new FileBlob(blob.getFile(), "image/png");

                return fb;
            } catch (Exception e) {
                throw new NuxeoException("Failed to generate Qrcode");
            }
        } else {
            try {
                blob = new FileBlob("." + format);
                FileOutputStream out = new FileOutputStream(blob.getFile());
                QRCodeService.generateBarcodeImage(barcodeText, out, format);
                fb = new FileBlob(blob.getFile(), "image/png");
                return fb;

            } catch (Exception e) {
                e.printStackTrace();
                throw new NuxeoException("Failed to generate barcode");
            }
        }

    }

}
