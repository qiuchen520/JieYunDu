// 文件：AppModule.kt
// 职责：提供解析器注册表与解析器多绑定
// 依赖：ParserRegistry、NetdiskParser、QuarkParser
// 协议：AGPL-3.0

package com.jieyundu.app.di

import com.jieyundu.app.domain.parser.NetdiskParser
import com.jieyundu.app.domain.parser.ParserRegistry
import com.jieyundu.app.domain.parser.quark.QuarkParser
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
     * 注册夸克解析器（阶段 9 会以同样方式注册其余三家）。
     *
     * @param parser 夸克解析器实例。
     * @return 以 [NetdiskParser] 身份暴露的同一实例。
     */
    @Provides
    @IntoSet
    fun provideQuarkParser(parser: QuarkParser): NetdiskParser = parser

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
}
