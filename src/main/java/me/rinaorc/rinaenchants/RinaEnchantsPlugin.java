package me.rinaorc.rinaenchants;

import me.rinaorc.rinaenchants.command.ReloadCommand;
import me.rinaorc.rinaenchants.enchant.AllayLaserEnchant;
import me.rinaorc.rinaenchants.enchant.BeeCollectorEnchant;
import me.rinaorc.rinaenchants.enchant.BlizzardEnchant;
import me.rinaorc.rinaenchants.enchant.PandaRollEnchant;
import me.rinaorc.rinaenchants.enchant.RavagerStampedeEnchant;
import me.rinaorc.rinaenchants.listener.CyberLevelXPListener;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import me.rivaldev.harvesterhoes.api.events.RivalBlockBreakEvent;
import me.rivaldev.harvesterhoes.api.events.RivalHarvesterHoesAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RinaEnchantsPlugin extends JavaPlugin implements Listener {

    private static RinaEnchantsPlugin instance;
    private RivalHarvesterHoesAPI hoesAPI;
    private CyberLevelXPListener cyberLevelListener;

    // Liste des enchantements enregistrés
    private final List<HoeEnchant> registeredEnchants = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════════
    // SYSTÈME DE CLEANUP DES ENTITÉS APRÈS REBOOT
    // ═══════════════════════════════════════════════════════════════════════

    // Clé pour marquer les entités spawned par ce plugin (PersistentDataContainer)
    private NamespacedKey enchantEntityKey;

    // ═══════════════════════════════════════════════════════════════════════
    // SYSTÈME DE TRACKING POUR EMPÊCHER LES PROC EN CASCADE
    // ═══════════════════════════════════════════════════════════════════════

    // Locations actuellement cassées par les entités (abeilles, pandas, allays)
    // Format: "world:x:y:z" -> timestamp d'expiration
    private final ConcurrentHashMap<String, Long> entityBreakingLocations = new ConcurrentHashMap<>();

    // Cache pour les entités client-side par joueur (pour cleanup)
    private final ConcurrentHashMap<UUID, Set<Integer>> playerClientEntities = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    // CULTURES SANS ÂGE (pas besoin de vérifier Ageable)
    // ═══════════════════════════════════════════════════════════════════════
    private static final Set<org.bukkit.Material> NO_AGE_CROPS = new HashSet<>(Arrays.asList(
        org.bukkit.Material.MELON, org.bukkit.Material.PUMPKIN, org.bukkit.Material.SUGAR_CANE,
        org.bukkit.Material.CACTUS, org.bukkit.Material.BAMBOO, org.bukkit.Material.KELP,
        org.bukkit.Material.KELP_PLANT,
        org.bukkit.Material.TUBE_CORAL, org.bukkit.Material.BUBBLE_CORAL, org.bukkit.Material.BRAIN_CORAL,
        org.bukkit.Material.FIRE_CORAL, org.bukkit.Material.HORN_CORAL,
        org.bukkit.Material.TUBE_CORAL_BLOCK, org.bukkit.Material.BUBBLE_CORAL_BLOCK,
        org.bukkit.Material.BRAIN_CORAL_BLOCK, org.bukkit.Material.FIRE_CORAL_BLOCK,
        org.bukkit.Material.HORN_CORAL_BLOCK,
        org.bukkit.Material.WARPED_ROOTS, org.bukkit.Material.CRIMSON_ROOTS, org.bukkit.Material.NETHER_SPROUTS,
        org.bukkit.Material.LILAC, org.bukkit.Material.ROSE_BUSH, org.bukkit.Material.PEONY,
        org.bukkit.Material.SUNFLOWER,
        org.bukkit.Material.OAK_SAPLING, org.bukkit.Material.BIRCH_SAPLING, org.bukkit.Material.JUNGLE_SAPLING,
        org.bukkit.Material.SPRUCE_SAPLING, org.bukkit.Material.CHERRY_SAPLING, org.bukkit.Material.ACACIA_SAPLING,
        org.bukkit.Material.DARK_OAK_SAPLING, org.bukkit.Material.MANGROVE_PROPAGULE,
        org.bukkit.Material.CHORUS_FLOWER, org.bukkit.Material.CHORUS_PLANT, org.bukkit.Material.SEA_PICKLE
    ));

    @Override
    public void onEnable() {
        instance = this;

        // Initialiser la clé pour marquer les entités du plugin
        enchantEntityKey = new NamespacedKey(this, "enchant_entity");

        // Sauvegarder la config par défaut
        saveDefaultConfig();

        // ═══════════════════════════════════════════════════════════════════════
        // NETTOYAGE DES ENTITÉS SURVIVANTES D'UN REBOOT/CRASH
        // ═══════════════════════════════════════════════════════════════════════
        cleanupEnchantEntities();

        // Enregistrer les listeners
        Bukkit.getPluginManager().registerEvents(this, this);

        // Initialiser et enregistrer le listener CyberLevel XP
        cyberLevelListener = new CyberLevelXPListener(this);
        Bukkit.getPluginManager().registerEvents(cyberLevelListener, this);

        // Enregistrer la commande reload
        getCommand("rinaenchants").setExecutor(new ReloadCommand(this));
        getCommand("rinaenchants").setTabCompleter(new ReloadCommand(this));

        // Task de nettoyage des locations expirées (toutes les 5 secondes)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::cleanupExpiredLocations, 100L, 100L);

        // Attendre que RivalHarvesterHoes soit chargé
        Bukkit.getScheduler().runTaskLater(this, this::initializeEnchants, 20L);

        getLogger().info("§6RinaEnchants §av" + getDescription().getVersion() + " §7activé!");
    }

    /**
     * Nettoie les locations expirées
     */
    private void cleanupExpiredLocations() {
        long now = System.currentTimeMillis();
        entityBreakingLocations.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    /**
     * Initialise les enchantements
     */
    public void initializeEnchants() {
        hoesAPI = getHarvesterHoesAPI();

        if (hoesAPI == null) {
            getLogger().severe("§c✗ Impossible de récupérer l'API de RivalHarvesterHoes!");
            getLogger().severe("§c  Vérifiez que RivalHarvesterHoes est bien installé et activé.");
            return;
        }

        registerAllEnchants();
    }

    /**
     * Enregistre tous les enchantements
     */
    private void registerAllEnchants() {
        registeredEnchants.clear();

        // ═══════════════════════════════════════════════════════════
        // ENCHANTEMENT BEE COLLECTOR
        // ═══════════════════════════════════════════════════════════
        if (getConfig().getBoolean("bee-collector.enabled", true)) {
            BeeCollectorEnchant beeEnchant = new BeeCollectorEnchant(this);
            hoesAPI.registerEnchant(beeEnchant);
            registeredEnchants.add(beeEnchant);

            getLogger().info("§a✓ Enchantement Bee Collector enregistré!");
        }

        // ═══════════════════════════════════════════════════════════
        // ENCHANTEMENT PANDA ROLL
        // ═══════════════════════════════════════════════════════════
        if (getConfig().getBoolean("panda-roll.enabled", true)) {
            PandaRollEnchant pandaEnchant = new PandaRollEnchant(this);
            hoesAPI.registerEnchant(pandaEnchant);
            registeredEnchants.add(pandaEnchant);

            getLogger().info("§a✓ Enchantement Panda Roll enregistré!");
        }

        // ═══════════════════════════════════════════════════════════
        // ENCHANTEMENT ALLAY LASER
        // ═══════════════════════════════════════════════════════════
        if (getConfig().getBoolean("allay-laser.enabled", true)) {
            AllayLaserEnchant allayEnchant = new AllayLaserEnchant(this);
            hoesAPI.registerEnchant(allayEnchant);
            registeredEnchants.add(allayEnchant);

            getLogger().info("§a✓ Enchantement Allay Laser enregistré!");
        }

        // ═══════════════════════════════════════════════════════════
        // ENCHANTEMENT RAVAGER STAMPEDE
        // ═══════════════════════════════════════════════════════════
        if (getConfig().getBoolean("ravager-stampede.enabled", true)) {
            RavagerStampedeEnchant ravagerEnchant = new RavagerStampedeEnchant(this);
            hoesAPI.registerEnchant(ravagerEnchant);
            registeredEnchants.add(ravagerEnchant);

            getLogger().info("§c✓ Enchantement Ravager Stampede enregistré!");
        }

        // ═══════════════════════════════════════════════════════════
        // ENCHANTEMENT BLIZZARD ÉTERNEL
        // ═══════════════════════════════════════════════════════════
        if (getConfig().getBoolean("blizzard-eternal.enabled", true)) {
            BlizzardEnchant blizzardEnchant = new BlizzardEnchant(this);
            hoesAPI.registerEnchant(blizzardEnchant);
            registeredEnchants.add(blizzardEnchant);

            getLogger().info("§b✓ Enchantement Blizzard Éternel enregistré!");
        }

        getLogger().info("§a✓ " + registeredEnchants.size() + " enchantement(s) chargé(s)!");
    }

    /**
     * Reload le plugin
     */
    public void reload() {
        getLogger().info("§eRechargement de RinaEnchants...");
        reloadConfig();

        // Recharger le listener CyberLevel
        if (cyberLevelListener != null) {
            cyberLevelListener.reload();
        }

        if (hoesAPI != null) {
            registerAllEnchants();
        } else {
            initializeEnchants();
        }

        getLogger().info("§a✓ RinaEnchants rechargé avec succès!");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("RivalHarvesterHoes")) {
            getLogger().info("§eRivalHarvesterHoes rechargé, ré-enregistrement des enchantements...");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                hoesAPI = getHarvesterHoesAPI();
                if (hoesAPI != null) {
                    registerAllEnchants();
                }
            }, 5L);
        }
    }

    private RivalHarvesterHoesAPI getHarvesterHoesAPI() {
        try {
            RegisteredServiceProvider<RivalHarvesterHoesAPI> provider =
                Bukkit.getServicesManager().getRegistration(RivalHarvesterHoesAPI.class);
            if (provider != null) {
                getLogger().info("§a✓ API récupérée via ServiceManager");
                return provider.getProvider();
            }
        } catch (Exception e) {
            if (getConfig().getBoolean("debug", false)) {
                getLogger().warning("§eServiceManager non disponible: " + e.getMessage());
            }
        }

        try {
            Plugin rivalPlugin = Bukkit.getPluginManager().getPlugin("RivalHarvesterHoes");
            if (rivalPlugin != null && rivalPlugin.isEnabled()) {
                java.lang.reflect.Field apiField = rivalPlugin.getClass().getDeclaredField("api");
                apiField.setAccessible(true);
                Object api = apiField.get(null);

                if (api instanceof RivalHarvesterHoesAPI) {
                    getLogger().info("§a✓ API récupérée via réflexion");
                    return (RivalHarvesterHoesAPI) api;
                }
            }
        } catch (Exception e) {
            if (getConfig().getBoolean("debug", false)) {
                getLogger().warning("§eRéflexion échouée: " + e.getMessage());
            }
        }

        try {
            Plugin rivalPlugin = Bukkit.getPluginManager().getPlugin("RivalHarvesterHoes");
            if (rivalPlugin != null && rivalPlugin.isEnabled()) {
                try {
                    Method getApiMethod = rivalPlugin.getClass().getMethod("getAPI");
                    Object api = getApiMethod.invoke(rivalPlugin);
                    if (api instanceof RivalHarvesterHoesAPI) {
                        return (RivalHarvesterHoesAPI) api;
                    }
                } catch (NoSuchMethodException ignored) {}

                try {
                    Method apiMethod = rivalPlugin.getClass().getMethod("api");
                    Object api = apiMethod.invoke(rivalPlugin);
                    if (api instanceof RivalHarvesterHoesAPI) {
                        return (RivalHarvesterHoesAPI) api;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            getLogger().warning("§eImpossible d'accéder à RivalHarvesterHoes: " + e.getMessage());
        }

        return null;
    }

    @Override
    public void onDisable() {
        // Nettoyer toutes les entités spawned par les enchantements
        cleanupEnchantEntities();

        entityBreakingLocations.clear();
        playerClientEntities.clear();
        getLogger().info("§6RinaEnchants §cdésactivé!");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SYSTÈME DE CLEANUP DES ENTITÉS D'ENCHANTEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Nettoie toutes les entités spawned par les enchantements dans tous les mondes.
     * Appelé au démarrage pour nettoyer les survivants d'un crash/reboot,
     * et à l'arrêt pour nettoyer proprement.
     */
    private void cleanupEnchantEntities() {
        int cleaned = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isEnchantEntity(entity)) {
                    entity.remove();
                    cleaned++;
                }
            }
        }

        if (cleaned > 0) {
            getLogger().info("§a✓ Nettoyage de " + cleaned + " entité(s) d'enchantement orpheline(s)");
        }
    }

    /**
     * Marque une entité comme appartenant à ce plugin.
     * Utilisé par les animations pour identifier leurs entités.
     */
    public void markAsEnchantEntity(Entity entity) {
        entity.getPersistentDataContainer().set(enchantEntityKey, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Vérifie si une entité a été spawned par ce plugin.
     */
    public boolean isEnchantEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(enchantEntityKey, PersistentDataType.BYTE);
    }

    /**
     * Retourne la clé utilisée pour marquer les entités (pour les animations).
     */
    public NamespacedKey getEnchantEntityKey() {
        return enchantEntityKey;
    }

    public static RinaEnchantsPlugin getInstance() {
        return instance;
    }

    public RivalHarvesterHoesAPI getHoesAPI() {
        return hoesAPI;
    }

    public CyberLevelXPListener getCyberLevelListener() {
        return cyberLevelListener;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTHODES DE TRACKING DES CASSAGES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Génère une clé unique pour une location
     */
    private String getLocationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    /**
     * Marque une location comme étant cassée par une entité (abeille/panda/allay)
     * Cette location ne pourra pas déclencher d'enchantement pendant 500ms
     */
    public void markEntityBreakingLocation(Location loc) {
        String key = getLocationKey(loc);
        // Expire après 500ms
        entityBreakingLocations.put(key, System.currentTimeMillis() + 500);
    }

    /**
     * Vérifie si une location est en train d'être cassée par une entité
     */
    public boolean isEntityBreakingLocation(Location loc) {
        String key = getLocationKey(loc);
        Long expiry = entityBreakingLocations.get(key);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) {
            entityBreakingLocations.remove(key);
            return false;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SYSTÈME CLIENT-SIDE (visibilité des entités)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Cache une entité à tous les joueurs sauf un (optimisation serveur 500 joueurs)
     */
    public void makeEntityClientSide(Entity entity, Player visibleTo) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(visibleTo)) {
                online.hideEntity(this, entity);
            }
        }

        // Tracker l'entité pour cleanup
        playerClientEntities.computeIfAbsent(visibleTo.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
            .add(entity.getEntityId());
    }

    /**
     * Enregistre un ID d'entité client-side pour un joueur
     */
    public void trackClientEntity(UUID playerId, int entityId) {
        playerClientEntities.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(entityId);
    }

    /**
     * Retire un ID d'entité du tracking
     */
    public void untrackClientEntity(UUID playerId, int entityId) {
        Set<Integer> entities = playerClientEntities.get(playerId);
        if (entities != null) {
            entities.remove(entityId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTHODE SÉCURISÉE POUR CASSER LES CULTURES
    // Utilise HellRainAbility.replaceWithDrops (comme AirStrike)
    // Cela donne les multiplicateurs RivalHarvesterHoes SANS déclencher d'autres enchantements
    // ═══════════════════════════════════════════════════════════════════════

    private java.lang.reflect.Method hellRainReplaceWithDrops = null;
    private java.lang.reflect.Method hellRainReplaceCropsde = null;
    private boolean hellRainMethodsChecked = false;

    /**
     * Casse une culture de manière sécurisée en utilisant HellRainAbility.
     * Cette méthode est utilisée par AirStrike et donne les multiplicateurs
     * de RivalHarvesterHoes SANS déclencher d'autres enchantements.
     *
     * Chaque culture cassée donne l'XP configuré dans cyber-level-xp-per-block.
     *
     * @param player Le joueur qui "casse" la culture
     * @param cropLocation La location de la culture
     * @param enchantId L'ID de la section d'enchantement (ex: "panda-roll", "bee-collector")
     * @return true si le bloc a été cassé
     */
    public boolean safeBreakCrop(Player player, Location cropLocation, String enchantId) {
        org.bukkit.block.Block block = cropLocation.getBlock();
        org.bukkit.Material blockType = block.getType();

        if (blockType.isAir()) {
            return false;
        }

        boolean debug = getConfig().getBoolean("debug", false);
        if (debug) {
            getLogger().info("§e[safeBreakCrop] Type détecté: " + blockType + " à " + cropLocation.toVector());
        }

        // Vérifier si c'est une culture mature
        // Pour les NO_AGE_CROPS (coraux, saplings, etc.), pas besoin de vérifier l'âge
        if (!NO_AGE_CROPS.contains(blockType)) {
            if (!(block.getBlockData() instanceof org.bukkit.block.data.Ageable)) {
                if (debug) {
                    getLogger().info("§c[safeBreakCrop] Bloc non-Ageable ignoré: " + blockType);
                }
                return false;
            }
            org.bukkit.block.data.Ageable ageable = (org.bukkit.block.data.Ageable) block.getBlockData();
            if (ageable.getAge() < ageable.getMaximumAge()) {
                if (debug) {
                    getLogger().info("§c[safeBreakCrop] Bloc non mature ignoré: " + blockType);
                }
                return false;
            }
        }

        // Marquer la location AVANT pour éviter les cascades internes
        markEntityBreakingLocation(cropLocation);

        // Charger les méthodes HellRainAbility via réflexion (une seule fois)
        if (!hellRainMethodsChecked) {
            hellRainMethodsChecked = true;
            try {
                Class<?> hellRainClass = Class.forName("me.rivaldev.harvesterhoes.abilities.HellRainAbility");

                // replaceWithDrops(Player, Location, double radius, Material, long level)
                hellRainReplaceWithDrops = hellRainClass.getMethod("replaceWithDrops",
                    Player.class, Location.class, double.class, org.bukkit.Material.class, long.class);

                // replaceCropsde comme backup
                hellRainReplaceCropsde = hellRainClass.getMethod("replaceCropsde",
                    Player.class, Location.class, double.class, org.bukkit.Material.class, long.class);

                if (debug) {
                    getLogger().info("§a[RinaEnchants] HellRainAbility méthodes trouvées!");
                }
            } catch (Exception e) {
                if (debug) {
                    getLogger().warning("§e[RinaEnchants] HellRainAbility non trouvé: " + e.getMessage());
                }
            }
        }

        // Variable pour tracker si le bloc a été cassé avec succès
        boolean cropBroken = false;

        // Utiliser HellRainAbility.replaceWithDrops (comme AirStrike)
        if (hellRainReplaceWithDrops != null) {
            try {
                // Rayon de 0.5 = seulement le bloc central (évite de casser les blocs adjacents)
                hellRainReplaceWithDrops.invoke(null, player, cropLocation, 0.5, blockType, 1L);
                cropBroken = true;

                if (debug) {
                    getLogger().info("§a[safeBreakCrop] Bloc cassé via HellRainAbility: " + blockType);
                }
            } catch (Exception e) {
                if (debug) {
                    getLogger().warning("§e[RinaEnchants] Erreur replaceWithDrops: " + e.getMessage());
                }
                // Essayer replaceCropsde comme backup
                if (hellRainReplaceCropsde != null) {
                    try {
                        hellRainReplaceCropsde.invoke(null, player, cropLocation, 0.5, blockType, 1L);
                        cropBroken = true;

                        if (debug) {
                            getLogger().info("§a[safeBreakCrop] Bloc cassé via HellRainAbility (backup): " + blockType);
                        }
                    } catch (Exception e2) {
                        if (debug) {
                            getLogger().warning("§e[RinaEnchants] Erreur replaceCropsde: " + e2.getMessage());
                        }
                    }
                }
            }
        }

        // Fallback: casser manuellement avec drops si HellRainAbility n'a pas fonctionné
        // ═══════════════════════════════════════════════════════════════════════
        // INTÉGRATION RIVALHARVESTERHOES: Déclencher RivalBlockBreakEvent
        // Cela permet à CyberLevel et autres plugins de détecter la cassure
        // ═══════════════════════════════════════════════════════════════════════
        if (!cropBroken) {
            org.bukkit.inventory.ItemStack hoeItem = player.getInventory().getItemInMainHand();

            // Créer et appeler l'événement RivalBlockBreakEvent
            // Paramètres: Player, Block, amount, hoe_level, hoe_prestige, ItemStack, Material
            RivalBlockBreakEvent rivalEvent = new RivalBlockBreakEvent(
                player,
                block,
                1,              // amount: 1 bloc cassé
                1,              // hoe_level: niveau par défaut
                0,              // hoe_prestige: prestige par défaut
                hoeItem,
                blockType
            );

            // Appeler l'événement pour que les autres plugins (CyberLevel) le voient
            Bukkit.getPluginManager().callEvent(rivalEvent);

            // Vérifier si un autre plugin a annulé l'événement
            if (rivalEvent.isCancelled()) {
                if (debug) {
                    getLogger().info("§c[safeBreakCrop] RivalBlockBreakEvent annulé par un autre plugin");
                }
                return false;
            }

            // L'événement n'est pas annulé, on peut casser le bloc
            java.util.Collection<org.bukkit.inventory.ItemStack> drops = block.getDrops(hoeItem);
            block.setType(org.bukkit.Material.AIR);

            org.bukkit.Location dropLoc = cropLocation.clone().add(0.5, 0.5, 0.5);
            for (org.bukkit.inventory.ItemStack drop : drops) {
                java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftover =
                    player.getInventory().addItem(drop);
                for (org.bukkit.inventory.ItemStack item : leftover.values()) {
                    block.getWorld().dropItemNaturally(dropLoc, item);
                }
            }
            cropBroken = true;

            if (debug) {
                getLogger().info("§a[safeBreakCrop] Bloc cassé via RivalBlockBreakEvent (fallback): " + blockType);
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // DONNER L'XP CYBERLEVEL APRÈS AVOIR CASSÉ LE BLOC
        // L'XP est configuré dans <enchant-id>.cyber-level-xp-per-block
        // ═══════════════════════════════════════════════════════════════════════
        if (cropBroken && cyberLevelListener != null && enchantId != null) {
            if (debug) {
                getLogger().info("§a[safeBreakCrop] Appel queueXPForCrop (" + enchantId + ") pour " + player.getName());
            }
            cyberLevelListener.queueXPForCrop(player, enchantId);
        }

        return cropBroken;
    }
}
