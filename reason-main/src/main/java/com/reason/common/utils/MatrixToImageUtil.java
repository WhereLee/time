package com.reason.common.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Hashtable;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.reason.common.exception.RRException;

/**
 * @description 二维码生成
 * @date 2019年7月17日
 */
public class MatrixToImageUtil {
	private static final int BLACK = 0xFF000000;
	private static final int WHITE = 0xFFFFFFFF;
	
	public static BufferedImage toBufferedImage(BitMatrix matrix) {
		int width = matrix.getWidth();
		int height = matrix.getHeight();
		BufferedImage image = new BufferedImage(width, height,BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
			}
		}
		return image;
	}

	public static void writeToFile(BitMatrix matrix, String format, File file)
			throws IOException {
		BufferedImage image = toBufferedImage(matrix);
		if (!ImageIO.write(image, format, file)) {
			throw new IOException("Could not write an image of format "
					+ format + " to " + file);
		}
	}

	public static void writeToStream(BitMatrix matrix, String format, OutputStream stream) throws IOException {
		BufferedImage image = toBufferedImage(matrix);
		if (!ImageIO.write(image, format, stream)) {
			throw new IOException("Could not write an image of format " + format);
		}
	}

	/**
	 * 生成二维码
	 * @param qrcode 二维码内容
	 * @param path 生成的二维码图片存放路径
	 * @param picname 生成的二维码图片名称
	 * @param width 二维码宽度
	 * @param height 二维码高度
	 * @param suffix 二维码图片后缀
	 * @throws Exception
	 */
	public static void createQrcodeImage(String qrcode,String path,String picname,int width,int height,String suffix) throws Exception{
		//生成二维码图片
		//内容所使用字符集编码
		Hashtable<EncodeHintType, String> hints = new Hashtable<EncodeHintType, String>();
		hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
		hints.put(EncodeHintType.MARGIN,"1");

		BitMatrix bitMatrix = new MultiFormatWriter().encode(qrcode,
				BarcodeFormat.QR_CODE, width, height, hints);
		//生成二维码
		File outputFile = new File(path + File.separator + picname);
		writeToFile(bitMatrix, suffix, outputFile);
	}
}
