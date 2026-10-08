// 文件：AppModule.kt
// 职责：提供解析器注册表与解析器多绑定
// 依赖：ParserRegistry、NetdiskParser、QuarkParser、BaiduParser、UcParser、XunleiParser
// 协议：AGPL-3.0

package com.jieyundu.app.di

import com.jieyundu.app.data.settings.AppSettingsStore
import com.jieyundu.app.domain.downloader.DownloadSettingsPort
import com.jieyundu.app.domain.parser.NetdiskParser
import com.jieyundu.app.domain.parser.NetdiskServiceRouter
import com.jieyundu.app.domain.parser.ParserRegistry
import com.jieyundu.app.domain.parser.PersonalBrowser
import com.jieyundu.app.domain.parser.ShareBrowser
import com.jieyundu.app.domain.parser.baidu.BaiduParser
import com.jieyundu.app.domain.parser.quark.QuarkParser
import com.jieyundu.app.domain.parser.uc.UcParser
import com.jieyundu.app.domain.parser.xunlei.XunleiParser
import com.jieyundu.app.domain.transfer.ShareDownloadPreparer
import com.jieyundu.app.domain.transfer.ShareTransfer
import com.jieyundu.app.domain.transfer.UcShareTransfer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * 应用级依赖提供者。
 *
 * 职责：把各网盘解析器以多绑定方式汇入 [ParserRegistry]（新增网盘只需在此加一行）。
 *
 * 说明：下载进度落库端口（DownloadProgressPort）自阶段 5 起改由 [DatabaseModule]
 * 通过 Room 的 DownloadDao 提供，本模块的临时端口绑定已删除
 * （依据【修订 JYD-ERRATA-2026-10-03】修订一）。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 注册夸克解析器。
     *
     * @param parser 夸克解析器实例。
     * @return 以 [NetdiskParser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideQuarkParser(parser: QuarkParser): NetdiskParser = parser

    /**
     * 注册百度网盘解析器（阶段 9 占位骨架，实现待抓包）。
     *
     * @param parser 百度网盘解析器实例。
     * @return 以 [NetdiskParser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideBaiduParser(parser: BaiduParser): NetdiskParser = parser

    /**
     * 注册 UC 网盘解析器（阶段 9 占位骨架，实现待抓包）。
     *
     * @param parser UC 网盘解析器实例。
     * @return 以 [NetdiskParser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideUcParser(parser: UcParser): NetdiskParser = parser

    /**
     * 注册迅雷云盘解析器（阶段 9 占位骨架，实现待抓包）。
     *
     * @param parser 迅雷云盘解析器实例。
     * @return 以 [NetdiskParser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideXunleiParser(parser: XunleiParser): NetdiskParser = parser

    /**
     * 提供解析器注册表。
     *
     * @param parsers 全部已注册解析器。
     * @return 注册表实例。
     */
    @Provides
    @Singleton
    fun provideParserRegistry(parsers: Set<@JvmSuppressWildcards NetdiskParser>): ParserRegistry =
        ParserRegistry(parsers.toList())

    /**
     * 注册夸克分享目录浏览器（多绑定）。
     *
     * @param parser 夸克解析器实例（同时实现 [NetdiskParser] 与 [ShareBrowser]）。
     * @return 以 [ShareBrowser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideQuarkShareBrowser(parser: QuarkParser): ShareBrowser = parser

    /**
     * 注册 UC 分享目录浏览器（多绑定，B2）。
     *
     * @param parser UC 解析器实例（同时实现 [NetdiskParser] 与 [ShareBrowser]）。
     * @return 以 [ShareBrowser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideUcShareBrowser(parser: UcParser): ShareBrowser = parser

    /**
     * 注册百度分享目录浏览器（多绑定，B3-1）。
     *
     * @param parser 百度解析器实例（同时实现 [NetdiskParser] 与 [ShareBrowser]）。
     * @return 以 [ShareBrowser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideBaiduShareBrowser(parser: BaiduParser): ShareBrowser = parser

    /**
     * 注册迅雷分享目录浏览器（多绑定，JYD-XUNLEI-P1A-2026-10-08）。
     *
     * @param parser 迅雷解析器实例（同时实现 [NetdiskParser] 与 [ShareBrowser]）。
     * @return 以 [ShareBrowser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideXunleiShareBrowser(parser: XunleiParser): ShareBrowser = parser

    /**
     * 注册夸克个人网盘浏览器（多绑定）。
     *
     * @param parser 夸克解析器实例（同时实现 [NetdiskParser] / [ShareBrowser] / [PersonalBrowser]）。
     * @return 以 [PersonalBrowser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideQuarkPersonalBrowser(parser: QuarkParser): PersonalBrowser = parser

    /**
     * 注册 UC 个人网盘浏览器（多绑定，B2）。
     *
     * @param parser UC 解析器实例（同时实现 [NetdiskParser] / [ShareBrowser] / [PersonalBrowser]）。
     * @return 以 [PersonalBrowser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideUcPersonalBrowser(parser: UcParser): PersonalBrowser = parser

    /**
     * 注册夸克分享下载准备器（多绑定）。
     *
     * @param transfer 夸克转存器实例。
     * @return 以 [ShareDownloadPreparer] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideQuarkShareDownloadPreparer(transfer: ShareTransfer): ShareDownloadPreparer = transfer

    /**
     * 注册 UC 分享下载准备器（多绑定，B2）。
     *
     * @param transfer UC 转存器实例。
     * @return 以 [ShareDownloadPreparer] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideUcShareDownloadPreparer(transfer: UcShareTransfer): ShareDownloadPreparer = transfer

    /**
     * 提供网盘能力路由器（按 type 取用多绑定实现）。
     *
     * @param shareBrowsers 全部分享目录浏览器。
     * @param personalBrowsers 全部个人网盘浏览器。
     * @param preparers 全部分享转存器。
     * @return 路由器实例。
     */
    @Provides
    @Singleton
    fun provideNetdiskServiceRouter(
        shareBrowsers: Set<@JvmSuppressWildcards ShareBrowser>,
        personalBrowsers: Set<@JvmSuppressWildcards PersonalBrowser>,
        preparers: Set<@JvmSuppressWildcards ShareDownloadPreparer>
    ): NetdiskServiceRouter =
        NetdiskServiceRouter(shareBrowsers, personalBrowsers, preparers)

    /**
     * 把 [AppSettingsStore] 绑定为下载设置端口（C1）。
     *
     * 说明：[AppSettingsStore] 已是 `@Singleton` 且以构造注入创建，此处仅把其接口身份
     * 暴露给 domain 层的 [DownloadSettingsPort]，供下载引擎读取并发/重试/限速设置。
     *
     * @param store 应用设置存储实例。
     * @return 下载设置端口实现。
     */
    @Provides
    @Singleton
    fun provideDownloadSettingsPort(store: AppSettingsStore): DownloadSettingsPort = store
}