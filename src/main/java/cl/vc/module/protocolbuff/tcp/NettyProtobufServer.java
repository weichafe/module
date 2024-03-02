package cl.vc.module.protocolbuff.tcp;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.routing.RoundRobinPool;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import cl.vc.module.protocolbuff.session.SessionsMessage;
import com.google.protobuf.Message;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static cl.vc.module.protocolbuff.tcp.ProtoMapping.packMessage;
import static cl.vc.module.protocolbuff.tcp.ProtoMapping.unpackMessage;

@Slf4j
@Data
public class NettyProtobufServer extends Thread {

    private final String hostname;
    private final int port;
    private final ActorRef receiverActor;
    private EventLoopGroup bossGroup, workerGroup;
    private final List<Channel> channels = new ArrayList<>();
    private ActorSystem system = ActorSystem.create("server");
    private ActorRef actorLogger;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private String destination;

    public NettyProtobufServer(String host, ActorRef receiverActor, String path, String destination) throws Exception {

        String[] parts = host.split(":", 2);
        assert parts.length == 2;

        this.hostname = parts[0];
        this.port = Integer.parseInt(parts[1]);
        this.receiverActor = receiverActor;
        this.destination = destination;
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

        actorLogger = system.actorOf(new RoundRobinPool(10).props(LoggerActor.props(fileLog, true)), "actorPool");
    }

    public NettyProtobufServer(String host, ActorRef receiverActor, String path, String destination, Boolean isLog) throws Exception {

        String[] parts = host.split(":", 2);
        assert parts.length == 2;

        this.hostname = parts[0];
        this.port = Integer.parseInt(parts[1]);
        this.receiverActor = receiverActor;
        this.destination = destination;
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

        actorLogger = system.actorOf(new RoundRobinPool(10).props(LoggerActor.props(fileLog, isLog)), "actorPool");
    }

    public void run() {
        try {
            NettyProtobufServer thisServer = this;
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();

            scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    channels.forEach(s-> {
                        ByteBuf ping = packMessage(SessionsMessage.Ping.newBuilder().setId(s.id().toString()).build());
                        s.writeAndFlush(ping);
                    });
                }
            }, 0, 5, TimeUnit.SECONDS);

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new ProtobufVarint32FrameDecoder());
                            p.addLast(new ProtobufVarint32LengthFieldPrepender());
                            p.addLast(new ProtobufServerHandler(thisServer));
                            p.addLast(new NettyProtoEncoder());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            Channel channel = bootstrap.bind(hostname, port).sync().channel();
            channel.closeFuture().sync();

        } catch (Exception e) {
            log.error(e.getMessage());

        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public void stopServer() {
        try {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void clientDisconnected(ChannelHandlerContext ctx) {
        try {
            channels.remove(ctx.channel());
            SessionsMessage.Disconnect disconnect = SessionsMessage.Disconnect.newBuilder()
                    .setId(ctx.channel().id().toString())
                    .setDestination(destination)
                    .setText("disconnected").build();
            receiverActor.tell(new TransportingObjects(ctx, disconnect), ActorRef.noSender());


        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void clientConnected(ChannelHandlerContext ctx) {
        try {
            channels.add(ctx.channel());
            SessionsMessage.Connect connect = SessionsMessage.Connect.newBuilder()
                    .setId(ctx.channel().id().toString()).setDestination(destination).setText("disconnected").build();
            receiverActor.tell(new TransportingObjects(ctx, connect), ActorRef.noSender());

            actorLogger.tell(connect, ActorRef.noSender());


        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    protected void messageReceived(ChannelHandlerContext ctx, ByteBuf msg) {
        try {

            Message message = unpackMessage(ByteBufUtil.getBytes(msg));
            receiverActor.tell(new TransportingObjects(ctx, message), ActorRef.noSender());
            actorLogger.tell(message, ActorRef.noSender());

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }


    public class ProtobufServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final NettyProtobufServer serverThread;

        public ProtobufServerHandler(NettyProtobufServer serverThread) {
            this.serverThread = serverThread;
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            log.info("Netty Client (ID: {}) connected.", ctx.channel().id());
            ctx.fireChannelRegistered();
            serverThread.clientConnected(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
            serverThread.messageReceived(channelHandlerContext, byteBuf);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            log.info("Netty Client (ID: {}) disconnected.", ctx.channel().id());
            ctx.fireChannelUnregistered();
            serverThread.clientDisconnected(ctx);
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

    public static void main(String[] arg) throws Exception {
        NettyProtobufServer nettyProtobufServer = new NettyProtobufServer("localhost:8080", null, "./logs/server", "server prueba");
        new Thread(nettyProtobufServer).start();
        while (true){
        }

    }



}
