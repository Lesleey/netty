/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;


/**
 * {@link ChannelOutboundHandlerAdapter} which encodes message in a stream-like fashion from one message to an
 * {@link ByteBuf}.
 *
 *
 * Example implementation which encodes {@link Integer}s to a {@link ByteBuf}.
 *
 * <pre>
 *     public class IntegerEncoder extends {@link MessageToByteEncoder}&lt;{@link Integer}&gt; {
 *         {@code @Override}
 *         public void encode({@link ChannelHandlerContext} ctx, {@link Integer} msg, {@link ByteBuf} out)
 *                 throws {@link Exception} {
 *             out.writeInt(msg);
 *         }
 *     }
 * </pre>
 *
 *  编码器: 以流式的方式将一条消息编码为 ByteBuf
 */
public abstract class MessageToByteEncoder<I> extends ChannelOutboundHandlerAdapter {

    private final TypeParameterMatcher matcher;
    private final boolean preferDirect;

    /**
     * see {@link #MessageToByteEncoder(boolean)} with {@code true} as boolean parameter.
     */
    protected MessageToByteEncoder() {
        this(true);
    }

    /**
     * see {@link #MessageToByteEncoder(Class, boolean)} with {@code true} as boolean value.
     */
    protected MessageToByteEncoder(Class<? extends I> outboundMessageType) {
        this(outboundMessageType, true);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     *
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     */
    protected MessageToByteEncoder(boolean preferDirect) {
        matcher = TypeParameterMatcher.find(this, MessageToByteEncoder.class, "I");
        this.preferDirect = preferDirect;
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   The type of messages to match
     * @param preferDirect          {@code true} if a direct {@link ByteBuf} should be tried to be used as target for
     *                              the encoded messages. If {@code false} is used it will allocate a heap
     *                              {@link ByteBuf}, which is backed by an byte array.
     */
    protected MessageToByteEncoder(Class<? extends I> outboundMessageType, boolean preferDirect) {
        matcher = TypeParameterMatcher.get(outboundMessageType);
        this.preferDirect = preferDirect;
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelOutboundHandler} in the {@link ChannelPipeline}.
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    /**
     *  编码的主要逻辑
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = null;
        try {
            // 如果当前编码器支持对接收的消息类型处理，则开始编码
            if (acceptOutboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                buf = allocateBuffer(ctx, cast, preferDirect);
                //1. 由具体实现类进行编码
                try {
                    encode(ctx, cast, buf);
                } finally {
                    ReferenceCountUtil.release(cast);
                }
                //2. 如果缓冲区包含可发送的字节，则调用 write 发送
                if (buf.isReadable()) {
                    ctx.write(buf, promise);
                 //3. 否则，发送空缓冲区
                } else {
                    buf.release();
                    ctx.write(Unpooled.EMPTY_BUFFER, promise);
                }
                buf = null;
             // 否则，交由下一处理器处理
            } else {
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable e) {
            throw new EncoderException(e);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }

    /**
     * Allocate a {@link ByteBuf} which will be used as argument of {@link #encode(ChannelHandlerContext, I, ByteBuf)}.
     * Sub-classes may override this method to return {@link ByteBuf} with a perfect matching {@code initialCapacity}.
     */
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, @SuppressWarnings("unused") I msg,
                               boolean preferDirect) throws Exception {
        if (preferDirect) {
            return ctx.alloc().ioBuffer();
        } else {
            return ctx.alloc().heapBuffer();
        }
    }

    /**
     * Encode a message into a {@link ByteBuf}. This method will be called for each written message that can be handled
     * by this encoder.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToByteEncoder} belongs to
     * @param msg           the message to encode
     * @param out           the {@link ByteBuf} into which the encoded message will be written
     * @throws Exception    is thrown if an error occurs
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, ByteBuf out) throws Exception;

    protected boolean isPreferDirect() {
        return preferDirect;
    }
}
