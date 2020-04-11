package github.tartaricacid.bakadanmaku.thread;

import com.google.common.io.ByteStreams;
import github.tartaricacid.bakadanmaku.BakaDanmaku;
import github.tartaricacid.bakadanmaku.api.event.DanmakuEvent;
import github.tartaricacid.bakadanmaku.api.event.GiftEvent;
import github.tartaricacid.bakadanmaku.api.event.PopularityEvent;
import github.tartaricacid.bakadanmaku.api.event.WelcomeEvent;
import github.tartaricacid.bakadanmaku.api.thread.BaseDanmakuThread;
import github.tartaricacid.bakadanmaku.config.BakaDanmakuConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import net.minecraftforge.common.MinecraftForge;
import org.apache.commons.lang3.RandomUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

public class BilibiliDanmakuThread extends BaseDanmakuThread {
    private static final String LIVE_URL = "livecmt-1.bilibili.com"; // B 站弹幕地址
    private static final int PORT = 788; // Websocket 端口
    private static final String INIT_URL = "https://api.live.bilibili.com/room/v1/Room/room_init"; // 获取真实直播房间号的 api 地址

    private static Pattern extractRoomId = Pattern.compile("\"room_id\":(\\d+),"); // 用来读取 JSON 的正则表达式
    private static Pattern readCmd = Pattern.compile("\"cmd\":\"(.*?)\""); // 读取 CMD 的
    private static Pattern readDanmakuUser = Pattern.compile("\\[\\d+,\"(.*?)\",\\d+"); // 读取弹幕发送者的
    private static Pattern readDanmakuInfo = Pattern.compile("],\"(.*?)\",\\["); // 读取具体弹幕内容的
    private static Pattern readGiftName = Pattern.compile("\"giftName\":\"(.*?)\""); // 读取礼物名称的
    private static Pattern readGiftNum = Pattern.compile("\"num\":(\\d+)"); // 读取礼物数量的
    private static Pattern readGiftUser = Pattern.compile("\"uname\":\"(.*?)\""); // 读取发送礼物者的
    private static Pattern readWelcomeUser = Pattern.compile("\"uname\":\"(.*?)\""); // 读取欢迎玩家的

    public class BilibiliChannalInboundHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private String roomId;

        public BilibiliChannalInboundHandler(String roomId) {
            this.roomId = roomId;
        }

