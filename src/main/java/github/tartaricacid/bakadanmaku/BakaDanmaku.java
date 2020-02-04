package github.tartaricacid.bakadanmaku;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLConstructionEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import github.tartaricacid.bakadanmaku.api.thread.DanmakuThreadFactory;
import github.tartaricacid.bakadanmaku.command.CommandBakaDM;
import github.tartaricacid.bakadanmaku.config.BakaDanmakuConfig;
import github.tartaricacid.bakadanmaku.handler.ChatMsgHandler;
import github.tartaricacid.bakadanmaku.handler.StartStopHandler;
import github.tartaricacid.bakadanmaku.thread.BilibiliDanmakuThread;
import github.tartaricacid.bakadanmaku.thread.ChushouDanmakuThread;
import github.tartaricacid.bakadanmaku.thread.DouyuDanmakuThread;
import github.tartaricacid.common.config.Config;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import github.tartaricacid.common.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = BakaDanmaku.MOD_ID, name = BakaDanmaku.MOD_NAME, acceptedMinecraftVersions = "[1.7.10]", version = BakaDanmaku.VERSION, acceptableRemoteVersions = "*",
guiFactory = "github.tartaricacid.bakadanmaku.ConfigGuiFactory")
public class BakaDanmaku {
    public static final String MOD_ID = "baka_danmaku";
    public static final String MOD_NAME = "Baka Danmaku";
    public static final String VERSION = "1.0.0";

    public static final Logger logger = LogManager.getLogger(MOD_ID);

    public static EntityPlayer player;

    @Mod.Instance(MOD_ID)
    public static BakaDanmaku INSTANCE;

    @Mod.EventHandler
    public void construct(FMLConstructionEvent event) {
        if (event.getSide().isServer()) // 1.7.10 Forge does not support client-side only annotation
            throw new IllegalStateException("baka_danmaku should be only installed on client side");
        ConfigManager.loadData(event.getASMHarvestedData());
        ConfigManager.sync(MOD_ID, Config.Type.INSTANCE);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(new BakaDanmakuConfig.ConfigSyncHandler());
        FMLCommonHandler.instance().bus().register(new StartStopHandler());
        MinecraftForge.EVENT_BUS.register(new ChatMsgHandler());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // 装载各大平台的弹幕线程
        DanmakuThreadFactory.setDanmakuThread("bilibili", new BilibiliDanmakuThread());
        DanmakuThreadFactory.setDanmakuThread("douyu", new DouyuDanmakuThread());
        DanmakuThreadFactory.setDanmakuThread("chushou", new ChushouDanmakuThread());

        // 注册开启，关闭弹幕事件处理器
        MinecraftForge.EVENT_BUS.register(StartStopHandler.class);

        // 注册聊天事件处理器
        MinecraftForge.EVENT_BUS.register(ChatMsgHandler.class);

        // 注册屏幕信息事件处理器
        // TODO: MinecraftForge.EVENT_BUS.register(ScreenMsgHandler.class);

        // 客户端命令注册
        ClientCommandHandler.instance.registerCommand(new CommandBakaDM());
    }
}
