package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.WebImgUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.regex.Matcher


/**
 * @description: 人生重开主文件
 * @author Nature Zero
 * @date 2024/2/14 18:54
 */
@Shiro
@Component
class LifeRestartMain {

    companion object {
        val restartUtil = LifeRestartUtil()
        var userList = mutableListOf<LifeRestartUtil.UserInfo>()
        var lastFetchTime: Long = 0
        var sendStrList: MutableList<MutableMap<String, Any>> = mutableListOf()
    }


    fun sendNewImage(
        bot: Bot,
        event: AnyMessageEvent?,
        imgName: String,
        webUrl: String,
        scale: Double? = null
    ) {
        val imgUrl =
            WebImgUtil().getImgFromWeb(url = webUrl, imgName = imgName, element = "body", channel = true, scale = scale)
        val sendMsg: String = MsgUtils.builder().img(imgUrl).build()
        bot.sendMsg(event, sendMsg, false)
    }


    @Scheduled(fixedDelay = 1 * 60 * 1000) // 每隔1分钟执行一次检查
    fun clearCacheIfExpired() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFetchTime > 5 * 60 * 1000 && (restartUtil.eventData != null || restartUtil.ageData != null)) {
            restartUtil.eventData = null
            restartUtil.ageData = null
            System.gc()
            // TODO 清除超时的用户数据
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "重开")
    fun startRestart(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val currentTime = System.currentTimeMillis()
        // 如果超过5分钟或者没有获取过数据，重新获取
        if (currentTime - lastFetchTime > 5 * 60 * 1000 || restartUtil.eventData == null || restartUtil.ageData == null) {
//            val readEvent = ExcelReader().readExcel("resources/lifeRestart/events.xlsx", "event")
//
//            val readAge = ExcelReader().readExcel("resources/lifeRestart/age.xlsx", "age")
//
//            if (readEvent != null) {
//                restartUtil.eventList = readEvent
//            }
//            if (readAge != null) {
//                restartUtil.ageList = readAge
//            }
            if (!restartUtil.fetchDataAndUpdateLists()) {
                bot.sendMsg(event, "人生重开数据缺失，请使用「更新资源」指令来下载缺失数据", false)
                return
            }

            // 更新时间戳
            lastFetchTime = currentTime
        }

        val realId = OtherUtil().getRealId(event)

        userList.find { it.userId == realId }.let {
            if (it != null) {
                userList.remove(it)
            }
        }

        sendStrList.find { sendMap ->
            sendMap["userId"] == realId
        }.let { sendMap ->
            sendStrList.remove(sendMap)
        }
        userList.add(LifeRestartUtil.UserInfo(realId, null, -1, mutableListOf(), null))
        bot.sendMsg(
            event,
            "游戏账号创建成功，请输入「分配属性 颜值 智力 体质 家境」或者「随机分配」来获取随机属性",
            false
        )
        bot.sendMsg(event, "请在5分钟内开始游戏", false)

    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "随机分配")
    @Suppress("UNCHECKED_CAST")
    fun randomAttribute(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        userList.find { it.userId == realId }.let { userInfo ->
            if (userInfo == null) {
                bot.sendMsg(event, "你还没有开始游戏，请发送 重开 进行游戏", false)
                return
            }
            if (userInfo.property != null) {
                bot.sendMsg(event, "你已经分配过属性了,请不要重复分配", false)
                return
            }
            restartUtil.randomAttributes(userInfo)

            val sendStr = restartUtil.trajectory(userInfo)

            sendStrList.add(mutableMapOf("userId" to realId, "sendStr" to mutableListOf(sendStr) as List<String>))

            sendNewImage(
                bot,
                event,
                "${userInfo.userId}-LifeStart",
                "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
            )

            bot.sendMsg(event, "请发送「继续」来进行游戏", false)

        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "分配属性 (.*)")
    fun dealAttribute(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)

        userList.find { it.userId == realId }.let { userInfo ->
            if (userInfo == null) {
                bot.sendMsg(event, "你还没有开始游戏，请发送 重开 进行游戏", false)
                return
            }
            if (userInfo.property != null) {
                bot.sendMsg(event, "你已经分配过属性了,请不要重复分配", false)
                return
            }
            when (restartUtil.assignAttributes(userInfo, matcher)) {
                "sizeOut" -> {
                    bot.sendMsg(event, "注意分配的5个属性值的和不能超过20哦", false)
                    return
                }

                "valueOut" -> {
                    bot.sendMsg(event, "单项属性值不能大于10", false)
                    return
                }
            }
            sendNewImage(
                bot,
                event,
                "${userInfo.userId}-LifeStart",
                "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
            )
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "继续")
    fun nextStep(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        userList.find { it.userId == realId }.let { userInfo ->
            if (userInfo == null) {
                bot.sendMsg(event, "你还没有开始游戏，请发送 重开 进行游戏", false)
                return
            }
            if (userInfo.property == null) {
                bot.sendMsg(event, "你还没有分配属性，请先分配属性", false)
                return
            }

            if (userInfo.isEnd == true) {
                sendNewImage(
                    bot,
                    event,
                    "${userInfo.userId}-LifeStart",
                    "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
                )
                bot.sendMsg(event, "游戏结束", false)
                userList.remove(userInfo)
                return
            }
            lastFetchTime = System.currentTimeMillis()

            val sendStr = restartUtil.trajectory(userInfo)
            sendStrList.find { sendMap ->
                sendMap["userId"] == realId
            }.let { sendMap ->
                sendMap?.set("sendStr", listOf(sendStr))
            }

            sendNewImage(
                bot,
                event,
                "${userInfo.userId}-LifeStart",
                "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
            )
        }
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "继续 (.*)")
    fun nextTenStep(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        val stepNext = matcher.group(1).toInt()
        userList.find { it.userId == realId }.let { userInfo ->
            if (userInfo == null) {
                bot.sendMsg(event, "你还没有开始游戏，请发送 重开 进行游戏", false)
                return
            }
            if (userInfo.property == null) {
                bot.sendMsg(event, "你还没有分配属性，请先分配属性", false)
                return
            }
            lastFetchTime = System.currentTimeMillis()
            val strList = mutableListOf<Any?>()
            for (i in 1..stepNext) {

                val sendStr = restartUtil.trajectory(userInfo)

                strList.add(sendStr)

                sendStrList.find { sendMap ->
                    sendMap["userId"] == realId
                }.let { sendMap ->
                    sendMap?.set("sendStr", strList)
                }

                if (userInfo.isEnd == true) {
                    sendNewImage(
                        bot,
                        event,
                        "${userInfo.userId}-LifeStart",
                        "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
                    )
                    userList.remove(userInfo)
                    bot.sendMsg(event, "游戏结束", false)
                    return
                }
            }

            sendNewImage(
                bot,
                event,
                "${userInfo.userId}-LifeStart",
                "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
            )
        }
    }
}