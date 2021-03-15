package ru.bruhabruh.nameonhover;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
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
            logger.info("Plugin is enabled!");
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.warning("Plugin getting error!");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        trySpawnArmorStand(event.getPlayer(), "MOVE");
    }

    private static void trySpawnArmorStand(Player p, String name) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        Entity entity = getTargetEntity(p);
        // Packet with Armor Stand
        PacketContainer packetAS = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_LIVING);

        packetAS.getIntegers().write(0, 500);
        packetAS.getIntegers().write(1, 1);
        packetAS.getUUIDs().write(0, UUID.randomUUID());
        if (entity != null && entity.getType().equals(EntityType.PLAYER)
                && p.getLocation().distance(entity.getLocation()) <= 5) {

            if (playerEntityHashMap.get(p) != null && playerEntityHashMap.get(p) == entity) { return; }
            playerEntityHashMap.put(p, entity);

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
                manager.sendServerPacket(p, packetMount);
                manager.sendServerPacket(p, packetE);
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
            } catch (InvocationTargetException e) {

            }
        }
    }

    public static Entity getTargetEntity(final Entity entity) {
        return getTarget(entity, entity.getWorld().getEntities());
    }

    public static <T extends Entity> T getTarget(final Entity entity,
                                                 final Iterable<T> entities) {
        if (entity == null)
            return null;
        T target = null;
        final double threshold = 1;
        for (final T other : entities) {
            final Vector n = other.getLocation().toVector()
                    .subtract(entity.getLocation().toVector());
            if (entity.getLocation().getDirection().normalize().crossProduct(n)
                    .lengthSquared() < threshold
                    && n.normalize().dot(
                    entity.getLocation().getDirection().normalize()) >= 0) {
                if (target == null
                        || target.getLocation().distanceSquared(
                        entity.getLocation()) > other.getLocation()
                        .distanceSquared(entity.getLocation()))
                    target = other;
            }
        }
        return target;
    }

    @Override
    public void onDisable() {
        Logger logger = Bukkit.getLogger();
        logger.info("Plugin is disabled!");
    }

}
