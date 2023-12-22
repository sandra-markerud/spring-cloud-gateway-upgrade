package de.markerud.upgrade.configuration

import io.micrometer.context.ContextSnapshotFactory
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise

class TracingChannelDuplexHandler(
    private val delegate: ChannelDuplexHandler? = null,
    private val contextSnapshotFactory: ContextSnapshotFactory
) : ChannelDuplexHandler() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        contextSnapshotFactory.setThreadLocalsFrom<String>(ctx.channel()).use {
            delegate?.channelRead(ctx, msg) ?: ctx.fireChannelRead(msg)
        }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        contextSnapshotFactory.setThreadLocalsFrom<String>(ctx.channel()).use {
            delegate?.write(ctx, msg, promise) ?: ctx.write(msg, promise)
        }
    }

    override fun flush(ctx: ChannelHandlerContext) {
        contextSnapshotFactory.setThreadLocalsFrom<String>(ctx.channel()).use { ctx.flush() }
    }

}