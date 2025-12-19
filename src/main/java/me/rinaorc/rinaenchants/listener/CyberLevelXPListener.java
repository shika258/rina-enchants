package me.rinaorc.rinaenchants.listener;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Listener pour le système d'XP CyberLevel des enchantements
 *
 * Nouveau système simplifié:
 * - Chaque culture cassée par un enchantement donne un XP fixe (cyber-level-xp-per-block)
 * - L'XP est accumulé en async puis donné par batch via /cyberlevel addExp
 * - Plus de multiplicateurs, plus de valeurs par type de culture
 */
public class CyberLevelXPListener implements Listener {

    private final RinaEnchantsPlugin plugin;

    // ═══════════════════════════════════════════════════════════════════════
    // SYSTÈME DE QUEUE ASYNC POUR L'XP
    // ═══════════════════════════════════════════════════════════════════════

    // Queue des XP à donner (thread-safe)
    private final ConcurrentLinkedQueue<XPQueueEntry> xpQueue = new ConcurrentLinkedQueue<>();

    // XP accumulé par joueur (pour batch processing)
    private final ConcurrentHashMap<UUID, Integer> accumulatedXP = new ConcurrentHashMap<>();

    // Intervalle de processing en ticks (5 ticks = 250ms)
    private static final long XP_PROCESS_INTERVAL = 5L;

    // XP par bloc cassé (chargé depuis la config)
    private int xpPerBlock = 200;

    public CyberLevelXPListener(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        loadConfig();

        // ═══════════════════════════════════════════════════════════════════════
        // TASK ASYNC: Traite la queue d'XP et accumule par joueur
        // ═══════════════════════════════════════════════════════════════════════
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            XPQueueEntry entry;
            while ((entry = xpQueue.poll()) != null) {
                accumulatedXP.merge(entry.playerId, entry.xpAmount, Integer::sum);
            }
        }, XP_PROCESS_INTERVAL, XP_PROCESS_INTERVAL);

        // ═══════════════════════════════════════════════════════════════════════
        // TASK SYNC: Donne l'XP accumulé via la commande CyberLevel
        // ═══════════════════════════════════════════════════════════════════════
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (accumulatedXP.isEmpty()) return;

            // Copier et vider l'accumulation
            Map<UUID, Integer> toProcess = new HashMap<>(accumulatedXP);
            accumulatedXP.clear();

            for (Map.Entry<UUID, Integer> xpEntry : toProcess.entrySet()) {
                Player player = Bukkit.getPlayer(xpEntry.getKey());
                if (player != null && player.isOnline()) {
                    int xp = xpEntry.getValue();
                    if (xp > 0) {
                        // Utilise /cyberlevel addExp <amount> <player>
                        String command = "cyberlevel addExp " + xp + " " + player.getName();
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                        if (plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().info("§a[CyberLevel] XP batch donné: " + xp + " à " + player.getName());
                        }
                    }
                }
            }
        }, XP_PROCESS_INTERVAL * 2, XP_PROCESS_INTERVAL * 2);

        plugin.getLogger().info("§a[CyberLevel] Listener XP initialisé (XP par bloc: " + xpPerBlock + ")");
        plugin.getLogger().info("§a[CyberLevel] Système de queue async activé (batch processing)");
    }

    /**
     * Charge la configuration
     */
    public void loadConfig() {
        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            plugin.getLogger().info("§e[CyberLevel] Hook désactivé dans la config");
            xpPerBlock = 0;
            return;
        }

        xpPerBlock = plugin.getConfig().getInt("cyberlevels-hook.cyber-level-xp-per-block", 200);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] XP par bloc configuré: " + xpPerBlock);
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * MÉTHODE PRINCIPALE: Queue l'XP pour une culture cassée par un enchantement
     * ═══════════════════════════════════════════════════════════════════════
     *
     * Appelé par safeBreakCrop APRÈS avoir cassé la culture.
     *
     * @param player Le joueur qui reçoit l'XP
     */
    public void queueXPForCrop(Player player) {
        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            return;
        }

        if (player == null || !player.isOnline()) {
            return;
        }

        if (xpPerBlock <= 0) {
            return;
        }

        // Ajouter à la queue (thread-safe, sera traité en batch)
        xpQueue.offer(new XPQueueEntry(player.getUniqueId(), xpPerBlock));

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] XP ajouté à la queue: " + xpPerBlock + " pour " + player.getName());
        }
    }

    /**
     * Réinitialise le listener (appelé lors du reload)
     */
    public void reload() {
        loadConfig();
    }

    /**
     * Retourne l'XP par bloc configuré
     */
    public int getXpPerBlock() {
        return xpPerBlock;
    }

    /**
     * Entrée dans la queue d'XP à donner
     */
    private static class XPQueueEntry {
        final UUID playerId;
        final int xpAmount;

        XPQueueEntry(UUID playerId, int xpAmount) {
            this.playerId = playerId;
            this.xpAmount = xpAmount;
        }
    }
}
