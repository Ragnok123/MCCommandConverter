execute as @e[tag=laser_cannon] at @s if block ~0 ~0 ~-1 minecraft:redstone_block positioned ~ ~3 ~ run scoreboard players add @e[tag=laser_fin,tag=north,tag=!locked,distance=..3] laser_fin_charge 2
execute as @e[tag=laser_cannon] at @s if block ~-1 ~0 ~0 minecraft:redstone_block positioned ~ ~3 ~ run scoreboard players add @e[tag=laser_fin,tag=west,tag=!locked,distance=..3] laser_fin_charge 2
execute as @e[tag=laser_cannon] at @s if block ~1 ~0 ~0 minecraft:redstone_block positioned ~ ~3 ~ run scoreboard players add @e[tag=laser_fin,tag=east,tag=!locked,distance=..3] laser_fin_charge 2
execute as @e[tag=laser_cannon] at @s if block ~0 ~0 ~1 minecraft:redstone_block positioned ~ ~3 ~ run scoreboard players add @e[tag=laser_fin,tag=south,tag=!locked,distance=..3] laser_fin_charge 2
execute as @e[tag=laser_fin,scores={laser_fin_charge=1}] at @s run rotate @s ~-180 ~
scoreboard players remove @e[tag=laser_fin,scores={laser_fin_charge=1..}] laser_fin_charge 1
scoreboard players set @e[tag=laser_fin,scores={laser_fin_charge=43..}] laser_fin_charge 43
execute as @e[tag=laser_fin,scores={laser_fin_charge=1..40}] at @s run rotate @s ~1.125 ~
scoreboard players add @e[tag=laser_bolt] laser_bolt_age 1
execute as @e[tag=laser_bolt] at @s run tp @s ~ ~3 ~
kill @e[tag=laser_bolt,scores={laser_bolt_age=90..}]
execute as @e[tag=laser_cannon] at @s unless score @s laser_cannon_fire_state matches 1.. positioned ~ ~3 ~ if entity @e[tag=laser_fin,tag=north,scores={laser_fin_charge=43},distance=..2] positioned ~ ~-3 ~ positioned ~ ~3 ~ if entity @e[tag=laser_fin,tag=west,scores={laser_fin_charge=43},distance=..2] positioned ~ ~-3 ~ positioned ~ ~3 ~ if entity @e[tag=laser_fin,tag=east,scores={laser_fin_charge=43},distance=..2] positioned ~ ~-3 ~ positioned ~ ~3 ~ if entity @e[tag=laser_fin,tag=south,scores={laser_fin_charge=43},distance=..2] positioned ~ ~-3 ~ run function laser:gen/f0
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=5,laser_cannon_fire_t=60..}] at @s run function laser:gen/f1
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=4,laser_cannon_fire_t=10..}] at @s run function laser:gen/f2
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=3,laser_cannon_fire_t=130..}] at @s run function laser:gen/f3
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=2,laser_cannon_fire_t=110..}] at @s run function laser:gen/f4
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=1,laser_cannon_fire_t=60..}] at @s run function laser:gen/f5
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=1,laser_cannon_fire_t=0}] at @s run function laser:gen/f6
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=1}] at @s run particle large_smoke ~ ~ ~ 0.1 0.1 0.1 0.1 9 force
scoreboard players add @e[tag=laser_cannon,scores={laser_cannon_fire_state=1}] laser_cannon_fire_t 1
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=2}] at @s run function laser:gen/f7
scoreboard players add @e[tag=laser_cannon,scores={laser_cannon_fire_state=2}] laser_cannon_fire_t 1
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=3,laser_cannon_fire_t=0}] at @s run function laser:gen/f8
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=3}] at @s run function laser:gen/f9
scoreboard players add @e[tag=laser_cannon,scores={laser_cannon_fire_state=3}] laser_cannon_fire_t 1
scoreboard players add @e[tag=laser_cannon,scores={laser_cannon_fire_state=4}] laser_cannon_fire_t 1
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=5,laser_cannon_fire_t=0}] at @s as @e[tag=laser_target,sort=nearest,limit=1] at @s run kill @s
scoreboard players add @e[tag=laser_cannon,scores={laser_cannon_fire_state=5}] laser_cannon_fire_t 1
execute as @e[tag=laser_cannon,scores={laser_cannon_fire_state=6,laser_cannon_fire_t=0}] at @s run function laser:gen/f10
