package github.tartaricacid.bakadanmaku.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import github.tartaricacid.bakadanmaku.BakaDanmaku;
import github.tartaricacid.bakadanmaku.api.thread.DanmakuThreadFactory;

public class StartStopHandler {
    // 玩家进入服务器时，依据配置提供的平台，启动对于的弹幕线程
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        BakaDanmaku.player = event.player;
        DanmakuThreadFactory.restartThreads();
    }

    // 当玩家离开游戏时，停止所有线程
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BakaDanmaku.player = null;
        DanmakuThreadFactory.stopAllThreads();
    }
}
