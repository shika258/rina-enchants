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
 * Système avec XP par enchantement:
 * - Chaque enchantement a sa propre valeur XP (cyber-level-xp-per-block)
 * - L'XP est accumulé en async puis donné par batch via /cyberlevel addExp
 */
public class CyberLevelXPListener implements Listener {

    private final RinaEnchantsPlugin plugin;

    // ═══════════════════════════════════════════════════════════════════════
    // SYSTÈME DE QUEUE ASYNC POUR L'XP
    // ═══════════════════════════════════════════════════════════════════════

    // Queue des XP à donner (thread-safe)
    private final ConcurrentLinkedQueue<XPQueueEntry> xpQueue = new ConcurrentLinkedQueue<>();

    // XP accumulé par joueur (pour batch processing) - Long pour supporter les grandes valeurs
    private final ConcurrentHashMap<UUID, Long> accumulatedXP = new ConcurrentHashMap<>();

    // Intervalle de processing en ticks (5 ticks = 250ms)
    private static final long XP_PROCESS_INTERVAL = 5L;

    // Map des XP par bloc pour chaque enchantement (chargé depuis la config)
    private final Map<String, Long> xpPerBlockByEnchant = new HashMap<>();

    // Liste des sections d'enchantements à charger
    private static final String[] ENCHANT_SECTIONS = {
        "bee-collector",
        "panda-roll",
        "allay-laser",
        "ravager-stampede",
        "blizzard-eternal",
        "ender-dragon-breath",
        "axolotl-tsunami",
        "golem-factory",
        "warden-pulse",
        "frog-tongue-lash"
    };

    public CyberLevelXPListener(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        loadConfig();

        // ═══════════════════════════════════════════════════════════════════════
        // TASK ASYNC: Traite la queue d'XP et accumule par joueur
        // ═══════════════════════════════════════════════════════════════════════
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            XPQueueEntry entry;
            while ((entry = xpQueue.poll()) != null) {
                accumulatedXP.merge(entry.playerId, entry.xpAmount, Long::sum);
            }
        }, XP_PROCESS_INTERVAL, XP_PROCESS_INTERVAL);

        // ═══════════════════════════════════════════════════════════════════════
        // TASK SYNC: Donne l'XP accumulé via la commande CyberLevel
        // ═══════════════════════════════════════════════════════════════════════
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (accumulatedXP.isEmpty()) return;

            // Copier et vider l'accumulation
            Map<UUID, Long> toProcess = new HashMap<>(accumulatedXP);
            accumulatedXP.clear();

            for (Map.Entry<UUID, Long> xpEntry : toProcess.entrySet()) {
                Player player = Bukkit.getPlayer(xpEntry.getKey());
                if (player != null && player.isOnline()) {
                    long xp = xpEntry.getValue();
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

        plugin.getLogger().info("§a[CyberLevel] Listener XP initialisé avec XP par enchantement");
        plugin.getLogger().info("§a[CyberLevel] Système de queue async activé (batch processing)");
    }

    /**
     * Charge la configuration - XP par enchantement
     */
    public void loadConfig() {
        xpPerBlockByEnchant.clear();

        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            plugin.getLogger().info("§e[CyberLevel] Hook désactivé dans la config");
            return;
        }

        boolean debug = plugin.getConfig().getBoolean("debug", false);

        // Charger l'XP de chaque enchantement
        for (String enchantSection : ENCHANT_SECTIONS) {
            String configPath = enchantSection + ".cyber-level-xp-per-block";
            long xp = plugin.getConfig().getLong(configPath, 200);
            xpPerBlockByEnchant.put(enchantSection, xp);

            if (debug) {
                plugin.getLogger().info("§a[CyberLevel] " + enchantSection + " XP par bloc: " + xp);
            }
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * MÉTHODE PRINCIPALE: Queue l'XP pour une culture cassée par un enchantement
     * ═══════════════════════════════════════════════════════════════════════
     *
     * @param player Le joueur qui reçoit l'XP
     * @param enchantId L'ID de la section d'enchantement (ex: "panda-roll", "bee-collector")
     */
    public void queueXPForCrop(Player player, String enchantId) {
        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            return;
        }

        if (player == null || !player.isOnline()) {
            return;
        }

        // Récupérer l'XP pour cet enchantement
        Long xpPerBlock = xpPerBlockByEnchant.get(enchantId);
        if (xpPerBlock == null || xpPerBlock <= 0) {
            return;
        }

        // Ajouter à la queue (thread-safe, sera traité en batch)
        xpQueue.offer(new XPQueueEntry(player.getUniqueId(), xpPerBlock));

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] XP ajouté à la queue: " + xpPerBlock + " (" + enchantId + ") pour " + player.getName());
        }
    }

    /**
     * Réinitialise le listener (appelé lors du reload)
     */
    public void reload() {
        loadConfig();
    }

    /**
     * Retourne l'XP par bloc pour un enchantement donné
     */
    public long getXpPerBlock(String enchantId) {
        return xpPerBlockByEnchant.getOrDefault(enchantId, 200L);
    }

    /**
     * Entrée dans la queue d'XP à donner
     */
    private static class XPQueueEntry {
        final UUID playerId;
        final long xpAmount;

        XPQueueEntry(UUID playerId, long xpAmount) {
            this.playerId = playerId;
            this.xpAmount = xpAmount;
        }
    }
}
