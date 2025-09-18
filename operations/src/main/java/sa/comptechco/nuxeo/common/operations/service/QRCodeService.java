package sa.comptechco.nuxeo.common.operations.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.Map;

public class QRCodeService {

	public static void encodeAsQRStream(String contentsToEncode, OutputStream out,Integer width ,Integer height,String format,Integer margin) throws WriterException, IOException {
	    
	    if (contentsToEncode == null) {
	      return ;
	    }
	    Map<EncodeHintType, Object> hints = null;
	    String encoding = "UTF-8";
	    if (encoding != null) {
	      hints = new EnumMap<>(EncodeHintType.class);
	      hints.put(EncodeHintType.CHARACTER_SET, encoding);
		  hints.put(EncodeHintType.MARGIN, margin);

	    }
	    BitMatrix result;
	    try {
	    	QRCodeWriter writer=new QRCodeWriter();
	    	if(width == null || width.equals(Integer.valueOf(0)))
	    		width=125;
			if(height == null || height.equals(Integer.valueOf(0)))
				height=125;
	      result =  writer.encode(contentsToEncode, BarcodeFormat.QR_CODE, width, height, hints);
	    } catch (IllegalArgumentException iae) {
	      // Unsupported format
	      return ;
	    }
	    int matrixWidth = result.getWidth();

	    BufferedImage image = new BufferedImage(matrixWidth, matrixWidth, BufferedImage.TYPE_INT_RGB);
		image.createGraphics();
		Graphics2D graphics = (Graphics2D) image.getGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, matrixWidth, matrixWidth);
		// Paint and save the image using the ByteMatrix
		graphics.setColor(Color.BLACK);

		for (int i = 0; i < matrixWidth; i++) {
			for (int j = 0; j < matrixWidth; j++) {
				if (result.get(i, j)) {
					graphics.fillRect(i, j, 1, 1);
				}
			}
		}
		if(StringUtils.isEmpty(format))
			format="png";
		ImageIO.write(image, format,out);
	  }
	public static void generateBarcodeImage(String barcodeText, OutputStream out, String format) throws Exception {
	Code128Writer barcodeWriter = new Code128Writer();
		BitMatrix bitMatrix = barcodeWriter.encode(barcodeText, BarcodeFormat.CODE_128, 300, 150);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
		ImageIO.write(image, format,out);
	}
}
