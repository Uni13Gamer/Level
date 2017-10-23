package bskyblock.addin.level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;
import org.bukkit.permissions.PermissionAttachmentInfo;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;

import bskyblock.addin.level.config.Settings;
import bskyblock.addin.level.event.IslandPostLevelEvent;
import bskyblock.addin.level.event.IslandPreLevelEvent;
import us.tastybento.bskyblock.BSkyBlock;
import us.tastybento.bskyblock.database.objects.Island;
import us.tastybento.bskyblock.util.Util;

/**
 * A class that calculates the level of an island very quickly by copying island
 * chunks to a list and then processing asynchronously.
 * 
 * @author tastybento
 * 
 */
public class LevelCalcByChunk {
    private static final boolean DEBUG = false;
    protected static final boolean LEVEL_LOGGING = false;
    private List<String> reportLines = new ArrayList<String>();

    public LevelCalcByChunk(Level plugin, BSkyBlock bSkyBlock, UUID targetPlayer, CommandSender asker) {
        this(plugin, bSkyBlock, targetPlayer, asker, false);
    }

    /**
     * Calculates the level of an island
     * @param bSkyBlock
     * @param targetPlayer - UUID of island owner or team member
     * @param sender - requester of the level calculation, if anyone
     * @param report - provide a report to the asker
     */
    public LevelCalcByChunk(final Level plugin, final BSkyBlock bSkyBlock, final UUID targetPlayer, final CommandSender sender, final boolean report) {
        // Get player's island
        final Island island = bSkyBlock.getIslands().getIsland(targetPlayer);
        if (DEBUG)
            plugin.getLogger().info("DEBUG: " + island);
        if (island != null) {
            // Get the permission multiplier if it is available
            Player player = plugin.getServer().getPlayer(targetPlayer);
            int multiplier = 1;
            if (player != null) {
                if (DEBUG)
                    plugin.getLogger().info("DEBUG: player is online");
                // Get permission multiplier                
                for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
                    if (perms.getPermission().startsWith(Settings.PERMPREFIX + "island.multiplier.")) {
                        String spl[] = perms.getPermission().split(Settings.PERMPREFIX + "island.multiplier.");
                        if (spl.length > 1) {
                            if (!NumberUtils.isDigits(spl[1])) {
                                bSkyBlock.getLogger().severe("Player " + player.getName() + " has permission: " + perms.getPermission() + " <-- the last part MUST be a number! Ignoring...");
                            } else {
                                // Get the max value should there be more than one
                                multiplier = Math.max(multiplier, Integer.valueOf(spl[1]));
                            }
                        }
                    }
                    // Do some sanity checking
                    if (multiplier < 1) {
                        multiplier = 1;
                    }
                }
                if (DEBUG)
                    plugin.getLogger().info("DEBUG: multiplier = " + multiplier);
            }
            final int levelMultiplier = multiplier;
            // Get the handicap
            final int levelHandicap = island.getLevelHandicap();
            if (DEBUG)
                plugin.getLogger().info("DEBUG: island level handicap = " + levelHandicap);
            // Get the death handicap
            int deaths = bSkyBlock.getPlayers().getDeaths(targetPlayer);
            if (DEBUG)
                plugin.getLogger().info("DEBUG: deaths = " + deaths);
            if (bSkyBlock.getPlayers().inTeam(targetPlayer)) {
                if (DEBUG)
                    plugin.getLogger().info("DEBUG: player is in a team");
                // Get the team leader's deaths
                deaths = bSkyBlock.getPlayers().getDeaths(bSkyBlock.getIslands().getTeamLeader(targetPlayer));
                if (Settings.sumTeamDeaths) {
                    deaths = 0;
                    //plugin.getLogger().info("DEBUG: player is in team");
                    for (UUID member : bSkyBlock.getIslands().getMembers(targetPlayer)) {
                        deaths += bSkyBlock.getPlayers().getDeaths(member);
                    }
                }
                if (DEBUG)
                    plugin.getLogger().info("DEBUG: deaths is now = " + deaths);
            }
            final int deathHandicap = deaths;
            // Check if player's island world is the nether or overworld and adjust accordingly
            final World world = bSkyBlock.getIslands().getIslandLocation(targetPlayer).getWorld();
            // Get the chunks
            if (DEBUG)
                plugin.getLogger().info("DEBUG: Getting chunks. Protection range = " + island.getProtectionRange());
            //long nano = System.nanoTime();
            Set<ChunkSnapshot> chunkSnapshot = new HashSet<ChunkSnapshot>();
            for (int x = island.getMinProtectedX(); x < (island.getMinProtectedX() + (island.getProtectionRange() *2) + 16); x += 16) {
                for (int z = island.getMinProtectedZ(); z < (island.getMinProtectedZ() + (island.getProtectionRange() * 2) + 16); z += 16) {
                    if (!world.isChunkLoaded((int)((double)x/16), (int)((double)z/16))) {
                        //plugin.getLogger().info("DEBUG: chunk is not loaded");
                        // If the chunk isn't already generated, don't try and generate it
                        if (world.loadChunk((int)((double)x/16), (int)((double)z/16), false)) {
                            //plugin.getLogger().info("DEBUG: chunk loaded");
                            Chunk chunk = world.getChunkAt((int)((double)x/16), (int)((double)z/16));
                            chunkSnapshot.add(chunk.getChunkSnapshot());
                            //plugin.getLogger().info("DEBUG: unload = " + chunk.unload(false));
                        }
                    } else {
                        //plugin.getLogger().info("DEBUG: chunk is loaded");
                        chunkSnapshot.add(world.getBlockAt(x, 0, z).getChunk().getChunkSnapshot());
                    } 
                    if (DEBUG)
                        plugin.getLogger().info("DEBUG: getting chunk at " + x + ", " + z);
                }
            }
            //plugin.getLogger().info("DEBUG: time = " + (System.nanoTime() - nano) / 1000000 + " ms");
            if (DEBUG)
                plugin.getLogger().info("DEBUG: size of chunk snapshot = " + chunkSnapshot.size());
            final Set<ChunkSnapshot> finalChunk = chunkSnapshot;
            final int worldHeight = world.getMaxHeight();
            //plugin.getLogger().info("DEBUG:world height = " +worldHeight);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {

                @Override
                public void run() {
                    // Logging
                    List<MaterialData> mdLog = null;
                    List<MaterialData> uwLog = null;
                    List<MaterialData> noCountLog = null;
                    List<MaterialData> overflowLog = null;
                    if (LEVEL_LOGGING || report) {
                        mdLog = new ArrayList<MaterialData>();
                        uwLog = new ArrayList<MaterialData>();
                        noCountLog = new ArrayList<MaterialData>();
                        overflowLog = new ArrayList<MaterialData>();
                    }
                    // Copy the limits hashmap
                    HashMap<MaterialData, Integer> limitCount = new HashMap<MaterialData, Integer>(Settings.blockLimits);
                    // Calculate the island score
                    long blockCount = 0;
                    long underWaterBlockCount = 0;
                    for (ChunkSnapshot chunk: finalChunk) {
                        for (int x = 0; x< 16; x++) { 
                            // Check if the block coord is inside the protection zone and if not, don't count it
                            if (chunk.getX() * 16 + x < island.getMinProtectedX() || chunk.getX() * 16 + x >= island.getMinProtectedX() + (island.getProtectionRange() * 2)) {
                                if (DEBUG)
                                    plugin.getLogger().info("Block is outside protected area - x = " + (chunk.getX() * 16 + x));
                                continue;
                            }
                            for (int z = 0; z < 16; z++) {
                                // Check if the block coord is inside the protection zone and if not, don't count it
                                if (chunk.getZ() * 16 + z < island.getMinProtectedZ() || chunk.getZ() * 16 + z >= island.getMinProtectedZ() + (island.getProtectionRange() * 2)) {
                                    if (DEBUG)
                                        plugin.getLogger().info("Block is outside protected area - z = " + (chunk.getZ() * 16 + z));
                                    continue;
                                }
                                
                                for (int y = 0; y < worldHeight; y++) {
                                    int type = chunk.getBlockTypeId(x, y, z);
                                    int data = chunk.getBlockData(x, y, z);
                                    MaterialData md = new MaterialData(type,(byte) data);                                    
                                    MaterialData generic = new MaterialData(type);                                    
                                    if (type != 0) { // AIR
                                        if (DEBUG)
                                            plugin.getLogger().info("Block is inside protected area " + (chunk.getX() * 16) + "," + (chunk.getZ() * 16 + z));
                                        if (DEBUG)
                                            plugin.getLogger().info("Block is " + md + "[" + generic +"]");
                                        if (limitCount.containsKey(md) && Settings.blockValues.containsKey(md)) {
                                            int count = limitCount.get(md);
                                            if (DEBUG)
                                                plugin.getLogger().info("DEBUG: Count for non-generic " + md + " is " + count);
                                            if (count > 0) {
                                                limitCount.put(md, --count);
                                                if (Settings.seaHeight > 0 && y<=Settings.seaHeight) {
                                                    underWaterBlockCount += Settings.blockValues.get(md);                                                    
                                                    if (LEVEL_LOGGING || report) {
                                                        uwLog.add(md);
                                                    }
                                                } else {
                                                    blockCount += Settings.blockValues.get(md);
                                                    if (LEVEL_LOGGING || report) {
                                                        mdLog.add(md); 
                                                    }
                                                }
                                            } else if (LEVEL_LOGGING || report) {
                                                overflowLog.add(md);
                                            }
                                        } else if (limitCount.containsKey(generic) && Settings.blockValues.containsKey(generic)) {
                                            int count = limitCount.get(generic);
                                            if (DEBUG)
                                                plugin.getLogger().info("DEBUG: Count for generic " + generic + " is " + count);
                                            if (count > 0) {  
                                                limitCount.put(generic, --count);
                                                if (Settings.seaHeight > 0 && y<=Settings.seaHeight) {
                                                    underWaterBlockCount += Settings.blockValues.get(generic);
                                                    if (LEVEL_LOGGING || report) {
                                                        uwLog.add(md);
                                                    }
                                                } else {
                                                    blockCount += Settings.blockValues.get(generic);
                                                    if (LEVEL_LOGGING || report) {
                                                        mdLog.add(md); 
                                                    }
                                                }
                                            } else if (LEVEL_LOGGING || report) {
                                                overflowLog.add(md);
                                            }
                                        } else if (Settings.blockValues.containsKey(md)) {
                                            if (DEBUG)
                                                plugin.getLogger().info("DEBUG: Adding " + md + " = " + Settings.blockValues.get(md));
                                            if (Settings.seaHeight > 0 && y<=Settings.seaHeight) {
                                                underWaterBlockCount += Settings.blockValues.get(md);
                                                if (LEVEL_LOGGING || report) {
                                                    uwLog.add(md);
                                                }
                                            } else {
                                                blockCount += Settings.blockValues.get(md);
                                                if (LEVEL_LOGGING || report) {
                                                    mdLog.add(md); 
                                                }
                                            }
                                        } else if (Settings.blockValues.containsKey(generic)) {
                                            if (DEBUG)
                                                plugin.getLogger().info("DEBUG: Adding " + generic + " = " + Settings.blockValues.get(generic));
                                            if (Settings.seaHeight > 0 && y<=Settings.seaHeight) {
                                                underWaterBlockCount += Settings.blockValues.get(generic);
                                                if (LEVEL_LOGGING || report) {
                                                    uwLog.add(md);
                                                }
                                            } else {
                                                blockCount += Settings.blockValues.get(generic);
                                                if (LEVEL_LOGGING || report) {
                                                    mdLog.add(md); 
                                                }
                                            }
                                        } else if (LEVEL_LOGGING || report) {
                                            noCountLog.add(md);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    blockCount += (long)((double)underWaterBlockCount * Settings.underWaterMultiplier);
                    if (DEBUG)
                        plugin.getLogger().info("DEBUG: block count = "+blockCount);

                    final long score = (((blockCount * levelMultiplier) - (deathHandicap * Settings.deathpenalty)) / Settings.levelCost) - levelHandicap;
                    // Logging or report
                    if (LEVEL_LOGGING || report) {
                        // provide counts
                        Multiset<MaterialData> uwCount = HashMultiset.create(uwLog);
                        Multiset<MaterialData> mdCount = HashMultiset.create(mdLog);
                        Multiset<MaterialData> ncCount = HashMultiset.create(noCountLog);
                        Multiset<MaterialData> ofCount = HashMultiset.create(overflowLog);
                        reportLines.add("Level Log for island at " + island.getCenter());
                        if (sender instanceof Player) {
                            reportLines.add("Asker is " + sender.getName() + " (" + ((Player)sender).getUniqueId().toString() + ")");
                        } else {
                            reportLines.add("Asker is console");
                        }
                        reportLines.add("Target player UUID = " + targetPlayer.toString());
                        reportLines.add("Total block value count = " + String.format("%,d",blockCount));
                        reportLines.add("Level cost = " + Settings.levelCost);
                        reportLines.add("Level multiplier = " + levelMultiplier + " (Player must be online to get a permission multiplier)");
                        reportLines.add("Schematic level handicap = " + levelHandicap + " (level is reduced by this amount)");
                        reportLines.add("Deaths handicap = " + (deathHandicap * Settings.deathpenalty) + " (" + deathHandicap + " deaths)");
                        reportLines.add("Level calculated = " + score);
                        reportLines.add("==================================");
                        int total = 0;
                        if (!uwCount.isEmpty()) {
                            reportLines.add("Underwater block count (Multiplier = x" + Settings.underWaterMultiplier + ") value");
                            reportLines.add("Total number of underwater blocks = " + String.format("%,d",uwCount.size()));
                            Iterable<Multiset.Entry<MaterialData>> entriesSortedByCount = 
                                    Multisets.copyHighestCountFirst(uwCount).entrySet();
                            Iterator<Entry<MaterialData>> it = entriesSortedByCount.iterator();
                            while (it.hasNext()) {
                                Entry<MaterialData> type = it.next();
                                int value = 0;
                                if (Settings.blockValues.containsKey(type)) {
                                    // Specific
                                    value = Settings.blockValues.get(type);
                                } else if (Settings.blockValues.containsKey(new MaterialData(type.getElement().getItemType()))) {
                                    // Generic
                                    value = Settings.blockValues.get(new MaterialData(type.getElement().getItemType()));
                                }
                                if (value > 0) {
                                    reportLines.add(type.getElement().toString() + ":" 
                                            + String.format("%,d",type.getCount()) + " blocks x " + value + " = " + (value * type.getCount()));
                                    total += (value * type.getCount());
                                }
                            }
                            reportLines.add("Subtotal = " + total);
                            reportLines.add("==================================");
                        }
                        reportLines.add("Regular block count");
                        reportLines.add("Total number of blocks = " + String.format("%,d",mdCount.size()));
                        //Iterable<Multiset.Entry<MaterialData>> entriesSortedByCount = 
                        //        Multisets.copyHighestCountFirst(mdCount).entrySet();
                        Iterable<Multiset.Entry<MaterialData>> entriesSortedByCount = 
                                mdCount.entrySet();
                        Iterator<Entry<MaterialData>> it = entriesSortedByCount.iterator();
                        while (it.hasNext()) {
                            Entry<MaterialData> type = it.next();
                            int value = 0;
                            if (Settings.blockValues.containsKey(type)) {
                                // Specific
                                value = Settings.blockValues.get(type);
                            } else if (Settings.blockValues.containsKey(new MaterialData(type.getElement().getItemType()))) {
                                // Generic
                                value = Settings.blockValues.get(new MaterialData(type.getElement().getItemType()));
                            }
                            if (value > 0) {
                                reportLines.add(type.getElement().toString() + ":" 
                                        + String.format("%,d",type.getCount()) + " blocks x " + value + " = " + (value * type.getCount()));
                                total += (value * type.getCount());
                            }
                        }
                        reportLines.add("Total = " + total);
                        reportLines.add("==================================");
                        reportLines.add("Blocks not counted because they exceeded limits: " + String.format("%,d",ofCount.size()));
                        //entriesSortedByCount = Multisets.copyHighestCountFirst(ofCount).entrySet();
                        entriesSortedByCount = ofCount.entrySet();
                        it = entriesSortedByCount.iterator();
                        while (it.hasNext()) {
                            Entry<MaterialData> type = it.next();
                            Integer limit = Settings.blockLimits.get(type.getElement());
                            String explain = ")";
                            if (limit == null) {
                                MaterialData generic = new MaterialData(type.getElement().getItemType());
                                limit = Settings.blockLimits.get(generic);
                                explain = " - All types)";
                            }
                            reportLines.add(type.getElement().toString() + ": " + String.format("%,d",type.getCount()) + " blocks (max " + limit + explain);
                        }
                        reportLines.add("==================================");
                        reportLines.add("Blocks on island that are not in blockvalues.yml");
                        reportLines.add("Total number = " + String.format("%,d",ncCount.size()));
                        //entriesSortedByCount = Multisets.copyHighestCountFirst(ncCount).entrySet();
                        entriesSortedByCount = ncCount.entrySet();
                        it = entriesSortedByCount.iterator();
                        while (it.hasNext()) {
                            Entry<MaterialData> type = it.next();
                            reportLines.add(type.getElement().toString() + ": " + String.format("%,d",type.getCount()) + " blocks");
                        }                        
                        reportLines.add("=================================");
                    }

                    // Calculate how many points are required to get to the next level
                    long calculatePointsToNextLevel = (Settings.levelCost * (score + 1 + levelHandicap)) - ((blockCount * levelMultiplier) - (deathHandicap * Settings.deathpenalty));
                    // Sometimes it will return 0, so calculate again to make sure it will display a good value
                    if(calculatePointsToNextLevel == 0) calculatePointsToNextLevel = (Settings.levelCost * (score + 2 + levelHandicap)) - ((blockCount * levelMultiplier) - (deathHandicap * Settings.deathpenalty));

                    final long pointsToNextLevel = calculatePointsToNextLevel;

                    // Return to main thread
                    plugin.getServer().getScheduler().runTask(plugin, new Runnable() {

                        @Override
                        public void run() {
                            // Fire the pre-level event
                            Island island = bSkyBlock.getIslands().getIsland(targetPlayer);
                            final IslandPreLevelEvent event = new IslandPreLevelEvent(targetPlayer, island, score);
                            event.setPointsToNextLevel(pointsToNextLevel);
                            plugin.getServer().getPluginManager().callEvent(event);
                            long oldLevel = plugin.getIslandLevel(targetPlayer);
                            if (!event.isCancelled()) {
                                if (DEBUG)
                                    plugin.getLogger().info("DEBUG: updating player");

                                if (oldLevel != event.getLevel()) {
                                    // Update player and team mates
                                    plugin.setIslandLevel(targetPlayer, event.getLevel());
                                    if (DEBUG)
                                        plugin.getLogger().info("DEBUG: set island level, now trying to save player");
                                    bSkyBlock.getPlayers().save(targetPlayer);
                                }
                                if (DEBUG)
                                    plugin.getLogger().info("DEBUG: save player, now looking at team members");
                                // Update any team members too
                                if (bSkyBlock.getPlayers().inTeam(targetPlayer)) {
                                    //plugin.getLogger().info("DEBUG: player is in team");
                                    for (UUID member : bSkyBlock.getIslands().getMembers(targetPlayer)) {
                                        //plugin.getLogger().info("DEBUG: updating team member level too");
                                        if (plugin.getIslandLevel(member) != event.getLevel()) {
                                            plugin.setIslandLevel(member, event.getLevel());
                                            bSkyBlock.getPlayers().save(member);
                                        }
                                    }
                                }
                                if (DEBUG) {
                                    plugin.getLogger().info("DEBUG: finished team member saving");
                                    plugin.getLogger().info("DEBUG: updating top ten");
                                }
                                if (bSkyBlock.getPlayers().inTeam(targetPlayer)) {
                                    UUID leader = bSkyBlock.getIslands().getTeamLeader(targetPlayer);
                                    if (leader != null) {
                                        TopTen.topTenAddEntry(leader, event.getLevel());
                                    }
                                } else {
                                    TopTen.topTenAddEntry(targetPlayer, event.getLevel());
                                }
                            }

                            // Fire the island post level calculation event
                            final IslandPostLevelEvent event3 = new IslandPostLevelEvent(targetPlayer, island, event.getLevel(), event.getPointsToNextLevel());
                            bSkyBlock.getServer().getPluginManager().callEvent(event3);

                            if(!event3.isCancelled()){
                                // Check that sender still is online
                                if (sender != null) {
                                    // Check if console
                                    if (!(sender instanceof Player)) {
                                        // Console  
                                        if (!report) {
                                            Util.sendMessage(sender, ChatColor.GREEN + plugin.getLocale(sender).get("island.islandLevelIs") + " " + ChatColor.WHITE + plugin.getIslandLevel(targetPlayer));
                                        } else {
                                            for (String line: reportLines) {
                                                Util.sendMessage(sender, line);
                                            }
                                            Util.sendMessage(sender, ChatColor.GREEN + plugin.getLocale(sender).get("island.islandLevelIs") + " " + ChatColor.WHITE + plugin.getIslandLevel(targetPlayer));
                                            if (event.getPointsToNextLevel() >= 0) {
                                                String toNextLevel = ChatColor.GREEN + plugin.getLocale(sender).get("island.requiredPointsToNextLevel").replace("[points]", String.valueOf(event.getPointsToNextLevel()));
                                                toNextLevel = toNextLevel.replace("[next]", String.valueOf(plugin.getIslandLevel(targetPlayer) + 1));
                                                Util.sendMessage(sender, toNextLevel);
                                            }
                                        }
                                    } else {
                                        // Player
                                        if (!report) {
                                            // Tell offline team members the island level changed
                                            if (plugin.getIslandLevel(targetPlayer) != oldLevel) {
                                                //plugin.getLogger().info("DEBUG: telling offline players");
                                                //bSkyBlock.getMessages().tellOfflineTeam(targetPlayer, ChatColor.GREEN + bSkyBlock.myLocale(targetPlayer).islandislandLevelis + " " + ChatColor.WHITE
                                                //        + plugin.getIslandLevel(targetPlayer));
                                            }
                                            if (sender instanceof Player && ((Player)sender).isOnline()) {
                                                String message = ChatColor.GREEN + plugin.getLocale(sender).get("island.islandLevelIs") + " " + ChatColor.WHITE + plugin.getIslandLevel(targetPlayer);
                                                if (Settings.deathpenalty != 0) {
                                                    message += " " + plugin.getLocale(sender).get("levelDeaths").replace("[number]", String.valueOf(deathHandicap));
                                                }
                                                Util.sendMessage(sender, message);
                                                //Send player how many points are required to reach next island level
                                                if (event.getPointsToNextLevel() >= 0) {
                                                    String toNextLevel = ChatColor.GREEN + plugin.getLocale(sender).get("island.requiredPointsToNextLevel").replace("[points]", String.valueOf(event.getPointsToNextLevel()));
                                                    toNextLevel = toNextLevel.replace("[next]", String.valueOf(plugin.getIslandLevel(targetPlayer) + 1));
                                                    Util.sendMessage(sender, toNextLevel);
                                                }
                                            }
                                        } else {
                                            if (((Player)sender).isOnline()) {
                                                for (String line: reportLines) {
                                                    Util.sendMessage(sender, line);
                                                }
                                            }
                                            Util.sendMessage(sender, ChatColor.GREEN + plugin.getLocale(sender).get("island.islandLevelIs") + " " + ChatColor.WHITE + plugin.getIslandLevel(targetPlayer));
                                            if (event.getPointsToNextLevel() >= 0) {
                                                String toNextLevel = ChatColor.GREEN + plugin.getLocale(sender).get("island.requiredPointsToNextLevel").replace("[points]", String.valueOf(event.getPointsToNextLevel()));
                                                toNextLevel = toNextLevel.replace("[next]", String.valueOf(plugin.getIslandLevel(targetPlayer) + 1));
                                                Util.sendMessage(sender, toNextLevel);
                                            }
                                        }
                                    }
                                }
                            }
                        }});
                }});
        }
    }

}