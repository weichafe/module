package cl.vc.module.protocolbuff.tcp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import static cl.vc.module.protocolbuff.tcp.ProtoMapping.packMessage;
import static cl.vc.module.protocolbuff.tcp.ProtoMapping.unpackMessage;

@Slf4j
public class NettyObjectClient extends Thread {
    private final String hostname;
    private final int port;
    private EventLoopGroup group;
    private Channel channel;
    @Getter
    private boolean connected = false;
    private boolean stopped = false;
    private ByteBuf pong = packMessage(SessionsMessage.Pong.newBuilder().build());
    private String detination;
    private NotificationMessage.Component component;
    private NettyInterface nettyInterface;

    public NettyObjectClient(String host, NettyInterface nettyInterface, String path, String detination, NotificationMessage.Component  component) {

        String[] parts = host.split(":", 2);
        assert parts.length == 2;
        this.hostname = parts[0];
        this.port = Integer.parseInt(parts[1]);
        this.nettyInterface = nettyInterface;
        this.detination = detination;
        this.component = component;

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("[%date{ISO8601}] %msg%n");
        encoder.start();

        FileAppender appender = new FileAppender();
        appender.setContext(context);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String currentDate = dateFormat.format(new Date());
        appender.setFile(path + "_" + currentDate + ".log");
        appender.setAppend(true);
        appender.setEncoder(encoder);
        appender.start();

        Logger fileLog = context.getLogger(path.replace(File.separator, "."));
        fileLog.setAdditive(false);
        fileLog.setLevel(Level.ALL);
        fileLog.addAppender(appender);
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
                log.error("Netty Client Exception ({}:{}): {}", hostname, port, e.getMessage());
                SessionsMessage.Disconnect disconnect = SessionsMessage.Disconnect.newBuilder().setId(e.getMessage())
                        .setDestination(detination)
                        .setComponent(component)
                        .setText("disconnected " + e.getMessage()).build();
                nettyInterface.send(disconnect);

            } finally {
                group.shutdownGracefully();
            }

            if (!stopped) {
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
        }
    }

    public void sendMessage(Message message) {
        try {

            if(message == null) {
                log.error("message null. {}", message);
                return;
            }

            if (connected) {
                ByteBuf orderss = packMessage(message);
                channel.writeAndFlush(orderss);
                nettyInterface.send(message);

            } else {
                log.error("Cannot send Message. Connection is closed. {}", message);
            }

        } catch (Exception e) {
            log.error("Cannot send Message...", e);
        }
    }


    public class NettyProtoHandler extends SimpleChannelInboundHandler<ByteBuf> {

        private final NettyObjectClient clientThread;

        public NettyProtoHandler(NettyObjectClient clientThread) {
            this.clientThread = clientThread;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelRegistered();
            ctx.flush();
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelUnregistered();
            clientThread.clientDisconnected(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelActive();
            clientThread.clientConnected(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            clientThread.messageReceived(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Netty Client (ID: {}): {}", ctx.channel().id(), cause.getMessage());
            ctx.close();
        }
    }

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
}
