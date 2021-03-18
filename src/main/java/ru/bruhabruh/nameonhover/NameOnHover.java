package ru.bruhabruh.nameonhover;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.event.Listener;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;

public final class NameOnHover extends JavaPlugin implements Listener {

    private static Map<Player, Entity> playerEntityHashMap = new HashMap<>();

    @Override
    public void onEnable() {

        Logger logger = Bukkit.getLogger();
        try {
            Bukkit.getPluginManager().registerEvents(this, this);
            /*ProtocolManager manager = ProtocolLibrary.getProtocolManager();
            manager.addPacketListener(new PacketAdapter(this,   ListenerPriority.NORMAL, PacketType.Play.Client.POSITION_LOOK) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (event.isCancelled()) { logger.warning("ИВЕНТ НИКОВ ОТМЕНЕН!"); }
                    trySpawnArmorStand(event.getPlayer(), event.getPlayer().getName()+" TEST");
                }
            });*/
            logger.info("Plugin is enabled!");
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.warning("Plugin getting error!");
        }
    }

    private static void startScheduler(Player player) {
        Bukkit.getScheduler().runTaskTimer(NameOnHover.getPlugin(NameOnHover.class), new Runnable() {
            @Override
            public void run() {
                trySpawnArmorStand(player);
            }
        }, 0, 4);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        startScheduler(event.getPlayer());
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            startScheduler(player);
        }
    }

    private static void trySpawnArmorStand(Player p) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        Entity entity = getTargetPlayer(p, 5);
        // Packet with Armor Stand
        PacketContainer packetAS = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_LIVING);
        //Logger logger = Bukkit.getLogger(); // DEBUG
        packetAS.getIntegers().write(0, 500);
        packetAS.getIntegers().write(1, 1);
        packetAS.getUUIDs().write(0, UUID.randomUUID());
        if (entity != null) {
            String name = entity.getName(); // ПЕРЕМЕННАЯ ИМЕНИ
            if (playerEntityHashMap.get(p) != null && playerEntityHashMap.get(p) == entity) { return; }
            playerEntityHashMap.put(p, entity);
            packetAS.getDoubles().write(0, entity.getLocation().getX());
            packetAS.getDoubles().write(1, entity.getLocation().getY() + entity.getHeight()-0.36);
            packetAS.getDoubles().write(2, entity.getLocation().getZ());
            // Mount Packet
            PacketContainer packetMount = manager.createPacket(PacketType.Play.Server.MOUNT);
            packetMount.getIntegers().write(0, entity.getEntityId());

            packetMount.getIntegerArrays().write(0, new int[] {packetAS.getIntegers().read(0)});

            // Packet with Name, Invisible and Size
            PacketContainer packetE = manager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packetE.getIntegers().write(0, packetAS.getIntegers().read(0)); //Set packet's entity id
            WrappedDataWatcher watcher = new WrappedDataWatcher(); //Create data watcher, the Entity Metadata packet requires this
            WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(Byte.class); //Found this through google, needed for some stupid reason
            watcher.setEntity(p); //Set the new data watcher's target
            watcher.setObject(0, serializer, (byte) (0x20)); // SET INVISIBLE
            watcher.setObject(14, serializer, (byte) (0x01)); // SIZE SET SMALL
            // NAME
            Optional<?> opt = Optional
                    .of(WrappedChatComponent
                            .fromChatMessage(name)[0].getHandle());
            // CUSTOM NAME SET
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(2, WrappedDataWatcher.Registry.getChatComponentSerializer(true)), opt);
            // CUSTOM VISIBLE TRUE
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(3, WrappedDataWatcher.Registry.get(Boolean.class)), true);
            packetE.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects()); //Make the packet's datawatcher the one we created

            // Try send packet
            try {
                manager.sendServerPacket(p, packetAS);
                manager.sendServerPacket(p, packetE);
                manager.sendServerPacket(p, packetMount);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }

        } else {
            // Packet Entity Metadata to hide name
            PacketContainer packetE = manager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packetE.getIntegers().write(0, packetAS.getIntegers().read(0)); //Set packet's entity id
            WrappedDataWatcher watcher = new WrappedDataWatcher(); //Create data watcher, the Entity Metadata packet requires this
            //WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(Byte.class); //Found this through google, needed for some stupid reason
            watcher.setEntity(p); //Set the new data watcher's target
            // CUSTOM VISIBLE TRUE
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(3, WrappedDataWatcher.Registry.get(Boolean.class)), false);
            packetE.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects()); //Make the packet's datawatcher the one we created
            playerEntityHashMap.put(p, null);
            try {
                manager.sendServerPacket(p, packetE);
            } catch (InvocationTargetException ignored) {

            }
        }
    }

    public static Player getTargetPlayer(Player player, int range) {
        Vector dir = player.getLocation().getDirection().clone().multiply(0.1);
        Location loc = player.getEyeLocation().clone();
        for (int i = 0; i <= range * 10; i++) {
            loc.add(dir.getX(), dir.getY(), dir.getZ());
            if (!loc.getBlock().getType().equals(Material.AIR)) return null;
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.getUniqueId().equals(player.getUniqueId())) continue;
                if (!player.canSee(target)) continue;
                if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) continue;
                if (target.getGameMode().equals(GameMode.SPECTATOR)) continue;
                if (!target.getBoundingBox().expand(0.2).contains(loc.toVector())) continue;
                return target;
            }
        }
        return null;
    }

    @Override
    public void onDisable() {
        Logger logger = Bukkit.getLogger();
        logger.info("Plugin is disabled!");
    }

}
