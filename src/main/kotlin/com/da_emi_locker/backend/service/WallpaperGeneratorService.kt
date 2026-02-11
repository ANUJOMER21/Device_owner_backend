package com.da_emi_locker.backend.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

/**
 * Service to generate custom wallpaper images with "Pay EMI" content
 * and dealer contact details on a white background.
 */
@Service
class WallpaperGeneratorService(
    private val s3StorageService: S3StorageService
) {
    private val logger = LoggerFactory.getLogger(WallpaperGeneratorService::class.java)

    /**
     * Generate a custom wallpaper image and upload to S3.
     * White background with "PAY YOUR EMI" message and dealer contact information.
     * Returns the URL of the uploaded wallpaper, or null on failure.
     */
    fun generateAndUploadWallpaper(
        dealerName: String,
        dealerPhone: String,
        dealerAddress: String,
        customerName: String
    ): String? {
        return try {
            val imageBytes = generateWallpaperImage(dealerName, dealerPhone, dealerAddress, customerName)
            val multipartFile = ByteArrayMultipartFile(
                name = "wallpaper",
                originalFilename = "wallpaper.png",
                contentType = "image/png",
                content = imageBytes
            )
            s3StorageService.uploadWallpaper(multipartFile)
        } catch (e: Exception) {
            logger.error("Failed to generate wallpaper", e)
            null
        }
    }

    private fun generateWallpaperImage(
        dealerName: String,
        dealerPhone: String,
        dealerAddress: String,
        customerName: String
    ): ByteArray {
        // Standard phone wallpaper size (1080x1920)
        val width = 1080
        val height = 1920
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = image.createGraphics()

        // Enable anti-aliasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)

        // White background
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        // Draw red warning banner at top
        g.color = Color(220, 53, 69) // Bootstrap danger red
        g.fillRect(0, 300, width, 200)

        // Warning text
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 72)
        drawCenteredString(g, "⚠ PAY YOUR EMI ⚠", width, 420)

        // Sub-warning
        g.font = Font("SansSerif", Font.PLAIN, 36)
        drawCenteredString(g, "Your device will be locked if EMI is not paid", width, 475)

        // Main content area
        var y = 600

        // Customer name
        g.color = Color(33, 37, 41) // Dark gray
        g.font = Font("SansSerif", Font.BOLD, 48)
        drawCenteredString(g, "Dear $customerName,", width, y)
        y += 80

        // Message
        g.font = Font("SansSerif", Font.PLAIN, 36)
        g.color = Color(73, 80, 87)
        val messages = listOf(
            "Please pay your EMI on time",
            "to avoid device restrictions.",
            "",
            "Contact your dealer for",
            "payment details."
        )
        for (msg in messages) {
            if (msg.isNotEmpty()) {
                drawCenteredString(g, msg, width, y)
            }
            y += 50
        }

        // Dealer contact section
        y += 40

        // Dealer section header
        g.color = Color(0, 123, 255) // Blue
        g.fillRect(100, y - 10, width - 200, 4)
        y += 40

        g.color = Color(33, 37, 41)
        g.font = Font("SansSerif", Font.BOLD, 44)
        drawCenteredString(g, "CONTACT DEALER", width, y)
        y += 70

        // Dealer details
        g.font = Font("SansSerif", Font.BOLD, 40)
        g.color = Color(33, 37, 41)
        drawCenteredString(g, dealerName, width, y)
        y += 60

        g.font = Font("SansSerif", Font.PLAIN, 38)
        g.color = Color(73, 80, 87)

        if (dealerPhone.isNotBlank()) {
            drawCenteredString(g, "📞 $dealerPhone", width, y)
            y += 55
        }

        if (dealerAddress.isNotBlank()) {
            // Wrap long address
            val maxChars = 35
            val addressLines = wrapText(dealerAddress, maxChars)
            g.font = Font("SansSerif", Font.PLAIN, 32)
            for (line in addressLines) {
                drawCenteredString(g, "📍 $line", width, y)
                y += 45
            }
        }

        // Bottom warning
        y = height - 200
        g.color = Color(220, 53, 69)
        g.font = Font("SansSerif", Font.BOLD, 32)
        drawCenteredString(g, "This wallpaper was set by your dealer.", width, y)
        y += 45
        g.font = Font("SansSerif", Font.PLAIN, 28)
        g.color = Color(108, 117, 125)
        drawCenteredString(g, "You cannot change this wallpaper.", width, y)

        g.dispose()

        // Convert to PNG bytes
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }

    private fun drawCenteredString(g: Graphics2D, text: String, width: Int, y: Int) {
        val fm = g.fontMetrics
        val x = (width - fm.stringWidth(text)) / 2
        g.drawString(text, x, y)
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine = word
            } else if (currentLine.length + 1 + word.length <= maxChars) {
                currentLine += " $word"
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }
}

/** Simple MultipartFile implementation wrapping a byte array */
private class ByteArrayMultipartFile(
    private val name: String,
    private val originalFilename: String,
    private val contentType: String,
    private val content: ByteArray
) : MultipartFile {
    override fun getName(): String = name
    override fun getOriginalFilename(): String = originalFilename
    override fun getContentType(): String = contentType
    override fun isEmpty(): Boolean = content.isEmpty()
    override fun getSize(): Long = content.size.toLong()
    override fun getBytes(): ByteArray = content
    override fun getInputStream(): InputStream = ByteArrayInputStream(content)
    override fun transferTo(dest: File) { dest.writeBytes(content) }
}
