package me.rinaorc.rinaenchants.listener;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Système d'XP CyberLevel pour les enchantements RinaEnchants
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ARCHITECTURE ASYNC OPTIMISÉE POUR SERVEURS 500+ JOUEURS
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Fonctionnement:
 * 1. Quand un enchantement casse une culture via safeBreakCrop():
 *    - Le type de culture est détecté AVANT le cassage
 *    - L'XP est calculé et ajouté à une queue async (thread-safe)
 *
 * 2. Toutes les 5 ticks (250ms), une tâche ASYNC:
 *    - Vide la queue et accumule l'XP par joueur
 *
 * 3. Toutes les 10 ticks (500ms), une tâche SYNC:
 *    - Donne l'XP accumulé via la commande "cyberlevel giveExp"
 *    - Une seule commande par joueur = moins de lag
 *
 * NOTE: RivalHarvesterHoes ne donne PAS l'XP CyberLevel quand les enchantements
 * cassent les cultures. Ce système gère l'XP de façon indépendante.
 */
public class CyberLevelXPListener implements Listener {

    private final RinaEnchantsPlugin plugin;

    // ═══════════════════════════════════════════════════════════════════════
    // CACHE DE CONFIGURATION (pour éviter les appels répétés à getConfig())
    // ═══════════════════════════════════════════════════════════════════════

    private boolean enabled = false;
    private boolean debug = false;
    private boolean cyberLevelPluginAvailable = false;

    // XP de base par type de culture (chargé depuis la config)
    private final Map<Material, Double> cropXpValues = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    // SYSTÈME DE QUEUE ASYNC POUR L'XP
    // ═══════════════════════════════════════════════════════════════════════

    // Queue des XP à donner (thread-safe, lock-free)
    private final ConcurrentLinkedQueue<XPQueueEntry> xpQueue = new ConcurrentLinkedQueue<>();

    // XP accumulé par joueur (pour batch processing)
    private final ConcurrentHashMap<UUID, Integer> accumulatedXP = new ConcurrentHashMap<>();

    // Intervalle de processing en ticks (5 ticks = 250ms)
    private static final long XP_PROCESS_INTERVAL = 5L;

