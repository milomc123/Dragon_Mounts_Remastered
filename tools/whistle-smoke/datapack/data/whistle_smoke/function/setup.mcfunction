execute unless entity @a[name=WhistleOwner] run return fail
execute as @a[name=WhistleOwner] run dmr clear_whistle white
execute as @a[name=WhistleObserver] run dmr clear_whistle white
execute in minecraft:overworld run kill @e[tag=whistle_smoke]
execute in minecraft:the_nether run kill @e[tag=whistle_smoke]
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set day
execute in minecraft:overworld run fill -20 80 -20 20 80 20 minecraft:stone
execute in minecraft:overworld run fill 1004 80 -20 1044 80 20 minecraft:stone
execute in minecraft:the_nether run fill -20 130 -20 20 130 20 minecraft:stone
execute in minecraft:the_nether run forceload add 0 0
execute in minecraft:overworld run tp WhistleOwner 0 81 0 0 0
execute in minecraft:overworld run tp WhistleObserver 1024 81 0 0 0
give WhistleOwner dmr:dragon_whistle.white
give WhistleOwner minecraft:chest
give WhistleObserver dmr:dragon_whistle.white
execute in minecraft:overworld run summon dmr:dragon 5 81 5 {Age:0,breed:"fire",breedIsSet:1b,NoAI:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b,OrderedToSit:1b,Tags:["whistle_smoke","whistle_smoke_bound"],CustomName:'{"text":"Whistle Bound"}',Items:[{Slot:2b,id:"minecraft:chest",count:1},{Slot:3b,id:"minecraft:diamond",count:5}]}
execute in minecraft:overworld as @a[name=WhistleOwner] run data modify entity @e[tag=whistle_smoke_bound,limit=1] Owner set from entity @s UUID
execute in minecraft:overworld run summon dmr:dragon -8 81 5 {Age:0,breed:"fire",breedIsSet:1b,NoAI:1b,NoGravity:1b,Invulnerable:1b,PersistenceRequired:1b,OrderedToSit:1b,Tags:["whistle_smoke","whistle_smoke_unbound"],CustomName:'{"text":"Whistle Unbound"}',Items:[{Slot:2b,id:"minecraft:chest",count:1},{Slot:3b,id:"minecraft:diamond",count:5}]}
execute in minecraft:overworld as @a[name=WhistleOwner] run data modify entity @e[tag=whistle_smoke_unbound,limit=1] Owner set from entity @s UUID
tellraw WhistleOwner {"text":"Bind the WHITE whistle to Whistle Bound with right-click. Leave Whistle Unbound unbound. Inventory contains 5 diamonds. Use /function whistle_smoke:tracking_near when ready.","color":"gold"}
