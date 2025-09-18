package sa.comptechco.nuxeo.common.operations.utils;

//import com.syncfusion.docio.WordDocument;
//import com.syncfusion.ej2.wordprocessor.FormatType;
//import com.syncfusion.ej2.wordprocessor.WordProcessorHelper;
//import com.syncfusion.licensing.SyncfusionLicenseProvider;
import com.syncfusion.docio.WordDocument;
import com.syncfusion.ej2.wordprocessor.FormatType;
import com.syncfusion.ej2.wordprocessor.WordProcessorHelper;
import com.syncfusion.licensing.SyncfusionLicenseProvider;
import org.mvel2.MVEL;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.scripting.MvelExpression;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.DirectoryFactoryDescriptor;
import org.nuxeo.ecm.directory.DirectoryServiceImpl;
import org.nuxeo.runtime.api.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.comptechco.nuxeo.common.operations.constants.StudioConstant;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Utils {
    static Logger log = LoggerFactory.getLogger(Utils.class);
    public static String[] getFieldsValues(String[] fields, DocumentModel nxDocument, CoreSession session, OperationContext ctx) {
        String[] fieldsValues = new String[fields.length];
        for (int i = 0; i< fields.length; i++) {
            String f = fields[i];
            String conF = f.trim();
            if (conF.startsWith("@{")) {
                fieldsValues[i] = evaluateExpression(conF, nxDocument, session, ctx);
            } else {
                String mvelExpr = "";
                if (conF.contains("[") && conF.contains("]")) {
                    conF = conF.substring(conF.indexOf("[") + 1, conF.indexOf("]")).replaceAll("\"", "");
                }
                if (f.trim().contains("].")) {
                    mvelExpr = f.trim().substring(f.trim().indexOf("].") + 2);
                } else if (f.trim().contains(".")) {
                    conF = f.trim().split("\\.")[0];
                    if (conF.endsWith("()")) {
                        conF = "";
                        mvelExpr = f.trim();
                    } else {
                        mvelExpr = f.trim().substring(conF.length() + 2);
                    }
                } else if (!f.trim().contains(".") && f.trim().endsWith("()")) {
                    conF = "";
                    mvelExpr = f.trim();
                }


                Property property = null;
                String value = "";

                if (conF.length() > 0) {
                    try {
                        property = nxDocument.getProperty(conF);
                    } catch (Throwable e) {
                        log.warn("Unable to ready property " + conF, e);
                    }
                    if (property != null) {
                        Serializable pValue = property.getValue();
                        if (pValue != null) {
                            if (Blob.class.isAssignableFrom(pValue.getClass())) {
                                Blob blob = (Blob) pValue;
                                // TODO: handle pictures
                            } else {
                                Type pType = property.getType();
                                switch (pType.getName()) {
                                    case BooleanType.ID:
                                        Boolean bVal = (Boolean) property.getValue();
                                        value = bVal ? "TRUE" : "FALSE";
                                        break;
                                    case DateType.ID:
                                        GregorianCalendar dVal = (GregorianCalendar) property.getValue();
                                        if (mvelExpr.length() > 0) {
                                            Serializable compiled = MVEL.compileExpression(mvelExpr);
                                            Serializable sValue = MVEL.executeExpression(compiled, dVal, Serializable.class);
                                            value = sValue.toString();
                                        } else {
                                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
                                            value = sdf.format(dVal.getTime());
                                        }
                                        break;
                                    case StringType.ID:
                                        value = (String) property.getValue();
                                        if (mvelExpr.length() > 0) {
                                            Serializable compiled = MVEL.compileExpression(mvelExpr);
                                            Serializable sValue = MVEL.executeExpression(compiled, value, Serializable.class);
                                            value = sValue.toString();
                                        }
                                        break;
                                    default:
                                        if (mvelExpr.length() > 0) {
                                            Serializable compiled = MVEL.compileExpression(mvelExpr);
                                            Serializable sValue = MVEL.executeExpression(compiled, property.getValue(), Serializable.class);
                                            value = sValue.toString();
                                        } else {
                                            value = property.getValue().toString();
                                        }
                                        break;
                                }
                            }
                        } else {
                            value = "";
                        }
                    }
                    boolean isPrem = false;
                    switch (conF) {
                        case "uid":
                        case "doc.uid":
                            isPrem = true;
                            value = nxDocument.getId();
                            break;
                        case "title":
                        case "doc.title":
                            isPrem = true;
                            value = nxDocument.getTitle();
                            break;
                        case "name":
                        case "doc.name":
                            isPrem = true;
                            value = nxDocument.getName();
                            break;
                    }
                    if (isPrem && mvelExpr.trim().length() > 0) {
                        Serializable compiled = MVEL.compileExpression(mvelExpr);
                        Serializable sValue = MVEL.executeExpression(compiled, value, Serializable.class);
                        value = sValue.toString();
                    }
                } else if (mvelExpr.length() > 0) {
                    Serializable compiled = MVEL.compileExpression(mvelExpr);
                    Serializable sValue = MVEL.executeExpression(compiled, nxDocument, Serializable.class);


                    value = sValue.toString();
                }
                fieldsValues[i] = value;
            }
        }
        return fieldsValues;
    }

    /*
    * @param: expression - string expression must be of the form "@{EXPRESSION | LOCAL | DATATYPE | VOCABULARY_ID}"
    *                       EXPRESSION is an MVEL expression to be evaluated (all nuxeo mvel expressions supported)
    *                       LOCAL: the language to be used i.e.  AR, EN
    *                       DATATYPE: type of value returned by EXPRESSION after evaluation i.e. (LIST, VOC, ....)
    *                       VOCABULARY_ID: (optional) the ID of the vocabulary to be used to get the localized value of evaluation result base on it
    * */
    public static String evaluateExpression(String expression, DocumentModel nxDocument, CoreSession session, OperationContext ctx) {
        Pattern      pattern      = Pattern.compile("(\\@\\{)(.+)(\\})");
        Matcher matcher      = pattern.matcher(expression);
        if (matcher.matches()) {
            String _requestedExpr = matcher.group(2);
            String[] split = _requestedExpr.split(" \\| ");
            org.nuxeo.ecm.directory.api.DirectoryService dservice =  Framework.getService(org.nuxeo.ecm.directory.api.DirectoryService.class);
            Directory directory = dservice.getDirectory("");
            String EXPRESSION = split[0].trim(),
                    LOCAL = split.length > 1 ? split[1].trim().toUpperCase() : "EN",
                    DATATYPE = split.length > 2 ? split[2].trim().toUpperCase() : "",
                    VOCABULARY_ID =  split.length > 3 ? split[3].trim() : "";
            Object exprResult = null;
            if (EXPRESSION.length() > 0) {
                try {
                    ctx.setInput(nxDocument);
                    MvelExpression expr = new MvelExpression(EXPRESSION);
                    exprResult = expr.eval(ctx);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
            if (exprResult == null) {
                return "";
            }

            switch (DATATYPE) {
                case "VOC":
                    return CorrespondenceUtils.getLocalizedVocabularyValue(VOCABULARY_ID, exprResult.toString(), LOCAL, session, ctx);
                case "STR_LIST":
                    String[] strList = (String[]) exprResult;
                    StringBuilder result = new StringBuilder();
                    int itr = 0;
                    for (String v: strList) {
                        itr++;
                        result.append(v);
                        if (itr < strList.length) {
                            result.append(", ");
                        }
                    }
                    return result.toString();
                default:
                    return exprResult.toString();
            }
        }
        return "";
    }
    public static InputStream processDocument(InputStream inputStream, CoreSession session, OperationContext ctx, boolean applyMerge, String documentIdToMerge) {
        try {
            SyncfusionLicenseProvider.registerLicense("GTIlMmhian1hfWN9Z2doY2J8Y2J8YWNqanNiYWliYWliYWg+PDsyPj4yN2E/MiA7ODIhEzQ+Mjo/fTA8Pg==");
            System.setProperty("javax.xml.stream.XMLOutputFactory", "com.sun.xml.internal.stream.XMLOutputFactoryImpl");
            if (applyMerge) {
//
//                String testBase64String = "data:application/vnd.openxmlformats-officedocument.wordprocessingml.document;base64,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";
//                 byte[] decodedContent = Base64.getDecoder().decode(testBase64String.split(",")[1].getBytes("UTF-8"));
//                InputStream targetStream = new ByteArrayInputStream(decodedContent);
//                WordDocument document1 = new WordDocument(targetStream, com.syncfusion.docio.FormatType.Docx);
//                try {
//                    document1.getMailMerge().execute(new String[]{"ExtendedPrice"}, new String[]{"100006"});
//                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
//                    document1.save(stream, com.syncfusion.docio.FormatType.Docx);
//
//                    InputStream inStream = new ByteArrayInputStream(stream.toByteArray());
//                    String out = WordProcessorHelper.load(inStream, FormatType.Docx);
//                    document1.close();
//                    inStream.close();
//                    targetStream.close();
//                    stream.close();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }

//                String TEMP_FILE_NAME = UUID.randomUUID() + ".docx";
                try(WordDocument document = new WordDocument(inputStream); ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                    String[] fields = document.getMailMerge().getMergeFieldNames();
                    if (fields != null && fields.length > 0) {
                        DocumentModel nxDocument = session.getDocument(new IdRef(documentIdToMerge));

                        String[] fieldsValues = Utils.getFieldsValues(fields, nxDocument, session, ctx);
                        document.getMailMerge().execute(fields, fieldsValues);
                    }
                    document.save(stream, com.syncfusion.docio.FormatType.Docx);
                    stream.flush();
                    InputStream is = new ByteArrayInputStream(stream.toByteArray());
//                    String out = WordProcessorHelper.load(new ByteArrayInputStream(stream.toByteArray()), FormatType.Docx);
                    stream.close();
                    return is;
                }
            } else {
                return inputStream;
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new NuxeoException("Error while processing document by { Utils.processDocument } [ IOException: " + e.getMessage() + " ]");
        } catch (Exception e) {
            e.printStackTrace();
            throw new NuxeoException("Error while processing document by { Utils.processDocument } [ Exception: " + e.getMessage() + " ]");
        }
    }
    public static String loadDocumentSFTD(InputStream inputStream, CoreSession session, OperationContext ctx, boolean applyMerge, String documentIdToMerge) {
        try {
            InputStream is = processDocument(inputStream, session, ctx, applyMerge, documentIdToMerge);
            return WordProcessorHelper.load(is, FormatType.Docx);
        } catch (Exception e) {
            e.printStackTrace();
            throw new NuxeoException("Error while loading document as SFTD by { Utils.loadDocumentSFTD } [ Exception: " + e.getMessage() + " ]");
        }
    }
    public static Blob processDocumentToPDF(InputStream inputStream, CoreSession session, OperationContext ctx, boolean applyMerge, String documentIdToMerge) {
        try(InputStream docxInputStream = processDocument(inputStream, session, ctx, applyMerge, documentIdToMerge); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ConversionService conversionService = Framework.getService(ConversionService.class);
            BlobHolder blobHolder = new SimpleBlobHolder();
            Blob blob = Blobs.createBlob(docxInputStream);
            blob.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"); // "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            blobHolder.setBlob(blob);
            BlobHolder pdfBlobHolder = conversionService.convertToMimeType("application/pdf", blobHolder, Collections.emptyMap());
            return pdfBlobHolder.getBlob();

//            XWPFDocument document = new XWPFDocument(docxInputStream);
//            PdfOptions options = PdfOptions.create();
//            options.fontEncoding("utf8");
//            PdfConverter.getInstance().convert(document, baos, options);
//            byte[] resultBytes = baos.toByteArray();
//            Blob result = new ByteArrayBlob(resultBytes);
            // For Test only
//            FileOutputStream fos = new FileOutputStream(new File(UUID.randomUUID().toString() + "Mail Merge.pdf"));
//
//            fos.write(resultBytes);
//            fos.close();
            // -------------------------
//            result.setMimeType("application/pdf");
//            result.setEncoding("UTF-8");
//            return result;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new NuxeoException("Error while processing document to PDF by {Utils.processDocumentToPDF}[ " + ioe.getMessage() + " ]");
        } catch (Exception e) {
            e.printStackTrace();
            throw new NuxeoException("Error while processing document to PDF by {Utils.processDocumentToPDF}[ " + e.getMessage() + " ]");
        }
    }

    public static byte[] decodeBase64ToByteArray(String base64Input) throws UnsupportedEncodingException {
        String[] split = base64Input.split(",");
        String input;
        if (split.length > 1) {
            input = split[1];
        } else {
            input = base64Input;
        }
        return Base64.getDecoder().decode(input.getBytes(StandardCharsets.UTF_8));
    }

    public static void checkInputDocumentType(DocumentModel input) {
        if (!(StudioConstant.CTDOCUMENT_TEMPLATE_DOC_TYPE.equals(input.getType()))) {
            throw new NuxeoException("Operation works only with "
                    + StudioConstant.CTDOCUMENT_TEMPLATE_DOC_TYPE + " document type.");
        }
    }
}