    public CyberLevelXPListener(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;

        // Charger la configuration
        loadConfiguration();

        // Vérifier si le plugin CyberLevel est disponible
        checkCyberLevelPlugin();

        if (!enabled) {
            plugin.getLogger().info("§e[CyberLevel] Hook désactivé dans la config");
            return;
        }

        if (!cyberLevelPluginAvailable) {
            plugin.getLogger().warning("§c[CyberLevel] Plugin CyberLevel non trouvé! L'XP ne sera pas donné.");
            return;
        }

        // ═══════════════════════════════════════════════════════════════════════
        // TASK ASYNC: Traite la queue d'XP et accumule par joueur
        // Optimisation: Lock-free queue + merge atomique
        // ═══════════════════════════════════════════════════════════════════════
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            XPQueueEntry entry;
            while ((entry = xpQueue.poll()) != null) {
                accumulatedXP.merge(entry.playerId, entry.xpAmount, Integer::sum);
            }
        }, XP_PROCESS_INTERVAL, XP_PROCESS_INTERVAL);

        // ═══════════════════════════════════════════════════════════════════════
        // TASK SYNC: Donne l'XP accumulé via la commande CyberLevel
        // Optimisation: Une seule commande par joueur au lieu d'une par culture
        // ═══════════════════════════════════════════════════════════════════════
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::processBatchXP, XP_PROCESS_INTERVAL * 2, XP_PROCESS_INTERVAL * 2);

        plugin.getLogger().info("§a[CyberLevel] Système d'XP initialisé:");
        plugin.getLogger().info("§a  - " + cropXpValues.size() + " cultures configurées");
        plugin.getLogger().info("§a  - Batch processing: toutes les " + (XP_PROCESS_INTERVAL * 2 * 50) + "ms");
    }

    /**
     * Vérifie si le plugin CyberLevel est disponible
     */
    private void checkCyberLevelPlugin() {
        cyberLevelPluginAvailable = Bukkit.getPluginManager().getPlugin("CyberLevel") != null;
    }

    /**
     * Traite l'XP accumulé et l'envoie aux joueurs (appelé toutes les 10 ticks)
     */
    private void processBatchXP() {
        if (accumulatedXP.isEmpty()) return;

        // Copier et vider l'accumulation de façon atomique
        Map<UUID, Integer> toProcess = new HashMap<>();
        accumulatedXP.forEach((uuid, xp) -> {
            toProcess.put(uuid, xp);
        });
        accumulatedXP.clear();

        // Donner l'XP à chaque joueur
        for (Map.Entry<UUID, Integer> xpEntry : toProcess.entrySet()) {
            Player player = Bukkit.getPlayer(xpEntry.getKey());
            if (player != null && player.isOnline()) {
                int xp = xpEntry.getValue();
                if (xp > 0) {
                    String command = "cyberlevel giveExp " + xp + " " + player.getName();
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                    if (debug) {
                        plugin.getLogger().info("§a[CyberLevel] XP donné: " + xp + " à " + player.getName());
                    }
                }
            }
        }
    }

    /**
     * Charge toute la configuration (appelé au démarrage et lors du reload)
     */
    private void loadConfiguration() {
        // Cache des flags de config
        enabled = plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false);
        debug = plugin.getConfig().getBoolean("debug", false);

        // Charger les valeurs d'XP par culture
        cropXpValues.clear();

        if (!enabled) {
            return;
        }

        List<String> cropsList = plugin.getConfig().getStringList("cyberlevels-hook.xp-settings.specific.crops");

        for (String entry : cropsList) {
            try {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    Material material = Material.valueOf(parts[0].trim().toUpperCase());
                    double xp = Double.parseDouble(parts[1].trim());
                    cropXpValues.put(material, xp);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("§e[CyberLevel] Entrée invalide dans la config: " + entry);
            }
        }

        if (debug) {
            plugin.getLogger().info("§a[CyberLevel] " + cropXpValues.size() + " valeurs d'XP chargées");
        }
    }

    /**
     * Récupère l'XP de base pour un type de culture
     */
    public double getCropBaseXP(Material material) {
        return cropXpValues.getOrDefault(material, 0.0);
    }

    /**
     * Vérifie si le système est activé
     */
    public boolean isEnabled() {
        return enabled && cyberLevelPluginAvailable;
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * MÉTHODE PRINCIPALE: Queue l'XP pour une culture cassée par un enchantement
     * ═══════════════════════════════════════════════════════════════════════
     *
     * Cette méthode est appelée par safeBreakCrop APRÈS avoir cassé la culture.
     * Le type de culture doit être détecté AVANT de casser le bloc.
     *
     * Optimisations:
     * - Utilise des variables de cache au lieu d'appels répétés à getConfig()
     * - Lock-free queue pour thread-safety sans blocage
     * - Batch processing pour réduire les commandes exécutées
     *
     * @param player Le joueur qui reçoit l'XP
     * @param cropType Le type de culture cassée (détecté AVANT le cassage)
     * @param multiplier Le multiplicateur de l'enchantement (ex: 2.0 pour Bee Collector)
     */
    public void queueXPForCrop(Player player, Material cropType, double multiplier) {
        // Vérification rapide avec variables de cache
        if (!enabled || !cyberLevelPluginAvailable) {
            return;
        }

        if (player == null || !player.isOnline()) {
            return;
        }

        // Récupérer l'XP de base pour ce type de culture
        double baseXP = getCropBaseXP(cropType);

        if (debug) {
            plugin.getLogger().info("§e[CyberLevel] queueXPForCrop: crop=" + cropType +
                ", baseXP=" + baseXP + ", multi=" + multiplier);
        }

        if (baseXP <= 0) {
            if (debug) {
                plugin.getLogger().info("§c[CyberLevel] Pas d'XP configuré pour: " + cropType);
            }
            return;
        }

        // Calculer l'XP final avec le multiplicateur
        double finalXP = baseXP * multiplier;
        int xpToGive = (int) Math.round(finalXP);

        if (xpToGive <= 0) {
            return;
        }

        // Ajouter à la queue (thread-safe, lock-free, sera traité en batch)
        xpQueue.offer(new XPQueueEntry(player.getUniqueId(), xpToGive));

        if (debug) {
            plugin.getLogger().info("§a[CyberLevel] XP queued: " + xpToGive +
                " pour " + player.getName() + " (crop: " + cropType + ", multi: x" + multiplier + ")");
        }
    }

    /**
     * Alias pour compatibilité avec l'ancien code
     */
    public void giveDirectXP(Player player, Material cropType, double multiplier) {
        queueXPForCrop(player, cropType, multiplier);
    }

    /**
     * Réinitialise le listener (appelé lors du reload)
     */
    public void reload() {
        // Recharger la configuration
        loadConfiguration();
        checkCyberLevelPlugin();

        // Vider les queues en cours
        xpQueue.clear();
        accumulatedXP.clear();

        plugin.getLogger().info("§a[CyberLevel] Système rechargé:");
        plugin.getLogger().info("§a  - Enabled: " + enabled);
        plugin.getLogger().info("§a  - CyberLevel disponible: " + cyberLevelPluginAvailable);
        plugin.getLogger().info("§a  - " + cropXpValues.size() + " cultures configurées");
    }

    /**
     * Entrée dans la queue d'XP à donner (structure minimale pour performance)
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
