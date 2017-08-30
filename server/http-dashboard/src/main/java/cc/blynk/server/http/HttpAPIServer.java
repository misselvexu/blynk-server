package cc.blynk.server.http;

import cc.blynk.core.http.handlers.*;
import cc.blynk.server.Holder;
import cc.blynk.server.api.http.handlers.LetsEncryptHandler;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.core.dao.CSVGenerator;
import cc.blynk.utils.UrlMapper;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 1/12/2015.
 */
public class HttpAPIServer extends BaseServer {

    static final int HTTP_REQUEST_SIZE_MAX = 10 * 1024 * 1024;

    public static final String WEBSOCKET_PATH = "/websocket";
    private final ChannelInitializer<SocketChannel> channelInitializer;

    public HttpAPIServer(Holder holder) {
        super(holder.props.getProperty("listen.address"), holder.props.getIntProperty("http.port"), holder.transportTypeHolder);

        String rootPath = holder.props.getProperty("admin.rootPath", "/dashboard");

        LetsEncryptHandler letsEncryptHandler = new LetsEncryptHandler(holder.sslContextHolder.contentHolder);

        UrlReWriterHandler favIconUrlRewriter = new UrlReWriterHandler(new UrlMapper("/favicon.ico", "/static/favicon.ico"),
                new UrlMapper(rootPath, "/static/index.html"));
        StaticFileHandler staticFileHandler = new StaticFileHandler(holder.props, new StaticFile("/static"),
                new StaticFileEdsWith(CSVGenerator.CSV_DIR, ".csv.gz"));
        NoMatchHandler noMatchHandler = new NoMatchHandler();

        channelInitializer = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline()
                .addLast("HttpServerCodec", new HttpServerCodec())
                .addLast("HttpServerKeepAlive", new HttpServerKeepAliveHandler())
                .addLast("HttpObjectAggregator", new HttpObjectAggregator(HTTP_REQUEST_SIZE_MAX, true))
                .addLast(letsEncryptHandler)
                .addLast(new ChunkedWriteHandler())
                .addLast(favIconUrlRewriter)
                .addLast(staticFileHandler)
                .addLast(noMatchHandler);
            }
        };
    }

    @Override
    public ChannelInitializer<SocketChannel> getChannelInitializer() {
        return channelInitializer;
    }

    @Override
    protected String getServerName() {
        return "HTTP API and WebSockets";
    }

    @Override
    public void close() {
        System.out.println("Shutting down HTTP API and WebSockets server...");
        super.close();
    }

}
