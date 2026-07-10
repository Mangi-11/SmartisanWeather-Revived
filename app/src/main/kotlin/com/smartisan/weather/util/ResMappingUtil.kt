package com.smartisan.weather.util

import com.smartisan.weather.R
import com.smartisan.weather.custom.WeatherResId

/**
 * 天气代码 → 图标/风向文字/城市简称映射，复刻自原版 ResMappingUtil.java。
 *
 * - a: 天气代码 → WeatherResId（日/夜图标 + 阴影）
 * - b: 风向代码 → 文字资源 ID
 * - c: 英文天气描述 → 天气代码（原版静态初始化，保留以维持全部逻辑）
 * - d: 行政区全称 → 简称
 */
object ResMappingUtil {
    private val a: HashMap<String, WeatherResId> = HashMap()
    private val b: HashMap<String, Int> = HashMap()
    private val c: HashMap<String, String> = HashMap()
    private val d: HashMap<String, String> = HashMap()

    init {
        a["00"] = WeatherResId(R.drawable.little_icon_sunny, R.drawable.little_icon_sunny_night, R.drawable.little_icon_sunny_shadow, R.drawable.little_icon_sunny_night_shadow)
        a["01"] = WeatherResId(R.drawable.little_icon_cloudy, R.drawable.little_icon_cloudy_night, R.drawable.little_icon_cloudy_shadow, R.drawable.little_icon_cloudy_night_shadow)
        a["02"] = WeatherResId(R.drawable.little_icon_overcast, R.drawable.little_icon_overcast, R.drawable.little_icon_overcast_shadow, -1)
        a["03"] = WeatherResId(R.drawable.little_icon_shower, R.drawable.little_icon_shower, R.drawable.little_icon_shower_shadow, -1)
        a["04"] = WeatherResId(R.drawable.little_icon_thundershower, R.drawable.little_icon_thundershower_night, R.drawable.little_icon_thundershower_shadow, R.drawable.little_icon_thundershower_night_shadow)
        a["05"] = WeatherResId(R.drawable.little_icon_thundershowerhail, R.drawable.little_icon_thundershowerhail_night, R.drawable.little_icon_thundershowerhail_shadow, R.drawable.little_icon_thundershowerhail_night_shadow)
        a["06"] = WeatherResId(R.drawable.little_icon_icerain, R.drawable.little_icon_icerain, R.drawable.little_icon_icerain_shadow, -1)
        a["07"] = WeatherResId(R.drawable.little_icon_lightrain, R.drawable.little_icon_lightrain, R.drawable.little_icon_lightrain_shadow, -1)
        a["08"] = WeatherResId(R.drawable.little_icon_moderaterain, R.drawable.little_icon_moderaterain, R.drawable.little_icon_moderaterain_shadow, -1)
        a["09"] = WeatherResId(R.drawable.little_icon_heavyrain, R.drawable.little_icon_heavyrain, R.drawable.little_icon_heavyrain_shadow, -1)
        a["10"] = WeatherResId(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1)
        a["11"] = WeatherResId(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1)
        a["12"] = WeatherResId(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1)
        a["13"] = WeatherResId(R.drawable.little_icon_snow, R.drawable.little_icon_snow_night, R.drawable.little_icon_snow_shadow, R.drawable.little_icon_snow_night_shadow)
        a["14"] = WeatherResId(R.drawable.little_icon_lightsnow, R.drawable.little_icon_lightsnow, R.drawable.little_icon_lightsnow_shadow, -1)
        a["15"] = WeatherResId(R.drawable.little_icon_moderatesnow, R.drawable.little_icon_moderatesnow, R.drawable.little_icon_moderatesnow_shadow, -1)
        a["16"] = WeatherResId(R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow_shadow, -1)
        a["17"] = WeatherResId(R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow_shadow, -1)
        a["18"] = WeatherResId(R.drawable.little_icon_foggy, R.drawable.little_icon_foggy, R.drawable.little_icon_foggy_shadow, -1)
        a["19"] = WeatherResId(R.drawable.little_icon_icerain, R.drawable.little_icon_icerain, R.drawable.little_icon_icerain_shadow, -1)
        a["20"] = WeatherResId(R.drawable.little_icon_sandstorm, R.drawable.little_icon_sandstorm, R.drawable.little_icon_sandstorm_shadow, -1)
        a["21"] = WeatherResId(R.drawable.little_icon_moderaterain, R.drawable.little_icon_moderaterain, R.drawable.little_icon_moderaterain_shadow, -1)
        a["22"] = WeatherResId(R.drawable.little_icon_heavyrain, R.drawable.little_icon_heavyrain, R.drawable.little_icon_heavyrain_shadow, -1)
        a["23"] = WeatherResId(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1)
        a["24"] = WeatherResId(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1)
        a["25"] = WeatherResId(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1)
        a["26"] = WeatherResId(R.drawable.little_icon_moderatesnow, R.drawable.little_icon_moderatesnow, R.drawable.little_icon_moderatesnow_shadow, -1)
        a["27"] = WeatherResId(R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow_shadow, -1)
        a["28"] = WeatherResId(R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow_shadow, -1)
        a["29"] = WeatherResId(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1)
        a["30"] = WeatherResId(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1)
        a["31"] = WeatherResId(R.drawable.little_icon_sandstorm, R.drawable.little_icon_sandstorm, R.drawable.little_icon_sandstorm_shadow, -1)
        a["53"] = WeatherResId(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1)
        a["99"] = WeatherResId(R.drawable.little_icon_unknown, R.drawable.little_icon_unknown, R.drawable.little_icon_unknown_shadow, -1)
        a["32"] = WeatherResId(R.drawable.little_icon_foggy, R.drawable.little_icon_foggy, R.drawable.little_icon_foggy_shadow, -1)
        a["49"] = WeatherResId(R.drawable.little_icon_foggy, R.drawable.little_icon_foggy, R.drawable.little_icon_foggy_shadow, -1)
        a["57"] = WeatherResId(R.drawable.little_icon_foggy, R.drawable.little_icon_foggy, R.drawable.little_icon_foggy_shadow, -1)
        a["58"] = WeatherResId(R.drawable.little_icon_foggy, R.drawable.little_icon_foggy, R.drawable.little_icon_foggy_shadow, -1)
        a["54"] = WeatherResId(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1)
        a["55"] = WeatherResId(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1)
        a["56"] = WeatherResId(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1)
        a["1000"] = WeatherResId(-1, -1, R.drawable.little_icon_sunrise_shadow, -1)
        a["1001"] = WeatherResId(-1, -1, R.drawable.little_icon_sunset_shadow, -1)

        b["0"] = R.string.wind_dir_0
        b["1"] = R.string.wind_dir_1
        b["2"] = R.string.wind_dir_2
        b["3"] = R.string.wind_dir_3
        b["4"] = R.string.wind_dir_4
        b["5"] = R.string.wind_dir_5
        b["6"] = R.string.wind_dir_6
        b["7"] = R.string.wind_dir_7
        b["8"] = R.string.wind_dir_8
        b["9"] = R.string.wind_dir_9

        c["clear"] = "00"
        c["cloudy"] = "01"
        c["chanceflurries"] = "14"
        c["chancerain"] = "07"
        c["chancesleet"] = "06"
        c["chancesnow"] = "14"
        c["chancetstorms"] = "10"
        c["flurries"] = "14"
        c["fog"] = "18"
        c["hazy"] = "53"
        c["mostlycloudy"] = "02"
        c["mostlysunny"] = "00"
        c["partlycloudy"] = "01"
        c["partlysunny"] = "02"
        c["cloudy"] = "02"
        c["sleet"] = "06"
        c["rain"] = "08"
        c["snow"] = "15"
        c["sunny"] = "00"
        c["tstorms"] = "10"
        c["chancetstorms"] = "10"
        c["clear"] = "00"
        c["unknow"] = "99"

        d["内蒙古自治区"] = "内蒙古"
        d["新疆维吾尔自治区"] = "新疆"
        d["西藏自治区"] = "西藏"
        d["宁夏回族自治区"] = "宁夏"
        d["广西壮族自治区"] = "广西"
        d["大兴安岭地区"] = "大兴安岭"
        d["延边朝鲜族自治州"] = "延边"
        d["锡林郭勒盟"] = "锡林郭勒"
        d["昌吉回族自治州"] = "昌吉"
        d["伊犁哈萨克自治州"] = "伊犁"
        d["吐鲁番地区"] = "吐鲁番"
        d["巴音郭楞蒙古自治州"] = "巴音郭楞"
        d["阿克苏地区"] = "阿克苏"
        d["喀什地区"] = "喀什"
        d["塔城地区"] = "塔城"
        d["哈密地区"] = "哈密"
        d["和田地区"] = "和田"
        d["阿勒泰地区"] = "阿勒泰"
        d["克孜勒苏柯尔克孜自治州"] = "克州"
        d["博尔塔拉蒙古自治州"] = "博尔塔拉"
        d["日喀则地区"] = "日喀则"
        d["山南地区"] = "山南"
        d["林芝地区"] = "林芝"
        d["昌都地区"] = "昌都"
        d["那曲地区"] = "那曲"
        d["阿里地区"] = "阿里"
        d["黄南藏族自治州"] = "黄南"
        d["海南藏族自治州"] = "海南"
        d["果洛藏族自治州"] = "果洛"
        d["玉树藏族自治州"] = "玉树"
        d["海西蒙古族藏族自治州"] = "海西"
        d["海北藏族自治州"] = "海北"
        d["临夏回族自治州"] = "临夏"
        d["甘南藏族自治州"] = "甘南"
        d["恩施土家族苗族自治州"] = "恩施"
        d["神农架林区"] = "神农架"
        d["湘西土家族苗族自治州"] = "湘西"
        d["黔南布依族苗族自治州"] = "黔南"
        d["黔东南苗族侗族自治州"] = "黔东南"
        d["黔西南布依族苗族自治州"] = "黔西南"
        d["凉山彝族自治州"] = "凉山"
        d["甘孜藏族自治州"] = "甘孜"
        d["阿坝藏族羌族自治州"] = "阿坝"
        d["大理白族自治州"] = "大理"
        d["红河哈尼族彝族自治州"] = "红河"
        d["文山壮族苗族自治州"] = "文山"
        d["楚雄彝族自治州"] = "楚雄"
        d["怒江傈僳族自治州"] = "怒江"
        d["迪庆藏族自治州"] = "迪庆"
        d["德宏傣族景颇族自治州"] = "德宏"
        d["西双版纳傣族自治州"] = "西双版纳"
        d["威宁彝族回族苗族自治县"] = "威宁"
        d["昌江黎族自治县"] = "昌江"
        d["白沙黎族自治县"] = "白沙"
        d["琼中黎族苗族自治县"] = "琼中"
        d["保亭黎族苗族自治县"] = "保亭"
        d["陵水黎族自治县"] = "陵水"
        d["乐东黎族自治县"] = "乐东"
        d["察布查尔锡伯自治县"] = "察布查尔"
        d["焉耆回族自治县"] = "焉耆"
        d["和布克赛尔蒙古自治县"] = "和布克赛尔"
        d["巴里坤哈萨克自治县"] = "巴里坤"
        d["河南蒙古族自治县"] = "河南"
        d["积石山保安族东乡族撒拉族自治县"] = "积石山"
        d["东乡族自治县"] = "东乡"
        d["三都水族自治县"] = "三都"
        d["木里藏族自治县"] = "木里"
        d["漾濞彝族自治县"] = "漾濞"
        d["巍山彝族回族自治县"] = "巍山"
        d["南涧彝族自治县"] = "南涧"
        d["屏边苗族自治县"] = "屏边"
        d["金平苗族瑶族傣族自治县"] = "金平"
        d["河口瑶族自治县"] = "河口"
        d["兰坪白族普米族自治县"] = "兰坪"
        d["贡山独龙族怒族自治县"] = "贡山"
        d["维西傈僳族自治县"] = "维西"
        d["融水苗族自治县"] = "融水"
        d["金秀瑶族自治县"] = "金秀"
        d["龙胜各族自治县"] = "龙胜"
        d["恭城瑶族自治县"] = "恭城"
        d["富川瑶族自治县"] = "富川"
        d["隆林各族自治县"] = "隆林"
        d["巴马瑶族自治县"] = "巴马"
        d["环江毛南族自治县"] = "环江"
        d["罗城仫佬族自治县"] = "罗城"
        d["都安瑶族自治县"] = "都安"
        d["大化瑶族自治县"] = "大化"
        d["道真仡佬族苗族自治县"] = "道真"
        d["丰宁满族自治县"] = "丰宁"
        d["围场满族蒙古族自治县"] = "围场"
        d["土默特右旗"] = "土右旗"
        d["土默特左旗"] = "土左旗"
        d["达尔罕茂明安联合旗"] = "达茂旗"
        d["察哈尔右翼前旗"] = "察右前旗"
        d["察哈尔右翼中旗"] = "察右中旗"
        d["察哈尔右翼后旗"] = "察右后旗"
        d["科尔沁左翼中旗"] = "科左中旗"
        d["科尔沁左翼后旗"] = "科左后旗"
        d["奈曼旗"] = "奈曼"
        d["扎鲁特旗"] = "扎鲁特"
        d["阿鲁科尔沁旗"] = "阿鲁旗"
        d["克什克腾旗"] = "克什克腾"
        d["翁牛特旗"] = "翁牛特"
        d["敖汉旗"] = "敖汉"
        d["达拉特旗"] = "达拉特"
        d["准格尔旗"] = "准格尔"
        d["鄂托克前旗"] = "鄂前旗"
        d["伊金霍洛旗"] = "伊金霍洛"
        d["乌拉特前旗"] = "乌前旗"
        d["乌拉特中旗"] = "乌中旗"
        d["乌拉特后旗"] = "乌后旗"
        d["阿巴嘎旗"] = "阿巴嘎"
        d["苏尼特左旗"] = "苏左旗"
        d["苏尼特右旗"] = "苏右旗"
        d["东乌珠穆沁旗"] = "东乌旗"
        d["西乌珠穆沁旗"] = "西乌旗"
        d["正蓝旗"] = "正兰旗"
        d["莫力达瓦达斡尔族自治旗"] = "莫力达瓦"
        d["鄂伦春自治旗"] = "鄂伦春旗"
        d["鄂温克族自治旗"] = "鄂温克旗"
        d["陈巴尔虎旗"] = "陈旗"
        d["新巴尔虎左旗"] = "新左旗"
        d["新巴尔虎右旗"] = "新右旗"
        d["科尔沁右翼中旗"] = "科右中旗"
        d["科尔沁右翼前旗"] = "科右前旗"
        d["阿拉善左旗"] = "阿左旗"
        d["阿拉善右旗"] = "阿右旗"
        d["额济纳旗"] = "额济纳"
        d["扎赉特旗"] = "扎赉特"
        d["六枝特区"] = "六枝"
        d["香港特别行政区"] = "香港"
        d["香港特別行政區"] = "香港"
        d["九龙城区"] = "九龙"
        d["九龍城區"] = "九龙"
        d["觀塘區"] = "九龙"
        d["深水埗區"] = "九龙"
        d["黃大仙區"] = "九龙"
        d["油尖旺區"] = "九龙"
        d["離島區"] = "新界"
        d["葵青區"] = "新界"
        d["北區"] = "新界"
        d["西貢區"] = "新界"
        d["沙田區"] = "新界"
        d["大埔區"] = "新界"
        d["荃灣區"] = "新界"
        d["屯門區"] = "新界"
        d["元朗區"] = "新界"
        d["香港特別行政區"] = "香港"
        d["中西區"] = "香港"
        d["東區"] = "香港"
        d["南區"] = "香港"
        d["灣仔區"] = "香港"
    }

    @JvmStatic
    fun getCitySimpleName(str: String?): String? = if (d.containsKey(str)) d[str] else str

    @JvmStatic
    fun getWeatherResId(str: String?): WeatherResId =
        if (!a.containsKey(str) || Utility.isDebugingUnknownIcon) a["99"]!! else a[str]!!

    @JvmStatic
    fun getWindDirRedId(str: String?): Int = if (b.containsKey(str)) b[str]!! else R.string.weather_null

    @JvmStatic
    fun isUnknown(str: String?): Boolean =
        "99" == str || str == null || "" == str || !a.containsKey(str)
}
