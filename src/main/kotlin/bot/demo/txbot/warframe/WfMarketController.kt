package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.warframe.database.WfLexiconEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.database.WfRivenService
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.regex.Matcher


/**
 * @description: Warframe 市场
 * @author Nature Zero
 * @date 2024/5/20 上午9:03
 */
@Shiro
@Component
class WfMarketController {
    @Autowired
    lateinit var wfLexiconService: WfLexiconService

    @Autowired
    lateinit var wfRivenService: WfRivenService

    /**
     * Warframe 市场物品
     *
     *
     * @property platinum 价格
     * @property quantity 数量
     * @property inGameName 游戏内名称
     */
    data class OrderInfo(
        val platinum: Int,
        val quantity: Int,
        val inGameName: String,
    )

    /**
     * Warframe 紫卡信息
     *
     * @property value 属性值
     * @property positive 是否为正属性
     * @property urlName 属性URL名
     */
    data class Attributes(
        val value: Double,
        val positive: Boolean,
        val urlName: String
    )

    /**
     * Warframe 紫卡订单信息
     *
     * @property modRank mod等级
     * @property reRolls 循环次数
     * @property startPlatinum 起拍价格
     * @property buyOutPlatinum 一口价
     * @property polarity 极性
     * @property positive 属性
     */
    data class RivenOrderInfo(
        val modRank: Int,
        val reRolls: Int,
        val startPlatinum: Int,
        val buyOutPlatinum: Int,
        val polarity: String,
        val positive: List<Attributes>,
    )

