package dmr.DragonMounts.server.ai.navigation;

import dmr.DragonMounts.config.ServerConfig;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/** Server-thread-only accounting shared by dragons in a level, without retaining unloaded worlds. */
public final class DragonPathfindingBudget {
    private static final Map<ServerLevel, Window> WINDOWS = new WeakHashMap<>();

    private DragonPathfindingBudget() {}

    private static Window window(ServerLevel level) {
        Window window = WINDOWS.computeIfAbsent(level, ignored -> new Window());
        if (window.tick != level.getGameTime()) {
            window.tick = level.getGameTime();
            window.spentNanos = 0;
        }
        return window;
    }

    public static boolean hasTime(ServerLevel level) {
        return ServerConfig.PATHFINDING_BUDGET_MS <= 0
                || window(level).spentNanos < ServerConfig.PATHFINDING_BUDGET_MS * 1_000_000.0;
    }

    public static void recordSearch(ServerLevel level, long elapsedNanos) {
        window(level).spentNanos += elapsedNanos;
    }

    private static final class Window {
        long tick = Long.MIN_VALUE;
        long spentNanos;
    }
}
