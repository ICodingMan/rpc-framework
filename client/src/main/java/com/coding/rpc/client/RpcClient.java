package com.coding.rpc.client;

import com.coding.rpc.common.bean.RpcRequest;
import com.coding.rpc.common.bean.RpcResponse;
import com.coding.rpc.common.codec.RpcDecoder;
import com.coding.rpc.common.codec.RpcEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by lishuisheng on 16/4/5.
 */
@ChannelHandler.Sharable
public class RpcClient extends SimpleChannelInboundHandler<RpcResponse>{

    private Logger logger = LoggerFactory.getLogger(RpcClient.class);

    private int workerGroupThreads = Runtime.getRuntime().availableProcessors() * 2;
    private EventLoopGroup workerGroup;
    private Channel channel;
    private final String host;
    private final int port;

    private ConcurrentHashMap<String, BlockingQueue<RpcResponse>> responseMap = new ConcurrentHashMap<>();

    public RpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        connect();
    }

    public void connect() {
        workerGroup = new NioEventLoopGroup(workerGroupThreads);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true).option(ChannelOption.TCP_NODELAY, true);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                ChannelPipeline pipeline = channel.pipeline();
                pipeline.addLast(new RpcEncoder(RpcRequest.class)); // 编码 RPC 请求
                pipeline.addLast(new RpcDecoder(RpcResponse.class)); // 解码 RPC 响应
                pipeline.addLast(RpcClient.this); // 处理 RPC 响应
            }
        });
        channel = bootstrap.connect(host, port).syncUninterruptibly().channel();
    }

    public RpcResponse send(RpcRequest request){
        channel.writeAndFlush(request);
        return getResponse(request.getRequestId());
    }


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcResponse rpcResponse) throws Exception {
        BlockingQueue<RpcResponse> queue = responseMap.get(rpcResponse.getRequestId());
        queue.add(rpcResponse);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("api caught exception", cause);
        ctx.close();
    }

    public RpcResponse getResponse(final String requestId) {
        RpcResponse result = null;
        responseMap.putIfAbsent(requestId, new LinkedBlockingQueue<RpcResponse>(1));
        try {
            result = responseMap.get(requestId).take();
            if (null == result) {
                System.out.println("result is null!");
                //result = getSystemMessage();
            }
        } catch (final InterruptedException ex) {
            ex.printStackTrace();
            //throw new ClientException(ex);
        } finally {
            responseMap.remove(requestId);
        }
        return result;
    }
}
