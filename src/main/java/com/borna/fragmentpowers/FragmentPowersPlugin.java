package com.borna.fragmentpowers;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FragmentPowersPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NamespacedKey idKey;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> phantomUsers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> phantomImmune = ConcurrentHashMap.newKeySet();
    private final Set<UUID> rockSkin = ConcurrentHashMap.newKeySet();
    private final Set<UUID> bubble = ConcurrentHashMap.newKeySet();
    private final Set<UUID> featherFall = ConcurrentHashMap.newKeySet();
    private final Set<UUID> ember = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> traps = new ConcurrentHashMap<>();
    private final Set<UUID> berserker = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemStack[]> berserkerHands = new ConcurrentHashMap<>();
    private final Map<UUID, Double> oldAttackSpeed = new ConcurrentHashMap<>();

    private static final List<String> IDS = List.of(
            "power_rock_skin", "power_quick_step", "power_feather_fall", "power_item_pull",
            "power_ember", "power_bubble", "power_tiny_trap", "power_silent_step",
            "super_phantom", "super_berserker", "super_frost_blast", "extreme_warden_sonic_boom"
    );

    @Override public void onEnable() {
        idKey = new NamespacedKey(this, "fragment_id");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("fragment")).setExecutor(this);
        Objects.requireNonNull(getCommand("fragment")).setTabCompleter(this);
        registerExtremeRecipe();
        getLogger().info("FragmentPowers enabled for Paper 1.21.11.");
    }

    private String niceName(String id) {
        return switch (id) {
            case "power_rock_skin" -> "Rock Skin";
            case "power_quick_step" -> "Quick Step";
            case "power_feather_fall" -> "Feather Fall";
            case "power_item_pull" -> "Item Pull";
            case "power_ember" -> "Ember";
            case "power_bubble" -> "Bubble";
            case "power_tiny_trap" -> "Tiny Trap";
            case "power_silent_step" -> "Silent Step";
            case "super_phantom" -> "Phantom";
            case "super_berserker" -> "Berserker";
            case "super_frost_blast" -> "Frost Blast";
            default -> "Warden Sonic Boom";
        };
    }

    private ItemStack fragment(String id) {
        Material material = id.startsWith("power_") ? Material.CANDLE : id.startsWith("super_") ? Material.RED_CANDLE : Material.BLACK_CANDLE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String tier = id.startsWith("power_") ? "§dFragment Power" : id.startsWith("super_") ? "§bFragment Super Power" : "§cFragment Extreme";
        meta.setDisplayName(tier + " §f• " + niceName(id));
        meta.setLore(List.of("§7Press Q to activate", "§8The fragment is consumed."));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);

        // Paper 1.21.11 component API: the resource pack supplies this item model.
        item.setData(DataComponentTypes.ITEM_MODEL, new NamespacedKey("fragmentpowers", "fragment/" + id));
        return item;
    }

    private String getId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    private boolean cooldown(Player p, String key, long millis) {
        long now = System.currentTimeMillis();
        long end = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < end) {
            p.sendMessage("§cCooldown: " + ((end - now + 999) / 1000) + "s");
            return false;
        }
        cooldowns.put(p.getUniqueId(), now + millis);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        String id = getId(item);
        if (id == null) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (!canActivate(p, id)) return;
        consumeMainHand(p);
        activate(p, id);
    }

    private boolean canActivate(Player p, String id) {
        return switch (id) {
            case "power_quick_step" -> cooldown(p, id, 4000);
            case "power_item_pull" -> cooldown(p, id, 5000);
            case "power_tiny_trap" -> cooldown(p, id, 8000);
            case "power_silent_step" -> cooldown(p, id, 10000);
            case "super_berserker" -> !berserker.contains(p.getUniqueId()) && cooldown(p, id, 15000);
            case "super_frost_blast" -> cooldown(p, id, 12000);
            case "extreme_warden_sonic_boom" -> cooldown(p, id, 15000);
            default -> true;
        };
    }

    private void consumeMainHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        else hand.setAmount(hand.getAmount() - 1);
    }

    private void activate(Player p, String id) {
        switch (id) {
            case "power_rock_skin" -> { rockSkin.add(p.getUniqueId()); p.sendMessage("§dRock Skin ready — next hit is reduced by 50%."); }
            case "power_quick_step" -> dash(p);
            case "power_feather_fall" -> { featherFall.add(p.getUniqueId()); p.sendMessage("§dFeather Fall ready — next fall does no damage."); }
            case "power_item_pull" -> pullItems(p);
            case "power_ember" -> { ember.add(p.getUniqueId()); p.sendMessage("§dEmber ready — next hit ignites the target."); }
            case "power_bubble" -> { bubble.add(p.getUniqueId()); p.sendMessage("§dBubble ready — next damage instance is blocked."); }
            case "power_tiny_trap" -> { traps.put(p.getUniqueId(), p.getLocation().clone()); p.sendMessage("§dTiny Trap placed."); }
            case "power_silent_step" -> { p.setSilent(true); Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) p.setSilent(false); }, 100); }
            case "super_phantom" -> { phantomUsers.add(p.getUniqueId()); p.sendMessage("§bPHANTOM acquired — every player kill gives 10s invisibility + immunity."); }
            case "super_berserker" -> startBerserker(p);
            case "super_frost_blast" -> frostBlast(p);
            case "extreme_warden_sonic_boom" -> sonicBoom(p);
        }
    }

    private void dash(Player p) {
        Vector v = p.getLocation().getDirection().normalize().multiply(1.7);
        v.setY(Math.max(0.35, v.getY() + 0.2));
        p.setVelocity(v);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.6f);
    }

    private void pullItems(Player p) {
        for (Entity e : p.getNearbyEntities(8, 8, 8)) {
            if (e instanceof Item item) {
                Vector v = p.getLocation().toVector().subtract(item.getLocation().toVector()).normalize().multiply(0.9);
                item.setVelocity(v);
            }
        }
    }

    private void startBerserker(Player p) {
        UUID u = p.getUniqueId();
        berserker.add(u);
        berserkerHands.put(u, new ItemStack[]{p.getInventory().getItemInMainHand().clone(), p.getInventory().getItemInOffHand().clone()});
        p.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
        p.getInventory().setItemInOffHand(new ItemStack(Material.NETHERITE_AXE));
        var attr = p.getAttribute(Attribute.ATTACK_SPEED);
        if (attr != null) { oldAttackSpeed.put(u, attr.getBaseValue()); attr.setBaseValue(20.0); }
        p.sendMessage("§cBERSERKER! §7Two axes, very fast attacks — 3 seconds.");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.8f);
        Bukkit.getScheduler().runTaskLater(this, () -> stopBerserker(p), 60L);
    }

    private void stopBerserker(Player p) {
        UUID u = p.getUniqueId();
        ItemStack[] old = berserkerHands.remove(u);
        if (old != null && p.isOnline()) {
            p.getInventory().setItemInMainHand(old[0]);
            p.getInventory().setItemInOffHand(old[1]);
        }
        var attr = p.getAttribute(Attribute.ATTACK_SPEED);
        Double oldSpeed = oldAttackSpeed.remove(u);
        if (attr != null && oldSpeed != null) attr.setBaseValue(oldSpeed);
        berserker.remove(u);
        if (p.isOnline()) p.sendMessage("§7Berserker ended.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target) {
            UUID u = target.getUniqueId();
            if (phantomImmune.contains(u)) { event.setCancelled(true); return; }
            if (bubble.remove(u)) {
                event.setCancelled(true);
                target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
                target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0,1,0), 15, .5,.7,.5,.02);
                return;
            }
            if (rockSkin.remove(u)) event.setDamage(event.getDamage() * 0.5);
        }

        if (event.getDamager() instanceof Player p) {
            UUID u = p.getUniqueId();
            if (ember.remove(u) && event.getEntity() instanceof LivingEntity target) target.setFireTicks(60);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGenericDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        UUID u = p.getUniqueId();
        if (phantomImmune.contains(u)) { event.setCancelled(true); return; }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && featherFall.remove(u)) event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && berserker.contains(u)) event.setDamage(event.getDamage());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        UUID deadId = dead.getUniqueId();
        phantomUsers.remove(deadId); phantomImmune.remove(deadId); rockSkin.remove(deadId); bubble.remove(deadId);
        featherFall.remove(deadId); ember.remove(deadId); traps.remove(deadId);
        if (berserker.contains(deadId)) stopBerserker(dead);

        Player killer = dead.getKiller();
        if (killer != null && phantomUsers.contains(killer.getUniqueId())) activatePhantom(killer);
    }

    private void activatePhantom(Player p) {
        UUID u = p.getUniqueId();
        phantomImmune.add(u);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, false, false, false));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.6f);
        p.sendMessage("§bPHANTOM! §7Invisible + immune for 10 seconds.");
        Bukkit.getScheduler().runTaskLater(this, () -> phantomImmune.remove(u), 200L);
    }

    private void frostBlast(Player p) {
        Location center = p.getLocation();
        World w = p.getWorld();
        w.playSound(center, Sound.BLOCK_GLASS_BREAK, 2f, .5f);
        w.spawnParticle(Particle.SNOWFLAKE, center.clone().add(0,1,0), 100, 3,1.5,3,.08);
        w.spawnParticle(Particle.CLOUD, center.clone().add(0,1,0), 50, 2,.5,2,.04);
        for (Entity e : p.getNearbyEntities(6, 3, 6)) {
            if (!(e instanceof Player target) || target == p) continue;
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), 60));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 10, false, false, false));
        }
    }

    private void sonicBoom(Player p) {
        Location start = p.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        World w = p.getWorld();
        for (int i = 1; i <= 30; i++) {
            Location point = start.clone().add(direction.clone().multiply(i));
            w.spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
            for (Entity entity : w.getNearbyEntities(point, 1.25, 1.25, 1.25)) {
                if (entity instanceof LivingEntity target && target != p) {
                    target.damage(20.0, p);
                    w.playSound(point, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 1f);
                    return;
                }
            }
        }
        w.playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 1f);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;
        Location trap = traps.get(p.getUniqueId());
        if (trap != null && sameWorld(trap, p.getLocation()) && trap.distanceSquared(p.getLocation()) < 2.25) triggerTrap(p, p.getUniqueId());
        for (Map.Entry<UUID, Location> entry : new ArrayList<>(traps.entrySet())) {
            if (entry.getKey().equals(p.getUniqueId())) continue;
            if (sameWorld(entry.getValue(), p.getLocation()) && entry.getValue().distanceSquared(p.getLocation()) < 2.25) triggerTrap(p, entry.getKey());
        }
    }

    private boolean sameWorld(Location a, Location b) { return a.getWorld() != null && a.getWorld().equals(b.getWorld()); }
    private void triggerTrap(Player p, UUID owner) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 10, false, false, false));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SNOW_PLACE, 1f, .7f);
        traps.remove(owner);
    }

    private void registerExtremeRecipe() {
        NamespacedKey key = new NamespacedKey(this, "fragment_extreme");
        ItemStack result = fragment("extreme_warden_sonic_boom");
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        RecipeChoice superChoice = new RecipeChoice.ExactChoice(
                fragment("super_phantom"), fragment("super_berserker"), fragment("super_frost_blast")
        );
        recipe.shape("NSN", "SES", "NSN");
        recipe.setIngredient('N', Material.NETHERITE_BLOCK);
        recipe.setIngredient('S', superChoice);
        recipe.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
        getServer().addRecipe(recipe);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
            String id = args[2].toLowerCase(Locale.ROOT);
            if (!IDS.contains(id)) { sender.sendMessage("§cUnknown fragment id."); return true; }
            int amount = 1;
            if (args.length >= 4) try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException ignored) {}
            ItemStack item = fragment(id);
            item.setAmount(Math.min(amount, item.getMaxStackSize()));
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
            leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
            sender.sendMessage("§aGave §f" + amount + "x §e" + id + " §ato " + target.getName());
            return true;
        }
        sender.sendMessage("§e/fragment give <player> <id> [amount]");
        sender.sendMessage("§7IDs: " + String.join(", ", IDS));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("give");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3) return IDS;
        return List.of();
    }
}
