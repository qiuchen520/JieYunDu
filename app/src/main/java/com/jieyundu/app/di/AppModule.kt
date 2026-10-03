// 文件：AppModule.kt
// 职责：提供解析器注册表与解析器多绑定
// 依赖：ParserRegistry、NetdiskParser、QuarkParser、BaiduParser、UcParser、XunleiParser
// 协议：AGPL-3.0

package com.jieyundu.app.di

import com.jieyundu.app.domain.parser.NetdiskParser
import com.jieyundu.app.domain.parser.ParserRegistry
import com.jieyundu.app.domain.parser.PersonalBrowser
import com.jieyundu.app.domain.parser.ShareBrowser
import com.jieyundu.app.domain.parser.baidu.BaiduParser
import com.jieyundu.app.domain.parser.quark.QuarkParser
import com.jieyundu.app.domain.parser.uc.UcParser
import com.jieyundu.app.domain.parser.xunlei.XunleiParser
import com.jieyundu.app.domain.transfer.ShareDownloadPreparer
import com.jieyundu.app.domain.transfer.ShareTransfer
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
     * 提供分享目录浏览器（当前仅夸克实现；流程 B 个人网盘浏览接入后按 type 路由）。
     *
     * @param parser 夸克解析器实例（同时实现 [NetdiskParser] 与 [ShareBrowser]）。
     * @return 以 [ShareBrowser] 身份暴露的同一实例。
     */
    @Provides
    @Singleton
    fun provideShareBrowser(parser: QuarkParser): ShareBrowser = parser

    /**
     * 提供个人网盘浏览器（当前仅夸克实现；UC / 百度 / 迅雷接入后按 type 路由）。
     *
     * 流程 B（网盘管理）：浏览本账号个人网盘目录与容量。
     *
     * @param parser 夸克解析器实例（同时实现 [NetdiskParser] / [ShareBrowser] / [PersonalBrowser]）。
     * @return 以 [PersonalBrowser] 身份暴露的同一实例。
     */
    @Provides
    @Singleton
    fun providePersonalBrowser(parser: QuarkParser): PersonalBrowser = parser

    /**
     * 提供分享下载准备器（转存到临时目录 + 轮询 + 取直链）。
     *
     * @param transfer 夸克转存器实例。
     * @return 以 [ShareDownloadPreparer] 身份暴露的同一实例。
     */
    @Provides
    @Singleton
    fun provideShareDownloadPreparer(transfer: ShareTransfer): ShareDownloadPreparer = transfer
}