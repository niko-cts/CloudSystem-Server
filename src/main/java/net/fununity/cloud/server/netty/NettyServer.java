package net.fununity.cloud.server.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class NettyServer implements Runnable {

	private final String hostname;
	private final int port;
	private EventLoopGroup bossGroup;

	public void run() {
		bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			log.debug("Booting up NettyServer with {}:{}....", hostname, port);
			ServerBootstrap bootstrap = new ServerBootstrap();
			bootstrap.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.handler(new LoggingHandler(LogLevel.INFO)) // For logging purposes
					.localAddress(hostname, port)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel socketChannel) {
							socketChannel.pipeline().addLast(
									new KryoEncoder(),
									new KryoDecoder(),
									new NettyHandler());
						}
					});
			ChannelFuture channelFuture = bootstrap.bind().sync();
			log.info("Server started on {}:{}", hostname, port);
			channelFuture.channel().closeFuture().sync();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("NioSocket was interrupted: ", e);
		} finally {
			log.debug("Shutting down boss and workergroup...");
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}

	public void stop() {
		bossGroup.shutdownGracefully();
	}
}
