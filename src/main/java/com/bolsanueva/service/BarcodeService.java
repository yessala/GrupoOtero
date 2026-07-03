package com.bolsanueva.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * SERVICIO DE GENERACIÓN DE CÓDIGOS DE BARRA PARA EL SGC
 * @author Autor: Yessalim Salazar
 * @version 6.7 (Corrección de Sintaxis de Asignación)
 */
@Service
public class BarcodeService {

    public void generarCodigoBarraCompleto(String codigoContenido, String descripcionLote) throws Exception {
        
        int anchoBarras = 450;
        int altoBarras = 120;
        int margenInferiorTexto = 40; 
        int altoTotalImagen = altoBarras + margenInferiorTexto;

        Code128Writer writer = new Code128Writer();
        BitMatrix bitMatrix = writer.encode(descripcionLote, BarcodeFormat.CODE_128, anchoBarras, altoBarras);
        BufferedImage imagenBarras = MatrixToImageWriter.toBufferedImage(bitMatrix);

        BufferedImage etiquetaFinal = new BufferedImage(anchoBarras, altoTotalImagen, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = etiquetaFinal.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, anchoBarras, altoTotalImagen);
        g2d.drawImage(imagenBarras, 0, 10, null);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Monospaced", Font.BOLD, 15));

        FontMetrics metrics = g2d.getFontMetrics();
        int xTextoCentrado = (anchoBarras - metrics.stringWidth(descripcionLote)) / 2;
        int yTextoPosicion = altoBarras + 25; 

        g2d.drawString(descripcionLote, xTextoCentrado, yTextoPosicion);
        g2d.dispose();

        // RUTA DE DESARROLLO EN WINDOWS
        String rutaCarpeta = "E:/Documentos/Programa/recuperadora/src/main/resources/static/etiquetas/";
        Path pathDirectorio = Paths.get(rutaCarpeta);
        
        if (!Files.exists(pathDirectorio)) {
            Files.createDirectories(pathDirectorio);
        }

        // CORREGIDO: Se cambió el ";" por el "=" para corregir la asignación
        String nombreArchivo = descripcionLote + ".png";
        File archivoSalida = new File(pathDirectorio.toFile(), nombreArchivo);
        
        // Escritura forzada en el disco físico
        boolean escritoConExito = ImageIO.write(etiquetaFinal, "png", archivoSalida);
        
        if (!escritoConExito) {
            throw new Exception("El motor de Java no pudo escribir el formato PNG en la ruta: " + archivoSalida.getAbsolutePath());
        }
        
        System.out.println("💾 [SGC AUDITORÍA] Etiqueta física guardada en: " + archivoSalida.getAbsolutePath());
    }
}