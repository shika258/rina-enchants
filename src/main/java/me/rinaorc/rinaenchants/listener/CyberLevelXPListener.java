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
 * Système optimisé:
 * - Chaque enchantement définit son propre cyber-level-xp-per-block
 * - L'XP est accumulé en async puis donné par batch via /cyberlevel addExp
 * - Valeurs cachées pour performance optimale
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

    // ═══════════════════════════════════════════════════════════════════════
    // VALEURS CACHÉES (évite les accès config répétés)
    // ═══════════════════════════════════════════════════════════════════════

    // Activé ou non (caché pour performance)
    private volatile boolean enabled = false;

    // Mode debug (caché pour performance)
    private volatile boolean debug = false;

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

            // Copier et vider l'accumulation de manière atomique
            Map<UUID, Integer> toProcess = new HashMap<>();
            accumulatedXP.forEach((uuid, xp) -> {
                toProcess.put(uuid, xp);
            });
            accumulatedXP.clear();

            for (Map.Entry<UUID, Integer> xpEntry : toProcess.entrySet()) {
                Player player = Bukkit.getPlayer(xpEntry.getKey());
                if (player != null && player.isOnline()) {
                    int xp = xpEntry.getValue();
                    if (xp > 0) {
                        // Commande: /cyberlevel addExp <amount> <player>
                        String command = "cyberlevel addExp " + xp + " " + player.getName();
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                        if (debug) {
                            plugin.getLogger().info("§a[CyberLevel] XP donné: " + xp + " à " + player.getName());
                        }
                    }
                }
            }
        }, XP_PROCESS_INTERVAL * 2, XP_PROCESS_INTERVAL * 2);

        if (enabled) {
            plugin.getLogger().info("§a[CyberLevel] Système XP initialisé (config par enchantement)");
        } else {
            plugin.getLogger().info("§e[CyberLevel] Système XP désactivé dans la config");
        }
    }

    /**
     * Charge la configuration et met en cache les valeurs
     */
    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false);
        debug = plugin.getConfig().getBoolean("debug", false);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * MÉTHODE PRINCIPALE: Queue l'XP pour une culture cassée
     * ═══════════════════════════════════════════════════════════════════════
     *
     * Appelé par safeBreakCrop APRÈS avoir cassé la culture.
     * L'XP est défini par l'enchantement qui appelle cette méthode.
     *
     * @param player Le joueur qui reçoit l'XP
     * @param xpAmount L'XP à donner (depuis la config de l'enchantement)
     */
    public void queueXPForCrop(Player player, int xpAmount) {
        // Vérifications rapides
        if (!enabled || xpAmount <= 0) {
            return;
        }

        if (player == null || !player.isOnline()) {
            return;
        }

        // Ajouter à la queue (thread-safe, sera traité en batch)
        xpQueue.offer(new XPQueueEntry(player.getUniqueId(), xpAmount));

        if (debug) {
            plugin.getLogger().info("§a[CyberLevel] +" + xpAmount + " XP queue pour " + player.getName());
        }
    }

    /**
     * Réinitialise le listener (appelé lors du reload)
     */
    public void reload() {
        loadConfig();
        plugin.getLogger().info("§a[CyberLevel] Config rechargée - " +
            (enabled ? "Activé" : "Désactivé"));
    }

    /**
     * Vérifie si le système est activé
     */
    public boolean isEnabled() {
        return enabled;
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