    /**
     * 发送物品信息
     *
     * @param bot 机器人
     * @param event 事件
     * @param item 物品
     * @param modLevel 模组等级
     */
    fun sendMarketItemInfo(bot: Bot, event: AnyMessageEvent, item: WfLexiconEntity, modLevel: Any? = null) {
        val url = "$WARFRAME_MARKET_ITEMS/${item.urlName}/orders"
        val headers = mutableMapOf<String, Any>("accept" to "application/json")
        val marketJson = HttpUtil.doGetJson(url = url, headers = headers)

        // 定义允许的状态集合
        val allowedStatuses = setOf("online", "ingame")
        val orders = marketJson["payload"]["orders"]

        // 获取所有订单中的最大 mod_rank 值
        val maxModRank = if (modLevel == "满级" && orders.any { it.has("mod_rank") }) {
            orders.filter { it.has("mod_rank") }.maxOfOrNull { it["mod_rank"].intValue() }
        } else {
            (modLevel as? String)?.toIntOrNull()
        }

        // 筛选出符合条件的订单
        val filteredOrders = orders.asSequence()
            .filter { order ->
                order["order_type"].textValue() == "sell" &&
                        order["user"]["status"].textValue() in allowedStatuses &&
                        (modLevel == null || (order.has("mod_rank") &&
                                order["mod_rank"].intValue() == maxModRank))
            }
            .sortedBy { it["platinum"].intValue() }
            .take(5)
            .map {
                OrderInfo(
                    platinum = it["platinum"].intValue(),
                    quantity = it["quantity"].intValue(),
                    inGameName = it["user"]["ingame_name"].textValue()
                )
            }
            .toList()

        val orderString = if (filteredOrders.isEmpty()) {
            "当前没有任何在线的玩家出售${item.zhItemName}"
        } else {
            filteredOrders.joinToString("\n") {
                "${it.inGameName} 价格: ${it.platinum} 数量: ${it.quantity}"
            } + "\n/w ${filteredOrders.first().inGameName} Hi! I want to buy: \"${item.enItemName}\" for ${filteredOrders.first().platinum} platinum.(warframe market)"
        }

        val modLevelString = when {
            modLevel == "满级" -> "满级"
            modLevel != null -> "${modLevel}级"
            else -> ""
        }

        bot.sendMsg(
            event,
            "你查询的物品是 $modLevelString「${item.zhItemName}」\n$orderString",
            false
        )
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wm (.*)")
    fun getMarketItem(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val key = matcher.group(1)
        val regex = """(\d+)(?=级)|(满级)""".toRegex()
        val matchResult = regex.find(key)
        val level = matchResult?.value

        // 移除匹配到的部分
        val cleanKey = if (matchResult != null) {
            key.replace("${matchResult.value}级", "").replace("满级", "").trim()
        } else {
            key
        }
        val itemEntity = wfLexiconService.turnKeyToUrlNameByLexicon(cleanKey)

        if (itemEntity != null) {
            sendMarketItemInfo(bot, event, itemEntity, level)
            return
        }

        val keyList = wfLexiconService.turnKeyToUrlNameByLexiconLike(cleanKey)
        if (keyList.isNullOrEmpty()) {
            val fuzzyList = mutableListOf<String>()

            // 遍历 key 的每个字符，并将其转换为字符串
            key.forEach { eachKey ->
                // 调用 wfLexiconService.fuzzyQuery() 方法进行模糊查询，并将结果存储在 result 变量中
                wfLexiconService.fuzzyQuery(eachKey.toString())?.forEach {
                    it?.zhItemName?.let { name ->
                        fuzzyList.add(name)
                    }
                }
            }

            if (fuzzyList.isNotEmpty()) {
                bot.sendMsg(event, "未找到该物品,也许你想找的是:[${fuzzyList.joinToString(", ")}]", false)
            } else {
                bot.sendMsg(event, "未找到任何匹配项。", false)
            }

            return
        }

        val item = keyList.last()!!
        sendMarketItemInfo(bot, event, item, level)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wr (.*)")
    fun getRiven(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val key = matcher.group(1)
        val parameterList = key.split(" ")

        val itemNameKey: String = parameterList.first()
        val itemEntity = wfRivenService.turnKeyToUrlNameByRiven(itemNameKey)

        // 判断 itemEntity 是否为空
        if (itemEntity == null) {
            // 创建一个空的字符串列表用于存储模糊查询的结果
            val fuzzyList = mutableListOf<String>()

            // 遍历 itemNameKey 的每个字符，并将其转换为字符串
            itemNameKey.forEach { eachKey ->
                // 调用 superFuzzyQuery() 方法进行模糊查询，并将结果存储在 result 变量中
                wfRivenService.superFuzzyQuery(eachKey.toString())
                    ?.forEach { it?.zhName?.let { name -> fuzzyList.add(name) } }
            }
            val fuzzy = fuzzyList.toSet().toList()
            if (fuzzy.isNotEmpty()) {
                bot.sendMsg(event, "未找到该物品,也许你想找的是:[${fuzzy.joinToString(", ")}]", false)
            } else {
                bot.sendMsg(event, "未找到任何匹配项。", false)
            }
            return
        }

        val positiveList = mutableListOf<String>()
        var negativeParam: String? = null

        parameterList.drop(1).forEach { parameter ->
            when {
                parameter.contains("负") -> {
                    if (parameter == "无负") negativeParam = "none"
                    else {
                        val negative = wfRivenService.turnKeyToUrlNameByRivenLike(parameter.replace("负", ""))
                        negative?.firstOrNull()?.urlName?.let { negativeParam = it }
                    }
                }

                else -> {
                    val positive = wfRivenService.turnKeyToUrlNameByRivenLike(parameter)
                    positive?.firstOrNull()?.urlName?.let { positiveList.add(it) }
                }
            }
        }

        val params = mutableMapOf(
            "weapon_url_name" to itemEntity.urlName,
            "sort_by" to "price_asc"
        ).apply {
            if (positiveList.isNotEmpty()) put("positive_stats", positiveList.joinToString(","))
            if (negativeParam != null) put("negative_stats", negativeParam!!)
        }

        val rivenJson = try {
            HttpUtil.doGetJson(WARFRAME_MARKET_RIVEN_AUCTIONS, params = params)
        } catch (e: Exception) {
            bot.sendMsg(event, "查询失败，请稍后重试", false)
            return
        }

        val orders = rivenJson["payload"]["auctions"]
        val allowedStatuses = setOf("online", "ingame")

        val rivenOrderList = orders.asSequence()
            .filter { order -> order["owner"]["status"].textValue() in allowedStatuses }
            .take(5)
            .map { order ->
                RivenOrderInfo(
                    modRank = order["item"]["mod_rank"].intValue(),
                    reRolls = order["item"]["re_rolls"].intValue(),
                    startPlatinum = order["starting_price"]?.intValue() ?: order["buyout_price"].intValue(),
                    buyOutPlatinum = order["buyout_price"]?.intValue() ?: order["starting_price"].intValue(),
                    polarity = order["item"]["polarity"].textValue(),
                    positive = order["item"]["attributes"].map { attribute ->
                        Attributes(
                            value = attribute["value"].doubleValue(),
                            positive = attribute["positive"].booleanValue(),
                            urlName = attribute["url_name"].textValue()
                        )
                    }
                )
            }.toList()

        val orderString = if (rivenOrderList.isEmpty()) "当前没有任何在线的玩家出售这种词条的${itemEntity.zhName}"
        else {
            rivenOrderList.joinToString("\n") { order ->
                val polaritySymbol = when (order.polarity) {
                    "madurai" -> "r"
                    "vazarin" -> "Δ"
                    else -> "-"
                }
                "mod等级:${order.modRank} 起拍价:${order.startPlatinum} 一口价:${order.buyOutPlatinum} 循环次数: ${order.reRolls} 极性:$polaritySymbol\n" +
                        order.positive.joinToString("|") { positive ->
                            "${wfRivenService.turnUrlNameToKeyByRiven(positive.urlName)} ${if (positive.positive) "+${positive.value}" else positive.value}%"
                        }
            }
        }

        bot.sendMsg(
            event,
            "你查找的「${itemEntity.zhName}」紫卡前5条拍卖信息如下:\n$orderString\n示例:wr 战刃 暴伤 暴击 负触发",
            false
        )
    }
}