package me.rinaorc.rinaenchants.listener;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rivaldev.harvesterhoes.api.events.HoeXPGainEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Listener pour le système d'XP CyberLevel des enchantements
 *
 * Fonctionnement:
 * 1. Quand un enchantement casse une culture, on ajoute l'XP à une queue async
 * 2. Une tâche périodique (sync) donne l'XP accumulée par batch
 * 3. Cela évite les lags sur les serveurs avec beaucoup de joueurs
 *
 * NOTE: RivalHarvesterHoes ne donne PAS l'XP CyberLevel quand les enchantements
 * cassent les cultures. Ce système gère l'XP de façon indépendante.
 */
public class CyberLevelXPListener implements Listener {

    private final RinaEnchantsPlugin plugin;

    // XP de base par type de culture (chargé depuis la config)
    private final Map<Material, Double> cropXpValues = new HashMap<>();

    // Multiplicateurs actifs par joueur (legacy, gardé pour compatibilité)
    private final ConcurrentHashMap<UUID, XPMultiplierData> activeMultipliers = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    // SYSTÈME DE QUEUE ASYNC POUR L'XP
    // ═══════════════════════════════════════════════════════════════════════

    // Queue des XP à donner (thread-safe)
    private final ConcurrentLinkedQueue<XPQueueEntry> xpQueue = new ConcurrentLinkedQueue<>();

    // XP accumulé par joueur (pour batch processing)
    private final ConcurrentHashMap<UUID, Integer> accumulatedXP = new ConcurrentHashMap<>();

    // Intervalle de processing en ticks (5 ticks = 250ms)
    private static final long XP_PROCESS_INTERVAL = 5L;

    // Durée de validité d'un multiplicateur (en ms)
    private static final long MULTIPLIER_EXPIRY_MS = 2000;