        @Override
        public void channelActive(ChannelHandlerContext channelHandlerContext) {
            channelHandlerContext.writeAndFlush(sendJoinMsg(Unpooled.buffer(), roomId));
            sendChatMessage("§8§l弹幕姬已经连接");
            channelHandlerContext.writeAndFlush(sendDataPack(Unpooled.buffer(), 2, ""));
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
            int length = buf.readInt(); // 头部的长度数据
            int headLength = buf.readShort(); // 头部长度
            int protocol = buf.readShort(); // 协议 0为文本数据, 2为zlib压缩后的文本数据, 1为纯int
            int action = buf.readInt(); // 操作码
            int sequence = buf.readInt(); // 协议

            // 如果长度小于等于 16，谨防万一，加一个上限
            if (length <= 16 || length > 65534) return;

            // 剔除头部，进行读取
            byte[] bodyByte = new byte[length - 16];
            buf.readBytes(bodyByte);
            byte[] result = new byte[length * 2];

            /* 如果长度大于 16，说明是有数据的
             3：人气值
             5：弹幕
            */
            if (action == 3) {
                // byte 数组数据转 Int
                int num = ByteBuffer.wrap(bodyByte).getInt();

                // Post PopularityEvent
                MinecraftForge.EVENT_BUS.post(new PopularityEvent(BakaDanmakuConfig.livePlatform.bilibiliRoom.platformDisplayName,
                        num));
            }

            if (action == 5) {
                if (protocol == 2) {
                    Inflater decompresser = new Inflater();
                    decompresser.setInput(bodyByte);
                    decompresser.inflate(result);
                } else {
                    result = bodyByte;
                }
                // UTF-8 解码
                String bodyString = new String(result, StandardCharsets.UTF_8);

                // 数据解析，扒出 CMD 信息
                String msgType = ""; // 初始化
                Matcher mCmd = readCmd.matcher(bodyString);
                if (mCmd.find()) msgType = mCmd.group(1);

                /*
                 * DANMU_MSG	收到弹幕
                 * SEND_GIFT	有人送礼
                 * WELCOME	欢迎加入房间
                 * WELCOME_GUARD	欢迎房管加入房间
                 * SYS_MSG	系统消息
                 * NOTICE_MSG 也是系统信息
                 * ENTRY_EFFECT 舰长进入房间信息
                 * COMBO_SEND 连击礼物起始
                 * COMBO_END 连击礼物结束
                 * ROOM_RANK 周星榜
                 * GUARD_MSG 开通舰长信息
                 * GUARD_BUY 舰长购买信息
                 * GUARD_LOTTERY_START 购买舰长后抽奖信息
                 * RAFFLE_END 抽奖结果
                 * SPECIAL_GIFT 神奇的东西，不知道是啥
                 * WISH_BOTTLE 这又是啥
                 */
                switch (msgType) {
                    case "DANMU_MSG": {
                        // 正则匹配
                        Matcher mDanmuMsg = readDanmakuInfo.matcher(bodyString);
                        Matcher mUser = readDanmakuUser.matcher(bodyString);

                        // 扒出具体的发送者和信息
                        if (mDanmuMsg.find() && mUser.find()) {
                            String danmuMsg = mDanmuMsg.group(1);
                            String user = mUser.group(1);

                            // Post DanmakuEvent
                            MinecraftForge.EVENT_BUS.post(new DanmakuEvent(BakaDanmakuConfig.livePlatform.bilibiliRoom.platformDisplayName,
                                    user, danmuMsg));
                        }

                        break;
                    }

                    case "SEND_GIFT": {
                        // 正则匹配
                        Matcher mGiftName = readGiftName.matcher(bodyString);
                        Matcher mNum = readGiftNum.matcher(bodyString);
                        Matcher mUser = readGiftUser.matcher(bodyString);

                        // 扒出具体的送礼信息
                        if (mGiftName.find() && mNum.find() && mUser.find()) {
                            String giftName = unicodeToString(mGiftName.group(1));
                            int num = Integer.valueOf(mNum.group(1));
                            String user = unicodeToString(mUser.group(1));

                            // Post GiftEvent
                            MinecraftForge.EVENT_BUS.post(new GiftEvent(BakaDanmakuConfig.livePlatform.bilibiliRoom.platformDisplayName,
                                    giftName, num, user));
                        }

                        break;
                    }

                    case "WELCOME": {
                        // 正则匹配
                        Matcher mUser = readWelcomeUser.matcher(bodyString);

                        // 具体的欢迎用户名
                        if (mUser.find()) {
                            String user = mUser.group(1);

                            // Post WelcomeEvent
                            MinecraftForge.EVENT_BUS.post(new WelcomeEvent(BakaDanmakuConfig.livePlatform.bilibiliRoom.platformDisplayName,
                                    user));
                        }

                        break;
                    }

                    case "SYS_MSG": {
                        //TODO: Emit SysMsgEvent
                        break;
                    }

                    default: {
                    }
                }
            }
        }
    }

    @Override
    public boolean preRunCheck() {
        boolean check = super.preRunCheck();
        // 处理直播房间未设置的问题
        if (BakaDanmakuConfig.livePlatform.bilibiliRoom.liveRoom == 0) {
            sendChatMessage("§8§l直播房间 ID 未设置，弹幕姬已停止工作！ ");
            check = false;
        }

        return check;
    }

    @Override
    public void doRun() {
        // 获取真实房间 ID
        String roomID = getRoomId(BakaDanmakuConfig.livePlatform.bilibiliRoom.liveRoom);

        // 提示，相关房间信息已经获取
        sendChatMessage("§8§l直播房间 ID 已经获取，ID 为 " + roomID);

        EventLoopGroup group = new NioEventLoopGroup();
        io.netty.util.Timer timer = new HashedWheelTimer();
        
        try {
            Bootstrap clientBootstrap = new Bootstrap();
            clientBootstrap.group(group);
            clientBootstrap.channel(NioSocketChannel.class);
            clientBootstrap.remoteAddress(LIVE_URL, PORT);
            clientBootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) throws Exception {
                    socketChannel.pipeline().addLast(new BilibiliChannalInboundHandler(roomID));
                }
            });
            ChannelFuture channelFuture = clientBootstrap.connect().sync();
            timer.newTimeout(timeout -> {
                channelFuture.channel().writeAndFlush(sendDataPack(Unpooled.buffer(), 2, ""));
            }, 30000, TimeUnit.MILLISECONDS);
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                timer.stop();
                group.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 清除部分
     */
    @Override
    public void clear() {
    }



    /**
     * 固定的发包方法
     *
     * @param buf 数据流
     * @param action 操作码，可选 2,3,5,7,8
     * @param body   发送的数据本体部分
     */
    private ByteBuf sendDataPack(ByteBuf buf, int action, String body) {
        // 当停止线程时直接返回
        if (!keepRunning) return buf;

        // 数据部分，以 UTF-8 编码解析成 Byte
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        // 封包总长度，因为头部固定为 16 字长，故加上 16
        int length = bodyBytes.length + 16;

        // 存入 4 字长的封包总大小数据，所以为 int
        buf.writeInt(length);

        // 存入 2 字长的头部长度数据，头部长度固定为 16
        buf.writeShort((short) 16);

        // 存入 2 字长的协议版本数据，默认为 1
        buf.writeShort((short) 1);

        // 存入 4 字长的操作码，操作码有 2,3,5,7,8
        buf.writeInt(action);

        // 存入 4 字长的 sequence，意味不明，取常数 1
        buf.writeInt(1);

        // 存入数据
        buf.writeBytes(bodyBytes);

        return buf;
    }

    /**
     * 首次连接发送的认证数据
     *
     * @param buf 数据流
     * @param roomId 真实的直播房间 ID
     */
    private ByteBuf sendJoinMsg(ByteBuf buf, String roomId) {
        // 生成随机的 UID
        long clientId = RandomUtils.nextLong(100000000000000L, 300000000000000L);

        // 发送验证包
        return sendDataPack(buf, 7, String.format("{\"roomid\":%s,\"uid\":%d,\"protover\": 2,\"platform\": \"web\",\"clientver\": \"1.4.0\"}",
                roomId,
                clientId));
    }

    /**
     * 获取真实的直播房间 ID
     *
     * @param roomId 直播房间 ID
     * @return 真实的直播房间 ID
     */
    private String getRoomId(int roomId) {
        // 初始化
        String realRoomId = null;

        try {
            // B 站提供的获取直播 id 的 api
            URL url = new URL(INIT_URL + "?id=" + roomId);

            // 获取网络数据流
            InputStream con = url.openStream();

            // 按照 UTF-8 编码解析
            String data = new String(ByteStreams.toByteArray(con), StandardCharsets.UTF_8);

            // 关闭数据流
            con.close();

            // 简单的 JSON 不需要用 Gson 解析，正则省事
            Matcher matcher = extractRoomId.matcher(data);
            if (matcher.find()) {
                realRoomId = matcher.group(1);
            } else {
                // 提示，获取失败
                sendChatMessage("直播房间获取失败，请检查房间 ID 是否出错");

                // 日志记录
                BakaDanmaku.logger.fatal("Cannot get room id.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return realRoomId;
    }

    /**
     * 把十六进制Unicode编码字符串转换为中文字符串
     * Ref: https://blog.csdn.net/wccmfc123/article/details/11610393
     *
     * @param str 传入的字符串，可能包含 16 位 Unicode 码
     * @return 反转义后的字符串
     */
    private String unicodeToString(String str) {
        // 获取内部的 U 码
        Pattern pattern = Pattern.compile("(\\\\u(\\p{XDigit}{4}))");
        Matcher matcher = pattern.matcher(str);

        // 字符初始化
        char ch;

        // 开始逐个替换
        while (matcher.find()) {
            // 将扒出来的 Int 转换成 char 类型，因为 Java 默认是 UTF-8 编码，所以会自动转换成对应文字
            ch = (char) Integer.parseInt(matcher.group(2), 16);

            // 将 Unicode 码替换成对应文字，注意后面用了一个隐式类型转换
            str = str.replace(matcher.group(1), ch + "");
        }

        return str;
    }
}
