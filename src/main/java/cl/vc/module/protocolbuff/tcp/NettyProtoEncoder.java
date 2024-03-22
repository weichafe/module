package cl.vc.module.protocolbuff.tcp;

import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import static cl.vc.module.protocolbuff.tcp.ProtoMapping.packMessage;

public class NettyProtoEncoder extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Message) {
            ByteBuf bytes = packMessage((Message) msg);
            super.write(ctx, bytes, promise);
        } else {
            super.write(ctx, msg, promise);
        }
    }
}