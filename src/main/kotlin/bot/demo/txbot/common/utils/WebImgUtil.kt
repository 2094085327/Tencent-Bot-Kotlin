package bot.demo.txbot.common.utils

import com.fasterxml.jackson.databind.JsonNode
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import net.coobird.thumbnailator.Thumbnails
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.imageio.ImageIO


/**
 *@Description: 进行浏览器绘图的主要工具类
 *@Author zeng
 *@Date 2023/6/11 15:05
 *@User 86188
 */
@Component
@Configuration
class WebImgUtil {
    companion object {
        var usePort: String = ""
        var key: String = ""
    }

    @Value("\${image_load.key}")
    fun getKey(imgKey: String) {
        key = imgKey
    }

    @Value("\${web_config.port}")
    fun setPort(port: String) {
        usePort = port
    }

    private val headers: MutableMap<String, Any> = mutableMapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0;Win64;x64)AppleWebKit/537.36 (KHTML,like Gecko)Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0",
        "Authorization" to key,
        "Content-Type" to "multipart/form-data"
    )
    private val baseUrl = "https://sm.ms/api/v2"


    fun convertImageToBase64(imagePath: String): String {
        val bytes = Files.readAllBytes(Paths.get(imagePath))
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun outputStreamToBase64(outputStream: ByteArray): String {
        return "base64://${Base64.getEncoder().encodeToString(outputStream)}"
    }


    fun loadImg(imgPath: String): JsonNode? {
        val files: Map<String, File> = mapOf("smfile" to File(imgPath))

        val params: Map<String, String> = mapOf("format" to "json")

        val imgData = HttpUtil.doPostJson(
            url = "$baseUrl/upload",
            files = files,
            params = params,
            headers = headers
        )

        return imgData["data"]
    }

    @Suppress("unused")
    fun removeImg(imgUrl: String) {
        HttpUtil.doGetStr(url = imgUrl, headers = headers)
        println("已经删除图片：$imgUrl")
    }

    /**
     * 缓存图片
     * @param imgName 图片名
     * @param imgType 图片类型
     * @param imgPath 图片存储路径
     * @param imgBuffer 图片流
     *
     * @return 本地文件路径
     */
    fun cacheImg(imgName: String, imgType: String, imgPath: String? = null, imgBuffer: ByteArray): String {
        deleteImgCache()

        val folderPath = "resources/imageCache"
        val screenshotFilePath =
            if (imgPath != null) File("$imgPath/$imgName.$imgType") else File("$folderPath/$imgName.$imgType")
        val folder = File(folderPath)

        if (!folder.exists()) folder.mkdirs()
        FileOutputStream(screenshotFilePath).use { fos ->
            fos.write(imgBuffer)
        }
        return screenshotFilePath.absolutePath
    }

    fun sendCachedImage(bot: Bot, event: AnyMessageEvent?, imgName: String, file: File) {
        val sendCacheImg: String = MsgUtils
            .builder()
            .img("base64://${WebImgUtil().convertImageToBase64(file.absolutePath)}")
            .build()
        bot.sendMsg(event, sendCacheImg, false)
        println("使用缓存文件:${file.name}")
    }


    /**
     *删除超过缓存时间的图片
     **/
    fun deleteImgCache() {
        val folderPath = "resources/imageCache"
        val folder = File(folderPath)
        val currentDate = Date()
        val fiveMinutesAgo = Date(currentDate.time - 5 * 60 * 1000)
        folder.listFiles()?.forEach { file ->
            if (file.lastModified() < fiveMinutesAgo.time) {
                println("删除缓存：${file.name}")
                file.delete()
            }
        }
    }

    fun bufferedImageToByteArray(image: BufferedImage, formatName: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()

        try {
            // 将 BufferedImage 写入 ByteArrayOutputStream
            ImageIO.write(image, formatName, byteArrayOutputStream)

            // 获取 ByteArrayOutputStream 中的字节数组
            val byteArray = byteArrayOutputStream.toByteArray()

            // 关闭 ByteArrayOutputStream
            byteArrayOutputStream.close()

            return byteArray
        } catch (e: Exception) {
            // 处理异常，比如 IOException
            e.printStackTrace()
            return byteArrayOf() // 返回空数组或其他默认值
        }
    }


    fun getImgFromWeb(
        url: String,
        channel: Boolean,
        imgName: String? = null,
        imgPath: String? = null,
        width: Int? = null,
        height: Int? = null,
        scale: Double? = null,
        sleepTime: Long = 0
    ): String? {
        Playwright.create().use { playwright ->
            val browser: Browser = playwright.chromium().launch()
            val page: Page = browser.newPage()
            val realImgName = imgName ?: System.currentTimeMillis().toString()
            page.navigate(url)

            var buffer = page.screenshot(
                Page.ScreenshotOptions()
                    .setFullPage(true)
            )
            if (scale != null) {
                val thumbnailBuilder = Thumbnails.of(buffer.inputStream()).scale(scale).asBufferedImage()
                buffer = bufferedImageToByteArray(thumbnailBuilder, "png")
            }

            val realImgPath = cacheImg(imgName = realImgName, imgType = "png", imgPath = imgPath, imgBuffer = buffer)

            if (channel) return "base64://${convertImageToBase64(realImgPath)}"
            else {
                val imgData = loadImg(realImgPath)

                return imgData?.get("url")?.textValue()
            }
        }
    }
}