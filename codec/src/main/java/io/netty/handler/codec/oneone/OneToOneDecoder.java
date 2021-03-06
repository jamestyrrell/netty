/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.oneone;

import static io.netty.channel.Channels.*;

import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelUpstreamHandler;
import io.netty.channel.MessageEvent;
import io.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.frame.Delimiters;
import io.netty.handler.codec.frame.FrameDecoder;

/**
 * Transforms a received message into another message.  Please note that this
 * decoder must be used with a proper {@link FrameDecoder} such as
 * {@link DelimiterBasedFrameDecoder} or you must implement proper framing
 * mechanism by yourself if you are using a stream-based transport such as
 * TCP/IP.  A typical setup for TCP/IP would be:
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 *
 * // Decoders
 * pipeline.addLast("frameDecoder", new {@link DelimiterBasedFrameDecoder}(80, {@link Delimiters#nulDelimiter()}));
 * pipeline.addLast("customDecoder", new {@link OneToOneDecoder}() { ... });
 *
 * // Encoder
 * pipeline.addLast("customEncoder", new {@link OneToOneEncoder}() { ... });
 * </pre>
 * @apiviz.landmark
 */
public abstract class OneToOneDecoder implements ChannelUpstreamHandler {

    /**
     * Creates a new instance with the current system character set.
     */
    protected OneToOneDecoder() {
    }

    @Override
    public void handleUpstream(
            ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendUpstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        Object originalMessage = e.getMessage();
        Object decodedMessage = decode(ctx, e.getChannel(), originalMessage);
        if (originalMessage == decodedMessage) {
            ctx.sendUpstream(evt);
        } else if (decodedMessage != null) {
            fireMessageReceived(ctx, decodedMessage, e.getRemoteAddress());
        }
    }

    /**
     * Transforms the specified received message into another message and return
     * the transformed message.  Return {@code null} if the received message
     * is supposed to be discarded.
     */
    protected abstract Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception;
}
