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
package io.netty.channel.socket.oio;

import static io.netty.channel.Channels.*;

import java.io.PushbackInputStream;
import java.net.SocketAddress;
import java.util.concurrent.Executor;

import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.MessageEvent;
import io.netty.util.internal.DeadLockProofWorker;

class OioClientSocketPipelineSink extends AbstractOioChannelSink {

    private final Executor workerExecutor;

    OioClientSocketPipelineSink(Executor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    @Override
    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        OioClientSocketChannel channel = (OioClientSocketChannel) e.getChannel();
        ChannelFuture future = e.getFuture();
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent stateEvent = (ChannelStateEvent) e;
            ChannelState state = stateEvent.getState();
            Object value = stateEvent.getValue();
            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    OioWorker.close(channel, future);
                }
                break;
            case BOUND:
                if (value != null) {
                    bind(channel, future, (SocketAddress) value);
                } else {
                    OioWorker.close(channel, future);
                }
                break;
            case CONNECTED:
                if (value != null) {
                    connect(channel, future, (SocketAddress) value);
                } else {
                    OioWorker.close(channel, future);
                }
                break;
            case INTEREST_OPS:
                OioWorker.setInterestOps(channel, future, ((Integer) value).intValue());
                break;
            }
        } else if (e instanceof MessageEvent) {
            OioWorker.write(
                    channel, future,
                    ((MessageEvent) e).getMessage());
        }
    }

    private void bind(
            OioClientSocketChannel channel, ChannelFuture future,
            SocketAddress localAddress) {
        try {
            channel.socket.bind(localAddress);
            future.setSuccess();
            fireChannelBound(channel, channel.getLocalAddress());
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void connect(
            OioClientSocketChannel channel, ChannelFuture future,
            SocketAddress remoteAddress) {

        boolean bound = channel.isBound();
        boolean connected = false;
        boolean workerStarted = false;

        future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        try {
            channel.socket.connect(
                    remoteAddress, channel.getConfig().getConnectTimeoutMillis());
            connected = true;

            // Obtain I/O stream.
            channel.in = new PushbackInputStream(channel.socket.getInputStream(), 1);
            channel.out = channel.socket.getOutputStream();

            // Fire events.
            future.setSuccess();
            if (!bound) {
                fireChannelBound(channel, channel.getLocalAddress());
            }
            fireChannelConnected(channel, channel.getRemoteAddress());

            // Start the business.
            DeadLockProofWorker.start(workerExecutor, new OioWorker(channel));
            workerStarted = true;
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        } finally {
            if (connected && !workerStarted) {
                OioWorker.close(channel, future);
            }
        }
    }
}
