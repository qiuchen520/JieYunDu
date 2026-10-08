// 文件：XunleiConfig.kt
// 职责：迅雷协议常量（域名 / 凭据 / UA / 签名盐 / 官方兜底指纹）——全部照抄抓包事实，不自造
// 依赖：无
// 协议：AGPL-3.0

package com.jieyundu.app.domain.parser.xunlei

/**
 * 迅雷云盘协议常量（【JYD-XUNLEI-P1A-2026-10-08】）。
 *
 * 事实来源：《抓包事实.md》§4（凭据 / 签名 / 端点 / UA）、§6.3（认证 Header 与业务字段）、
 * §11.4（全量接口表）。
 *
 * ⚠️ 边界（铁律 R3 + Owner 显式放行）：
 * - 下列常量是**同类客户端通用常量**（非本项目的账号密钥、非用户数据），按 Owner 放行照抄；
 * - **不得**在此新增任何未经抓包证实的字段 / 端点 / 参数；
 * - 客户端凭据常量只允许存在于代码与内部文档，不得写入任何对外文档（§6.1 脱敏红线）。
 */
object XunleiConfig {

    /** 认证主机基址（验证码盾 / 登录 / 换 token / 刷新）。 */
    const val AUTH_BASE_URL = "https://xluser-ssl.xunlei.com/"

    /** 业务主机基址（分享 / 文件 / 转存 / 任务 / 配额）。 */
    const val PAN_BASE_URL = "https://api-pan.xunlei.com/"

    /** Web 端 CLIENT_ID（§4 凭据表）。 */
    const val WEB_CLIENT_ID = "Xp6pAdwyJv9sQuoN"

    /** Web 端 CLIENT_SECRET（§4 凭据表）。 */
    const val WEB_CLIENT_SECRET = "standard_a@api#"

    /** App 端 CLIENT_ID（§4 凭据表）。 */
    const val APP_CLIENT_ID = "Xp6vsxz_7IYVw2BB"

    /** App 端 CLIENT_SECRET（§4 凭据表）。 */
    const val APP_CLIENT_SECRET = "Xp6vsy4tN9toTVdMSpomVdXpRmES"

    /** App 版本号（同时是 `X-Client-Version` 头值）。 */
    const val APP_VERSION = "8.31.0.9726"

    /** App 包名（devicesign 公式的一段）。 */
    const val PACKAGE_NAME = "com.xunlei.downloadprovider"

    /** APPID（devicesign 公式的一段）。 */
    const val APP_ID = "40"

    /** APP_KEY（devicesign 公式的一段）。 */
    const val APP_KEY = "34a062aaa22f906fca4fefe9fb3a3021"

    /** App 客户端 UA（认证与业务接口；§4「UA」之 App）。 */
    const val UA_APP =
        "ANDROID-com.xunlei.downloadprovider/8.31.0.9726 netWorkType/5G appid/40 " +
            "deviceName/Xiaomi_M2004j7ac deviceModel/M2004J7AC OSVersion/12 " +
            "protocolVersion/301 platformVersion/10 sdkVersion/512000 Oauth2Client/0.9 " +
            "(Linux 4_14_186-perf-gddfs8vbb238b) (JAVA 0)"

    /** Web UA（内嵌登录页；§4「UA」之 Web）。 */
    const val UA_WEB =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    /** 登录接口专用 UA（§6.3）。 */
    const val UA_LOGIN = "android-ok-http-client/xl-acc-sdk/version-5.1.3.513006"

    /** 发短信接口专用 UA（§6.3）。 */
    const val UA_SMS = "android-ok-http-client/xl-acc-sdk/version-5.0.12.512000"

    /** OAuth2 重定向地址（§6.3 captcha/init 的 redirect_uri）。 */
    const val REDIRECT_URI = "xlaccsdk01://xunlei.com/callback?state=harbor"

    /** 验证码盾注入用的 App 名（§4 ⑤ `XlCaptcha.init` 配置）。 */
    const val APP_NAME_FOR_CAPTCHA = "ANDROID-com.xunlei.downloadprovider"

    /** 验证码盾注入用的 platformVersion（§4 ⑤ 配置为 `"10"`）。 */
    const val CAPTCHA_PLATFORM_VERSION = "10"

    /** 业务与认证请求的 Origin / Referer（§11.4）。 */
    const val ORIGIN = "https://pan.xunlei.com"
    const val REFERER = "https://pan.xunlei.com/"

    /**
     * 签名①：`captcha_sign` 的 10 个盐（§4「签名①」，**顺序即文档顺序，照抄**）。
     *
     * ⚠️ 缺依据（`TODO(用户抓包)`）：文档只给了「`captcha_sign = 1.<10层md5>`」与这 10 个盐，
     * **没有给折叠算法**——即：以哪一段作为初始输入、10 层如何串联（逐层加盐？盐在前在后？）、
     * 以及是否混入 deviceId/timestamp。在拿到该算法前，本常量仅作留档，**不实现任何猜测公式**
     * （铁律 R3：不明处标 TODO，不臆造）。
     */
    val CAPTCHA_SALTS: List<String> = listOf(
        "9uJNVj/wLmdwKrJaVj/omlQ",
        "Oz64Lp0GigmChHMf/6TNfxx7O9PyopcczMsnf",
        "Eb+L7Ce+Ej48u",
        "jKY0",
        "ASr0zCl6v8W4aidjPK5KHd1Lq3t+vBFf41dqv5+fnOd",
        "wQlozdg6r1qxh0eRmt3QgNXOvSZO6q/GXK",
        "gmirk+ciAvIgA/cxUUCema47jr/YToixTT+Q6O",
        "5IiCoM9B1/788ntB",
        "P07JH0h6qoM6TSUAK2aL9T5s2QBVeY9JWvalf",
        "+oK0AN"
    )

    /** 签名②：devicesign 固定前缀（§4）。 */
    const val DEVICE_SIGN_PREFIX = "div101."

    /** 官方抓包兜底 deviceId（§4「官方抓包 fallback 值」；仅异常路径使用）。 */
    const val FALLBACK_DEVICE_ID = "78a70629a2b17d0b4302317ffa94807a"

    /** 官方抓包兜底 peerId（同上）。 */
    const val FALLBACK_PEER_ID = "92df4c42e0926ff55f1c605ebe4c3754"

    /** 分享解析固定分页与缩略图参数（§11.4 #10/#11）。 */
    const val SHARE_PAGE_SIZE = 100
    const val THUMBNAIL_SIZE_SMALL = "SIZE_SMALL"

    /** 分享状态（§11.4 #10：`share_status` 三种取值）。 */
    const val SHARE_STATUS_PASS_CODE_EMPTY = "PASS_CODE_EMPTY"
    const val SHARE_STATUS_PASS_CODE_ERROR = "PASS_CODE_ERROR"
    const val SHARE_STATUS_PASS_CODE_NEED = "PASS_CODE_NEED"

    /** `files[]` 条目中表示目录的 `kind` 取值（§11.4 #8 建目录请求体实证）。 */
    const val KIND_FOLDER = "drive#folder"

    /** 加密存储用的文件名（token / 指纹各自一份）。 */
    const val PREFS_FILE_TOKENS = "xunlei_tokens"
    const val PREFS_FILE_FINGERPRINT = "xunlei_fingerprint"

    /** 分享根目录的 parent_id 占位（§11.4 #11：子目录接口用 parent_id）。 */
    const val SHARE_ROOT_PARENT_ID = ""
}