    public CyberLevelXPListener(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        loadCropXpValues();

        // Task de nettoyage des multiplicateurs expirés (toutes les 2 secondes)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredMultipliers, 40L, 40L);

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
                        String command = "cyberlevel giveExp " + xp + " " + player.getName();
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                        if (plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().info("§a[CyberLevel] XP batch donné: " + xp + " à " + player.getName());
                        }
                    }
                }
            }
        }, XP_PROCESS_INTERVAL * 2, XP_PROCESS_INTERVAL * 2);

        plugin.getLogger().info("§a[CyberLevel] Listener XP initialisé avec " + cropXpValues.size() + " cultures configurées");
        plugin.getLogger().info("§a[CyberLevel] Système de queue async activé (batch processing)");
    }

    /**
     * Charge les valeurs d'XP par culture depuis la config
     */
    public void loadCropXpValues() {
        cropXpValues.clear();

        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            plugin.getLogger().info("§e[CyberLevel] Hook désactivé dans la config");
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

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] " + cropXpValues.size() + " valeurs d'XP chargées");
        }
    }

    /**
     * Enregistre un multiplicateur pour un joueur (appelé par les enchantements)
     */
    public void registerMultiplier(UUID playerId, String enchantId, double multiplier, Location location) {
        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            return;
        }

        XPMultiplierData data = new XPMultiplierData(enchantId, multiplier, System.currentTimeMillis(), location);
        activeMultipliers.put(playerId, data);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] Multiplicateur x" + multiplier + " enregistré pour " + enchantId);
        }
    }

    /**
     * Vérifie si un multiplicateur est actif pour un joueur
     */
    public boolean hasActiveMultiplier(UUID playerId) {
        XPMultiplierData data = activeMultipliers.get(playerId);
        if (data == null) return false;

        // Vérifier l'expiration
        if (System.currentTimeMillis() - data.timestamp > MULTIPLIER_EXPIRY_MS) {
            activeMultipliers.remove(playerId);
            return false;
        }

        return true;
    }

    /**
     * Récupère le multiplicateur actif pour un joueur
     */
    public double getActiveMultiplier(UUID playerId) {
        XPMultiplierData data = activeMultipliers.get(playerId);
        if (data == null) return 1.0;

        // Vérifier l'expiration
        if (System.currentTimeMillis() - data.timestamp > MULTIPLIER_EXPIRY_MS) {
            activeMultipliers.remove(playerId);
            return 1.0;
        }

        return data.multiplier;
    }

    /**
     * Récupère l'XP de base pour un type de culture
     */
    public double getCropBaseXP(Material material) {
        return cropXpValues.getOrDefault(material, 0.0);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * MÉTHODE PRINCIPALE: Queue l'XP pour une culture cassée par un enchantement
     * ═══════════════════════════════════════════════════════════════════════
     *
     * Cette méthode est appelée par safeBreakCrop APRÈS avoir cassé la culture.
     * Le type de culture doit être détecté AVANT de casser le bloc.
     *
     * @param player Le joueur qui reçoit l'XP
     * @param cropType Le type de culture cassée (détecté AVANT le cassage)
     * @param multiplier Le multiplicateur de l'enchantement (ex: 2.0 pour Bee Collector)
     */
    public void queueXPForCrop(Player player, Material cropType, double multiplier) {
        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("§c[CyberLevel] Hook désactivé, XP ignoré");
            }
            return;
        }

        if (player == null || !player.isOnline()) {
            return;
        }

        // Récupérer l'XP de base pour ce type de culture
        double baseXP = getCropBaseXP(cropType);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§e[CyberLevel] queueXPForCrop appelé: crop=" + cropType +
                ", baseXP=" + baseXP + ", multi=" + multiplier);
        }

        if (baseXP <= 0) {
            if (plugin.getConfig().getBoolean("debug", false)) {
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

        // Ajouter à la queue (thread-safe, sera traité en batch)
        xpQueue.offer(new XPQueueEntry(player.getUniqueId(), xpToGive, cropType));

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] XP ajouté à la queue: " + xpToGive +
                " pour " + player.getName() + " (crop: " + cropType + ", multi: x" + multiplier + ")");
        }
    }

    /**
     * Donne l'XP CyberLevel directement au joueur (méthode synchrone legacy).
     * Préférez queueXPForCrop pour les performances.
     *
     * @param player Le joueur qui reçoit l'XP
     * @param cropType Le type de culture cassée
     * @param multiplier Le multiplicateur de l'enchantement (1.0 = pas de multiplicateur)
     */
    public void giveDirectXP(Player player, Material cropType, double multiplier) {
        // Utiliser la nouvelle méthode de queue
        queueXPForCrop(player, cropType, multiplier);
    }

    /**
     * Écoute l'événement HoeXPGainEvent pour modifier l'XP
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHoeXPGain(HoeXPGainEvent event) {
        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();

        // Vérifier si un multiplicateur est actif
        if (!hasActiveMultiplier(playerId)) {
            return;
        }

        double multiplier = getActiveMultiplier(playerId);
        if (multiplier <= 1.0) {
            return;
        }

        // Appliquer le multiplicateur
        double originalXP = event.getXP();
        double newXP = originalXP * multiplier;

        event.setXP(newXP);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] XP modifié: " + originalXP + " -> " + newXP + " (x" + multiplier + ")");
        }

        // NOTE: On ne consomme PAS le multiplicateur ici car les enchantements
        // récoltent plusieurs cultures. Le multiplicateur expire naturellement après 2s.
    }

    /**
     * Nettoie les multiplicateurs expirés
     */
    private void cleanupExpiredMultipliers() {
        long now = System.currentTimeMillis();
        activeMultipliers.entrySet().removeIf(entry ->
            now - entry.getValue().timestamp > MULTIPLIER_EXPIRY_MS
        );
    }

    /**
     * Réinitialise le listener (appelé lors du reload)
     */
    public void reload() {
        loadCropXpValues();
        activeMultipliers.clear();
    }

    /**
     * Données d'un multiplicateur actif (legacy)
     */
    private static class XPMultiplierData {
        final String enchantId;
        final double multiplier;
        final long timestamp;
        final Location location;

        XPMultiplierData(String enchantId, double multiplier, long timestamp, Location location) {
            this.enchantId = enchantId;
            this.multiplier = multiplier;
            this.timestamp = timestamp;
            this.location = location;
        }
    }

    /**
     * Entrée dans la queue d'XP à donner
     */
    private static class XPQueueEntry {
        final UUID playerId;
        final int xpAmount;
        final Material cropType;

        XPQueueEntry(UUID playerId, int xpAmount, Material cropType) {
            this.playerId = playerId;
            this.xpAmount = xpAmount;
            this.cropType = cropType;
        }
    }
}
