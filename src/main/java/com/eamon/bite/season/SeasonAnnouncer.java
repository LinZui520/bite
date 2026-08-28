package com.eamon.bite.season;

/**
 * 季节提示触发器：跨自然日检测（睡觉跳清晨、熬夜到点都是 totalTicks
 * 跨过 24000 倍数）。
 *
 * <p>展示已全部由客户端 SeasonHud 完成（普通字体、屏幕中上方、渐入渐出）——
 * 客户端拿 client.level 自己算日序号，无需服务端发任何包。本类仅保留
 * 服务端 tick 钩子的占位语义；若未来需要服务端权威的季节事件（如向
 * 世界属性系统广播换季），在这里发。
 */
public final class SeasonAnnouncer {
    private SeasonAnnouncer() {}
}
