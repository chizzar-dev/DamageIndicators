package com.metox.indicators;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Hasar gostergeleri, oyuncu can gosterimi ve mob can barlari.
 *
 * Gostergeler ArmorStand ile yapilir; bu sayede 1.8'den 1.21'e kadar
 * ek bir kutuphane olmadan calisir. 1.8'de setGravity API'si olmadigi
 * icin tasiyicinin konumu her karede sabitlenir, boylece dusmez.
 */
public class DamageIndicators extends JavaPlugin implements Listener, TabExecutor {

    private final Random random = new Random();
    private DecimalFormat format;

    /** Ayni varliga art arda gosterge cikmasini engeller. */
    private final Map<UUID, Long> lastShown = new HashMap<UUID, Long>();
    /** Can barindan once mobun kendi ismi (geri yuklemek icin). */
    private final Map<UUID, String> originalNames = new HashMap<UUID, String>();
    /** Can barinin gizlenecegi zaman. */
    private final Map<UUID, Long> hideAt = new HashMap<UUID, Long>();

    private Objective belowName;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        buildFormat();

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("dmgindicators") != null) {
            getCommand("dmgindicators").setExecutor(this);
            getCommand("dmgindicators").setTabCompleter(this);
        }

        setupBelowName();
        startTasks();

        getLogger().info("DamageIndicators aktif - algilanan surum 1." + Compat.MINOR
                + (Compat.PATCH > 0 ? "." + Compat.PATCH : ""));
    }

    @Override
    public void onDisable() {
        // Degistirdigimiz mob isimlerini geri ver
        for (Map.Entry<UUID, String> en : new HashMap<UUID, String>(originalNames).entrySet()) {
            Entity ent = findEntity(en.getKey());
            if (ent != null) restoreName(ent, en.getValue());
        }
        originalNames.clear();
        hideAt.clear();
        removeBelowName();
    }

    private void buildFormat() {
        int decimals = Math.max(0, Math.min(4, getConfig().getInt("damage-indicator.decimals", 1)));
        StringBuilder pattern = new StringBuilder("0");
        if (decimals > 0) {
            pattern.append('.');
            for (int i = 0; i < decimals; i++) pattern.append('#');
        }
        format = new DecimalFormat(pattern.toString(), new DecimalFormatSymbols(Locale.ENGLISH));
    }

    // ------------------------------------------------------------------
    // Hasar gostergesi
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!getConfig().getBoolean("damage-indicator.enabled", true)) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;

        LivingEntity victim = (LivingEntity) e.getEntity();
        if (isDisabledWorld(victim.getWorld())) return;
        if (isIgnoredType(victim)) return;

        String cause = causeName(e);
        if (isIgnoredCause(cause)) return;

        Entity damager = null;
        if (e instanceof EntityDamageByEntityEvent) {
            damager = ((EntityDamageByEntityEvent) e).getDamager();
        }
        if (getConfig().getBoolean("damage-indicator.only-from-players", false)
                && !(damager instanceof Player)) return;

        double damage = e.getFinalDamage();
        if (damage <= 0) return;
        if (!throttle(victim.getUniqueId())) return;

        boolean dies = victim.getHealth() - damage <= 0;
        boolean crit = damager instanceof Player && isCritical((Player) damager);

        String text;
        if (dies && has("damage-indicator.death-format")) {
            text = getConfig().getString("damage-indicator.death-format");
        } else {
            String custom = getConfig().getString("damage-indicator.causes." + cause, null);
            String base = custom != null ? custom
                    : crit ? getConfig().getString("damage-indicator.crit-format",
                    getConfig().getString("damage-indicator.format", "&c-%damage%"))
                    : getConfig().getString("damage-indicator.format", "&c-%damage%");
            text = base;
        }
        spawnIndicator(victim, text.replace("%damage%", format.format(damage)));

        if (getConfig().getBoolean("health-bar.enabled", true)) {
            // Can, olaydan sonra guncellenir; bir tick bekle
            scheduleHealthBar(victim);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (!getConfig().getBoolean("damage-indicator.show-heal", false)) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;

        LivingEntity ent = (LivingEntity) e.getEntity();
        if (isDisabledWorld(ent.getWorld()) || isIgnoredType(ent)) return;
        if (e.getAmount() <= 0 || !throttle(ent.getUniqueId())) return;

        spawnIndicator(ent, getConfig().getString("damage-indicator.heal-format", "&a+%damage%")
                .replace("%damage%", format.format(e.getAmount())));
    }

    /** Ayni varlik icin gostergeler arasi en az sure gecti mi? */
    private boolean throttle(UUID id) {
        long gap = getConfig().getLong("damage-indicator.throttle-ms", 350);
        if (gap <= 0) return true;
        long now = System.currentTimeMillis();
        Long last = lastShown.get(id);
        if (last != null && now - last < gap) return false;
        lastShown.put(id, now);
        return true;
    }

    private boolean isCritical(Player p) {
        try {
            return p.getFallDistance() > 0.0F && !p.isOnGround()
                    && p.getLocation().getBlock().getType().name().equals("AIR");
        } catch (Throwable t) {
            return false;
        }
    }

    /** Yuzen yaziyi olusturur ve suresi dolunca kaldirir. */
    private void spawnIndicator(LivingEntity victim, String text) {
        final World world = victim.getWorld();
        if (world == null) return;

        double spread = getConfig().getDouble("damage-indicator.random-spread", 0.5);
        double offsetY = getConfig().getDouble("damage-indicator.offset-y", 1.4);

        final Location at = victim.getLocation().clone().add(
                (random.nextDouble() - 0.5) * spread,
                offsetY,
                (random.nextDouble() - 0.5) * spread);

        final ArmorStand stand;
        try {
            stand = world.spawn(at, ArmorStand.class);
        } catch (Throwable t) {
            return;
        }
        Compat.prepHologramStand(stand);
        stand.setCustomName(Compat.color(text));
        stand.setCustomNameVisible(true);

        final int duration = Math.max(1, getConfig().getInt("damage-indicator.duration-ticks", 20));
        final double rise = getConfig().getDouble("damage-indicator.rise", 0.6);
        final double perStep = rise / (duration / 2.0);

        new BukkitRunnable() {
            int ticks = 0;
            final Location pos = at.clone();

            @Override
            public void run() {
                if (ticks >= duration || stand.isDead() || !stand.isValid()) {
                    try {
                        stand.remove();
                    } catch (Throwable ignored) {
                    }
                    cancel();
                    return;
                }
                // 1.8'de yercekimi kapatilamaz; konumu sabitleyerek dusmesini onleriz
                pos.add(0, perStep, 0);
                try {
                    stand.teleport(pos);
                } catch (Throwable ignored) {
                }
                ticks += 2;
            }
        }.runTaskTimer(this, 2L, 2L);
    }

    // ------------------------------------------------------------------
    // Mob can bari
    // ------------------------------------------------------------------

    private void scheduleHealthBar(final LivingEntity ent) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (ent.isDead() || !ent.isValid()) return;
                applyHealthBar(ent);
            }
        }.runTaskLater(this, 1L);
    }

    private void applyHealthBar(LivingEntity ent) {
        if (!getConfig().getBoolean("health-bar.enabled", true)) return;
        if (ent instanceof Player) return;
        if (isDisabledWorld(ent.getWorld()) || isIgnoredType(ent)) return;

        UUID id = ent.getUniqueId();
        if (!originalNames.containsKey(id)) {
            String current = null;
            try {
                current = ent.getCustomName();
            } catch (Throwable ignored) {
            }
            originalNames.put(id, current == null ? "" : current);
        }

        try {
            ent.setCustomName(buildBar(ent));
            ent.setCustomNameVisible(true);
        } catch (Throwable ignored) {
            return;
        }

        if (!getConfig().getBoolean("health-bar.always", true)) {
            int after = Math.max(1, getConfig().getInt("health-bar.hide-after", 6));
            hideAt.put(id, System.currentTimeMillis() + after * 1000L);
        }
    }

    private String buildBar(LivingEntity ent) {
        double current = Math.max(0, ent.getHealth());
        double max = Math.max(1, Compat.maxHealth(ent));
        double ratio = Math.max(0, Math.min(1, current / max));

        String mode = getConfig().getString("health-bar.mode", "NUMBER");
        if ("NUMBER".equalsIgnoreCase(mode)) {
            return Compat.color(getConfig().getString("health-bar.number-format", "&f%current% &c❤")
                    .replace("%current%", format.format(current))
                    .replace("%max%", format.format(max))
                    .replace("%percent%", String.valueOf((int) (ratio * 100))));
        }

        int length = Math.max(1, getConfig().getInt("health-bar.length", 10));
        String symbol = getConfig().getString("health-bar.symbol", "❤");
        int filled = (int) Math.round(ratio * length);

        String color = ratio > 0.66 ? getConfig().getString("health-bar.high-color", "&a")
                : ratio > 0.33 ? getConfig().getString("health-bar.medium-color", "&e")
                : getConfig().getString("health-bar.low-color", "&c");
        String empty = getConfig().getString("health-bar.empty-color", "&7");

        StringBuilder bar = new StringBuilder(color);
        for (int i = 0; i < length; i++) {
            if (i == filled) bar.append(empty);
            bar.append(symbol);
        }
        return Compat.color(getConfig().getString("health-bar.format", "%bar%")
                .replace("%bar%", bar.toString())
                .replace("%current%", format.format(current))
                .replace("%max%", format.format(max)));
    }

    private void restoreName(Entity ent, String original) {
        try {
            if (original == null || original.isEmpty()) {
                ent.setCustomName(null);
                ent.setCustomNameVisible(false);
            } else {
                ent.setCustomName(original);
                ent.setCustomNameVisible(true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Olen mobun ismini geri yukleme kaydini temizler. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        originalNames.remove(id);
        hideAt.remove(id);
        lastShown.remove(id);
    }

    // ------------------------------------------------------------------
    // Zamanlayicilar
    // ------------------------------------------------------------------

    private void startTasks() {
        // Can barlarini tazele ve suresi dolanlari gizle
        int refresh = Math.max(5, getConfig().getInt("health-bar.refresh-ticks", 20));
        new BukkitRunnable() {
            @Override
            public void run() {
                if (originalNames.isEmpty()) return;
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, String> en : new HashMap<UUID, String>(originalNames).entrySet()) {
                    Entity ent = findEntity(en.getKey());
                    if (ent == null || ent.isDead() || !ent.isValid()) {
                        originalNames.remove(en.getKey());
                        hideAt.remove(en.getKey());
                        continue;
                    }
                    Long until = hideAt.get(en.getKey());
                    if (until != null && now >= until) {
                        restoreName(ent, en.getValue());
                        originalNames.remove(en.getKey());
                        hideAt.remove(en.getKey());
                        continue;
                    }
                    if (getConfig().getBoolean("health-bar.always", true)
                            && ent instanceof LivingEntity) {
                        try {
                            ent.setCustomName(buildBar((LivingEntity) ent));
                            ent.setCustomNameVisible(true);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }.runTaskTimer(this, refresh, refresh);

        // Kendi canini action bar'da goster
        final int selfInterval = Math.max(5, getConfig().getInt("self-health.interval-ticks", 10));
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!getConfig().getBoolean("self-health.actionbar", false)) return;
                String fmt = getConfig().getString("self-health.format",
                        "&c❤ &f%current%&7/&f%max%");
                for (Player p : Compat.online()) {
                    if (isDisabledWorld(p.getWorld())) continue;
                    Compat.actionBar(p, fmt
                            .replace("%current%", format.format(Math.max(0, p.getHealth())))
                            .replace("%max%", format.format(Compat.maxHealth(p))));
                }
            }
        }.runTaskTimer(this, selfInterval, selfInterval);

        // Bellek temizligi
        new BukkitRunnable() {
            @Override
            public void run() {
                if (lastShown.size() > 500) lastShown.clear();
            }
        }.runTaskTimer(this, 1200L, 1200L);
    }

    // ------------------------------------------------------------------
    // Isim altinda can (scoreboard)
    // ------------------------------------------------------------------

    private void setupBelowName() {
        if (!getConfig().getBoolean("player-health.enabled", true)) return;
        try {
            if (getServer().getScoreboardManager() == null) return;
            Scoreboard board = getServer().getScoreboardManager().getMainScoreboard();

            Objective existing = board.getObjective("ci_health");
            if (existing != null) existing.unregister();

            belowName = board.registerNewObjective("ci_health", "health");
            belowName.setDisplayName(Compat.color(getConfig().getString("player-health.symbol", "&c❤")));
            belowName.setDisplaySlot(DisplaySlot.BELOW_NAME);

            if (getConfig().getBoolean("player-health.tablist", false)) {
                Objective tab = board.getObjective("ci_health_tab");
                if (tab != null) tab.unregister();
                Objective t = board.registerNewObjective("ci_health_tab", "health");
                t.setDisplaySlot(DisplaySlot.PLAYER_LIST);
            }
        } catch (Throwable t) {
            getLogger().warning("Isim alti can gosterimi kurulamadi: " + t.getMessage());
            belowName = null;
        }
    }

    private void removeBelowName() {
        try {
            if (getServer().getScoreboardManager() == null) return;
            Scoreboard board = getServer().getScoreboardManager().getMainScoreboard();
            for (String name : new String[]{"ci_health", "ci_health_tab"}) {
                Objective o = board.getObjective(name);
                if (o != null) o.unregister();
            }
        } catch (Throwable ignored) {
        }
        belowName = null;
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    private String causeName(EntityDamageEvent e) {
        try {
            return e.getCause().name();
        } catch (Throwable t) {
            return "CUSTOM";
        }
    }

    private boolean isIgnoredCause(String cause) {
        for (String s : getConfig().getStringList("damage-indicator.ignored-causes")) {
            if (s != null && s.equalsIgnoreCase(cause)) return true;
        }
        return false;
    }

    private boolean isIgnoredType(Entity ent) {
        String type;
        try {
            type = ent.getType().name();
        } catch (Throwable t) {
            return true;
        }
        if (type.equals("ARMOR_STAND")) return true;
        for (String s : getConfig().getStringList("health-bar.ignored")) {
            if (s != null && s.equalsIgnoreCase(type)) return true;
        }
        return false;
    }

    private boolean isDisabledWorld(World w) {
        if (w == null) return true;
        for (String s : getConfig().getStringList("disabled-worlds")) {
            if (s != null && s.equalsIgnoreCase(w.getName())) return true;
        }
        return false;
    }

    private boolean has(String path) {
        String s = getConfig().getString(path, "");
        return s != null && !s.isEmpty();
    }

    /** Bukkit.getEntity 1.12+ ile geldi; eskide dunyalari tararız. */
    private Entity findEntity(UUID id) {
        try {
            Object e = Bukkit.class.getMethod("getEntity", UUID.class).invoke(null, id);
            if (e instanceof Entity) return (Entity) e;
        } catch (Throwable ignored) {
        }
        for (World w : Bukkit.getWorlds()) {
            try {
                for (Entity e : w.getEntities()) {
                    if (e.getUniqueId().equals(id)) return e;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Komut
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dmgindicators.reload")) {
                sender.sendMessage(Compat.color("&cBunun icin yetkin yok."));
                return true;
            }
            reloadConfig();
            buildFormat();
            removeBelowName();
            setupBelowName();
            sender.sendMessage(Compat.color(getConfig().getString(
                    "messages.reloaded", "&aDamageIndicators yeniden yuklendi.")));
            return true;
        }
        sender.sendMessage(Compat.color("&7/" + label + " reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ENGLISH))) {
            return new ArrayList<String>(Arrays.asList("reload"));
        }
        return Collections.emptyList();
    }
}
