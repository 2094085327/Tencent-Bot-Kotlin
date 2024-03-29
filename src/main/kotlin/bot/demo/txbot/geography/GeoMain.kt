package bot.demo.txbot.geography

import bot.demo.txbot.common.utils.WebImgUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.util.regex.Matcher


/**
 *@Description:
 *@Author zeng
 *@Date 2023/6/11 15:14
 *@User 86188
 */
@Shiro
@Component
class GeoMain {
    @Autowired
    val geoApi = GetGeoApi()
    @Autowired
    val webImgUtil = WebImgUtil()
    private fun sendNewImage(bot: Bot, event: AnyMessageEvent?, imgName: String, city: String, webUrl: String) {

        if (!geoApi.checkCode(geoApi.getWeatherData(city))) {
            bot.sendMsg(event, "没有找到'$city'的信息，请检查是否输入错误", false)
            return
        }
        val imgUrl = webImgUtil.getImgFromWeb(url = webUrl, imgName = imgName, channel = true)
        val sendMsg: String = MsgUtils.builder().img(imgUrl).build()
        bot.sendMsg(event, sendMsg, false)
        // 群聊模式下通过图床发送图片
        // val imgData = GetGeoImg().loadImg(imgPath)
        // val imgUrl = imgData?.get("url")?.textValue()
        // val sendMsg: String = MsgUtils.builder().img(imgUrl).build()
        // bot.sendMsg(event, sendMsg, false)
        // imgData?.get("delete")?.let { GetGeoImg().removeImg(it.textValue()) }
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "(.*)天气")
    fun getWeatherImg(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        if (event == null) return

        bot.sendMsg(event, "正在查询信息，请耐心等待", false)
        webImgUtil.deleteImgCache()

        val city = matcher?.group(1)
        val imgName = "${city}天气"
        val folderPath = "resources/imageCache"
        val folder = File(folderPath)
        val matchingFile = folder.listFiles()?.firstOrNull { it.nameWithoutExtension == imgName }

        if (matchingFile != null) {
            webImgUtil.sendCachedImage(bot, event, imgName, matchingFile)
        } else {
            sendNewImage(bot, event, imgName, city ?: "", "http://localhost:${WebImgUtil.usePort}/weather")
        }
        System.gc()
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "(.*)地理")
    fun getGeoImg(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        if (event == null) return

        bot.sendMsg(event, "正在查询信息，请耐心等待", false)
        webImgUtil.deleteImgCache()

        val city = matcher?.group(1)
        val imgName = "${city}地理"
        val folderPath = "resources/imageCache"
        val folder = File(folderPath)

        val matchingFile = folder.listFiles()?.firstOrNull { it.nameWithoutExtension == imgName }

        if (matchingFile != null) {
            webImgUtil.sendCachedImage(bot, event, imgName, matchingFile)
        } else {
            sendNewImage(bot, event, imgName, city ?: "", "http://localhost:${WebImgUtil.usePort}/geo")
        }
        System.gc()
    }

}