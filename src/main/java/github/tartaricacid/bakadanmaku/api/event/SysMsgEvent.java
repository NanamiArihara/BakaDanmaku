package github.tartaricacid.bakadanmaku.api.event;


import cpw.mods.fml.common.eventhandler.Cancelable;

@Cancelable
public class SysMsgEvent extends BaseDanmakuEvent {
    private String msg; // 系统信息

    /**
     * 系统信息事件
     *
     * @param msg 系统信息
     */
    public SysMsgEvent(String platform, String msg) {
        super(platform);
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }
}
