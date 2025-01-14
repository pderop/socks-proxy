/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.contrib.handler.codec.socksx;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty.contrib.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.contrib.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.contrib.handler.codec.socksx.v5.Socks5AddressEncoder;
import io.netty.contrib.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.contrib.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty5.handler.codec.ByteToMessageDecoder;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Detects the version of the current SOCKS connection and initializes the pipeline with
 * {@link Socks4ServerDecoder} or {@link Socks5InitialRequestDecoder}.
 */
public class SocksPortUnificationServerHandler extends ByteToMessageDecoder {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SocksPortUnificationServerHandler.class);

    private final Socks5ServerEncoder socks5encoder;

    /**
     * Creates a new instance with the default configuration.
     */
    public SocksPortUnificationServerHandler() {
        this(Socks5ServerEncoder.DEFAULT);
    }

    /**
     * Creates a new instance with the specified {@link Socks5ServerEncoder}.
     * This constructor is useful when a user wants to use an alternative {@link Socks5AddressEncoder}.
     */
    public SocksPortUnificationServerHandler(Socks5ServerEncoder socks5encoder) {
        requireNonNull(socks5encoder, "socks5encoder");

        this.socks5encoder = socks5encoder;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
        final int readerIndex = in.readerOffset();
        if (in.writerOffset() == readerIndex) {
            return;
        }

        ChannelPipeline p = ctx.pipeline();
        final byte versionVal = in.getByte(readerIndex);
        SocksVersion version = SocksVersion.valueOf(versionVal);

        switch (version) {
        case SOCKS4a:
            logKnownVersion(ctx, version);
            p.addAfter(ctx.name(), null, Socks4ServerEncoder.INSTANCE);
            p.addAfter(ctx.name(), null, new Socks4ServerDecoder());
            break;
        case SOCKS5:
            logKnownVersion(ctx, version);
            p.addAfter(ctx.name(), null, socks5encoder);
            p.addAfter(ctx.name(), null, new Socks5InitialRequestDecoder());
            break;
        default:
            logUnknownVersion(ctx, versionVal);
            in.skipReadableBytes(in.readableBytes());
            ctx.close();
            return;
        }

        p.remove(this);
    }

    private static void logKnownVersion(ChannelHandlerContext ctx, SocksVersion version) {
        logger.debug("{} Protocol version: {}({})", ctx.channel(), version);
    }

    private static void logUnknownVersion(ChannelHandlerContext ctx, byte versionVal) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} Unknown protocol version: {}", ctx.channel(), versionVal & 0xFF);
        }
    }
}
