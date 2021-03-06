# addon-level
Add-on for BentoBox to calculate island levels for BSkyBlock and AcidIsland. This add-on will work
for both game modes.

## How to use

1. Place the level addon jar in the addons folder of the BentoBox plugin
2. Restart the server
3. The addon will create a data folder and inside the folder will be a config.yml
4. Edit the config.yml how you want. The config specifies how much blocks are worth (see below)
5. Restart the server if you make a change

## Config.yml

The config.yml has the following sections:

* Command hook-in configuration
* General settings
* Limits
* Block values
* Per-world block values

### Command hook-in configuration

This section allows you to tell the add-on which commands it should hook into. Do not edit this unless
you are changing the game mode command names.

### General Settings

This section defines a number of overall settings for the add-on.

* Underwater block multiplier - default value = 1. If this value is > 1 then blocks below sea level will have a greater value.
* Level cost - Value of one island level. Default 100. Minimum value is 1.
* Level wait - Cooldown between level requests in seconds
* Death penalty - How many block values a player will lose per death. Default value of 100 means that for every death, the player will lose 1 level (if levelcost is 100)
* Sum Team Deaths - if true, all the team member deaths are summed. If false, only the leader's deaths counts.
* Max deaths - If player dies more than this, it doesn't count anymore.
* Reset deaths on island reset
* Reset deaths on team join

### Limits
This section lists the limits for any particular block. Blocks over this amount are not counted. This limit applies to all game modes and is not world-specific.
Format is MATERIAL: value 

### Block values
This section lists the value of a block in all game modes (worlds). To specific world-specific values, use the next section. Value must be an integer. Any blocks not listed will have a value of 0. AIR is always zero.
Format is MATERIAL: value. 

### World-specific block values
List any blocks that have a different value in a specific world. If a block is not listed, the default value will be used from the blocks section.
Prefix with world name. The values will apply to the associated nether and the end if they exist. Example:

```
worlds:
  AcidIsland_world:
    SAND: 0
    SANDSTONE: 0
    ICE: 0
```

In this example, AcidIsland will use the same values as BSkyBlock for all blocks except for sand, sandstone and ice.
