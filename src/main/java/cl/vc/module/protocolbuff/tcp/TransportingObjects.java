package cl.vc.module.protocolbuff.tcp;

import com.google.protobuf.Message;
import io.netty.channel.ChannelHandlerContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Data
public class TransportingObjects {

    private final ChannelHandlerContext ctx;
    private final Message message;

    public TransportingObjects(ChannelHandlerContext ctx, Message message) {
        this.ctx = ctx;
        this.message = message;
    }
}
