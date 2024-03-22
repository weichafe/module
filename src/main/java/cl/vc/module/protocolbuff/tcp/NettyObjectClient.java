package cl.vc.module.protocolbuff.tcp;

import cl.vc.module.protocolbuff.interfaces.NettyInterface;
import cl.vc.module.protocolbuff.notification.NotificationMessage;
import cl.vc.module.protocolbuff.session.SessionsMessage;
import com.google.protobuf.Message;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import static cl.vc.module.protocolbuff.tcp.ProtoMapping.packMessage;
import static cl.vc.module.protocolbuff.tcp.ProtoMapping.unpackMessage;

public class NettyObjectClient extends Thread {
    private final String hostname;
    private final int port;
    private EventLoopGroup group;
    private Channel channel;
    private boolean connected = false;
    private boolean stopped = false;
    private ByteBuf pong = packMessage(SessionsMessage.Pong.newBuilder().build());
    private String detination;
    private NotificationMessage.Component component;
    private NettyInterface nettyInterface;

    public NettyObjectClient(String host, NettyInterface nettyInterface, String detination, NotificationMessage.Component  component) {

        String[] parts = host.split(":", 2);
        assert parts.length == 2;
        this.hostname = parts[0];
        this.port = Integer.parseInt(parts[1]);
        this.nettyInterface = nettyInterface;
        this.detination = detination;
        this.component = component;
    }

    public void run() {
        while (!stopped) {
            try {
                NettyObjectClient thisClient = this;
                group = new NioEventLoopGroup();
                Bootstrap bootStrap = new Bootstrap();
                bootStrap.group(group);
                bootStrap.channel(NioSocketChannel.class);
                bootStrap.option(ChannelOption.SO_KEEPALIVE, true);
                bootStrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
                bootStrap.handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new ProtobufVarint32FrameDecoder());
                        p.addLast(new ProtobufVarint32LengthFieldPrepender());
                        p.addLast(new NettyProtoHandler(thisClient));
                        p.addLast(new NettyProtoEncoder());
                    }
                });

                channel = bootStrap.connect(hostname, port).sync().channel();
                channel.closeFuture().sync();

            } catch (Exception e) {
                SessionsMessage.Disconnect disconnect = SessionsMessage.Disconnect.newBuilder().setId(e.getMessage())
                        .setDestination(detination)
                        .setComponent(component)
                        .setText("disconnected " + e.getMessage()).build();
                nettyInterface.send(new TransportingObjects(null, disconnect));

            } finally {
                group.shutdownGracefully();
            }

            if (!stopped) {
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                }
            }
        }
    }

    public void stopClient() {
        try {
            stopped = true;
            channel.close();
            group.shutdownGracefully();

        } catch (Exception e) {
        }
    }

    public void clientDisconnected(ChannelHandlerContext ctx) {
        try {

            if (connected) {
                SessionsMessage.Disconnect disconnect = SessionsMessage.Disconnect.newBuilder()
                        .setId(ctx.channel().id().toString())
                        .setDestination(detination)
                        .setComponent(component)
                        .setText("disconnected").build();
                nettyInterface.send(new TransportingObjects(ctx, disconnect));
                connected = false;
            }

        } catch (Exception e) {
        }
    }

    public void clientConnected(ChannelHandlerContext ctx) {
        try {

            connected = true;
            SessionsMessage.Connect connect = SessionsMessage.Connect
                    .newBuilder().setId(ctx.channel().id().toString())
                    .setDestination(detination)
                    .setComponent(component)
                    .setText("connected")
                    .build();
            nettyInterface.send(new TransportingObjects(ctx, connect));

        } catch (Exception e) {
        }
    }

    protected void messageReceived(ChannelHandlerContext ctx, ByteBuf msg) {
        try {

            Message message = unpackMessage(ByteBufUtil.getBytes(msg));

            if (message instanceof SessionsMessage.Ping) {
                channel.writeAndFlush(pong);
            } else {
                nettyInterface.send(new TransportingObjects(ctx, message));
            }

        } catch (Exception e) {
        }
    }

    public void sendMessage(Message message) {
        try {
            if(message == null) {
                return;
            }
            if (connected) {
                ByteBuf orderss = packMessage(message);
                channel.writeAndFlush(orderss);
            } else {
            }

        } catch (Exception e) {
        }
    }

}
